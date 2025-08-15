package com.thesis.cloudsim.controller;

import com.thesis.cloudsim.algorithm.ISchedulingAlgorithm;
import com.thesis.cloudsim.dto.ProcessedResults;
import com.thesis.cloudsim.dto.SimulationRequest;
import com.thesis.cloudsim.matlab.MatlabIntegrationService;
import com.thesis.cloudsim.metrics.SimulationResults;
import com.thesis.cloudsim.service.AsyncPlotGenerationService;
import com.thesis.cloudsim.service.AsyncPlotGenerationService.PlotGenerationStatus;
import com.thesis.cloudsim.service.AnalysisInterpretationService;
import com.thesis.cloudsim.simulation.EnhancedSimulationManager;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;


/**
 * REST Controller for simulation endpoints
 * 
 * I implement the main API endpoints so that the frontend can trigger simulations
 * and retrieve results in different formats (raw, with plots, or async)
 */
@RestController
@RequestMapping("/api/simulate")
public class SimulationController {

    private static final Logger logger = LoggerFactory.getLogger(SimulationController.class);
    
    // I inject algorithm beans so that they can be reused across requests
    private final ISchedulingAlgorithm epso;
    private final ISchedulingAlgorithm eaco;
    
    // I mark MATLAB service as optional so that the app works without MATLAB installed
    @Autowired(required = false)
    private MatlabIntegrationService matlabService;
    
    // I mark async plot service as optional for environments without plot generation
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

    /**
     * Run simulation and return raw results without plot generation
     * 
     * I provide this endpoint so that clients can get results quickly without
     * waiting for plot generation, which can be time-consuming
     */
    @PostMapping("/raw")
    public Map<String, Object> runSimulationRaw(@RequestBody SimulationRequest request) throws IOException {
        logger.debug("Received simulation request for algorithm: {}", request.getOptimizationAlgorithm());
        
        /*
         * I select and reset the algorithm to ensure clean state for each request.
         * This prevents state pollution between requests from the frontend.
         */
        ISchedulingAlgorithm algorithm = selectAlgorithm(request);
        String algorithmName = algorithm.getAlgorithmName();
        
        try {
            algorithm.reset(); /* I reset before use to ensure clean state */
            
            long startTime = System.currentTimeMillis();
            EnhancedSimulationManager manager = new EnhancedSimulationManager(algorithm, request);
            SimulationResults results = manager.run();
        
        /**
         * I add metadata to results
         */
        results.setRunId(java.util.UUID.randomUUID().toString());
        results.setSeed(request.getSeed() != null ? request.getSeed() : System.currentTimeMillis());
        results.setConfigSnapshot(createConfigSnapshot(request));
        results.setDatasetId(request.getWorkloadPath() != null ? 
            "custom-" + request.getWorkloadPath().hashCode() : "synthetic");
        
            long executionTime = System.currentTimeMillis() - startTime;
            logger.info("Simulation completed in {} ms", executionTime);
            
            /*
             * I generate comprehensive analysis and interpretations
             * to provide meaningful insights instead of placeholders
             */
            Map<String, Object> analysis = analysisService.generateCompleteAnalysis(results, algorithmName);
            
            /*
             * I return both raw results and analysis so the frontend
             * can display accurate interpretations
             */
            Map<String, Object> response = new HashMap<>();
            response.put("simulationResults", results);
            response.put("analysis", analysis);
            response.put("executionTimeMs", executionTime);
            
            return response;
        } finally {
            algorithm.reset(); /* I clean up after use to free resources */
        }
    }
    
    /*
     * I extract algorithm selection logic to avoid duplication.
     * This helper method selects the appropriate algorithm based on the request.
     */
    private ISchedulingAlgorithm selectAlgorithm(SimulationRequest request) {
        if ("EPSO".equalsIgnoreCase(request.getOptimizationAlgorithm())) {
            logger.debug("Using EPSO algorithm");
            return epso;
        } else {
            logger.debug("Using EACO algorithm");
            return eaco;
        }
    }

