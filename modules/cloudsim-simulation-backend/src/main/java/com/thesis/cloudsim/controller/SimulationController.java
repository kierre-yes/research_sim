package com.thesis.cloudsim.controller;

import com.thesis.cloudsim.algorithm.ISchedulingAlgorithm;
import com.thesis.cloudsim.dto.ProcessedResults;
import com.thesis.cloudsim.dto.SimulationRequest;
import com.thesis.cloudsim.matlab.MatlabIntegrationService;
import com.thesis.cloudsim.metrics.SimulationResults;
import com.thesis.cloudsim.controller.EnhancedSimulationManager;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;

import java.io.IOException;

@RestController
@RequestMapping("/api/simulate")
public class SimulationController {

    private final ISchedulingAlgorithm epso;
    private final ISchedulingAlgorithm eaco;
    private final MatlabIntegrationService matlabService;

    public SimulationController(@Qualifier("epso") ISchedulingAlgorithm epso,
                                @Qualifier("eaco") ISchedulingAlgorithm eaco,
                                MatlabIntegrationService matlabService) {
        this.epso = epso;
        this.eaco = eaco;
        this.matlabService = matlabService;
    }

    @PostMapping("/raw")
    public SimulationResults runSimulationRaw(@RequestBody SimulationRequest request) throws IOException {
        ISchedulingAlgorithm algorithm = "EPSO".equalsIgnoreCase(request.getOptimizationAlgorithm()) ? epso : eaco;
EnhancedSimulationManager manager = new EnhancedSimulationManager(algorithm, request);
        return manager.run();
    }

@PostMapping("/with-plots")
public ResponseEntity<Object> runSimulationWithPlots(@RequestBody SimulationRequest request) throws IOException {
        ISchedulingAlgorithm algorithm = "EPSO".equalsIgnoreCase(request.getOptimizationAlgorithm()) ? epso : eaco;
        EnhancedSimulationManager manager = new EnhancedSimulationManager(algorithm, request);
        // Check if MATLAB engine is ready; if not, ask client to retry later
        if (!matlabService.isReady()) {
            return ResponseEntity
                    .accepted()
                    .header(org.springframework.http.HttpHeaders.RETRY_AFTER, "5")
                    .body(java.util.Map.of("status", "WARMING_UP"));
        }
        SimulationResults raw = manager.run();
        ProcessedResults out = matlabService.processResults(raw);
        return ResponseEntity.ok(out);
    }
}
