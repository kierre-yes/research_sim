package com.thesis.cloudsim.service;

import com.thesis.cloudsim.dto.ProcessedResults;
import com.thesis.cloudsim.matlab.MatlabIntegrationService;
import com.thesis.cloudsim.metrics.SimulationResults;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

@Service
@EnableAsync
public class AsyncPlotGenerationService {
    
    private static final Logger logger = LoggerFactory.getLogger(AsyncPlotGenerationService.class);
    
    @Autowired(required = false)
    private MatlabIntegrationService matlabService;
    
    private final Map<String, PlotGenerationStatus> plotStatusMap = new ConcurrentHashMap<>();
    private final Map<String, ProcessedResults> plotResultsMap = new ConcurrentHashMap<>();
    
    public enum PlotStatus {
        PENDING,
        IN_PROGRESS,
        COMPLETED,
        FAILED
    }
    
    public static class PlotGenerationStatus {
        private final String simulationId;
        private PlotStatus status;
        private String message;
        private long startTime;
        private long endTime;
        private double progress;
        
        public PlotGenerationStatus(String simulationId) {
            this.simulationId = simulationId;
            this.status = PlotStatus.PENDING;
            this.startTime = System.currentTimeMillis();
            this.progress = 0.0;
        }
        
        public String getSimulationId() { return simulationId; }
        public PlotStatus getStatus() { return status; }
        public void setStatus(PlotStatus status) { this.status = status; }
        public String getMessage() { return message; }
        public void setMessage(String message) { this.message = message; }
        public long getStartTime() { return startTime; }
        public long getEndTime() { return endTime; }
        public void setEndTime(long endTime) { this.endTime = endTime; }
        public double getProgress() { return progress; }
        public void setProgress(double progress) { this.progress = progress; }
        
        public long getElapsedTime() {
            if (endTime > 0) {
                return endTime - startTime;
            }
            return System.currentTimeMillis() - startTime;
        }
    }
    
    public String submitForPlotGeneration(ProcessedResults processedResults, String algorithmName) {
        String simulationId = UUID.randomUUID().toString();
        
        PlotGenerationStatus status = new PlotGenerationStatus(simulationId);
        plotStatusMap.put(simulationId, status);
        
        generatePlotsAsync(simulationId, processedResults, algorithmName);
        
        logger.info("Submitted simulation {} for async plot generation", simulationId);
        return simulationId;
    }
    
