package com.thesis.cloudsim.controller;

import com.thesis.cloudsim.algorithm.ISchedulingAlgorithm;
import com.thesis.cloudsim.dto.ProcessedResults;
import com.thesis.cloudsim.dto.SimulationRequest;
import com.thesis.cloudsim.matlab.MatlabIntegrationService;
import com.thesis.cloudsim.service.ComparisonService;
import com.thesis.cloudsim.service.AsyncPlotGenerationService;
import com.thesis.cloudsim.service.AnalysisInterpretationService;
import com.thesis.cloudsim.service.AsyncPlotGenerationService.PlotGenerationStatus;
import com.thesis.cloudsim.metrics.SimulationResults;
import com.thesis.cloudsim.util.ConfigurationSnapshotUtil;
import com.thesis.cloudsim.simulation.EnhancedSimulationManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;


@RestController
@RequestMapping("/api/simulate")
@Validated
public class SimulationController {

    private static final Logger logger = LoggerFactory.getLogger(SimulationController.class);
    
    private final ISchedulingAlgorithm epso;
    private final ISchedulingAlgorithm eaco;
    
    @Autowired(required = false)
    private MatlabIntegrationService matlabService;
    
    @Autowired(required = false)
    private AsyncPlotGenerationService asyncPlotService;
    
    @Autowired
    private AnalysisInterpretationService analysisService;

    public SimulationController(@Qualifier("epso") ISchedulingAlgorithm epso,
                                @Qualifier("eaco") ISchedulingAlgorithm eaco) {
        this.epso = epso;
        this.eaco = eaco;
        logger.info("SimulationController initialized with EPSO and EACO algorithms");
    }

    @PostMapping("/raw")
    public Map<String, Object> runSimulationRaw(@Valid @RequestBody SimulationRequest request) throws IOException {
        logger.debug("Received simulation request for algorithm: {}", request.getOptimizationAlgorithm());
        
        ISchedulingAlgorithm algorithm = selectAlgorithm(request);
        String algorithmName = algorithm.getAlgorithmName();
        
        try {
            algorithm.reset();
            
            long startTime = System.currentTimeMillis();
            EnhancedSimulationManager manager = new EnhancedSimulationManager(algorithm, request);
            SimulationResults results = manager.run();
        
            results.setRunId(java.util.UUID.randomUUID().toString());
            results.setSeed(request.getSeed() != null ? request.getSeed() : System.currentTimeMillis());
            results.setConfigSnapshot(createConfigSnapshot(request));
            results.setDatasetId(request.getWorkloadPath() != null ? 
                "custom-" + request.getWorkloadPath().hashCode() : "synthetic");
        
            long executionTime = System.currentTimeMillis() - startTime;
            logger.info("Simulation completed in {} ms", executionTime);
            
            ProcessedResults processedResults = ProcessedResults.builder().rawResults(results).build();

            Map<String, Object> analysis = analysisService.generateCompleteAnalysis(processedResults, algorithmName);
            
            Map<String, Object> response = new HashMap<>();
            response.put("simulationResults", results);
            response.put("analysis", analysis);
            response.put("executionTimeMs", executionTime);
            
            return response;
        } finally {
            algorithm.reset();
        }
    }
    
    private ISchedulingAlgorithm selectAlgorithm(SimulationRequest request) {
        if ("EPSO".equalsIgnoreCase(request.getOptimizationAlgorithm())) {
            logger.debug("Using EPSO algorithm");
            return epso;
        } else {
            logger.debug("Using EACO algorithm");
            return eaco;
        }
    }

