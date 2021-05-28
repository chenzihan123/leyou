package com.leyou.item.service;

import com.leyou.item.mapper.CategoryMapper;
import com.leyou.item.pojo.Category;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class CategoryService {
    @Autowired
    private CategoryMapper categoryMapper;

    /**
     * 根据夫类目的id查询所有子节点
     * @param pid
     * @return
     */
    public List<Category> queryCategoriesByPid(Long pid) {
        Category category = new Category();
        category.setParentId(pid);
        return this.categoryMapper.select(category);
    }

    /**
     * 根据品牌id查询商品分类
     * @param bid
     * @return
     */
    public List<Category> queryByBrandId(Long bid) {
        return this.categoryMapper.queryByBrandId(bid);
    }

    /**
     * 根据多个分类id查询id分类名称
     * @param ids
     * @return
     */
    public List<String> queryNameByIds(List<Long> ids){
        List<Category> categories = this.categoryMapper.selectByIdList(ids);
        //把一个List<Category>转化成List<String>
        return categories.stream().map(category -> category.getName()).collect(Collectors.toList());
    }
}
