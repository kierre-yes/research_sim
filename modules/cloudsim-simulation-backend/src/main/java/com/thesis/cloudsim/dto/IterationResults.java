package com.thesis.cloudsim.dto;

import com.thesis.cloudsim.metrics.SimulationResults;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;
import java.util.Comparator;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class IterationResults {
    private int totalIterations;
    private String algorithm;
    private List<SimulationResults> individualResults;
    private Map<String, Double> averageMetrics;
    private Map<String, Double> minMetrics;
    private Map<String, Double> maxMetrics;
    private Map<String, Double> stdDevMetrics;
    private long totalExecutionTime;
    private double successRate;

    // run metadata
    private String runId;
    private Long seed;
    private Map<String, Object> configSnapshot;
    private String datasetId;
    
    /**
     * Get the best individual result based on fitness score
     * @return Best SimulationResults or null if no results available
     */
    public SimulationResults getBestResult() {
        if (individualResults == null || individualResults.isEmpty()) {
            return null;
        }
        
        return individualResults.stream()
            .filter(result -> result != null && result.getSummary() != null)
            .min(Comparator.comparing(result -> result.getSummary().getFitness()))
            .orElse(individualResults.get(0));
    }
}
