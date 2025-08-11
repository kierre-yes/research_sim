package com.thesis.cloudsim;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.EnableAsync;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * CloudSim Simulation Backend Application
 * 
 * This application provides a RESTful API for running cloud computing simulations
 * using Enhanced Particle Swarm Optimization (EPSO) and Enhanced Ant Colony 
 * Optimization (EACO) algorithms as described in our thesis manuscript.
 * 
 * @author [Kier]
 * @version 1.0
 * @since 2025-07-10
 * 
 */
@SpringBootApplication
@EnableConfigurationProperties
@EnableAsync  // I enable async here so that plot generation can run in background threads without blocking simulation requests
public class CloudSimSimulationApplication {

    private static final Logger logger = LoggerFactory.getLogger(CloudSimSimulationApplication.class);

    public static void main(String[] args) {
        logger.info("Starting CloudSim Simulation Backend...");
        SpringApplication.run(CloudSimSimulationApplication.class, args);
    }
    
    @EventListener(ApplicationReadyEvent.class)
    public void applicationReady() {
        // I log these startup messages so that developers can quickly verify the server is running and know the exact endpoints
        logger.info("CloudSim Simulation Backend is ready!");
        logger.info("Server running at: http://localhost:8081");
        logger.info("API endpoints available at: http://localhost:8081/api/*");
    }
}
