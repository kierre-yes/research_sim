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
    
    // store plot generation status and results
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
        
        // getters and setters
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
    
    /**
     * Submit simulation results for async plot generation
     * @return simulationId for tracking
     */
    public String submitForPlotGeneration(SimulationResults results, String algorithmName) {
        String simulationId = UUID.randomUUID().toString();
        
        PlotGenerationStatus status = new PlotGenerationStatus(simulationId);
        plotStatusMap.put(simulationId, status);
        
        // start async plot generation
        generatePlotsAsync(simulationId, results, algorithmName);
        
        logger.info("Submitted simulation {} for async plot generation", simulationId);
        return simulationId;
    }
    
    /**
     * Generate plots asynchronously in background
     */
    @Async
    public CompletableFuture<ProcessedResults> generatePlotsAsync(String simulationId, SimulationResults results, String algorithmName) {
        PlotGenerationStatus status = plotStatusMap.get(simulationId);
        
        try {
            logger.info("Starting async plot generation for simulation {}", simulationId);
            status.setStatus(PlotStatus.IN_PROGRESS);
            status.setMessage("Initializing MATLAB engine...");
            status.setProgress(0.1);
            
            if (matlabService == null) {
                throw new RuntimeException("MATLAB service not available");
            }
            
            // ensure matlab engine is ready
            if (!matlabService.isReady()) {
                status.setMessage("Waiting for MATLAB engine...");
                status.setProgress(0.2);
                // wait a bit for engine to warm up
                Thread.sleep(2000);
            }
            
            status.setMessage("Generating plots...");
            status.setProgress(0.5);
            
            // generate plots using matlab service
            ProcessedResults processedResults = matlabService.processResults(results, algorithmName);
            
            // override simulation id to match our tracking
            processedResults = ProcessedResults.builder()
                    .simulationId(simulationId)
                    .plotData(processedResults.getPlotData())
                    .rawResults(processedResults.getRawResults())
                    .build();
            
            status.setProgress(0.9);
            status.setMessage("Finalizing plots...");
            
            // store results
            plotResultsMap.put(simulationId, processedResults);
            
            status.setStatus(PlotStatus.COMPLETED);
            status.setMessage("Plot generation completed successfully");
            status.setProgress(1.0);
            status.setEndTime(System.currentTimeMillis());
            
            logger.info("Completed async plot generation for simulation {} in {} ms", 
                    simulationId, status.getElapsedTime());
            
            return CompletableFuture.completedFuture(processedResults);
            
        } catch (Exception e) {
            logger.error("Failed to generate plots for simulation {}", simulationId, e);
            
            status.setStatus(PlotStatus.FAILED);
            status.setMessage("Error: " + e.getMessage());
            status.setEndTime(System.currentTimeMillis());
            
            return CompletableFuture.failedFuture(e);
        }
    }
    
    /**
     * Get plot generation status
     */
    public PlotGenerationStatus getStatus(String simulationId) {
        return plotStatusMap.get(simulationId);
    }
    
    /**
     * Get completed plot results
     */
    public ProcessedResults getResults(String simulationId) {
        return plotResultsMap.get(simulationId);
    }
    
    /**
     * Check if plots are ready
     */
    public boolean isPlotsReady(String simulationId) {
        PlotGenerationStatus status = plotStatusMap.get(simulationId);
        return status != null && status.getStatus() == PlotStatus.COMPLETED;
    }
    
    /**
     * Clean up old results (optional, call periodically)
     */
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
