package com.thesis.cloudsim.dto;

import com.thesis.cloudsim.metrics.SimulationResults;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

/**
 * Wrapper class to maintain backward compatibility with frontend
 * while providing iteration results
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class IterationResponseWrapper {
    // Original iteration results
    private IterationResults iterationResults;
    
    // Best individual result for frontend compatibility
    private SimulationResults.Summary summary;
    private java.util.List<SimulationResults.VmUtilization> vmUtilization;
    private java.util.List<SimulationResults.SchedulingLogEntry> schedulingLog;
    private double energyConsumption;
    
    // Additional iteration-specific fields
    private int totalIterations;
    private String algorithm;
    private java.util.Map<String, Double> averageMetrics;
    private java.util.Map<String, Double> minMetrics;
    private java.util.Map<String, Double> maxMetrics;
    private java.util.Map<String, Double> stdDevMetrics;
    private long totalExecutionTime;
    private double successRate;
    
    /**
     * Create wrapper from iteration results
     */
    public static IterationResponseWrapper fromIterationResults(IterationResults results) {
        IterationResponseWrapper wrapper = new IterationResponseWrapper();
        
        // Set iteration results
        wrapper.setIterationResults(results);
        wrapper.setTotalIterations(results.getTotalIterations());
        wrapper.setAlgorithm(results.getAlgorithm());
        wrapper.setAverageMetrics(results.getAverageMetrics());
        wrapper.setMinMetrics(results.getMinMetrics());
        wrapper.setMaxMetrics(results.getMaxMetrics());
        wrapper.setStdDevMetrics(results.getStdDevMetrics());
        wrapper.setTotalExecutionTime(results.getTotalExecutionTime());
        wrapper.setSuccessRate(results.getSuccessRate());
        
        // Extract best individual result for backward compatibility
        SimulationResults bestResult = results.getBestResult();
        if (bestResult != null) {
            wrapper.setSummary(bestResult.getSummary());
            wrapper.setVmUtilization(bestResult.getVmUtilization());
            wrapper.setSchedulingLog(bestResult.getSchedulingLog());
            wrapper.setEnergyConsumption(bestResult.getEnergyConsumption());
        }
        
        return wrapper;
    }
}