    @PostMapping("/with-plots")
    public ResponseEntity<Object> runSimulationWithPlots(@Valid @RequestBody SimulationRequest request) throws IOException {
        if (matlabService == null) {
            logger.warn("MATLAB service not available - plots disabled");
            return ResponseEntity
                    .status(503)
                    .body(java.util.Map.of(
                        "error", "MATLAB integration is not available",
                        "message", "Please use the /raw endpoint for simulation without plots"
                    ));
        }
        
        ISchedulingAlgorithm algorithm = selectAlgorithm(request);
        algorithm.reset();
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
            
            raw.setRunId(java.util.UUID.randomUUID().toString());
            raw.setSeed(request.getSeed() != null ? request.getSeed() : System.currentTimeMillis());
            raw.setConfigSnapshot(createConfigSnapshot(request));
            raw.setDatasetId(request.getWorkloadPath() != null ? 
                "custom-" + request.getWorkloadPath().hashCode() : "synthetic");
            
            String algorithmName = request.getOptimizationAlgorithm() != null ? request.getOptimizationAlgorithm() : "CloudSim";
            ProcessedResults out = matlabService.processResults(raw, algorithmName);

            Map<String, Object> analysis = analysisService.generateCompleteAnalysis(out, algorithmName);
            
            Map<String, Object> response = new HashMap<>();
            response.put("processedResults", out);
            response.put("analysis", analysis);

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error during MATLAB processing", e);
            throw e;
        } finally {
            algorithm.reset();
        }
    }
    
    @PostMapping("/async")
    public ResponseEntity<Map<String, Object>> runSimulationAsync(@Valid @RequestBody SimulationRequest request) throws IOException {
        logger.info("Received async simulation request for algorithm: {}", request.getOptimizationAlgorithm());
        
        if (asyncPlotService == null) {
            logger.warn("Async plot service not available");
            return ResponseEntity
                    .status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of(
                        "error", "Async plot generation is not available",
                        "message", "Please use /with-plots for synchronous plot generation"
                    ));
        }
        
        ISchedulingAlgorithm algorithm = selectAlgorithm(request);
        algorithm.reset();
        EnhancedSimulationManager manager = new EnhancedSimulationManager(algorithm, request);
        
        long startTime = System.currentTimeMillis();
        SimulationResults results = manager.run();
        
        results.setRunId(java.util.UUID.randomUUID().toString());
        results.setSeed(request.getSeed() != null ? request.getSeed() : System.currentTimeMillis());
        results.setConfigSnapshot(createConfigSnapshot(request));
        results.setDatasetId(request.getWorkloadPath() != null ? 
            "custom-" + request.getWorkloadPath().hashCode() : "synthetic");
        
        long executionTime = System.currentTimeMillis() - startTime;
        
        logger.info("Simulation completed in {} ms, submitting for async plot generation", executionTime);
        
        String algorithmName = request.getOptimizationAlgorithm() != null ? request.getOptimizationAlgorithm() : "CloudSim";
        ProcessedResults processedResults = ProcessedResults.builder().rawResults(results).build();
        String plotTrackingId = asyncPlotService.submitForPlotGeneration(processedResults, algorithmName);
        
        Map<String, Object> response = new HashMap<>();
        response.put("simulationResults", results);
        response.put("plotTrackingId", plotTrackingId);
        response.put("plotStatus", "PENDING");
        response.put("message", "Simulation completed. Plots are being generated in background.");
        response.put("executionTimeMs", executionTime);
        
        return ResponseEntity.ok(response);
    }
    
    @GetMapping("/plot-status/{trackingId}")
    public ResponseEntity<Map<String, Object>> getPlotStatus(@PathVariable @NotBlank String trackingId) {
        if (asyncPlotService == null) {
            return ResponseEntity
                    .status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("error", "Async plot service not available"));
        }
        
        PlotGenerationStatus status = asyncPlotService.getStatus(trackingId);
        
        if (status == null) {
            return ResponseEntity
                    .status(HttpStatus.NOT_FOUND)
                    .body(Map.of(
                        "error", "Invalid tracking ID",
                        "trackingId", trackingId
                    ));
        }
        
        Map<String, Object> response = new HashMap<>();
        response.put("trackingId", trackingId);
        response.put("status", status.getStatus().toString());
        response.put("progress", status.getProgress());
        response.put("message", status.getMessage());
        response.put("elapsedTimeMs", status.getElapsedTime());
        
        if (status.getStatus() == AsyncPlotGenerationService.PlotStatus.COMPLETED) {
            ProcessedResults results = asyncPlotService.getResults(trackingId);
            if (results != null) {
                response.put("plotData", results.getPlotData());
                response.put("plotPaths", results.getPlotData().get("plotPaths"));
            }
        }
        
        return ResponseEntity.ok(response);
    }
    
    @GetMapping("/plot-results/{trackingId}")
    public ResponseEntity<Object> getPlotResults(@PathVariable String trackingId) {
        if (asyncPlotService == null) {
            return ResponseEntity
                    .status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("error", "Async plot service not available"));
        }
        
        if (!asyncPlotService.isPlotsReady(trackingId)) {
            PlotGenerationStatus status = asyncPlotService.getStatus(trackingId);
            if (status == null) {
                return ResponseEntity
                        .status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "Invalid tracking ID"));
            }
            
            return ResponseEntity
                    .status(HttpStatus.ACCEPTED)
                    .body(Map.of(
                        "status", status.getStatus().toString(),
                        "message", "Plots not ready yet",
                        "progress", status.getProgress()
                    ));
        }
        
        ProcessedResults results = asyncPlotService.getResults(trackingId);
        return ResponseEntity.ok(results);
    }
    
    private Map<String, Object> createConfigSnapshot(SimulationRequest request) {
        return ConfigurationSnapshotUtil.createBasicSnapshot(request);
    }
}
