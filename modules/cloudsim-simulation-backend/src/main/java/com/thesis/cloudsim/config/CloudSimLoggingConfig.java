package com.thesis.cloudsim.config;

import org.cloudbus.cloudsim.Log;
import org.springframework.context.annotation.Configuration;
import jakarta.annotation.PostConstruct;

@Configuration
public class CloudSimLoggingConfig {
    
    @PostConstruct
    public void disableCloudSimLogging() {
        //stop flooding on prod
        Log.disable();
    }
}
