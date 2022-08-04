package com.corgi;

import com.corgi.common.CorgiQueueName;
import com.corgi.common.config.CorgiSocketSpringConfigurator;
import com.corgi.common.filter.CorgiCorsFilter;
import com.corgi.common.filter.RequestFilter;
import com.corgi.common.wxpay.sdk.WXPayConfig;
import com.corgi.common.wxpay.sdk.WXPayConstants;
import org.apache.dubbo.config.spring.context.annotation.EnableDubboConfig;
import org.springframework.amqp.core.Queue;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.core.annotation.Order;
import org.springframework.web.socket.server.standard.ServerEndpointExporter;

import javax.servlet.Filter;


@SpringBootApplication
@EnableDubboConfig
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

    /**
     * 注入一个ServerEndpointExporter,该Bean会自动注册使用@ServerEndpoint注解申明的websocket endpoint
     */
    @Bean
    public ServerEndpointExporter serverEndpointExporter() {
        return new ServerEndpointExporter();
    }

    @Bean
    public CorgiSocketSpringConfigurator corgiSocketSpringConfigurator() {
        return new CorgiSocketSpringConfigurator();
    }

    @Bean
    public Queue pushMessageQueue() {
        return new Queue(CorgiQueueName.PUSH_MESSAGE_QUEUE);
    }

    @Bean
    public Queue traceQueue() {
        return new Queue(CorgiQueueName.TRACE_FOLLOW_QUEUE);
    }

    @Bean
    public Queue feedQueue() {
        return new Queue(CorgiQueueName.FEED_REFRESH);
    }

    @Bean
    public Queue barActivityQueue() {
        return new Queue(CorgiQueueName.BAR_ACTIVITY_QUEUE);
    }

    @Bean
    public Queue influencerQueue() {
        return new Queue(CorgiQueueName.INFLUENCER_JOIN_QUEUE);
    }

    @Bean
    public Queue influencerLeftQueue() {
        return new Queue(CorgiQueueName.INFLUENCER_LEFT_QUEUE);
    }

    @Bean
    public Queue registerQueue() {
        return new Queue(CorgiQueueName.REGISTER_QUEUE);
    }

    @Bean
    public Queue registerPostActivityQueue() {
        return new Queue(CorgiQueueName.ACTIVITY_POST_QUEUE);
    }


}
