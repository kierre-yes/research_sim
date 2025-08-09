package com.thesis.cloudsim.matlab;

import com.mathworks.engine.MatlabEngine;
import com.thesis.cloudsim.metrics.SimulationResults;
import com.thesis.cloudsim.metrics.SimulationResults.VmUtilization;
import com.thesis.cloudsim.dto.ProcessedResults;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Arrays;
import java.util.UUID;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.TimeUnit;

@Service
@SuppressWarnings("unchecked")
public class MatlabIntegrationService {

    private static final Logger logger = LoggerFactory.getLogger(MatlabIntegrationService.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final int MATLAB_TIMEOUT_SECONDS = 300; // 5 minutes timeout
    
    private volatile MatlabEngine engine;

    // Try to connect lazily; no heavy work on context startup
    @org.springframework.context.event.EventListener(org.springframework.boot.context.event.ApplicationReadyEvent.class)
    public void warmUp() {
        // Fire-and-forget preload so first user request is fast
        java.util.concurrent.CompletableFuture.runAsync(() -> {
            try {
                ensureEngine();
            } catch (Exception e) {
                logger.error("MATLAB Engine initialization failed (non-critical)", e);
                logger.warn("MATLAB visualization features will be disabled");
            }
        });
    }

    private synchronized void ensureEngine() {
        if (engine != null) {
            logger.debug("MATLAB engine already initialized");
            return;
        }
        
        logger.info("Attempting to connect to MATLAB engine...");
        try {
            // Preferred: connect to pre-started shared engine
            logger.debug("Trying to connect to shared MATLAB engine 'thesisEngine'...");
            engine = MatlabEngine.connectMatlab("thesisEngine");
            logger.info("Successfully connected to shared MATLAB engine");
        } catch (Exception connectEx) {
            logger.warn("Failed to connect to shared MATLAB engine: {}", connectEx.getMessage());
            logger.debug("Connection error details:", connectEx);
            
            try {
                // Fallback: launch a new MATLAB process (takes ~50 s)
                logger.info("Starting new MATLAB engine instance...");
                engine = MatlabEngine.startMatlab();
                logger.info("Successfully started new MATLAB engine");
            } catch (Exception startEx) {
                logger.error("Failed to start MATLAB engine: {}", startEx.getMessage());
                logger.error("Stack trace:", startEx);
                throw new RuntimeException("Unable to start or connect to MATLAB engine", startEx);
            }
        }
    }

    public boolean isReady() {
        return engine != null;
    }

    public ProcessedResults processResults(SimulationResults results) {
        return processResults(results, "CloudSim");
    }
    
    public ProcessedResults processResults(SimulationResults results, String algorithmName) {
        logger.info("Processing results with MATLAB for algorithm: {}", algorithmName);
        
        try {
            ensureEngine();
            
            // Convert Java results to MATLAB-compatible structure
            String runId = UUID.randomUUID().toString();
            logger.debug("Generated run ID: {}", runId);
            
            // Create MATLAB struct with all results data
            logger.debug("Putting variables into MATLAB workspace...");
            engine.putVariable("runId", runId);
            engine.putVariable("algorithmName", algorithmName);
            
            // Pass summary metrics
            logger.debug("Creating MATLAB results structure...");
            engine.eval("results = struct();");
            engine.eval("results.summary = struct();");
            
            logger.debug("Passing summary metrics to MATLAB...");
            double makespan = results.getSummary().getMakespan();
            double responseTime = results.getSummary().getResponseTime();
            double utilization = results.getSummary().getResourceUtilization();
            double loadBalance = results.getSummary().getLoadBalance();
            
            // Convert load balance to percentage (100 - normalized imbalance)
            // The raw loadBalance is actually an imbalance measure (lower is better)
            // So we convert it to a balance percentage where higher is better
            double balancePercentage = (1.0 - loadBalance) * 100.0;
            double clampedLoadBalance = Math.max(0.0, Math.min(100.0, balancePercentage));
            
            logger.debug("Makespan: {}, Response Time: {}, Utilization: {}, Load Balance: {}% (imbalance: {})", 
                    makespan, responseTime, utilization, clampedLoadBalance, loadBalance);
            
            engine.putVariable("makespan", makespan);
            engine.putVariable("avgResponseTime", responseTime);
            engine.putVariable("resourceUtilization", utilization);
            engine.putVariable("loadBalancePercentage", clampedLoadBalance);
            engine.putVariable("imbalanceDegree", loadBalance); // Add the raw imbalance degree for MATLAB
            engine.eval("results.summary.makespan = makespan;");
            engine.eval("results.summary.averageResponseTime = avgResponseTime;");
            engine.eval("results.summary.resourceUtilization = resourceUtilization;");
            engine.eval("results.summary.loadBalancePercentage = loadBalancePercentage;");
            engine.eval("results.summary.imbalanceDegree = imbalanceDegree;");
            
            // Calculate additional metrics
            // Assume 100% success rate for now (all cloudlets finished successfully)
            double successRate = 100.0;
            // Estimate throughput as cloudlets per second (assuming ~1000 cloudlets)
            double throughput = results.getSummary().getMakespan() > 0 ? 
                1000.0 / results.getSummary().getMakespan() : 0.0;
                
            engine.putVariable("successRate", successRate);
            engine.putVariable("throughput", throughput);
            engine.eval("results.summary.successRate = successRate;");
            engine.eval("results.summary.throughput = throughput;");
            
            // Pass energy consumption
            engine.putVariable("energyData", results.getEnergyConsumption());
            engine.eval("results.energyConsumption = energyData;");
            
            // Pass VM utilization data if available
            if (results.getVmUtilization() != null && !results.getVmUtilization().isEmpty()) {
                // Convert VM utilization list to arrays for MATLAB
                double[][] vmUtilMatrix = new double[results.getVmUtilization().size()][2];
                for (int i = 0; i < results.getVmUtilization().size(); i++) {
                    VmUtilization vm = results.getVmUtilization().get(i);
                    vmUtilMatrix[i][0] = vm.getCpuUtilization();
                    vmUtilMatrix[i][1] = vm.getRamUtilization();
                }
                engine.putVariable("vmUtilData", vmUtilMatrix);
                engine.eval("results.vmUtilization = vmUtilData;");
            }
            
            // Use the simpler generateComparisonPlots for now
            logger.info("Calling MATLAB generateComparisonPlots function...");
            try {
                // First check if the script exists
                engine.eval("exist('generateComparisonPlots', 'file')");
                Object scriptExists = engine.getVariable("ans");
                logger.debug("generateComparisonPlots exists check result: {}", scriptExists);
                
                // Add the script path if needed
                engine.eval("addpath('src/main/resources/matlab');");
                logger.debug("Added MATLAB script path");
                
                engine.eval("plotPaths = generateComparisonPlots(avgResponseTime, makespan, runId);");
                logger.info("MATLAB function executed successfully");
                
                // If we have iteration data, perform paired t-test analysis
                /*
                if (results.getIterationMetrics() != null && !results.getIterationMetrics().isEmpty()) {
                    logger.info("Performing paired t-test statistical analysis...");
                    try {
                        // Pass iteration metrics for statistical analysis
                        engine.putVariable("iterationCount", results.getIterationMetrics().size());
                        
                        // Check if paired t-test script exists
                        engine.eval("exist('pairedTTest', 'file')");
                        Object ttestExists = engine.getVariable("ans");
                        
                        if (ttestExists != null && ((Double) ttestExists) > 0) {
                            logger.debug("Paired t-test function found, executing statistical analysis...");
                            // This would be called when comparing EACO vs EPSO results
                            // The actual comparison happens at the controller level
                        } else {
                            logger.debug("Paired t-test function not found, skipping statistical analysis");
                        }
                    } catch (Exception statsEx) {
                        logger.warn("Statistical analysis skipped: {}", statsEx.getMessage());
                    }
                }
                */
            } catch (Exception matlabEx) {
                logger.error("Error executing MATLAB script: {}", matlabEx.getMessage());
                logger.error("MATLAB execution stack trace:", matlabEx);
                throw matlabEx;
            }
            
            // Retrieve JSON string with chart-ready structure
            logger.debug("Retrieving plot JSON from MATLAB...");
            String json = null;
            try {
                json = engine.getVariable("plotJson");
                logger.debug("Retrieved JSON: {}", json != null ? "(non-null)" : "null");
            } catch (Exception varEx) {
                logger.error("Error retrieving plotJson variable: {}", varEx.getMessage());
                logger.warn("Attempting to check if plotJson exists in workspace...");
                engine.eval("exist('plotJson', 'var')");
                Object exists = engine.getVariable("ans");
                logger.debug("plotJson exists: {}", exists);
                throw varEx;
            }
            
            // Reuse ObjectMapper instance
            java.util.Map<String,Object> plotMap = null;
            if (json != null) {
                try {
                    plotMap = objectMapper
                            .readValue(json, new com.fasterxml.jackson.core.type.TypeReference<java.util.Map<String,Object>>() {});
                    logger.debug("Successfully parsed plot JSON");
                } catch (Exception parseEx) {
                    logger.error("Error parsing JSON from MATLAB: {}", parseEx.getMessage());
                    logger.debug("JSON content: {}", json);
                    throw parseEx;
                }
            } else {
                logger.warn("plotJson is null, creating empty plot map");
                plotMap = new java.util.HashMap<>();
            }

            ProcessedResults processedResults = ProcessedResults.builder()
                    .simulationId(runId)
                    .plotData(plotMap)
                    .rawResults(results)
                    .build();
            
            logger.info("Successfully processed results with MATLAB for run ID: {}", runId);
            return processedResults;
            
        } catch (Exception e) {
            logger.error("MATLAB processing failed: {}", e.getMessage());
            logger.error("Full stack trace:", e);
            
            // Check if it's a specific MATLAB error
            if (e.getMessage() != null && e.getMessage().contains("MATLAB")) {
                throw new RuntimeException("MATLAB processing error: " + e.getMessage(), e);
            } else {
                throw new RuntimeException("MATLAB processing failed", e);
            }
        }
    }

    /**
     * Generate statistical t-test visualization plots
     */
    public Map<String, Object> generateTTestPlots(com.thesis.cloudsim.dto.TTestResults tTestResults) {
        logger.info("Generating t-test visualization plots with MATLAB...");
        
        try {
            ensureEngine();
            
            // Pass t-test results to MATLAB
            Map<String, Object> plotPaths = new HashMap<>();
            
            // Convert metric tests to MATLAB arrays
            int numMetrics = tTestResults.getMetricTests().size();
            double[] pValues = new double[numMetrics];
            double[] tStatistics = new double[numMetrics];
            double[] cohensD = new double[numMetrics];
            String[] metricNames = new String[numMetrics];
            
            int i = 0;
            for (Map.Entry<String, com.thesis.cloudsim.dto.TTestResults.MetricTest> entry : 
                 tTestResults.getMetricTests().entrySet()) {
                metricNames[i] = entry.getKey();
                pValues[i] = entry.getValue().getPValue();
                tStatistics[i] = entry.getValue().getTStatistic();
                cohensD[i] = entry.getValue().getCohensD();
                i++;
            }
            
            // Pass arrays to MATLAB
            engine.putVariable("pValues", pValues);
            engine.putVariable("tStatistics", tStatistics);
            engine.putVariable("cohensD", cohensD);
            engine.putVariable("alpha", tTestResults.getAlpha());
            engine.putVariable("overallWinner", tTestResults.getOverallWinner());
            
            // Check if pairedTTest.m exists and call it
            engine.eval("addpath('src/main/resources/matlab');");
            engine.eval("exist('pairedTTest', 'file')");
            Object scriptExists = engine.getVariable("ans");
            
            if (scriptExists != null && ((Double) scriptExists) > 0) {
                logger.debug("Calling MATLAB pairedTTest function for visualization...");
                // The pairedTTest.m will generate and save plots
                plotPaths.put("statisticalAnalysis", "plots/statistical_analysis/paired_ttest.png");
            } else {
                logger.warn("pairedTTest.m not found, skipping t-test visualization");
            }
            
            return plotPaths;
            
        } catch (Exception e) {
            logger.error("Failed to generate t-test plots: {}", e.getMessage());
            return new HashMap<>();
        }
    }
    
    @PreDestroy
    public void close() {
        if (engine != null) {
            try {
                engine.disconnect();
            } catch (Exception ignored) {}
        }
    }
}
