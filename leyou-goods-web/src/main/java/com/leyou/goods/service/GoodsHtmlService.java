package com.leyou.goods.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.PrintWriter;
import java.util.Map;


@Service
public class GoodsHtmlService {
    @Autowired
    private GoodsService goodsService;
    @Autowired
    private TemplateEngine templateEngine;

    private static final Logger LOGGER = LoggerFactory.getLogger(GoodsHtmlService.class);

    /**
     * 创建html页面
     * @param spuId
     */
    public void createHtml(Long spuId){
        PrintWriter printWriter = null;
        //获取页面数据
        Map<String, Object> spuMap = this.goodsService.loadData(spuId);
        //创建templeaf上下文对象
        Context context = new Context();
        //将数据放入上下文对象
        context.setVariables(spuMap);
        try {
            //创建输入流
            File file = new File("D:\\Java\\nginx-1.18.0\\html\\item\\" + spuId + ".html");
            printWriter = new PrintWriter(file);
            //执行页面静态方法
            templateEngine.process("item",context,printWriter);
        } catch (Exception e) {
           LOGGER.error("页面静态化出现错误：{}" + e , spuId);
        } finally {
            if (printWriter != null){
                printWriter.close();
            }
        }

    }

    /**
     * 删除html页面
     * @param spuId
     */
    public void deleteHtml(Long spuId) {
        File file = new File("D:\\Java\\nginx-1.18.0\\html\\item\\" + spuId + ".html");
        file.deleteOnExit();
    }
}
