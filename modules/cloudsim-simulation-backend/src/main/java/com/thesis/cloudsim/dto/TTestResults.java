package com.thesis.cloudsim.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.util.Map;

/**
 * Paired t-test statistical analysis results
 * Based on manuscript methodology for comparing EACO vs EPSO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TTestResults {
    private double alpha = 0.05; // Significance level
    private int sampleSize;
    private Map<String, MetricTest> metricTests;
    private Map<String, WilcoxonTest> wilcoxonTests;
    private String overallWinner;
    private int significantDifferences;
    private Map<String, Object> plotPaths; // Optional: MATLAB visualization paths
    private Map<String, Object> interpretation; // will validate later
    
    /**
     * Individual metric t-test results
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MetricTest {
        private String metricName;
        
        // Paired t-test statistics
        private double meanDifference;      // d̄ (EACO - EPSO)
        private double stdDifference;       // Sd
        private double standardError;       // SE = Sd/√n
        private double tStatistic;          // t = d̄/(Sd/√n)
        private int degreesOfFreedom;       // df = n-1
        private double pValue;              // Two-tailed p-value
        
        // Individual algorithm statistics
        private double eacoStd;             // Standard deviation of EACO measurements
        private double epsoStd;             // Standard deviation of EPSO measurements
        private String stdInterpretation;   // Interpretation of standard deviations
        
        // Confidence interval
        private double ciLower;             // 95% CI lower bound
        private double ciUpper;             // 95% CI upper bound
        
        // Effect size
        private double cohensD;             // Cohen's d effect size
        private String effectSize;          // Interpretation: Negligible/Small/Medium/Large
        
        // Results interpretation
        private boolean significant;        // p < alpha
        private String betterAlgorithm;     // EACO or EPSO
        private double improvementPercentage;
        
        /**
         * Format result for display
         */
        public String getFormattedResult() {
            if (significant) {
                return String.format("%s: %s performs %.2f%% better (p=%.4f, d=%.3f)",
                    metricName, betterAlgorithm, improvementPercentage, pValue, cohensD);
            } else {
                return String.format("%s: No significant difference (p=%.4f)", 
                    metricName, pValue);
            }
        }
        
        /**
         * Get significance stars for display
         */
        public String getSignificanceStars() {
            if (pValue < 0.001) return "***";
            if (pValue < 0.01) return "**";
            if (pValue < 0.05) return "*";
            return "ns";
        }
    }
    
    /**
     * wilcoxon application as an additional statistical test
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class WilcoxonTest {
        private String metricName;
        
        // Wilcoxon signed-rank statistics
        private double testStatistic;       // W = min(|W+|, |W-|)
        private double positiveSum;         // W+ = sum of positive signed ranks
        private double negativeSum;         // W- = sum of negative signed ranks
        private double zScore;              // Standardized test statistic with continuity correction
        private double pValue;              // Two-tailed p-value from normal approximation
        private int sampleSize;             // n = number of non-zero differences
        
        // Effect size
        private double effectSizeR;         // r = |Z| / √n, range [0,1]
        private String effectSize;          // Interpretation: Negligible/Small/Medium/Large
        
        // Results interpretation
        private boolean significant;        // p < alpha
        private String betterAlgorithm;     // EACO or EPSO
        private double improvementPercentage;
        
        /**
         * Format result for display
         */
        public String getFormattedResult() {
            if (significant) {
                return String.format("%s: %s performs %.2f%% better (W=%.0f, p=%.4f, r=%.3f)",
                    metricName, betterAlgorithm, improvementPercentage, testStatistic, pValue, effectSizeR);
            } else {
                return String.format("%s: No significant difference (W=%.0f, p=%.4f)", 
                    metricName, testStatistic, pValue);
            }
        }
        
        /**
         * Get significance stars for display
         */
        public String getSignificanceStars() {
            if (pValue < 0.001) return "***";
            if (pValue < 0.01) return "**";
            if (pValue < 0.05) return "*";
            return "ns";
        }
    }
}
