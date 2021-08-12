package com.corgi.common.config;

import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.AsyncConfigurer;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
@EnableAsync
public class AsyncTaskConfig implements AsyncConfigurer {
    @Override
    public Executor getAsyncExecutor() {
        // 新建一个任务执行器
        ThreadPoolTaskExecutor taskExecutor = new ThreadPoolTaskExecutor();
        taskExecutor.setCorePoolSize(10);// 设置最小的线程数量
        taskExecutor.setMaxPoolSize(50);// 设置最大的线程数量
        taskExecutor.setQueueCapacity(5);// 等待队列
        taskExecutor.initialize();// 初始化
        return taskExecutor;
    }

    @Override
    public AsyncUncaughtExceptionHandler getAsyncUncaughtExceptionHandler() {
        return null;
    }
}
