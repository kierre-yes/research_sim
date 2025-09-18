package com.thesis.cloudsim.service;

import com.thesis.cloudsim.dto.ComparisonResults;
import com.thesis.cloudsim.dto.IterationResults;
import com.thesis.cloudsim.dto.SimulationRequest;
import com.thesis.cloudsim.dto.TTestResults;
import com.thesis.cloudsim.algorithm.ISchedulingAlgorithm;
import com.thesis.cloudsim.matlab.MatlabIntegrationService;
import com.thesis.cloudsim.metrics.SimulationResults;
import com.thesis.cloudsim.util.ConfigurationSnapshotUtil;
import com.thesis.cloudsim.util.SimulationProgressHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;
import org.apache.commons.math3.distribution.TDistribution;
import org.apache.commons.math3.stat.inference.TTest;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;

@Service
public class ComparisonService {
    
    private static final Logger logger = LoggerFactory.getLogger(ComparisonService.class);
    private static volatile boolean isCancelled = false;
    
    @Autowired
    private IterationService iterationService;
    
    @Autowired(required = false)
    private MatlabIntegrationService matlabService;
    
    @Autowired
    private AnalysisInterpretationService analysisService;
    
    private final ISchedulingAlgorithm epso;
    private final ISchedulingAlgorithm eaco;
    
    public ComparisonService(@Qualifier("epso") ISchedulingAlgorithm epso,
                            @Qualifier("eaco") ISchedulingAlgorithm eaco) {
        this.epso = epso;
        this.eaco = eaco;
    }
    