    /**
     * Run simulation with synchronous plot generation via MATLAB
     * 
     * I provide this endpoint so that clients can get results with plots
     * in a single request, though it takes longer than the raw endpoint
     */
    @PostMapping("/with-plots")
    public ResponseEntity<Object> runSimulationWithPlots(@RequestBody SimulationRequest request) throws IOException {
        // I check MATLAB availability first so that we fail fast if plots can't be generated
        if (matlabService == null) {
            logger.warn("MATLAB service not available - plots disabled");
            return ResponseEntity
                    .status(503)
                    .body(java.util.Map.of(
                        "error", "MATLAB integration is not available",
                        "message", "Please use the /raw endpoint for simulation without plots"
                    ));
        }
        

        /*
         * I select and reset the algorithm to ensure clean state
         */
        ISchedulingAlgorithm algorithm = selectAlgorithm(request);
        algorithm.reset();
        EnhancedSimulationManager manager = new EnhancedSimulationManager(algorithm, request);
        
        // I check if MATLAB is still warming up so that clients can retry later
        if (!matlabService.isReady()) {
            logger.info("MATLAB engine warming up...");
            return ResponseEntity
                    .accepted()
                    .header(org.springframework.http.HttpHeaders.RETRY_AFTER, "5")
                    .body(java.util.Map.of("status", "WARMING_UP"));
        }
        
        try {
            SimulationResults raw = manager.run();
            
            /*
             * I add metadata before passing to MATLAB to avoid null errors
             */
            raw.setRunId(java.util.UUID.randomUUID().toString());
            raw.setSeed(request.getSeed() != null ? request.getSeed() : System.currentTimeMillis());
            raw.setConfigSnapshot(createConfigSnapshot(request));
            raw.setDatasetId(request.getWorkloadPath() != null ? 
                "custom-" + request.getWorkloadPath().hashCode() : "synthetic");
            
            String algorithmName = request.getOptimizationAlgorithm() != null ? request.getOptimizationAlgorithm() : "CloudSim";
            /* I process results through MATLAB to generate visualization plots */
            ProcessedResults out = matlabService.processResults(raw, algorithmName);
            return ResponseEntity.ok(out);
        } catch (Exception e) {
            logger.error("Error during MATLAB processing", e);
            /* I rethrow so that Spring's exception handler can format the error response */
            throw e;
        } finally {
            algorithm.reset(); /* I clean up algorithm state after use */
        }
    }
    
    /**
     * Run simulation and generate plots asynchronously
     * 
     * I provide this endpoint so that clients can get simulation results immediately
     * while plots are generated in the background, improving perceived performance
     */
    @PostMapping("/async")
    public ResponseEntity<Map<String, Object>> runSimulationAsync(@RequestBody SimulationRequest request) throws IOException {
        logger.info("Received async simulation request for algorithm: {}", request.getOptimizationAlgorithm());
        
        // I verify service availability so that we don't promise async plots we can't deliver
        if (asyncPlotService == null) {
            logger.warn("Async plot service not available");
            return ResponseEntity
                    .status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of(
                        "error", "Async plot generation is not available",
                        "message", "Please use /with-plots for synchronous plot generation"
                    ));
        }
        
        /*
         * I run the simulation synchronously first to get results
         */
        ISchedulingAlgorithm algorithm = selectAlgorithm(request);
        algorithm.reset();
        EnhancedSimulationManager manager = new EnhancedSimulationManager(algorithm, request);
        
        long startTime = System.currentTimeMillis();
        SimulationResults results = manager.run();
        
        /**
         * I add metadata before passing to async plot service to avoid null errors
         */
        results.setRunId(java.util.UUID.randomUUID().toString());
        results.setSeed(request.getSeed() != null ? request.getSeed() : System.currentTimeMillis());
        results.setConfigSnapshot(createConfigSnapshot(request));
        results.setDatasetId(request.getWorkloadPath() != null ? 
            "custom-" + request.getWorkloadPath().hashCode() : "synthetic");
        
        long executionTime = System.currentTimeMillis() - startTime;
        
        logger.info("Simulation completed in {} ms, submitting for async plot generation", executionTime);
        
