package com.freeswitch.demoCall.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

@Configuration
public class AppConfig {

    @Bean
    public ThreadPoolTaskExecutor taskExecutor() {
        ThreadPoolTaskExecutor poolExecutor = new ThreadPoolTaskExecutor();

        poolExecutor.setCorePoolSize(5);
        poolExecutor.setQueueCapacity(5);
        poolExecutor.setMaxPoolSize(200);
        poolExecutor.setThreadNamePrefix("customPoolExecutor");
        poolExecutor.initialize();
        poolExecutor.afterPropertiesSet();
        return poolExecutor;
    }

    @Bean
    public ScheduledExecutorService scheduledExecutor() {
        ScheduledExecutorService scheduledExecutor = Executors.newScheduledThreadPool(99);
        return scheduledExecutor;
    }

}
