package com.leyou.search.client;

import com.leyou.common.pojo.PageResult;
import com.leyou.item.bo.SpuBo;
import com.leyou.item.pojo.Spu;
import com.leyou.search.GoodsRepository;
import com.leyou.search.pojo.Goods;
import com.leyou.search.service.SearchService;
import org.elasticsearch.index.query.MatchQueryBuilder;
import org.elasticsearch.index.query.Operator;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.aggregations.AggregationBuilders;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.core.ElasticsearchTemplate;
import org.springframework.data.elasticsearch.core.aggregation.AggregatedPage;
import org.springframework.data.elasticsearch.core.query.FetchSourceFilter;
import org.springframework.data.elasticsearch.core.query.NativeSearchQueryBuilder;
import org.springframework.test.context.junit4.SpringRunner;

import java.io.IOException;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@SpringBootTest
@RunWith(SpringRunner.class)
public class SearchTest {
    @Autowired
    private ElasticsearchTemplate template;
    @Autowired
    private GoodsRepository goodsRepository;
    @Autowired
    private GoodsClient goodsClient;
    @Autowired
    private SearchService searchService;

    @Test
    public void testBulidGoods(){
        this.template.createIndex(Goods.class);//创建索引库
        this.template.putMapping(Goods.class);//创建映射
        //分页导入数据
        Integer page = 1;
        Integer rows = 100;
        do {
            //分批查询spu
            PageResult<SpuBo> spuBoPageResult = this.goodsClient.querySpuByPage(null, true, page, rows);//分页对象
            List<SpuBo> items = spuBoPageResult.getItems();
            //list<spuBo> ==> List<Goods>
            // 遍历spubo集合转化为List<Goods>
            List<Goods> goodsList = items.stream().map(spuBo -> {
                try {
                    System.out.println(spuBo);
                    return this.searchService.bulidGoods((Spu) spuBo);
                } catch (IOException e) {
                    e.printStackTrace();
                }
                return null;
            }).collect(Collectors.toList());
            this.goodsRepository.saveAll(goodsList);
            // 获取当前页的数据条数，如果是最后一页，没有100条
            rows = items.size();
            // 每次循环页码加1
            page++;
        } while (rows == 100);
    }

    @Test
    public void testSerachGoods(){
        //自定义查询构建器
        NativeSearchQueryBuilder queryBuilder = new NativeSearchQueryBuilder();
        //添加查询条件
        MatchQueryBuilder basicQuery = QueryBuilders.matchQuery("all", "手机").operator(Operator.AND);//基本查询条件
        queryBuilder.withQuery(basicQuery);
        //添加分页，行号从0开始
        queryBuilder.withPageable(PageRequest.of(0,20));
        //添加结果集过滤：id subTitle skus
        queryBuilder.withSourceFilter(new FetchSourceFilter(new String[]{"id","subTitle","skus"},null));
        //添加聚合
        String brandAggName = "brands";
        String categoryAggName = "categories";
        queryBuilder.addAggregation(AggregationBuilders.terms(brandAggName).field("brandId"));
        queryBuilder.addAggregation(AggregationBuilders.terms(categoryAggName).field("cid3"));
        //执行查询获取结果集
        AggregatedPage<Goods> goodsPage = (AggregatedPage<Goods>)this.goodsRepository.search(queryBuilder.build());
        System.out.println("TotalPages为： " + goodsPage.getTotalPages());
        List<Goods> goods = goodsPage.getContent();
        goods.forEach(good -> System.out.println(good));
    }

}
