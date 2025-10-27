package com.thesis.cloudsim.controller;

import com.thesis.cloudsim.dto.SimulationRequest;
import com.thesis.cloudsim.dto.IterationResults;
import com.thesis.cloudsim.dto.ComparisonResults;
import com.thesis.cloudsim.metrics.SimulationResults;
import com.thesis.cloudsim.simulation.EnhancedSimulationManager;
import com.thesis.cloudsim.service.IterationService;
import com.thesis.cloudsim.service.ComparisonService;
import com.thesis.cloudsim.algorithm.ISchedulingAlgorithm;
import com.thesis.cloudsim.matlab.MatlabIntegrationService;
import com.thesis.cloudsim.dto.ProcessedResults;
import com.thesis.cloudsim.service.AnalysisInterpretationService;
import com.thesis.cloudsim.util.SimulationProgressHolder;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.HashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;

@RestController
@RequestMapping("/api")
@Tag(name = "Simulation API", description = "Endpoints for running EPSO and EACO load balancing simulations")
public class ApiController {

    private static final Logger logger = LoggerFactory.getLogger(ApiController.class);
    private final ISchedulingAlgorithm epso;
    private final ISchedulingAlgorithm eaco;
    private final ObjectMapper objectMapper;
    
    @Autowired
    private IterationService iterationService;
    
    @Autowired
    private ComparisonService comparisonService;
    
    // I mark MATLAB service as optional so the app works without MATLAB installed
    @Autowired(required = false)
    private MatlabIntegrationService matlabService;

    @Autowired
    private AnalysisInterpretationService analysisService;

