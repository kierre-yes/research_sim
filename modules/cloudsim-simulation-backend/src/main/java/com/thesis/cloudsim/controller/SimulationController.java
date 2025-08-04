package com.thesis.cloudsim.controller;

import com.thesis.cloudsim.algorithm.ISchedulingAlgorithm;
import com.thesis.cloudsim.dto.ProcessedResults;
import com.thesis.cloudsim.dto.SimulationRequest;
import com.thesis.cloudsim.matlab.MatlabIntegrationService;
import com.thesis.cloudsim.metrics.SimulationResults;
import com.thesis.cloudsim.simulation.EnhancedSimulationManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;


@RestController
@RequestMapping("/api/simulate")
public class SimulationController {

    private static final Logger logger = LoggerFactory.getLogger(SimulationController.class);
    
    private final ISchedulingAlgorithm epso;
    private final ISchedulingAlgorithm eaco;
    
    @Autowired(required = false)
    private MatlabIntegrationService matlabService;

    public SimulationController(@Qualifier("epso") ISchedulingAlgorithm epso,
                                @Qualifier("eaco") ISchedulingAlgorithm eaco) {
        this.epso = epso;
        this.eaco = eaco;
        logger.info("SimulationController initialized with EPSO and EACO algorithms");
    }

    @PostMapping("/raw")
    public SimulationResults runSimulationRaw(@RequestBody SimulationRequest request) throws IOException {
        logger.debug("Received simulation request for algorithm: {}", request.getOptimizationAlgorithm());
        

        ISchedulingAlgorithm algorithm;
        if ("EPSO".equalsIgnoreCase(request.getOptimizationAlgorithm())) {
            algorithm = epso;
            logger.debug("Using EPSO algorithm");
        } else {
            algorithm = eaco;
            logger.debug("Using EACO algorithm");
        }
        

        long startTime = System.currentTimeMillis();
        EnhancedSimulationManager manager = new EnhancedSimulationManager(algorithm, request);
        SimulationResults results = manager.run();
        
        long executionTime = System.currentTimeMillis() - startTime;
        logger.info("Simulation completed in {} ms", executionTime);
        
        return results;
    }

    /**

     */
    @PostMapping("/with-plots")
    public ResponseEntity<Object> runSimulationWithPlots(@RequestBody SimulationRequest request) throws IOException {
        // Verify MATLAB service availability
        if (matlabService == null) {
            logger.warn("MATLAB service not available - plots disabled");
            return ResponseEntity
                    .status(503)
                    .body(java.util.Map.of(
                        "error", "MATLAB integration is not available",
                        "message", "Please use the /raw endpoint for simulation without plots"
                    ));
        }
        

        ISchedulingAlgorithm algorithm = "EPSO".equalsIgnoreCase(request.getOptimizationAlgorithm()) ? epso : eaco;
        EnhancedSimulationManager manager = new EnhancedSimulationManager(algorithm, request);
        

        if (!matlabService.isReady()) {
            logger.info("MATLAB engine warming up...");
            return ResponseEntity
                    .accepted()
                    .header(org.springframework.http.HttpHeaders.RETRY_AFTER, "5")
                    .body(java.util.Map.of("status", "WARMING_UP"));
        }
        
        try {
            SimulationResults raw = manager.run();
            String algorithmName = request.getOptimizationAlgorithm() != null ? request.getOptimizationAlgorithm() : "CloudSim";
            ProcessedResults out = matlabService.processResults(raw, algorithmName);
            return ResponseEntity.ok(out);
        } catch (Exception e) {
            logger.error("Error during MATLAB processing", e);

            throw e;
        }
    }
}
