package com.leyou.search.client;

import com.leyou.item.api.CategoryApi;
import org.springframework.cloud.openfeign.FeignClient;

@FeignClient("item-service")//服务间远程调用
public interface CategoryClient extends CategoryApi {

}
