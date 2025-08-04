package com.thesis.cloudsim.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
@Configuration
@ConfigurationProperties(prefix = "algorithm.debug")
public class AlgorithmDebugConfig {
    
    private boolean enabled = false;
    private int iterationInterval = 10;
    
    public boolean isEnabled() {
        return enabled;
    }
    
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
    
    public int getIterationInterval() {
        return iterationInterval;
    }
    
    public void setIterationInterval(int iterationInterval) {
        this.iterationInterval = iterationInterval;
    }
}
