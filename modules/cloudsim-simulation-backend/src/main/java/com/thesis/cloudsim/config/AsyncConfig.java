package com.thesis.cloudsim.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.servlet.config.annotation.AsyncSupportConfigurer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;


@Configuration
@EnableAsync
public class AsyncConfig implements WebMvcConfigurer {
    
    /**
     * Configure async support with extended timeout for hour-long simulations
     */
    @Override
    public void configureAsyncSupport(AsyncSupportConfigurer configurer) {
        configurer.setDefaultTimeout(TimeUnit.HOURS.toMillis(2)); // 2 hour timeout
        configurer.setTaskExecutor(taskExecutor());
    }
    
    @Bean(name = "taskExecutor")
    public Executor taskExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        
        // configure thread pool 
        executor.setCorePoolSize(4); // minimum threads
        executor.setMaxPoolSize(8); // maximum threads
        executor.setQueueCapacity(100); // increased queue for pending tasks
        executor.setThreadNamePrefix("AsyncSim-");
        

        executor.setKeepAliveSeconds(3600);
        
        executor.setAllowCoreThreadTimeOut(true);
        
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(7200); 
        
        executor.initialize();
        return executor;
    }
}
