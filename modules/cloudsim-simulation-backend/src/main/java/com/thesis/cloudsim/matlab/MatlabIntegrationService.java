package com.thesis.cloudsim.matlab;

import com.mathworks.engine.MatlabEngine;
import com.thesis.cloudsim.metrics.SimulationResults;
import com.thesis.cloudsim.dto.ProcessedResults;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.UUID;

@Service
public class MatlabIntegrationService {

    private volatile MatlabEngine engine;

    // Try to connect lazily; no heavy work on context startup
    @org.springframework.context.event.EventListener(org.springframework.boot.context.event.ApplicationReadyEvent.class)
    public void warmUp() {
        // Fire-and-forget preload so first user request is fast
        java.util.concurrent.CompletableFuture.runAsync(this::ensureEngine);
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
        try {
            ensureEngine();
            // Pass Java data to MATLAB workspace (example: summary metrics only)
            engine.putVariable("avgResp", results.getSummary().getAverageResponseTime());
            engine.putVariable("makespan", results.getSummary().getMakespan());

            // Call a MATLAB script/function that generates plots under a unique folder
            String runId = UUID.randomUUID().toString();
            engine.putVariable("runId", runId);
            engine.eval("plotPaths = generateComparisonPlots(avgResp, makespan, runId);");

            // Retrieve JSON string with chart-ready structure
            String json = engine.getVariable("plotJson");
            java.util.Map<String,Object> plotMap = new com.fasterxml.jackson.databind.ObjectMapper()
                    .readValue(json, java.util.Map.class);

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
