package com.demo.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Configuration
@EnableAsync
public class AsyncConfig {

    @Bean(name = "asyncExecutor")
    public Executor asyncExecutor() {

        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        executor.setCorePoolSize(20);      // 기본 쓰레드 수
        executor.setMaxPoolSize(20);      // 최대 쓰레드 수
        executor.setQueueCapacity(100);   // 대기 큐

        executor.setThreadNamePrefix("async-");

        executor.initialize();

        return executor;
    }
}