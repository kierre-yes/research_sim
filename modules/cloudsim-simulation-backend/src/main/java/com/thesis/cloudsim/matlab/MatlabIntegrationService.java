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
        if (engine != null) return;
        try {
            // Preferred: connect to pre-started shared engine
            engine = MatlabEngine.connectMatlab("thesisEngine");
        } catch (Exception connectEx) {
            try {
                // Fallback: launch a new MATLAB process (takes ~50 s)
                engine = MatlabEngine.startMatlab();
            } catch (Exception startEx) {
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
        try {
            ensureEngine();
            // Convert Java results to MATLAB-compatible structure
            String runId = UUID.randomUUID().toString();
            
            // Create MATLAB struct with all results data
            engine.putVariable("runId", runId);
            engine.putVariable("algorithmName", algorithmName);
            
            // Pass summary metrics
            engine.eval("results = struct();");
            engine.eval("results.summary = struct();");
            engine.putVariable("makespan", results.getSummary().getMakespan());
            engine.putVariable("avgResponseTime", results.getSummary().getResponseTime());
            engine.putVariable("resourceUtilization", results.getSummary().getResourceUtilization());
            engine.putVariable("imbalanceDegree", results.getSummary().getLoadBalance());
            engine.eval("results.summary.makespan = makespan;");
            engine.eval("results.summary.averageResponseTime = avgResponseTime;");
            engine.eval("results.summary.resourceUtilization = resourceUtilization;");
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
            engine.eval("plotPaths = generateComparisonPlots(avgResponseTime, makespan, runId);");
            
            // Retrieve JSON string with chart-ready structure
            String json = engine.getVariable("plotJson");
            // Reuse ObjectMapper instance
            java.util.Map<String,Object> plotMap = objectMapper
                    .readValue(json, new com.fasterxml.jackson.core.type.TypeReference<java.util.Map<String,Object>>() {});

            return ProcessedResults.builder()
                    .simulationId(runId)
                    .plotData(plotMap)
                    .rawResults(results)
                    .build();
        } catch (Exception e) {
            throw new RuntimeException("MATLAB processing failed", e);
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
