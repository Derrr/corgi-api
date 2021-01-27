package com.corgi;

import com.alibaba.dubbo.spring.boot.annotation.EnableDubboConfiguration;
import com.corgi.common.CorgiQueueName;
import com.corgi.common.filter.CorgiCorsFilter;
import com.corgi.common.filter.RequestFilter;
import org.springframework.amqp.core.Queue;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.core.annotation.Order;

import javax.servlet.Filter;


@SpringBootApplication
@EnableDubboConfiguration
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

}
