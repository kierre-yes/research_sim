package com.thesis.cloudsim.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Results from comparing EACO and EPSO algorithms with statistical analysis
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ComparisonResults {
    private IterationResults eacoResults;
    private IterationResults epsoResults;
    private TTestResults tTestResults;
    private String workloadName;
    private int iterations;
    private long totalExecutionTime;
    
    /**
     * Get summary for frontend display
     */
    public String getSummaryMessage() {
        if (tTestResults == null) {
            return "No statistical analysis available";
        }
        
        return String.format(
            "Comparison completed: %s shows superior performance with %d/%d metrics showing significant differences (p < 0.05)",
            tTestResults.getOverallWinner(),
            tTestResults.getSignificantDifferences(),
            tTestResults.getMetricTests().size()
        );
    }
}
