package com.thesis.cloudsim.controller;

import com.thesis.cloudsim.dto.SimulationRequest;
import com.thesis.cloudsim.dto.IterationResults;
import com.thesis.cloudsim.dto.ComparisonResults;
import com.thesis.cloudsim.metrics.SimulationResults;
import com.thesis.cloudsim.simulation.EnhancedSimulationManager;
import com.thesis.cloudsim.service.IterationService;
import com.thesis.cloudsim.service.ComparisonService;
import com.thesis.cloudsim.algorithm.ISchedulingAlgorithm;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.HashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RestController
@RequestMapping("/api")
public class ApiController {

    private static final Logger logger = LoggerFactory.getLogger(ApiController.class);
    private final ISchedulingAlgorithm epso;
    private final ISchedulingAlgorithm eaco;
    private final ObjectMapper objectMapper;
    
    @Autowired
    private IterationService iterationService;
    
    @Autowired
    private ComparisonService comparisonService;

    public ApiController(@Qualifier("epso") ISchedulingAlgorithm epso,
                        @Qualifier("eaco") ISchedulingAlgorithm eaco,
                        ObjectMapper objectMapper) {
        this.epso = epso;
        this.eaco = eaco;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/run")
    public ResponseEntity<?> runSimulation(@RequestBody SimulationRequest request) {
        try {
            if (request.getIterations() > 1) {
                return runIterations(request);
            }
            
            ISchedulingAlgorithm algorithm = getAlgorithm(request.getOptimizationAlgorithm());
            EnhancedSimulationManager manager = new EnhancedSimulationManager(algorithm, request);
            SimulationResults results = manager.run();

            return ResponseEntity.ok(results);
        } catch (Exception e) {
            return createErrorResponse(e, request.getOptimizationAlgorithm(), null);
        }
    }
    
    @PostMapping("/run-iterations")
    public ResponseEntity<?> runIterations(@RequestBody SimulationRequest request) {
        try {
            ISchedulingAlgorithm algorithm = getAlgorithm(request.getOptimizationAlgorithm());
            logger.debug("Running {} iterations", request.getIterations());
            IterationResults results = iterationService.runIterations(algorithm, request);
            return ResponseEntity.ok(results);
        } catch (Exception e) {
            return createErrorResponse(e, request.getOptimizationAlgorithm(), String.valueOf(request.getIterations()));
        }
    }

    
    @PostMapping("/run-with-file")
    public ResponseEntity<?> runSimulationWithFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam Map<String, String> params) {
        Path tempFile = null;
        try {
            SimulationRequest request = mapParamsToRequest(params);
            tempFile = saveUploadedFile(file);
            request.setWorkloadPath(tempFile.toString());
            
            if (request.getIterations() > 1) {
                ISchedulingAlgorithm algorithm = getAlgorithm(request.getOptimizationAlgorithm());
                IterationResults results = iterationService.runIterations(algorithm, request);
                return ResponseEntity.ok(results);
            }
            
            ISchedulingAlgorithm algorithm = getAlgorithm(request.getOptimizationAlgorithm());
            EnhancedSimulationManager manager = new EnhancedSimulationManager(algorithm, request);
            SimulationResults results = manager.run();
            return ResponseEntity.ok(results);
        } catch (Exception e) {
            return createErrorResponse(e, params.get("optimizationAlgorithm"), "with-file");
        } finally {
            cleanupTempFile(tempFile);
        }
    }
    
