package com.thesis.cloudsim.config;

import com.thesis.cloudsim.algorithm.EnhancedACO;
import com.thesis.cloudsim.algorithm.EnhancedPSO;
import com.thesis.cloudsim.algorithm.ISchedulingAlgorithm;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AlgorithmConfig {

    @Bean
    @Qualifier("epso")
    public ISchedulingAlgorithm epsoAlgorithm() {
        return new EnhancedPSO();
    }

    @Bean
    @Qualifier("eaco")
    public ISchedulingAlgorithm eacoAlgorithm() {
        return new EnhancedACO();
    }
}