        // I submit the results for background plot generation so that the client doesn't wait
        String algorithmName = request.getOptimizationAlgorithm() != null ? request.getOptimizationAlgorithm() : "CloudSim";
        String plotTrackingId = asyncPlotService.submitForPlotGeneration(results, algorithmName);
        
        // I return results immediately with a tracking ID so that clients can poll for plot status
        Map<String, Object> response = new HashMap<>();
        response.put("simulationResults", results);
        response.put("plotTrackingId", plotTrackingId);
        response.put("plotStatus", "PENDING");
        response.put("message", "Simulation completed. Plots are being generated in background.");
        response.put("executionTimeMs", executionTime);
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Check plot generation status
     * 
     * I provide this endpoint so that clients can poll for plot generation progress
     * without blocking their UI while waiting for plots to complete
     */
    @GetMapping("/plot-status/{trackingId}")
    public ResponseEntity<Map<String, Object>> getPlotStatus(@PathVariable String trackingId) {
        if (asyncPlotService == null) {
            return ResponseEntity
                    .status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("error", "Async plot service not available"));
        }
        
        PlotGenerationStatus status = asyncPlotService.getStatus(trackingId);
        
        // I check if the tracking ID exists so that we can return appropriate error
        if (status == null) {
            return ResponseEntity
                    .status(HttpStatus.NOT_FOUND)
                    .body(Map.of(
                        "error", "Invalid tracking ID",
                        "trackingId", trackingId
                    ));
        }
        
        // I build a comprehensive status response so that clients know exactly what's happening
        Map<String, Object> response = new HashMap<>();
        response.put("trackingId", trackingId);
        response.put("status", status.getStatus().toString());
        response.put("progress", status.getProgress());
        response.put("message", status.getMessage());
        response.put("elapsedTimeMs", status.getElapsedTime());
        
        // I include plot data only when completed so that response size stays small during polling
        if (status.getStatus() == AsyncPlotGenerationService.PlotStatus.COMPLETED) {
            ProcessedResults results = asyncPlotService.getResults(trackingId);
            if (results != null) {
                response.put("plotData", results.getPlotData());
                response.put("plotPaths", results.getPlotData().get("plotPaths"));
            }
        }
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Get completed plot results
     * 
     * I provide this endpoint so that clients can retrieve the full plot data
     * once generation is complete, separate from the status polling
     */
    @GetMapping("/plot-results/{trackingId}")
    public ResponseEntity<Object> getPlotResults(@PathVariable String trackingId) {
        if (asyncPlotService == null) {
            return ResponseEntity
                    .status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("error", "Async plot service not available"));
        }
        
        // I check if plots are ready so that we can return appropriate status
        if (!asyncPlotService.isPlotsReady(trackingId)) {
            PlotGenerationStatus status = asyncPlotService.getStatus(trackingId);
            if (status == null) {
                return ResponseEntity
                        .status(HttpStatus.NOT_FOUND)
                        .body(Map.of("error", "Invalid tracking ID"));
            }
            
            // I return 202 Accepted so that clients know to keep waiting
            return ResponseEntity
                    .status(HttpStatus.ACCEPTED)
                    .body(Map.of(
                        "status", status.getStatus().toString(),
                        "message", "Plots not ready yet",
                        "progress", status.getProgress()
                    ));
        }
        
        // I return the complete results once plots are ready
        ProcessedResults results = asyncPlotService.getResults(trackingId);
        return ResponseEntity.ok(results);
    }
    
    /**
     * I create a configuration snapshot for metadata tracking
     * Following the same pattern as ApiController
     */
    private Map<String, Object> createConfigSnapshot(SimulationRequest request) {
        Map<String, Object> snapshot = new HashMap<>();
        snapshot.put("algorithm", request.getOptimizationAlgorithm());
        snapshot.put("numHosts", request.getNumHosts());
        snapshot.put("numVMs", request.getNumVMs());
        snapshot.put("numCloudlets", request.getNumCloudlets());
        snapshot.put("workloadType", request.getWorkloadType());
        snapshot.put("vmScheduler", request.getVmScheduler());
        snapshot.put("iterations", request.getIterations());
        return snapshot;
    }
}
