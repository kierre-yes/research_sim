package com.thesis.cloudsim.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

/**
 * Configuration for async execution
 * Optimized for plot generation tasks
 */
@Configuration
public class AsyncConfig {
    
    @Bean(name = "taskExecutor")
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        
        // configure thread pool for plot generation
        executor.setCorePoolSize(4); // minimum threads
        executor.setMaxPoolSize(8); // maximum threads
        executor.setQueueCapacity(10); // queue for pending tasks
        executor.setThreadNamePrefix("PlotGen-");
        
        // keep threads alive for 60 seconds when idle
        executor.setKeepAliveSeconds(60);
        
        // wait for tasks to complete on shutdown
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(300); // wait up to 5 minutes
        
        executor.initialize();
        return executor;
    }
}
