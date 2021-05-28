package com.leyou.search.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.leyou.common.pojo.PageResult;
import com.leyou.item.pojo.*;
import com.leyou.search.GoodsRepository;
import com.leyou.search.client.BrandClient;
import com.leyou.search.client.CategoryClient;
import com.leyou.search.client.GoodsClient;
import com.leyou.search.client.SpecificationClient;
import com.leyou.search.pojo.Goods;
import com.leyou.search.pojo.SearchRequest;
import com.leyou.search.pojo.SearchResult;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.math.NumberUtils;
import org.elasticsearch.index.query.*;
import org.elasticsearch.search.aggregations.Aggregation;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.elasticsearch.search.aggregations.bucket.terms.LongTerms;
import org.elasticsearch.search.aggregations.bucket.terms.StringTerms;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.core.aggregation.AggregatedPage;
import org.springframework.data.elasticsearch.core.query.FetchSourceFilter;
import org.springframework.data.elasticsearch.core.query.NativeSearchQueryBuilder;
import org.springframework.data.elasticsearch.core.query.SourceFilter;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.yaml.snakeyaml.events.Event;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class SearchService {
    @Autowired
    private BrandClient brandClient;
    @Autowired
    private CategoryClient categoryClient;
    @Autowired
    private GoodsClient goodsClient;
    @Autowired
    private SpecificationClient specificationClient;
    @Autowired
    private GoodsRepository goodsRepository;
    private static final ObjectMapper MAPPER = new ObjectMapper();//用于json的序列化和反序列化

    public Goods bulidGoods(Spu spu) throws IOException {
        Goods goods = new Goods();
        //根据品牌id查询品牌
        Brand brand = this.brandClient.queryBrandById(spu.getBrandId());
        //根据cid1、cid2、cid3查询对应的分类名称
        List<String> names = this.categoryClient.queryNamesByIds(Arrays.asList(spu.getCid1(), spu.getCid2(), spu.getCid3()));
        //根据skuId查询所有的sku
        List<Sku> skus = this.goodsClient.querySkusBySpuId(spu.getId());
        //初始化价格集合
        List<Long> prices = new ArrayList<>();
        //初始化skuMapList，每一个map相当于一个sku，map中的key的取值：id，title，image，price
        List<Map<String,Object>> skuMapList = new ArrayList<>();
        skus.forEach(sku -> {
            prices.add(sku.getPrice());
            Map<String,Object> skuMap = new HashMap<>();
            skuMap.put("id",sku.getId());
            skuMap.put("title",sku.getTitle());
            skuMap.put("image",StringUtils.isBlank(sku.getImages()) ? "" : StringUtils.split(sku.getImages(),",")[0]);
            skuMap.put("price",sku.getPrice());
            skuMapList.add(skuMap);
        });
        //查询所有spuDetail，目的拿到genericSpec SpecialSpec
        SpuDetail spuDetail = this.goodsClient.querySpuDetailBySpuId(spu.getId());
        //进行反序列化成一个map
        //获取通用的规格参数
        Map<Long, Object> genericSpecMap = MAPPER.readValue(spuDetail.getGenericSpec(), new TypeReference<Map<Long, Object>>(){});
        //获取特殊的规格参数
        Map<Long, List<Object>> specialSpecMap = MAPPER.readValue(spuDetail.getSpecialSpec(), new TypeReference<Map<Long, List<Object>>>(){});

        //查询所有搜索的规格参数
        List<SpecParam> params = this.specificationClient.queryParams(null, spu.getCid3(), null, true);

        HashMap<String, Object> specs = new HashMap<>();
        params.forEach(param -> {
            //判断是否为通用规格参数
            if (param.getGeneric()){
                //通用的规格参数
                String value = genericSpecMap.get(param.getId()).toString();
                //判断是否是数值类型
                if (param.getNumeric()){
                    // 如果是数值的话，判断该数值落在那个区间
                    value = chooseSegment(value,param);
                }
                // 把参数名和值放入结果集中
                specs.put(param.getName(),value);
            }else {
                //特殊的规格参数
                List<Object> value = specialSpecMap.get(param.getId());
                specs.put(param.getName(),value);
            }
        });

        //把简单的字段的值复制为goods对象，id，subtitle，brandId，cid，createTime
        BeanUtils.copyProperties(spu,goods);
        goods.setAll(spu.getTitle() + " " + brand.getName() + " " + StringUtils.join(names," "));
        goods.setPrice(prices);
        goods.setSkus(MAPPER.writeValueAsString(skuMapList));//将一个集合序列化成一个json字符串
        goods.setSpecs(specs);
        return goods;
    }

    private String chooseSegment(String value, SpecParam p) {
        double val = NumberUtils.toDouble(value);
        String result = "其它";
        // 保存数值段
        for (String segment : p.getSegments().split(",")) {
            String[] segs = segment.split("-");
            // 获取数值范围
            double begin = NumberUtils.toDouble(segs[0]);
            double end = Double.MAX_VALUE;
            if(segs.length == 2){
                end = NumberUtils.toDouble(segs[1]);
            }
            // 判断是否在范围内
            if(val >= begin && val < end){
                if(segs.length == 1){
                    result = segs[0] + p.getUnit() + "以上";
                }else if(begin == 0){
                    result = segs[1] + p.getUnit() + "以下";
                }else{
                    result = segment + p.getUnit();
                }
                break;
            }
        }
        return result;
    }

    public SearchResult search(SearchRequest searchRequest) {
        //判断查询条件是否为空
        if (StringUtils.isBlank(searchRequest.getKey())){
            return null;
        }
        //自定义查询构建器
        NativeSearchQueryBuilder queryBuilder = new NativeSearchQueryBuilder();
        //添加查询条件
//        MatchQueryBuilder basicQuery = QueryBuilders.matchQuery("all", searchRequest.getKey()).operator(Operator.AND);//基本查询条件
        BoolQueryBuilder basicQuery = bulidBasicQuery(searchRequest);
        queryBuilder.withQuery(basicQuery);
        //添加分页，行号从0开始
        queryBuilder.withPageable(PageRequest.of(searchRequest.getPage() - 1,searchRequest.getSize()));
        //添加结果集过滤：id subTitle skus
        queryBuilder.withSourceFilter(new FetchSourceFilter(new String[]{"id","subTitle","skus"},null));
        //添加聚合
        String brandAggName = "brands";
        String categoryAggName = "categories";
        queryBuilder.addAggregation(AggregationBuilders.terms(brandAggName).field("brandId"));
        queryBuilder.addAggregation(AggregationBuilders.terms(categoryAggName).field("cid3"));
        //执行查询获取结果集
        AggregatedPage<Goods> goodsPage = (AggregatedPage<Goods>)this.goodsRepository.search(queryBuilder.build());
//        goodsPage.forEach(goods -> System.out.println(goods));
        //解析聚合结果集
        List<Brand> brands = getBrandAggResult(goodsPage.getAggregation(brandAggName));
        List<Map<String,Object>> categories = getCategoryAggResult(goodsPage.getAggregation(categoryAggName));
        //聚合规格参数
        List<Map<String,Object>> specs = null;
        if (!CollectionUtils.isEmpty(categories) && categories.size() == 1){
            specs = getParamAggName((Long) categories.get(0).get("id"),basicQuery);
        }
        //返回分页结果集
        return new SearchResult(goodsPage.getTotalElements(),goodsPage.getTotalPages(),goodsPage.getContent(),categories,brands,specs);
    }

    private BoolQueryBuilder bulidBasicQuery(SearchRequest searchRequest) {
        //初始化bool查询
        BoolQueryBuilder boolQueryBuilder = QueryBuilders.boolQuery();
        //添加基本查询条件  小木手机  and分为两个过滤条件
        boolQueryBuilder.must(QueryBuilders.matchQuery("all",searchRequest.getKey()).operator(Operator.AND));
        //添加过滤
        for (Map.Entry<String, String> entry : searchRequest.getFilter().entrySet()) {
            String key = entry.getKey();
            if (StringUtils.equals(key,"品牌")){
                key = "brandId";
            }else if (StringUtils.equals(key,"分类")){
                key = "cid3";
            }else {
                key = "specs." + key + ".keyword";
            }
            boolQueryBuilder.filter(QueryBuilders.termQuery(key,entry.getValue()));
        }
        return boolQueryBuilder;
    }

    /**
     * 规格参数的聚合
     * @param id
     * @param basicQuery
     * @return
     */
    private List<Map<String, Object>> getParamAggName(Long id, QueryBuilder basicQuery) {
        //查询需要聚合的规格参数
        List<SpecParam> params = this.specificationClient.queryParams(null, id, null, true);
        //添加聚合，需要自定义查询构建器
        NativeSearchQueryBuilder queryBuilder = new NativeSearchQueryBuilder();
        //添加基本查询条件
        queryBuilder.withQuery(basicQuery);
        //添加规格参数的聚合
        params.forEach(param -> {
            queryBuilder.addAggregation(AggregationBuilders.terms(param.getName()).field("specs."+ param.getName() + ".keyword"));
        });
        //添加结果集过滤
        queryBuilder.withSourceFilter(new FetchSourceFilter(new String[]{},null));
        //执行查询
        AggregatedPage<Goods> goodsPage = (AggregatedPage<Goods>)this.goodsRepository.search(queryBuilder.build());
        //解析聚合结果集
        List<Map<String, Object>> paramMapList = new ArrayList<>();//初始化聚合结果集
        //获取所有的规格参数聚合结果集Map<paramName, aggregation>
        Map<String, Aggregation> paramAggregationMap = goodsPage.getAggregations().asMap();
        //遍历规格参数聚合结果集
        for (Map.Entry<String, Aggregation> entry : paramAggregationMap.entrySet()) {
            //每一个规格参数的聚合结果集，对应一个map
            Map<String, Object> map = new HashMap<>();
            //设置k字段
            map.put("k",entry.getKey());
            //解析每一个聚合内的桶
            StringTerms terms = (StringTerms)entry.getValue();
            //处理桶内集合
            List<Object> options = terms.getBuckets().stream().map(bucket -> bucket.getKeyAsString()).collect(Collectors.toList());
            //设置options字段
            map.put("options",options);
            paramMapList.add(map);
        }
        return paramMapList;
    }

    /**
     * 解析分类的结果集
     * @param aggregation
     * @return
     */
    private List<Map<String, Object>> getCategoryAggResult(Aggregation aggregation) {
        //强转LongTerms
        LongTerms terms = (LongTerms) aggregation;
        //获取桶集合
        return terms.getBuckets().stream().map(bucket -> {
            Map<String, Object> map = new HashMap<>();
            long id = bucket.getKeyAsNumber().longValue();
            List<String> names = this.categoryClient.queryNamesByIds(Arrays.asList(id));
            map.put("id",id);//获取桶内的id
            map.put("name",names.get(0));
            return map;
        }).collect(Collectors.toList());
    }

    /**
     * 解析品牌的结果集
     * @param aggregation
     * @return
     */
    private List<Brand> getBrandAggResult(Aggregation aggregation) {
        //强转成LongTerms
        LongTerms terms = (LongTerms) aggregation;
        //获取桶的集合
        return terms.getBuckets().stream().map(bucket -> {
            Long id = bucket.getKeyAsNumber().longValue();
            return this.brandClient.queryBrandById(id);
        }).collect(Collectors.toList());
    }

    /**
     * 创建索引库
     * @param spuId
     */
    public void saveIndex(Long spuId) throws IOException {
        Spu spu = this.goodsClient.querySpuById(spuId);
        Goods goods = this.bulidGoods(spu);
        this.goodsRepository.save(goods);
    }

    /**
     * 删除索引
     * @param spuId
     */
    public void deleteIndex(Long spuId) {
        this.goodsRepository.deleteById(spuId);
    }
}
