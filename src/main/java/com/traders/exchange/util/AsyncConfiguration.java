package com.traders.exchange.util;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

@Configuration
@EnableAsync
public class AsyncConfiguration {
    @Bean
    public Executor asyncExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }
}