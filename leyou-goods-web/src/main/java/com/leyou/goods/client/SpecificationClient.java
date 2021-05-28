package com.leyou.goods.client;

import com.leyou.item.api.SpecificationApi;
import org.springframework.cloud.openfeign.FeignClient;

@FeignClient("item-service")//服务间远程调用
public interface SpecificationClient extends SpecificationApi {
}
