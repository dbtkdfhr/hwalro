package com.hwalro.simulation.simulation.config;

import java.util.concurrent.ThreadPoolExecutor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@Configuration
public class SimulationExecutionConfig {
    public static final int MAX_CONCURRENT_EXECUTIONS = 4;
    public static final int EXECUTION_QUEUE_CAPACITY = 20;

    @Bean(name = "simulationExecutionExecutor")
    public ThreadPoolTaskExecutor simulationExecutionExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("simulation-engine-");
        executor.setCorePoolSize(MAX_CONCURRENT_EXECUTIONS);
        executor.setMaxPoolSize(MAX_CONCURRENT_EXECUTIONS);
        executor.setQueueCapacity(EXECUTION_QUEUE_CAPACITY);
        executor.setRejectedExecutionHandler(new ThreadPoolExecutor.AbortPolicy());
        executor.setWaitForTasksToCompleteOnShutdown(false);
        executor.initialize();
        return executor;
    }
}
