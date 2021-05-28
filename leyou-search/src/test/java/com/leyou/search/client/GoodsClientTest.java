package com.leyou.search.client;

import com.leyou.LeyouSearchApplication;
import com.leyou.common.pojo.PageResult;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;
import com.leyou.item.bo.SpuBo;

import static org.junit.Assert.*;

@SpringBootTest(classes = LeyouSearchApplication.class)
@RunWith(SpringRunner.class)
public class GoodsClientTest {
    @Autowired
    private GoodsClient goodsClient;

    @Test
    public void testSpu() {
        PageResult<SpuBo> spuBoPageResult = this.goodsClient.querySpuByPage(null, true, 1, 5);
        spuBoPageResult.getItems().forEach(spuBo -> System.out.println(spuBo.getTitle()));
    }
}