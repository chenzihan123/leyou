package com.leyou.upload.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

@Configuration//声明java配置类，跨域问题
public class LeyouCorsFilterConfiguration {

    @Bean
    public CorsFilter corsFilter(){
        CorsConfiguration corsConfiguration = new CorsConfiguration();//初始化配置对象
        corsConfiguration.addAllowedOrigin("http://manage.leyou.com");//允许跨域的域名
        corsConfiguration.setAllowCredentials(true);//允许携带cookie
        corsConfiguration.addAllowedMethod("*");//允许跨域的方法
        corsConfiguration.addAllowedHeader("*");//允许携带头信息

        UrlBasedCorsConfigurationSource urlBasedCorsConfigurationSource = new UrlBasedCorsConfigurationSource();//初始化配置源对象
        urlBasedCorsConfigurationSource.registerCorsConfiguration("/**",corsConfiguration);//拦截所有请求，校验是否允许跨域

        return new CorsFilter(urlBasedCorsConfigurationSource);
    }
}
