package com.corgi;

import com.alibaba.dubbo.config.spring.context.annotation.EnableDubboConfig;
import com.corgi.common.filter.CorgiCorsFilter;
import com.corgi.common.filter.RequestFilter;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.core.annotation.Order;
import org.springframework.scheduling.annotation.EnableAsync;

import javax.servlet.Filter;


@SpringBootApplication
public class CorgiApplication {

    public static void main(String[] args) {
        SpringApplication.run(CorgiApplication.class, args);
    }

    @Bean
    @Order(1)
    public Filter getCorsFilter() {
        return new CorgiCorsFilter("*", 3600L);
    }

    @Bean
    @Order(2)
    public Filter getRequestFilter() {
        return new RequestFilter();
    }
}