    public ApiController(@Qualifier("epso") ISchedulingAlgorithm epso,
                        @Qualifier("eaco") ISchedulingAlgorithm eaco,
                        ObjectMapper objectMapper) {
        this.epso = epso;
        this.eaco = eaco;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/run")
    @Operation(summary = "Run single simulation", 
              description = "Execute a single simulation run with EPSO or EACO algorithm")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Simulation completed successfully",
                    content = @Content(schema = @Schema(implementation = SimulationResults.class))),
        @ApiResponse(responseCode = "400", description = "Invalid request parameters")
    })
    public ResponseEntity<?> runSimulation(@RequestBody SimulationRequest request) {
        try {
            normalizeAndValidate(request);
            ensureSeed(request);
            return runOrIterate(request);
        } catch (Exception e) {
            return createErrorResponse(e, request.getOptimizationAlgorithm(), null);
        }
    }
    
    @PostMapping("/run-iterations")
    @Operation(summary = "Run multiple iterations", 
              description = "Execute multiple simulation iterations for statistical analysis")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Iterations completed successfully",
                    content = @Content(schema = @Schema(implementation = IterationResults.class))),
        @ApiResponse(responseCode = "400", description = "Invalid request parameters")
    })
    public ResponseEntity<?> runIterations(@RequestBody SimulationRequest request) {
        try {
            normalizeAndValidate(request);
            ensureSeed(request);
            ISchedulingAlgorithm algorithm = getAlgorithm(request.getOptimizationAlgorithm());
            logger.debug("Running {} iterations", request.getIterations());
            IterationResults results = iterationService.runIterations(algorithm, request);
            return ResponseEntity.ok(results);
        } catch (Exception e) {
            return createErrorResponse(e, request.getOptimizationAlgorithm(), String.valueOf(request.getIterations()));
        }
    }

    
    @PostMapping("/run-with-file")
    @Operation(summary = "Run simulation with CSV file", 
              description = "Execute simulation using uploaded CSV workload file")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Simulation completed successfully"),
        @ApiResponse(responseCode = "400", description = "Invalid file or parameters")
    })
    public ResponseEntity<?> runSimulationWithFile(
            @Parameter(description = "CSV workload file", required = true)
            @RequestParam("file") MultipartFile file,
            @Parameter(description = "Simulation parameters")
            @RequestParam Map<String, String> params) {
        Path tempFile = null;
        try {
            enforceCsvUploadPolicy(file);
            SimulationRequest request = mapParamsToRequest(params);
            // Save the uploaded file and attach its path before validation
            tempFile = saveUploadedFile(file);
            request.setWorkloadPath(tempFile.toString());
            // Now validate with the workloadPath in place
            normalizeAndValidate(request);
            ensureSeed(request);
            
            // Check if MATLAB plots are requested
            boolean enableMatlabPlots = Boolean.parseBoolean(params.getOrDefault("enableMatlabPlots", "false"));
            int iterations = request.getIterations();
            
            // I only generate plots for single iteration runs when explicitly requested
            if (enableMatlabPlots && iterations == 1 && matlabService != null && matlabService.isReady()) {
                logger.info("MATLAB plots requested for file-based simulation");
                return runWithMatlabPlots(request);
            } else {
                return runOrIterate(request);
            }
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
            enforceCsvUploadPolicy(file);
            SimulationRequest request = mapParamsToRequest(params);
            // Save the uploaded file and attach its path before validation
            tempFile = saveUploadedFile(file);
            request.setWorkloadPath(tempFile.toString());
            // Now validate with the workloadPath in place
            normalizeAndValidate(request);
            ensureSeed(request);
            
            // Check if MATLAB plots are requested for iterations
            boolean enableMatlabPlots = Boolean.parseBoolean(params.getOrDefault("enableMatlabPlots", "false"));
            
            // For iterations, we don't generate MATLAB plots (they only work for single runs)
            return runOrIterate(request);
        } catch (Exception e) {
            return createErrorResponse(e, params.get("optimizationAlgorithm"), params.get("iterations"));
        } finally {
            cleanupTempFile(tempFile);
        }
    }
    
    /**
     * Run comparison between EACO and EPSO with statistical analysis
     * Performs both paired t-test and Wilcoxon signed-rank test
     */
    @PostMapping("/compare")
    @Operation(summary = "Compare EPSO and EACO algorithms", 
              description = "Run both algorithms and perform statistical comparison with paired t-test and Wilcoxon signed-rank test")
    @ApiResponses(value = {
        @ApiResponse(responseCode = "200", description = "Comparison completed successfully",
                    content = @Content(schema = @Schema(implementation = ComparisonResults.class))),
        @ApiResponse(responseCode = "400", description = "Invalid request parameters")
    })
    public ResponseEntity<?> compareAlgorithms(@RequestBody SimulationRequest request) {
        try {
            logger.debug("Starting algorithm comparison with paired t-test analysis");
            // For comparison, do not require a single optimizationAlgorithm
            normalizeAndValidateForComparison(request);
            ensureSeed(request);
            
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
            enforceCsvUploadPolicy(file);
            SimulationRequest request = mapParamsToRequest(params);
            // Save the uploaded file and attach its path before validation
            tempFile = saveUploadedFile(file);
            request.setWorkloadPath(tempFile.toString());
            // For comparison, do not require a single optimizationAlgorithm
            normalizeAndValidateForComparison(request);
            ensureSeed(request);
            
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
        validateCsvHeaders(tempFile);
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
            // optional seed
            if (params.containsKey("seed")) {
                try {
                    request.setSeed(Long.parseLong(params.get("seed")));
                } catch (NumberFormatException ignore) {
                    // will be generated later
                }
            }
            
            logger.debug("Successfully created SimulationRequest with {} iterations", request.getIterations());
        } catch (NumberFormatException e) {
            logger.error("Failed to parse numeric parameter: {}", e.getMessage());
            throw e;
        }
        
        return request;
    }

    /**
     * Run simulation with MATLAB plot generation
     * I provide this method so file-based simulations can also generate plots
     */
    private ResponseEntity<?> runWithMatlabPlots(SimulationRequest request) throws Exception {
        long startTime = System.currentTimeMillis();
        
        ISchedulingAlgorithm algorithm = getAlgorithm(request.getOptimizationAlgorithm());
        EnhancedSimulationManager manager = new EnhancedSimulationManager(algorithm, request);
        SimulationResults rawResults = manager.run();
        
        long executionTime = System.currentTimeMillis() - startTime;
        
        /**
         * I add metadata before passing to MATLAB to avoid null errors
         */
        rawResults.setRunId(java.util.UUID.randomUUID().toString());
        rawResults.setSeed(request.getSeed());
        rawResults.setConfigSnapshot(createConfigSnapshot(request));
        rawResults.setDatasetId(request.getWorkloadPath() != null ? 
            "custom-" + request.getWorkloadPath().hashCode() : "synthetic");
        
        try {
            // Process results through MATLAB to generate visualization plots
            String algorithmName = request.getOptimizationAlgorithm() != null ? 
                request.getOptimizationAlgorithm() : "CloudSim";
            ProcessedResults processedResults = matlabService.processResults(rawResults, algorithmName);
            // Generate analysis (same as other runs)
            java.util.Map<String, Object> analysis = analysisService.generateCompleteAnalysis(processedResults, algorithmName);
            // respond with top-level fields so frontend can consume directly
            java.util.Map<String, Object> resp = new java.util.LinkedHashMap<>();
            resp.put("rawResults", processedResults.getRawResults());
            resp.put("plotData", processedResults.getPlotData());
            resp.put("plotMetadata", processedResults.getPlotMetadata());
            resp.put("analysis", analysis);
            resp.put("executionTimeMs", executionTime);
            logger.info("Returning simulation results with MATLAB plots and analysis in {} ms", executionTime);
            return ResponseEntity.ok(resp);
        } catch (Exception e) {
            logger.error("Failed to generate MATLAB plots, returning raw results", e);
            // Fall back to raw results if plot generation fails
            rawResults.setRunId(java.util.UUID.randomUUID().toString());
            rawResults.setSeed(request.getSeed());
            rawResults.setConfigSnapshot(createConfigSnapshot(request));
            // Also include analysis 
            ProcessedResults fallback = ProcessedResults.builder().rawResults(rawResults).build();
            java.util.Map<String, Object> analysis = analysisService.generateCompleteAnalysis(fallback, request.getOptimizationAlgorithm());
            java.util.Map<String, Object> resp = new java.util.LinkedHashMap<>();
            resp.put("simulationResults", rawResults);
            resp.put("analysis", analysis);
            resp.put("executionTimeMs", executionTime);
            return ResponseEntity.ok(resp);
        }
    }
    
    private ResponseEntity<?> runOrIterate(SimulationRequest request) throws Exception {
        long startTime = System.currentTimeMillis();
        
        if (request.getIterations() > 1) {
            ISchedulingAlgorithm algorithm = getAlgorithm(request.getOptimizationAlgorithm());
            IterationResults results = iterationService.runIterations(algorithm, request);
           
            return ResponseEntity.ok(results);
        }
        ISchedulingAlgorithm algorithm = getAlgorithm(request.getOptimizationAlgorithm());
        EnhancedSimulationManager manager = new EnhancedSimulationManager(algorithm, request);
        SimulationResults results = manager.run();
        
        long executionTime = System.currentTimeMillis() - startTime;
        
        // add run metadata for reproducibility
        results.setRunId(java.util.UUID.randomUUID().toString());
        results.setSeed(request.getSeed());
        results.setConfigSnapshot(createConfigSnapshot(request));
        results.setDatasetId(request.getWorkloadPath() != null ? 
            "custom-" + request.getWorkloadPath().hashCode() : "synthetic");
        
        //also return analysis like other endpoints do
        ProcessedResults processed = ProcessedResults.builder().rawResults(results).build();
        java.util.Map<String, Object> analysis = analysisService.generateCompleteAnalysis(processed, request.getOptimizationAlgorithm());
        java.util.Map<String, Object> resp = new java.util.LinkedHashMap<>();
        resp.put("simulationResults", results);
        resp.put("analysis", analysis);
        resp.put("executionTimeMs", executionTime);
        
        logger.debug("Simulation completed in {} ms for algorithm {}", executionTime, request.getOptimizationAlgorithm());
        
        return ResponseEntity.ok(resp);
    }
    
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

    // --- Helpers: validation, seed, and CSV upload policy ---
    private void normalizeAndValidate(SimulationRequest request) {
        // Normalize
        if (request.getOptimizationAlgorithm() == null) {
            throw new IllegalArgumentException("optimizationAlgorithm is required (EPSO or EACO)");
        }
        String algo = request.getOptimizationAlgorithm().trim().toUpperCase();
        if (!"EPSO".equals(algo) && !"EACO".equals(algo)) {
            throw new IllegalArgumentException("optimizationAlgorithm must be EPSO or EACO");
        }
        request.setOptimizationAlgorithm(algo);
        if (request.getVmScheduler() == null || request.getVmScheduler().isBlank()) {
            request.setVmScheduler("TimeShared");
        }
        if (request.getWorkloadType() == null || request.getWorkloadType().isBlank()) {
            request.setWorkloadType("CSV");
        }
        
        // Normalize workloadType and handle common variations
        String normalizedType = request.getWorkloadType().trim().toUpperCase();
        
        // Handle common aliases
        if ("RANDOM".equals(normalizedType)) {
            normalizedType = "RAND";
        }
        
        request.setWorkloadType(normalizedType);
        
        // basic numeric validation and sensible bounds
        ensurePositive("numHosts", request.getNumHosts(), 1, 10000);
        ensurePositive("numVMs", request.getNumVMs(), 1, 100000);
        ensurePositive("numPesPerHost", request.getNumPesPerHost(), 1, 256);
        ensurePositive("peMips", request.getPeMips(), 1, 100000);
        ensurePositive("ramPerHost", request.getRamPerHost(), 128, 1048576);
        ensurePositive("bwPerHost", request.getBwPerHost(), 1, Integer.MAX_VALUE);
        ensurePositive("storagePerHost", request.getStoragePerHost(), 1, Integer.MAX_VALUE);
        ensurePositive("vmMips", request.getVmMips(), 1, 100000);
        ensurePositive("vmPes", request.getVmPes(), 1, 256);
        ensurePositive("vmRam", request.getVmRam(), 128, 1048576);
        ensurePositive("vmBw", request.getVmBw(), 1, Integer.MAX_VALUE);
        ensurePositive("vmSize", request.getVmSize(), 1, Integer.MAX_VALUE);
        ensurePositive("numCloudlets", request.getNumCloudlets(), 1, 1000000);
        ensurePositive("iterations", request.getIterations(), 1, 1000);
        
        // workload type validation
        String workloadType = request.getWorkloadType();
        if (!"CSV".equals(workloadType) && !"RAND".equals(workloadType)) {
            throw new IllegalArgumentException("workloadType must be CSV or RAND");
        }
        
        // if CSV requested, ensure either default workload or path will be provided
        if ("CSV".equals(request.getWorkloadType()) && !request.isUseDefaultWorkload() && (request.getWorkloadPath() == null || request.getWorkloadPath().isBlank())) {
            throw new IllegalArgumentException("CSV workload requires a file upload or useDefaultWorkload=true");
        }
    }

    // variant used for comparison endpoints where a single algorithm is not required
    private void normalizeAndValidateForComparison(SimulationRequest request) {
        // Do not enforce optimizationAlgorithm here; comparison runs both internally
        if (request.getVmScheduler() == null || request.getVmScheduler().isBlank()) {
            request.setVmScheduler("TimeShared");
        }
        if (request.getWorkloadType() == null || request.getWorkloadType().isBlank()) {
            request.setWorkloadType("CSV");
        }
        
        // Normalize workloadType and handle common variations
        String normalizedType = request.getWorkloadType().trim().toUpperCase();
        
        // Handle common aliases
        if ("RANDOM".equals(normalizedType)) {
            normalizedType = "RAND";
        }
        
        request.setWorkloadType(normalizedType);

        // Basic numeric validation and sensible bounds
        ensurePositive("numHosts", request.getNumHosts(), 1, 10000);
        ensurePositive("numVMs", request.getNumVMs(), 1, 100000);
        ensurePositive("numPesPerHost", request.getNumPesPerHost(), 1, 256);
        ensurePositive("peMips", request.getPeMips(), 1, 100000);
        ensurePositive("ramPerHost", request.getRamPerHost(), 128, 1048576);
        ensurePositive("bwPerHost", request.getBwPerHost(), 1, Integer.MAX_VALUE);
        ensurePositive("storagePerHost", request.getStoragePerHost(), 1, Integer.MAX_VALUE);
        ensurePositive("vmMips", request.getVmMips(), 1, 100000);
        ensurePositive("vmPes", request.getVmPes(), 1, 256);
        ensurePositive("vmRam", request.getVmRam(), 128, 1048576);
        ensurePositive("vmBw", request.getVmBw(), 1, Integer.MAX_VALUE);
        ensurePositive("vmSize", request.getVmSize(), 1, Integer.MAX_VALUE);
        ensurePositive("numCloudlets", request.getNumCloudlets(), 1, 1000000);
        ensurePositive("iterations", request.getIterations(), 1, 1000);

        // Workload type
        if (!"CSV".equals(request.getWorkloadType()) && !"RAND".equals(request.getWorkloadType())) {
            throw new IllegalArgumentException("workloadType must be CSV or RAND");
        }

        // If CSV requested, ensure either default workload or path will be provided
        if ("CSV".equals(request.getWorkloadType()) && !request.isUseDefaultWorkload() && (request.getWorkloadPath() == null || request.getWorkloadPath().isBlank())) {
            throw new IllegalArgumentException("CSV workload requires a file upload or useDefaultWorkload=true");
        }
    }

    private void ensurePositive(String field, int value, int min, int max) {
        if (value < min || value > max) {
            throw new IllegalArgumentException(field + " out of range [" + min + ", " + max + "]: " + value);
        }
    }

    private void ensureSeed(SimulationRequest request) {
        if (request.getSeed() == null) {
            long generated = java.util.concurrent.ThreadLocalRandom.current().nextLong();
            request.setSeed(generated);
            logger.debug("Generated seed {} for request", generated);
        }
    }

    private void enforceCsvUploadPolicy(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File is required and must not be empty");
        }
        String name = file.getOriginalFilename() != null ? file.getOriginalFilename() : "";
        if (!name.toLowerCase().endsWith(".csv")) {
            throw new IllegalArgumentException("Only .csv files are accepted");
        }
