package com.hwalro.simulation.search.config;

import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class LayoutSearchConfig {
    private static final int COORDINATOR_THREADS = 2;

    @Bean(name = "layoutSearchCoordinatorExecutor")
    public ThreadPoolTaskExecutor layoutSearchCoordinatorExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("layout-search-coordinator-");
        executor.setCorePoolSize(COORDINATOR_THREADS);
        executor.setMaxPoolSize(COORDINATOR_THREADS);
        executor.setQueueCapacity(16);
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(false);
        executor.initialize();
        return executor;
    }

    @Bean(name = "layoutSearchTrialExecutor")
    public ThreadPoolTaskExecutor layoutSearchTrialExecutor(LayoutSearchProperties properties) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("layout-search-trial-");
        executor.setCorePoolSize(properties.getTrialConcurrency());
        executor.setMaxPoolSize(properties.getTrialConcurrency());
        executor.setQueueCapacity(64);
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(false);
        executor.initialize();
        return executor;
    }
}