    @Async
    public CompletableFuture<ProcessedResults> generatePlotsAsync(String simulationId, ProcessedResults processedResults, String algorithmName) {
        PlotGenerationStatus status = plotStatusMap.get(simulationId);
        
        try {
            logger.info("Starting async plot generation for simulation {}", simulationId);
            status.setStatus(PlotStatus.IN_PROGRESS);
            status.setMessage("Initializing MATLAB engine...");
            status.setProgress(0.1);
            
            // Check if MATLAB service is available
            if (matlabService == null) {
                logger.warn("MATLAB service not available, returning results without plots");
                ProcessedResults resultsWithoutPlots = ProcessedResults.builder()
                        .simulationId(simulationId)
                        .plotData(new java.util.HashMap<>())
                        .rawResults(processedResults.getRawResults())
                        .plotMetadata(new java.util.ArrayList<>())
                        .build();
                
                plotResultsMap.put(simulationId, resultsWithoutPlots);
                status.setStatus(PlotStatus.COMPLETED);
                status.setMessage("Simulation completed (MATLAB not available - plots skipped)");
                status.setProgress(1.0);
                status.setEndTime(System.currentTimeMillis());
                
                return CompletableFuture.completedFuture(resultsWithoutPlots);
            }
            
            // Check if MATLAB is ready
            if (!matlabService.isReady()) {
                status.setMessage("Waiting for MATLAB engine...");
                status.setProgress(0.2);
                Thread.sleep(2000);
                
                // Check again after waiting
                if (!matlabService.isReady()) {
                    logger.warn("MATLAB engine not ready after waiting, returning results without plots");
                    ProcessedResults resultsWithoutPlots = ProcessedResults.builder()
                            .simulationId(simulationId)
                            .plotData(new java.util.HashMap<>())
                            .rawResults(processedResults.getRawResults())
                            .plotMetadata(new java.util.ArrayList<>())
                            .build();
                    
                    plotResultsMap.put(simulationId, resultsWithoutPlots);
                    status.setStatus(PlotStatus.COMPLETED);
                    status.setMessage("Simulation completed (MATLAB not ready - plots skipped)");
                    status.setProgress(1.0);
                    status.setEndTime(System.currentTimeMillis());
                    
                    return CompletableFuture.completedFuture(resultsWithoutPlots);
                }
            }
            
            status.setMessage("Generating plots...");
            status.setProgress(0.5);
            
            // Process results with MATLAB (now handles failures gracefully internally)
            ProcessedResults resultsWithPlots = matlabService.processResults(processedResults.getRawResults(), algorithmName);
            
            // Check if plots were actually generated
            boolean plotsGenerated = resultsWithPlots.getPlotData() != null &&
                                    !resultsWithPlots.getPlotData().isEmpty() &&
                                    resultsWithPlots.getPlotData().get("plotPaths") != null;
            
            resultsWithPlots = ProcessedResults.builder()
                    .simulationId(simulationId)
                    .plotData(resultsWithPlots.getPlotData() != null ? resultsWithPlots.getPlotData() : new java.util.HashMap<>())
                    .rawResults(resultsWithPlots.getRawResults())
                    .plotMetadata(resultsWithPlots.getPlotMetadata() != null ? resultsWithPlots.getPlotMetadata() : new java.util.ArrayList<>())
                    .build();
            
            status.setProgress(0.9);
            status.setMessage("Finalizing...");
            
            plotResultsMap.put(simulationId, resultsWithPlots);
            
            status.setStatus(PlotStatus.COMPLETED);
            if (plotsGenerated) {
                status.setMessage("Plot generation completed successfully");
            } else {
                status.setMessage("Simulation completed (plots could not be generated)");
            }
            status.setProgress(1.0);
            status.setEndTime(System.currentTimeMillis());
            
            logger.info("Completed async processing for simulation {} in {} ms (plots: {})",
                    simulationId, status.getElapsedTime(), plotsGenerated ? "yes" : "no");
            
            return CompletableFuture.completedFuture(resultsWithPlots);
            
        } catch (Exception e) {
            logger.error("Error during async plot generation for simulation {}: {}", simulationId, e.getMessage());
            
            // Even on error, return the raw results
            ProcessedResults fallbackResults = ProcessedResults.builder()
                    .simulationId(simulationId)
                    .plotData(new java.util.HashMap<>())
                    .rawResults(processedResults.getRawResults())
                    .plotMetadata(new java.util.ArrayList<>())
                    .build();
            
            plotResultsMap.put(simulationId, fallbackResults);
            
            status.setStatus(PlotStatus.COMPLETED);
            status.setMessage("Simulation completed (plot generation error: " + e.getMessage() + ")");
            status.setProgress(1.0);
            status.setEndTime(System.currentTimeMillis());
            
            return CompletableFuture.completedFuture(fallbackResults);
        }
    }
    
    public PlotGenerationStatus getStatus(String simulationId) {
        return plotStatusMap.get(simulationId);
    }
    
    public ProcessedResults getResults(String simulationId) {
        return plotResultsMap.get(simulationId);
    }
    
    public boolean isPlotsReady(String simulationId) {
        PlotGenerationStatus status = plotStatusMap.get(simulationId);
        return status != null && status.getStatus() == PlotStatus.COMPLETED;
    }
    
    public void cleanupOldResults(long maxAgeMs) {
        long now = System.currentTimeMillis();
        plotStatusMap.entrySet().removeIf(entry -> {
            PlotGenerationStatus status = entry.getValue();
            if (status.getEndTime() > 0 && (now - status.getEndTime()) > maxAgeMs) {
                plotResultsMap.remove(entry.getKey());
                return true;
            }
            return false;
        });
    }
}