    /**
     * Run comparison between EACO and EPSO with statistical analysis
     */
    public ComparisonResults runComparison(SimulationRequest request) {
        isCancelled = false;
        
        logger.info("Starting algorithm comparison with {} iterations", request.getIterations());
        
        long startTime = System.currentTimeMillis();
        
        // ensure we have multiple iterations for statistical validity
        boolean iterationsAdjusted = false;
        int originalIterations = request.getIterations();
        if (request.getIterations() < 30) {
            logger.warn("Iterations set to {} but need at least 30 for t-test validity. Adjusting to 30.", request.getIterations());
            request.setIterations(30);
            iterationsAdjusted = true;
        }
        
        int totalComparisons = request.getIterations();
        SimulationProgressHolder.setCurrentIteration(0, totalComparisons, "Starting Comparison");
        

        List<SimulationResults> eacoResultsList = new ArrayList<>();
        List<SimulationResults> epsoResultsList = new ArrayList<>();
        
        for (int i = 1; i <= totalComparisons; i++) {
            if (isCancelled) {
                logger.info("Comparison cancelled at iteration {}", i);
                SimulationProgressHolder.reset(); // Reset progress on cancellation
                throw new RuntimeException("Comparison cancelled by user");
            }
            SimulationProgressHolder.setCurrentIteration(i, totalComparisons, "Running");
            

            request.setOptimizationAlgorithm("EACO");
            request.setIterations(1);
            IterationResults eacoSingleResult = iterationService.runIterations(eaco, request);
            eacoResultsList.addAll(eacoSingleResult.getIndividualResults());
            
            if (isCancelled) {
                SimulationProgressHolder.reset(); // Reset progress on cancellation
                throw new RuntimeException("Comparison cancelled by user");
            }
            
            request.setOptimizationAlgorithm("EPSO");
            IterationResults epsoSingleResult = iterationService.runIterations(epso, request);
            epsoResultsList.addAll(epsoSingleResult.getIndividualResults());
        }
        
        request.setIterations(totalComparisons);
        
        IterationResults eacoResults = new IterationResults();
        eacoResults.setTotalIterations(totalComparisons);
        eacoResults.setAlgorithm("EACO");
        eacoResults.setIndividualResults(eacoResultsList);
        
        IterationResults epsoResults = new IterationResults();
        epsoResults.setTotalIterations(totalComparisons);
        epsoResults.setAlgorithm("EPSO");
        epsoResults.setIndividualResults(epsoResultsList);
        
        SimulationProgressHolder.setCurrentIteration(totalComparisons, totalComparisons, "Statistical Analysis");
        
        // Perform paired t-test analysis
        TTestResults tTestResults = performPairedTTest(eacoResults, epsoResults, request);
        /**
         * I generate statistical interpretations using the new analysis service
         * to provide meaningful explanations instead of just raw numbers
         */
        Map<String, Object> statisticalInterpretation = analysisService.generateStatisticalInterpretation(tTestResults);
        tTestResults.setInterpretation(statisticalInterpretation);
        
        // Build comparison results
        ComparisonResults comparison = new ComparisonResults();
        comparison.setEacoResults(eacoResults);
        comparison.setEpsoResults(epsoResults);
        comparison.setTTestResults(tTestResults);
        comparison.setTotalExecutionTime(System.currentTimeMillis() - startTime);
        comparison.setWorkloadName(request.getWorkloadPath() != null ? 
            "Custom Workload" : "Random Workload");
        comparison.setIterations(request.getIterations());
        comparison.setIterationsAdjusted(iterationsAdjusted);
        comparison.setOriginalIterations(originalIterations);
        if (iterationsAdjusted) {
            comparison.setAdjustmentMessage(String.format(
                "Iterations were automatically adjusted from %d to %d to ensure statistical validity (minimum 30 required for paired t-test).",
                originalIterations, request.getIterations()
            ));
        }

        // Populate run metadata (top-level)
        comparison.setRunId(java.util.UUID.randomUUID().toString());
        comparison.setSeed(request.getSeed());
        comparison.setConfigSnapshot(buildConfigSnapshot(request));
        comparison.setDatasetId(computeDatasetId(request.getWorkloadPath()));
        
        SimulationProgressHolder.setStage("Comparison Completed");
        java.util.concurrent.CompletableFuture.runAsync(() -> {
            try {
                Thread.sleep(2000); 
                SimulationProgressHolder.reset();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        
        logger.info("Comparison completed in {} ms", comparison.getTotalExecutionTime());
        
        return comparison;
    }
    
    /**
     * Perform paired t-test statistical analysis
     */
    private TTestResults performPairedTTest(IterationResults eacoResults, 
                                           IterationResults epsoResults,
                                           SimulationRequest request) {
        logger.info("Performing paired t-test analysis");
        
        TTestResults results = new TTestResults();
        results.setAlpha(0.05); // Significance level
        
        // Get paired observations for each metric
        List<SimulationResults> eacoList = eacoResults.getIndividualResults();
        List<SimulationResults> epsoList = epsoResults.getIndividualResults();
        
        int n = Math.min(eacoList.size(), epsoList.size());
        results.setSampleSize(n);
        
        // Analyze each metric
        String[] metrics = {"makespan", "energyConsumption", "resourceUtilization", 
                          "responseTime", "loadBalance"};
        
        Map<String, TTestResults.MetricTest> metricTests = new HashMap<>();
        
        for (String metric : metrics) {
            TTestResults.MetricTest test = calculateTTest(
                extractMetricValues(eacoList, metric),
                extractMetricValues(epsoList, metric),
                metric
            );
            metricTests.put(metric, test);
        }
        
        results.setMetricTests(metricTests);
        
        // Calculate overall winner
        long eacoWins = metricTests.values().stream()
            .filter(t -> t.isSignificant() && "EACO".equals(t.getBetterAlgorithm()))
            .count();
        long epsoWins = metricTests.values().stream()
            .filter(t -> t.isSignificant() && "EPSO".equals(t.getBetterAlgorithm()))
            .count();
            
        if (eacoWins > epsoWins) {
            results.setOverallWinner("EACO");
        } else if (epsoWins > eacoWins) {
            results.setOverallWinner("EPSO");
        } else {
            results.setOverallWinner("No clear winner");
        }
        
        results.setSignificantDifferences((int)(eacoWins + epsoWins));
        
        // If MATLAB is available, generate visualization
        if (matlabService != null && matlabService.isReady()) {
            try {
                logger.info("Generating statistical analysis plots with MATLAB");
                Map<String, Object> plotData = matlabService.generateTTestPlots(results);
                results.setPlotPaths(plotData);
            } catch (Exception e) {
                logger.warn("Failed to generate t-test plots: {}", e.getMessage());
            }
        }
        
        return results;
    }
    
    /**
     * Calculate t-test for a single metric using Apache Commons Math3
     */
    private TTestResults.MetricTest calculateTTest(double[] eacoValues, 
                                                   double[] epsoValues, 
                                                   String metricName) {
        TTestResults.MetricTest test = new TTestResults.MetricTest();
        test.setMetricName(metricName);
        
        int n = Math.min(eacoValues.length, epsoValues.length);
        
        // Ensure equal length arrays
        if (eacoValues.length > n) {
            eacoValues = Arrays.copyOf(eacoValues, n);
        }
        if (epsoValues.length > n) {
            epsoValues = Arrays.copyOf(epsoValues, n);
        }
        
        // Calculate differences for paired test
        double[] differences = new double[n];
        for (int i = 0; i < n; i++) {
            differences[i] = eacoValues[i] - epsoValues[i];
        }
        
        // Descriptive stats on differences
        DescriptiveStatistics stats = new DescriptiveStatistics();
        for (double diff : differences) {
            stats.addValue(diff);
        }
        
        double meanDiff = stats.getMean();
        double stdDev = stats.getStandardDeviation();
        if (Double.isNaN(stdDev)) stdDev = 0.0;
        
        // Initialize outputs with safe defaults
        double stdError = 0.0;
        double tStatistic = 0.0;
        int df = Math.max(n - 1, 0);
        double pValue = 1.0;
        double ciLower = meanDiff;
        double ciUpper = meanDiff;
        double cohensD = 0.0;
        double alpha = 0.05;
        
        if (n < 2) {
            // Not enough samples: keep defaults (p=1, t=0, CI collapsed to meanDiff, d=0)
        } else if (stdDev == 0.0) {
            // Zero variance across differences
            if (meanDiff == 0.0) {
                // All paired differences are zero
                pValue = 1.0;
                tStatistic = 0.0;
                stdError = 0.0;
                ciLower = 0.0;
                ciUpper = 0.0;
                cohensD = 0.0;
            } else {
                // All paired differences are the same non-zero constant
                // Use exact two-sided sign-flip (permutation) test: p = 2^(1-n)
                pValue = Math.min(1.0, Math.pow(2.0, 1 - n));
                tStatistic = 0.0; // undefined t; keep finite sentinel
                stdError = 0.0;
                ciLower = meanDiff; // CI undefined; collapse to meanDiff
                ciUpper = meanDiff;
                cohensD = 0.0; // undefined; avoid NaN
            }
        } else {
            // Standard t-test case
            stdError = stdDev / Math.sqrt(n);
            if (stdError == 0.0) {
                tStatistic = 0.0;
            } else {
                tStatistic = meanDiff / stdError;
            }
            df = n - 1;
            try {
                TDistribution tDistribution = new TDistribution(df);
                pValue = 2.0 * tDistribution.cumulativeProbability(-Math.abs(tStatistic));
                double tCritical = tDistribution.inverseCumulativeProbability(1.0 - alpha / 2.0);
                ciLower = meanDiff - tCritical * stdError;
                ciUpper = meanDiff + tCritical * stdError;
            } catch (Exception ex) {
                // If distribution fails, keep conservative defaults
                pValue = 1.0;
                ciLower = meanDiff;
                ciUpper = meanDiff;
            }
            cohensD = meanDiff / stdDev;
        }
        
        // Final sanitation to avoid NaN/Infinity in outputs
        if (!Double.isFinite(stdError)) stdError = 0.0;
        if (!Double.isFinite(tStatistic)) tStatistic = 0.0;
        if (!Double.isFinite(pValue)) pValue = 1.0;
        if (!Double.isFinite(ciLower)) ciLower = meanDiff;
        if (!Double.isFinite(ciUpper)) ciUpper = meanDiff;
        if (!Double.isFinite(cohensD)) cohensD = 0.0;
        
        // Populate test results
        test.setMeanDifference(meanDiff);
        test.setStdDifference(stdDev);
        test.setStandardError(stdError);
        test.setTStatistic(tStatistic);
        test.setDegreesOfFreedom(df);
        test.setPValue(pValue);
        test.setCiLower(ciLower);
        test.setCiUpper(ciUpper);
        test.setCohensD(cohensD);
        test.setSignificant(pValue < 0.05);
        
        // Debug logging to trace potential serialization issues
        if (logger.isDebugEnabled()) {
            logger.debug("TTEST {} -> n={}, meanDiff={}, sd={}, se={}, t={}, df={}, p={}",
                metricName, n,
                String.format("%.6f", meanDiff),
                String.format("%.6f", stdDev),
                String.format("%.6f", stdError),
                String.format("%.6f", tStatistic),
                df,
                String.format("%.6f", pValue)
            );
        }
        
        // i fixed this supposed to find the better algo
        boolean isLowerBetter = isLowerBetterMetric(metricName);
        
        // compute avg values
        double eacoAvg = Arrays.stream(eacoValues).average().orElse(1.0);
        double epsoAvg = Arrays.stream(epsoValues).average().orElse(1.0);
        if (eacoAvg == 0.0) eacoAvg = 1.0;
        if (epsoAvg == 0.0) epsoAvg = 1.0;
        
        if (meanDiff < 0) {
            // eaco lower than epso
            if (isLowerBetter) {
                test.setBetterAlgorithm("EACO");
                // improvement relative to EPSO (baseline)
                test.setImprovementPercentage(Math.abs(meanDiff / epsoAvg) * 100);
            } else {
                // For "higher is better" metrics, EPSO wins if it has higher values
                test.setBetterAlgorithm("EPSO");
                // improvement relative to EACO (which performed worse)
                test.setImprovementPercentage(Math.abs(meanDiff / eacoAvg) * 100);
            }
        } else if (meanDiff > 0) {
            // EPSO has lower values than EACO
            if (isLowerBetter) {
                test.setBetterAlgorithm("EPSO");
                // improvement relative to EACO (baseline)
                test.setImprovementPercentage(Math.abs(meanDiff / eacoAvg) * 100);
            } else {
                // For "higher is better" metrics, EACO wins if it has higher values
                test.setBetterAlgorithm("EACO");
                // improvement relative to EPSO (which performed worse)
                test.setImprovementPercentage(Math.abs(meanDiff / epsoAvg) * 100);
            }
        } else {
            // meanDiff == 0, no difference
            test.setBetterAlgorithm("None");
            test.setImprovementPercentage(0.0);
        }
        
        //cloud computing metrics need adjusted thresholds
        double absCohensD = Math.abs(cohensD);
        String effectSize = getCloudComputingEffectSize(absCohensD, metricName);
        test.setEffectSize(effectSize);
        
        return test;
    }
    
    /**
     * Request cancellation of ongoing comparison
     */
    public static void requestCancellation() {
        isCancelled = true;
        logger.info("Cancellation requested for ComparisonService");
    }
    
    /**
     * Extract metric values from simulation results
     */
    private double[] extractMetricValues(List<SimulationResults> results, String metric) {
        return results.stream()
            .map(r -> {
                SimulationResults.Summary summary = r.getSummary();
                if (summary == null) return 0.0;
                
                switch (metric) {
                    case "makespan": return summary.getMakespan();
                    case "energyConsumption": return summary.getEnergyConsumption();
                    case "resourceUtilization": return summary.getResourceUtilization();
                    case "responseTime": return summary.getResponseTime();
                    case "loadBalance": 
                        return summary.getLoadImbalance() != 0.0 ? summary.getLoadImbalance() : summary.getLoadBalance();
                    default: return 0.0;
                }
            })
            .mapToDouble(Double::doubleValue)
            .toArray();
    }
    
    /**
     * determines if lower values are better for a given metric.
     * this is crucial for correctly interpreting t-test results.
     * 
     * @param metricName the name of the metric
     * @return true if lower values are better, false if higher values are better
     */
    private boolean isLowerBetterMetric(String metricName) {
        switch (metricName) {
            case "makespan":
            case "energyConsumption":
            case "responseTime":
            case "loadBalance": 
                return true;
            case "resourceUtilization":
                return false; 
            default:
                logger.warn("Unknown metric type: {}, assuming lower is better", metricName);
                return true;
        }
    }
    
    
    private String getCloudComputingEffectSize(double cohensD, String metricName) {
        
        switch (metricName) {
            case "makespan":
            case "responseTime":
                if (cohensD < 0.15) return "Negligible";
                else if (cohensD < 0.4) return "Small";
                else if (cohensD < 0.75) return "Medium";
                else return "Large";
                
            case "energyConsumption":
                if (cohensD < 0.25) return "Negligible";
                else if (cohensD < 0.6) return "Small";
                else if (cohensD < 1.0) return "Medium";
                else return "Large";
                
            case "resourceUtilization":
                if (cohensD < 0.2) return "Negligible";
                else if (cohensD < 0.5) return "Small";
                else if (cohensD < 0.8) return "Medium";
                else return "Large";
                
            case "loadBalance":
            case "loadImbalance":
                if (cohensD < 0.1) return "Negligible";
                else if (cohensD < 0.35) return "Small";
                else if (cohensD < 0.7) return "Medium";
                else return "Large";
                
            default:
                if (cohensD < 0.2) return "Negligible";
                else if (cohensD < 0.5) return "Small";
                else if (cohensD < 0.8) return "Medium";
                else return "Large";
        }
    }
    
    /**
     * straightforward fix using apache math commons but it causing the p-values to return unformatted results
     */
    public double performPairedTTest(double[] sample1, double[] sample2) {
        TTest tTest = new TTest();
        return tTest.pairedTTest(sample1, sample2);
    }
    
    /**
     * Check if the difference is statistically significant
     */
    public boolean isSignificant(double[] sample1, double[] sample2, double alpha) {
        TTest tTest = new TTest();
        return tTest.pairedTTest(sample1, sample2, alpha);
    }

    private Map<String, Object> buildConfigSnapshot(SimulationRequest r) {
        // Use centralized utility to avoid duplication
        return ConfigurationSnapshotUtil.createDetailedSnapshot(r);
    }

    private String computeDatasetId(String path) {
        // Delegate to utility class to avoid duplication
        return ConfigurationSnapshotUtil.computeDatasetId(path);
    }
}
