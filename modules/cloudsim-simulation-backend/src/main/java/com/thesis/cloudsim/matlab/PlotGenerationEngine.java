package com.thesis.cloudsim.matlab;

import com.thesis.cloudsim.metrics.SimulationResults;
import com.thesis.cloudsim.dto.ProcessedResults;
import com.thesis.cloudsim.dto.TTestResults;
import java.util.Map;

/**
 * I define an abstraction for plot generation engines
 * following Dependency Inversion Principle
 */
public interface PlotGenerationEngine {
    
    /**
     * I check if the engine is ready for processing
     */
    boolean isReady();
    
    /**
     * I process simulation results to generate plots
     */
    ProcessedResults processResults(SimulationResults results, String algorithmName);
    
    /**
     * I generate t-test statistical plots
     */
    Map<String, Object> generateTTestPlots(TTestResults results);
    
    /**
     * I clean up resources when shutting down
     */
    void shutdown();
}