    @PostMapping("/run-iterations-with-file")
    public ResponseEntity<?> runIterationsWithFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam Map<String, String> params) {
        Path tempFile = null;
        try {
            SimulationRequest request = mapParamsToRequest(params);
            tempFile = saveUploadedFile(file);
            request.setWorkloadPath(tempFile.toString());
            
            ISchedulingAlgorithm algorithm = getAlgorithm(request.getOptimizationAlgorithm());
            IterationResults results = iterationService.runIterations(algorithm, request);
            return ResponseEntity.ok(results);
        } catch (Exception e) {
            return createErrorResponse(e, params.get("optimizationAlgorithm"), params.get("iterations"));
        } finally {
            cleanupTempFile(tempFile);
        }
    }
    
    /**
     * Run comparison between EACO and EPSO with paired t-test statistical analysis
     * This endpoint implements the methodology from the manuscript for statistical validation
     */
    @PostMapping("/compare")
    public ResponseEntity<?> compareAlgorithms(@RequestBody SimulationRequest request) {
        try {
            logger.debug("Starting algorithm comparison with paired t-test analysis");
            
            // Ensure we have enough iterations for statistical validity
            if (request.getIterations() < 30) {
                logger.debug("Setting iterations to 30 for statistical significance");
                request.setIterations(30);
            }
            
            ComparisonResults results = comparisonService.runComparison(request);
            
            logger.debug("Comparison completed. Winner: {}", 
                results.getTTestResults().getOverallWinner());
            
            return ResponseEntity.ok(results);
        } catch (Exception e) {
            return createErrorResponse(e, "COMPARISON", "paired-ttest");
        }
    }
    
    @PostMapping("/compare-with-file")
    public ResponseEntity<?> compareAlgorithmsWithFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam Map<String, String> params) {
        Path tempFile = null;
        try {
            SimulationRequest request = mapParamsToRequest(params);
            tempFile = saveUploadedFile(file);
            request.setWorkloadPath(tempFile.toString());
            
            // Ensure we have enough iterations for statistical validity
            if (request.getIterations() < 30) {
                logger.debug("Setting iterations to 30 for statistical significance");
                request.setIterations(30);
            }
            
            ComparisonResults results = comparisonService.runComparison(request);
            
            return ResponseEntity.ok(results);
        } catch (Exception e) {
            return createErrorResponse(e, "COMPARISON", "with-file");
        } finally {
            cleanupTempFile(tempFile);
        }
    }
    
    private ISchedulingAlgorithm getAlgorithm(String algorithmName) {
        if ("EPSO".equalsIgnoreCase(algorithmName)) {
            logger.debug("Using EPSO algorithm");
            return epso;
        } else {
            logger.debug("Using EACO algorithm");
            return eaco;
        }
    }
    
    private ResponseEntity<?> createErrorResponse(Exception e, String algorithm, String details) {
        logger.error("Error in {} algorithm{}: {}", algorithm, 
            details != null ? " (" + details + ")" : "", e.getMessage(), e);
        Map<String, String> errorResponse = new HashMap<>();
        errorResponse.put("error", e.getClass().getSimpleName());
        errorResponse.put("message", e.getMessage());
        errorResponse.put("algorithm", algorithm);
        if (details != null) {
            errorResponse.put("details", details);
        }
        return ResponseEntity.badRequest().body(errorResponse);
    }
    
    private Path saveUploadedFile(MultipartFile file) throws IOException {
        logger.debug("File name: {}", file.getOriginalFilename());
        logger.debug("File size: {}", file.getSize());
        Path tempFile = Files.createTempFile("workload", ".csv");
        Files.copy(file.getInputStream(), tempFile, StandardCopyOption.REPLACE_EXISTING);
        return tempFile;
    }
    
    private void cleanupTempFile(Path tempFile) {
        if (tempFile != null) {
            try {
                Files.deleteIfExists(tempFile);
            } catch (IOException e) {
                logger.warn("Failed to delete temp file: {}", e.getMessage());
            }
        }
    }

    private SimulationRequest mapParamsToRequest(Map<String, String> params) {
        SimulationRequest request = new SimulationRequest();
        
        if (logger.isDebugEnabled()) {
            logger.debug("mapParamsToRequest - All received params:");
            params.forEach((key, value) -> logger.debug("  {} = {}", key, value));
        }
        
        try {

            request.setOptimizationAlgorithm(params.get("optimizationAlgorithm"));
            request.setNumHosts(Integer.parseInt(params.getOrDefault("numHosts", "10")));
            request.setNumVMs(Integer.parseInt(params.getOrDefault("numVMs", "50")));
            request.setNumPesPerHost(Integer.parseInt(params.getOrDefault("numPesPerHost", "2")));
            request.setPeMips(Integer.parseInt(params.getOrDefault("peMips", "2000")));
            request.setRamPerHost(Integer.parseInt(params.getOrDefault("ramPerHost", "2048")));
            request.setBwPerHost(Integer.parseInt(params.getOrDefault("bwPerHost", "10000")));
            request.setStoragePerHost(Integer.parseInt(params.getOrDefault("storagePerHost", "100000")));
            request.setVmMips(Integer.parseInt(params.getOrDefault("vmMips", "1000")));
            request.setVmPes(Integer.parseInt(params.getOrDefault("vmPes", "2")));
            request.setVmRam(Integer.parseInt(params.getOrDefault("vmRam", "1024")));
            request.setVmBw(Integer.parseInt(params.getOrDefault("vmBw", "1000")));
            request.setVmSize(Integer.parseInt(params.getOrDefault("vmSize", "10000")));
            request.setVmScheduler(params.getOrDefault("vmScheduler", "TimeShared"));
            request.setNumCloudlets(Integer.parseInt(params.getOrDefault("numCloudlets", "100")));
            request.setWorkloadType(params.getOrDefault("workloadType", "CSV"));
            request.setUseDefaultWorkload(Boolean.parseBoolean(params.getOrDefault("useDefaultWorkload", "false")));
            request.setIterations(Integer.parseInt(params.getOrDefault("iterations", "1")));
            
            logger.debug("Successfully created SimulationRequest with {} iterations", request.getIterations());
        } catch (NumberFormatException e) {
            logger.error("Failed to parse numeric parameter: {}", e.getMessage());
            throw e;
        }
        
        return request;
    }
}
