package com.thesis.cloudsim.controller;

import com.thesis.cloudsim.algorithm.ISchedulingAlgorithm;
import com.thesis.cloudsim.dto.ProcessedResults;
import com.thesis.cloudsim.dto.SimulationRequest;
import com.thesis.cloudsim.matlab.MatlabIntegrationService;
import com.thesis.cloudsim.metrics.SimulationResults;
// Replaced with the simpler simulation orchestrator
import com.thesis.cloudsim.simulation.EnhancedSimulationManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;

import java.io.IOException;

@RestController
@RequestMapping("/api/simulate")
public class SimulationController {

    private final ISchedulingAlgorithm epso;
    private final ISchedulingAlgorithm eaco;
    
    @Autowired(required = false)
    private MatlabIntegrationService matlabService;

    public SimulationController(@Qualifier("epso") ISchedulingAlgorithm epso,
                                @Qualifier("eaco") ISchedulingAlgorithm eaco) {
        this.epso = epso;
        this.eaco = eaco;
    }

    @PostMapping("/raw")
    public SimulationResults runSimulationRaw(@RequestBody SimulationRequest request) throws IOException {
        // Decide which optimisation algorithm to run (EPSO or EACO)
        ISchedulingAlgorithm algorithm = "EPSO".equalsIgnoreCase(request.getOptimizationAlgorithm()) ? epso : eaco;
        EnhancedSimulationManager manager = new EnhancedSimulationManager(algorithm, request);
        return manager.run();
    }

@PostMapping("/with-plots")
public ResponseEntity<Object> runSimulationWithPlots(@RequestBody SimulationRequest request) throws IOException {
        // Check if MATLAB service is available
        if (matlabService == null) {
            return ResponseEntity
                    .status(503)
                    .body(java.util.Map.of(
                        "error", "MATLAB integration is not available",
                        "message", "Please use the /raw endpoint for simulation without plots"
                    ));
        }
        
        // Re-use the same algorithm-selection logic
        ISchedulingAlgorithm algorithm = "EPSO".equalsIgnoreCase(request.getOptimizationAlgorithm()) ? epso : eaco;
        EnhancedSimulationManager manager = new EnhancedSimulationManager(algorithm, request);
        // If MATLAB engine is still starting, instruct front-end to retry later
        if (!matlabService.isReady()) {
            return ResponseEntity
                    .accepted()
                    .header(org.springframework.http.HttpHeaders.RETRY_AFTER, "5")
                    .body(java.util.Map.of("status", "WARMING_UP"));
        }
        SimulationResults raw = manager.run();
        String algorithmName = request.getOptimizationAlgorithm() != null ? request.getOptimizationAlgorithm() : "CloudSim";
        ProcessedResults out = matlabService.processResults(raw, algorithmName);
        return ResponseEntity.ok(out);
    }
}