long maxBytes = 1024L * 1024 * 1024; // 1 GB
        if (file.getSize() > maxBytes) {
            throw new IllegalArgumentException("File too large. Max 500 MB");
        }
    }

    // CSV template endpoint for clients
    @GetMapping(value = "/workload/template.csv")
    @Operation(summary = "Download CSV template", 
              description = "Download a template CSV file for workload upload")
    @ApiResponse(responseCode = "200", description = "CSV template file")
    public ResponseEntity<byte[]> getCsvTemplate() {
        String template = "length,pes,file_size,output_size,arrival_time\n" +
                          "5000,1,300,300,0\n" +
                          "12000,2,500,500,1\n";
        byte[] body = template.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.TEXT_PLAIN);
        headers.set(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=workload_template.csv");
        return ResponseEntity.ok().headers(headers).body(body);
    }

    // CSV schema endpoint for clients
    @GetMapping(value = "/workload/schema", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Get CSV schema", 
              description = "Get required CSV headers and field descriptions for workload upload")
    @ApiResponse(responseCode = "200", description = "CSV schema information")
    public ResponseEntity<Map<String, Object>> getCsvSchema() {
        Map<String, Object> schema = new java.util.LinkedHashMap<>();
        schema.put("requiredHeaders", java.util.List.of("length", "pes", "file_size", "output_size"));
        Map<String, String> desc = new java.util.LinkedHashMap<>();
        desc.put("length", "Integer > 0: cloudlet length in MI (alias: task_length)");
        desc.put("pes", "Integer > 0: number of processing elements per cloudlet");
        desc.put("file_size", "Integer >= 0: input file size (bytes)");
        desc.put("output_size", "Integer >= 0: output file size (bytes)");
        desc.put("arrival_time", "Number >= 0: optional arrival time in seconds");
        schema.put("descriptions", desc);
        schema.put("notes", "Headers are case-insensitive. Extra columns will be ignored. 'task_length' is accepted as an alias for 'length'. 'arrival_time' is optional.");
        return ResponseEntity.ok(schema);
    }
    
    @GetMapping("/progress")
    @Operation(summary = "Get current simulation progress", 
              description = "Returns current iteration and stage for CloudLoadingModal")
    @ApiResponse(responseCode = "200", description = "Current progress information")
    public ResponseEntity<Map<String, Object>> getCurrentProgress() {
        Map<String, Object> progress = new HashMap<>();
        progress.put("currentIteration", SimulationProgressHolder.getCurrentIteration());
        progress.put("totalIterations", SimulationProgressHolder.getTotalIterations());
        progress.put("currentStage", SimulationProgressHolder.getCurrentStage());
        progress.put("message", SimulationProgressHolder.getProgressInfo());
        return ResponseEntity.ok(progress);
    }
    
    @PostMapping("/cancel")
    @Operation(summary = "Cancel ongoing simulations", 
              description = "Request cancellation of any running simulations, iterations, or comparisons")
    @ApiResponse(responseCode = "200", description = "Cancellation requested successfully")
    public ResponseEntity<Map<String, Object>> cancelSimulation() {
        logger.info("Received simulation cancellation request via ApiController");
        
        try {
            EnhancedSimulationManager.cancelSimulation();
            
            // Also signal cancellation to services
            iterationService.requestCancellation();
            comparisonService.requestCancellation();
            
            // Reset progress on cancellation
            SimulationProgressHolder.reset();
            
            Map<String, Object> response = new HashMap<>();
            response.put("message", "Simulation cancellation requested");
            response.put("status", "cancelled");
            response.put("timestamp", System.currentTimeMillis());
            response.put("controller", "ApiController");
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            logger.error("Error during cancellation", e);
            
            Map<String, Object> response = new HashMap<>();
            response.put("error", "Failed to cancel simulation");
            response.put("message", e.getMessage());
            
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    private void validateCsvHeaders(Path csvPath) throws IOException {
        try (java.io.BufferedReader reader = java.nio.file.Files.newBufferedReader(csvPath)) {
            String header = reader.readLine();
            if (header == null) {
                throw new IllegalArgumentException("CSV is empty");
            }
            String[] cols = header.split(",");
            java.util.Set<String> present = new java.util.HashSet<>();
            for (String c : cols) {
                present.add(c.trim().toLowerCase());
            }

            // Accept normalized schema: length|task_length + pes (file_size, output_size optional)
            boolean hasLength = present.contains("length") || present.contains("task_length");
            boolean hasPes = present.contains("pes");
            boolean normalizedAcceptable = hasLength && hasPes;

            // Accept Google-like schema if cpu_request or arrival_ts present (loader can handle it)
            boolean googleAcceptable = present.contains("cpu_request") || present.contains("arrival_ts");

            if (!(normalizedAcceptable || googleAcceptable)) {
                java.util.Set<String> missing = new java.util.HashSet<>();
                if (!hasLength) missing.add("length (or task_length)");
                if (!hasPes) missing.add("pes");
                throw new IllegalArgumentException("CSV missing required headers: " + missing);
            }
        }
    }
}
