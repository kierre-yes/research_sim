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
import org.springframework.beans.factory.annotation.Value;
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
    
    @Value("${statistical.analysis.wilcoxon.enabled:false}")
    private boolean wilcoxonEnabled;
    
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
        // Protect progress holder from interference during comparison
        SimulationProgressHolder.setComparisonRunning(true);
        SimulationProgressHolder.setCurrentIteration(0, totalComparisons, "Starting Comparison");
        

        List<SimulationResults> eacoResultsList = new ArrayList<>();
        List<SimulationResults> epsoResultsList = new ArrayList<>();
        
        for (int i = 1; i <= totalComparisons; i++) {
            if (isCancelled) {
                logger.info("Comparison cancelled at iteration {}", i);
                SimulationProgressHolder.setComparisonRunning(false); // End protection on cancellation
                SimulationProgressHolder.reset(); // Reset progress on cancellation
                throw new RuntimeException("Comparison cancelled by user");
            }
            SimulationProgressHolder.setCurrentIteration(i, totalComparisons, "Running");
            

            request.setOptimizationAlgorithm("EACO");
            request.setIterations(1);
            IterationResults eacoSingleResult = iterationService.runIterations(eaco, request);
            eacoResultsList.addAll(eacoSingleResult.getIndividualResults());
            
            if (isCancelled) {
                SimulationProgressHolder.setComparisonRunning(false); // End protection on cancellation
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
        
        TTestResults tTestResults = performPairedTTest(eacoResults, epsoResults, request);
        
        performNormalityTests(tTestResults, eacoResults, epsoResults);
        
        if (wilcoxonEnabled) {
            logger.info("Wilcoxon test enabled - performing Wilcoxon signed-rank test");
            performWilcoxonSignedRankTest(tTestResults, eacoResults, epsoResults);
        } else {
            logger.info("Wilcoxon test disabled - skipping Wilcoxon analysis");
        }
        
        Map<String, Object> statisticalInterpretation = analysisService.generateStatisticalInterpretation(tTestResults);
        
        if (tTestResults.getNormalityTests() != null && !tTestResults.getNormalityTests().isEmpty()) {
            Map<String, Object> normalityInterpretation = analysisService.generateNormalityInterpretation(tTestResults);
            statisticalInterpretation.put("normalityAnalysis", normalityInterpretation);
        }
        
        if (wilcoxonEnabled && tTestResults.getWilcoxonTests() != null && !tTestResults.getWilcoxonTests().isEmpty()) {
            Map<String, Object> wilcoxonInterpretation = analysisService.generateWilcoxonInterpretation(tTestResults);
            statisticalInterpretation.put("wilcoxonAnalysis", wilcoxonInterpretation);
        }
        
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
        
        Map<String, Object> snapshot = buildConfigSnapshot(request);
        snapshot.put("optimizationAlgorithm", "EACO vs EPSO Comparison");
        comparison.setConfigSnapshot(snapshot);
        
        comparison.setDatasetId(computeDatasetId(request.getWorkloadPath()));
        

        SimulationProgressHolder.setStage("Comparison Completed");
        SimulationProgressHolder.setComparisonRunning(false);
        SimulationProgressHolder.reset();
        
        logger.info("Comparison completed in {} ms", comparison.getTotalExecutionTime());
        
        return comparison;
    }
    
    private void performNormalityTests(TTestResults results,
                                       IterationResults eacoResults,
                                       IterationResults epsoResults) {
        logger.info("Performing Anderson-Darling normality tests");
        
        List<SimulationResults> eacoList = eacoResults.getIndividualResults();
        List<SimulationResults> epsoList = epsoResults.getIndividualResults();
        
        String[] metrics = {"makespan", "energyConsumption", "resourceUtilization",
                          "responseTime", "loadBalance"};
        
        Map<String, TTestResults.NormalityTest> normalityTests = new HashMap<>();
        
        for (String metric : metrics) {
            double[] eacoValues = extractMetricValues(eacoList, metric);
            double[] epsoValues = extractMetricValues(epsoList, metric);
            
            TTestResults.NormalityTest test = performShapiroWilkTest(eacoValues, epsoValues, metric);
            normalityTests.put(metric, test);
        }
        
        results.setNormalityTests(normalityTests);
        
        logger.info("Normality testing completed for {} metrics", metrics.length);
    }
    
    private TTestResults.NormalityTest performShapiroWilkTest(double[] eacoValues,
                                                               double[] epsoValues,
                                                               String metricName) {
        TTestResults.NormalityTest test = new TTestResults.NormalityTest();
        test.setMetricName(metricName);
        
        int n = Math.min(eacoValues.length, epsoValues.length);
        double[] differences = new double[n];
        
        for (int i = 0; i < n; i++) {
            differences[i] = eacoValues[i] - epsoValues[i];
        }
        
        if (n < 3) {
            test.setTestStatistic(1.0);
            test.setPValue(1.0);
            test.setNormal(true);
            test.setRecommendation("Paired T-Test (insufficient data for normality test)");
            test.setInterpretation("Sample size too small (n < 3) for Anderson-Darling test. Assuming normality by default.");
            return test;
        }
        
        try {
            ShapiroWilkResult swResult = calculateShapiroWilk(differences);
            double wStatistic = swResult.wStatistic;
            double pValue = swResult.pValue;
            
            boolean isNormal = pValue > 0.05;
            
            test.setTestStatistic(wStatistic);
            test.setPValue(pValue);
            test.setNormal(isNormal);
            
            if (isNormal) {
                test.setRecommendation("Paired T-Test");
                test.setInterpretation(String.format(
                    "The data follows a bell curve pattern (p=%.4f, which is > 0.05). " +
                    "This means the t-test results are trustworthy and should be used for this metric.",
                    pValue
                ));
            } else {
                if (wilcoxonEnabled) {
                    test.setRecommendation("Wilcoxon Signed-Rank Test");
                    test.setInterpretation(String.format(
                        "The data doesn't follow a bell curve pattern (p=%.4f, which is ≤ 0.05). " +
                        "Use the Wilcoxon test results for this metric instead - they're more reliable when data isn't bell-curve shaped.",
                        pValue
                    ));
                } else {
                    test.setRecommendation("Paired T-Test");
                    test.setInterpretation(String.format(
                        "The data doesn't follow a perfect bell curve pattern (p=%.4f, which is ≤ 0.05). " +
                        "However, paired t-test is still used as the primary analysis method for this research.",
                        pValue
                    ));
                }
            }
            
        } catch (Exception e) {
            logger.warn("Shapiro-Wilk test failed for {}: {}", metricName, e.getMessage());
            test.setTestStatistic(1.0);
            test.setPValue(1.0);
            test.setNormal(true);
            test.setRecommendation("Both Tests");
            test.setInterpretation("Normality test inconclusive. Review both parametric and non-parametric results.");
        }
        
        return test;
    }
    
    private void performWilcoxonSignedRankTest(TTestResults results,
                                                IterationResults eacoResults,
                                                IterationResults epsoResults) {
        logger.info("Performing Wilcoxon signed-rank test analysis");
        
        List<SimulationResults> eacoList = eacoResults.getIndividualResults();
        List<SimulationResults> epsoList = epsoResults.getIndividualResults();
        
        String[] metrics = {"makespan", "energyConsumption", "resourceUtilization",
                          "responseTime", "loadBalance"};
        
        Map<String, TTestResults.WilcoxonTest> wilcoxonTests = new HashMap<>();
        
        for (String metric : metrics) {
            TTestResults.WilcoxonTest test = calculateWilcoxonTest(
                extractMetricValues(eacoList, metric),
                extractMetricValues(epsoList, metric),
                metric
            );
            wilcoxonTests.put(metric, test);
        }
        
        results.setWilcoxonTests(wilcoxonTests);
        
        logger.info("Wilcoxon signed-rank test analysis completed for {} metrics", metrics.length);
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
        
        DescriptiveStatistics eacoStats = new DescriptiveStatistics();
        DescriptiveStatistics epsoStats = new DescriptiveStatistics();
        for (int i = 0; i < n; i++) {
            eacoStats.addValue(eacoValues[i]);
            epsoStats.addValue(epsoValues[i]);
        }
        double eacoStd = eacoStats.getStandardDeviation();
        double epsoStd = epsoStats.getStandardDeviation();
        if (Double.isNaN(eacoStd)) eacoStd = 0.0;
        if (Double.isNaN(epsoStd)) epsoStd = 0.0;
        
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
        test.setEacoStd(eacoStd);
        test.setEpsoStd(epsoStd);
        
        double eacoMean = eacoStats.getMean();
        double epsoMean = epsoStats.getMean();
        test.setStdInterpretation(generateStdInterpretation(eacoStd, epsoStd, eacoMean, epsoMean, metricName));
        
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
        
        test.setEacoMean(eacoAvg);
        test.setEpsoMean(epsoAvg);
        
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
        
        String meanInterpretation = analysisService.generateMeanInterpretation(metricName, test);
        test.setMeanInterpretation(meanInterpretation);
        
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
    
    private String generateStdInterpretation(double eacoStd, double epsoStd, double eacoMean, double epsoMean, String metricName) {
        double eacoCV = eacoMean != 0.0 ? (eacoStd / Math.abs(eacoMean)) * 100 : 0.0;
        double epsoCV = epsoMean != 0.0 ? (epsoStd / Math.abs(epsoMean)) * 100 : 0.0;
        
        String eacoStability = categorizeStability(eacoCV);
        String epsoStability = categorizeStability(epsoCV);
        
        StringBuilder interpretation = new StringBuilder();
        
        interpretation.append(String.format(
            "Consistency check - EACO varies by %.2f%% (%s), EPSO varies by %.2f%% (%s) across test runs. ",
            eacoCV, eacoStability,
            epsoCV, epsoStability
        ));
        
        double cvDifference = Math.abs(eacoCV - epsoCV);
        
        if (cvDifference < 5.0) {
            interpretation.append("Both algorithms show similar consistency - you'll get reliable results from either. ");
        } else if (eacoCV < epsoCV) {
            double improvement = ((epsoCV - eacoCV) / epsoCV) * 100;
            interpretation.append(String.format(
                "EACO is %.1f%% more consistent than EPSO, meaning it delivers more predictable performance in real deployments. ",
                improvement
            ));
        } else {
            double improvement = ((eacoCV - epsoCV) / eacoCV) * 100;
            interpretation.append(String.format(
                "EPSO is %.1f%% more consistent than EACO, meaning it delivers more predictable performance in real deployments. ",
                improvement
            ));
        }
        
        double avgCV = (eacoCV + epsoCV) / 2.0;
        interpretation.append(getStabilityImplication(avgCV, metricName));
        
        return interpretation.toString();
    }
    
    private String categorizeStability(double cv) {
        if (cv < 10.0) {
            return "highly stable";
        } else if (cv < 30.0) {
            return "stable";
        } else if (cv < 50.0) {
            return "moderately stable";
        } else {
            return "unstable";
        }
    }
    
    private String getStabilityImplication(double avgCV, String metricName) {
        String metricContext = getMetricDisplayContext(metricName);
        
        if (avgCV < 10.0) {
            return String.format(
                "Excellent news for %s: with less than 10%% variation, the algorithm will perform consistently every time you use it. "
                + "Perfect for production systems where you need guaranteed performance levels.",
                metricContext
            );
        } else if (avgCV < 30.0) {
            return String.format(
                "Good consistency for %s: with less than 30%% variation, the algorithm is reliable enough for most real-world uses. "
                + "Performance won't surprise you.",
                metricContext
            );
        } else if (avgCV < 50.0) {
            return String.format(
                "Moderate consistency for %s: variation up to 50%% means performance can fluctuate noticeably between runs. "
                + "Run multiple tests before deploying to critical systems.",
                metricContext
            );
        } else {
            return String.format(
                "Warning for %s: over 50%% variation means performance is unpredictable. "
                + "The algorithm is sensitive to starting conditions or workload patterns. You'll need multiple test runs to understand typical behavior.",
                metricContext
            );
        }
    }
    
    private String getMetricDisplayContext(String metricName) {
        switch (metricName) {
            case "makespan":
                return "total job completion time";
            case "energyConsumption":
                return "datacenter energy consumption";
            case "resourceUtilization":
                return "compute resource utilization";
            case "responseTime":
                return "average task response time";
            case "loadBalance":
            case "loadImbalance":
                return "workload distribution balance";
            default:
                return "this metric";
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
    
    private double[] rankAbsoluteDifferences(double[] differences) {
        int n = differences.length;
        double[] ranks = new double[n];
        double[] absDiffs = new double[n];
        
        for (int i = 0; i < n; i++) {
            absDiffs[i] = Math.abs(differences[i]);
        }
        
        for (int i = 0; i < n; i++) {
            if (differences[i] == 0.0) {
                ranks[i] = 0.0;
                continue;
            }
            
            int smallerCount = 0;
            for (int j = 0; j < n; j++) {
                if (differences[j] != 0.0 && absDiffs[j] < absDiffs[i]) {
                    smallerCount++;
                }
            }
            
            ranks[i] = smallerCount + 1;
        }
        
        return ranks;
    }
    
    private double calculateWilcoxonZScore(double testStatistic, int n) {
        if (n < 2) {
            return 0.0;
        }
        
        double expectedValue = n * (n + 1) / 4.0;
        double variance = n * (n + 1) * (2 * n + 1) / 24.0;
        double standardDeviation = Math.sqrt(variance);
        
        if (standardDeviation == 0.0) {
            return 0.0;
        }
        
        double continuityCorrection;
        if (testStatistic < expectedValue) {
            continuityCorrection = -0.5;
        } else {
            continuityCorrection = 0.5;
        }
        
        double zScore = (testStatistic - expectedValue + continuityCorrection) / standardDeviation;
        
        return zScore;
    }
    
    private double calculateNormalCDF(double z) {
        return 0.5 * (1.0 + erf(z / Math.sqrt(2.0)));
    }
    
    private double erf(double x) {
        double a1 =  0.254829592;
        double a2 = -0.284496736;
        double a3 =  1.421413741;
        double a4 = -1.453152027;
        double a5 =  1.061405429;
        double p  =  0.3275911;
        
        int sign = (x < 0) ? -1 : 1;
        x = Math.abs(x);
        
        double t = 1.0 / (1.0 + p * x);
        double y = 1.0 - (((((a5 * t + a4) * t) + a3) * t + a2) * t + a1) * t * Math.exp(-x * x);
        
        return sign * y;
    }
    
    private double[] calculateHodgesLehmannCI(double[] differences, int nonZeroCount, double alpha) {
        if (nonZeroCount < 5) {
            return new double[]{0.0, 0.0};
        }
        
        List<Double> nonZeroDiffs = new ArrayList<>();
        for (double diff : differences) {
            if (diff != 0.0) {
                nonZeroDiffs.add(diff);
            }
        }
        
        List<Double> walshAverages = new ArrayList<>();
        int n = nonZeroDiffs.size();
        
        for (int i = 0; i < n; i++) {
            for (int j = i; j < n; j++) {
                double avg = (nonZeroDiffs.get(i) + nonZeroDiffs.get(j)) / 2.0;
                walshAverages.add(avg);
            }
        }
        
        Collections.sort(walshAverages);
        
        int m = walshAverages.size();
        
        double z = 1.96;
        if (alpha == 0.01) {
            z = 2.576;
        } else if (alpha == 0.10) {
            z = 1.645;
        }
        
        double expectedValue = n * (n + 1) / 4.0;
        double variance = n * (n + 1) * (2 * n + 1) / 24.0;
        double k = expectedValue - z * Math.sqrt(variance);
        
        int lowerIndex = Math.max(0, (int) Math.floor(k) - 1);
        int upperIndex = Math.min(m - 1, m - lowerIndex - 1);
        
        lowerIndex = Math.max(0, Math.min(m - 1, lowerIndex));
        upperIndex = Math.max(0, Math.min(m - 1, upperIndex));
        
        return new double[]{walshAverages.get(lowerIndex), walshAverages.get(upperIndex)};
    }
    
    private TTestResults.WilcoxonTest calculateWilcoxonTest(double[] eacoValues,
                                                             double[] epsoValues,
                                                             String metricName) {
        TTestResults.WilcoxonTest test = new TTestResults.WilcoxonTest();
        test.setMetricName(metricName);
        
        int n = Math.min(eacoValues.length, epsoValues.length);
        
        if (eacoValues.length > n) {
            eacoValues = Arrays.copyOf(eacoValues, n);
        }
        if (epsoValues.length > n) {
            epsoValues = Arrays.copyOf(epsoValues, n);
        }
        
        double eacoMedian = calculateMedian(eacoValues);
        double epsoMedian = calculateMedian(epsoValues);
        double eacoMAD = calculateMAD(eacoValues, eacoMedian);
        double epsoMAD = calculateMAD(epsoValues, epsoMedian);
        double eacoIQR = calculateIQR(eacoValues);
        double epsoIQR = calculateIQR(epsoValues);
        
        test.setEacoMedian(eacoMedian);
        test.setEpsoMedian(epsoMedian);
        test.setEacoMAD(eacoMAD);
        test.setEpsoMAD(epsoMAD);
        test.setEacoIQR(eacoIQR);
        test.setEpsoIQR(epsoIQR);
        
        double[] differences = new double[n];
        int zeroCount = 0;
        for (int i = 0; i < n; i++) {
            differences[i] = eacoValues[i] - epsoValues[i];
            if (differences[i] == 0.0) {
                zeroCount++;
            }
        }
        
        int tiesCount = detectTies(differences);
        boolean hasTies = tiesCount > 0;
        
        double[] ranks = rankAbsoluteDifferences(differences);
        
        double positiveSum = 0.0;
        double negativeSum = 0.0;
        int nonZeroCount = 0;
        
        for (int i = 0; i < n; i++) {
            if (differences[i] != 0.0) {
                nonZeroCount++;
                double signedRank = Math.signum(differences[i]) * ranks[i];
                if (signedRank > 0) {
                    positiveSum += signedRank;
                } else {
                    negativeSum += signedRank;
                }
            }
        }
        
        test.setPositiveSum(positiveSum);
        test.setNegativeSum(negativeSum);
        test.setSampleSize(nonZeroCount);
        
        if (nonZeroCount < 2) {
            test.setTestStatistic(0.0);
            test.setZScore(0.0);
            test.setPValue(1.0);
            test.setSignificant(false);
            test.setBetterAlgorithm("None");
            test.setImprovementPercentage(0.0);
            test.setEffectSizeR(0.0);
            test.setEffectSize("Negligible");
            return test;
        }
        
        double testStatistic = Math.min(Math.abs(positiveSum), Math.abs(negativeSum));
        test.setTestStatistic(testStatistic);
        
        double zScore = calculateWilcoxonZScore(testStatistic, nonZeroCount);
        test.setZScore(zScore);
        
        double pValue = 2.0 * (1.0 - calculateNormalCDF(Math.abs(zScore)));
        test.setPValue(pValue);
        
        test.setSignificant(pValue < 0.05);
        
        double effectSizeR = Math.abs(zScore) / Math.sqrt(nonZeroCount);
        test.setEffectSizeR(effectSizeR);
        
        if (effectSizeR < 0.1) {
            test.setEffectSize("Negligible");
        } else if (effectSizeR < 0.3) {
            test.setEffectSize("Small");
        } else if (effectSizeR < 0.5) {
            test.setEffectSize("Medium");
        } else {
            test.setEffectSize("Large");
        }
        
        double[] ci = calculateHodgesLehmannCI(differences, nonZeroCount, 0.05);
        test.setCiLower(ci[0]);
        test.setCiUpper(ci[1]);
        
        boolean isLowerBetter = isLowerBetterMetric(metricName);
        
        double eacoMean = Arrays.stream(eacoValues).average().orElse(1.0);
        double epsoMean = Arrays.stream(epsoValues).average().orElse(1.0);
        if (eacoMean == 0.0) eacoMean = 1.0;
        if (epsoMean == 0.0) epsoMean = 1.0;
        
        double meanDiff = epsoMean - eacoMean;
        
        if (meanDiff < 0) {
            if (isLowerBetter) {
                test.setBetterAlgorithm("EPSO");
                test.setImprovementPercentage(Math.abs(meanDiff / eacoMean) * 100);
            } else {
                test.setBetterAlgorithm("EACO");
                test.setImprovementPercentage(Math.abs(meanDiff / epsoMean) * 100);
            }
        } else if (meanDiff > 0) {
            if (isLowerBetter) {
                test.setBetterAlgorithm("EACO");
                test.setImprovementPercentage(Math.abs(meanDiff / epsoMean) * 100);
            } else {
                test.setBetterAlgorithm("EPSO");
                test.setImprovementPercentage(Math.abs(meanDiff / eacoMean) * 100);
            }
        } else {
            test.setBetterAlgorithm("None");
            test.setImprovementPercentage(0.0);
        }
        
        test.setVariabilityInterpretation(
            generateWilcoxonVariabilityInterpretation(eacoMAD, epsoMAD, eacoMedian, epsoMedian, metricName)
        );
        
        test.setZeroExclusions(zeroCount);
        test.setTiesPresent(hasTies);
        test.setTiesCount(tiesCount);
        
        return test;
    }
    
    private double calculateMedian(double[] values) {
        if (values == null || values.length == 0) {
            return 0.0;
        }
        double[] sorted = Arrays.copyOf(values, values.length);
        Arrays.sort(sorted);
        int n = sorted.length;
        if (n % 2 == 0) {
            return (sorted[n / 2 - 1] + sorted[n / 2]) / 2.0;
        } else {
            return sorted[n / 2];
        }
    }
    
    private double calculateMAD(double[] values, double median) {
        if (values == null || values.length == 0) {
            return 0.0;
        }
        double[] deviations = new double[values.length];
        for (int i = 0; i < values.length; i++) {
            deviations[i] = Math.abs(values[i] - median);
        }
        return calculateMedian(deviations);
    }
    
    private double calculateIQR(double[] values) {
        if (values == null || values.length < 4) {
            return 0.0;
        }
        double[] sorted = Arrays.copyOf(values, values.length);
        Arrays.sort(sorted);
        int n = sorted.length;
        
        int q1Index = n / 4;
        int q3Index = 3 * n / 4;
        
        double q1 = sorted[q1Index];
        double q3 = sorted[q3Index];
        
        return q3 - q1;
    }
    
    private int detectTies(double[] differences) {
        Map<Double, Integer> absValueCounts = new HashMap<>();
        
        for (double diff : differences) {
            if (diff != 0.0) {
                double absDiff = Math.abs(diff);
                absValueCounts.put(absDiff, absValueCounts.getOrDefault(absDiff, 0) + 1);
            }
        }
        
        int tiesCount = 0;
        for (int count : absValueCounts.values()) {
            if (count > 1) {
                tiesCount += count;
            }
        }
        
        return tiesCount;
    }
    
    private static class ShapiroWilkResult {
        double wStatistic;
        double pValue;
        
        ShapiroWilkResult(double w, double p) {
            this.wStatistic = w;
            this.pValue = p;
        }
    }
    
    private ShapiroWilkResult calculateShapiroWilk(double[] data) {
        int n = data.length;
        
        if (n < 3 || n > 5000) {
            return new ShapiroWilkResult(1.0, 1.0);
        }
        
        DescriptiveStatistics stats = new DescriptiveStatistics(data);
        double mean = stats.getMean();
        double stdDev = stats.getStandardDeviation();
        
        if (stdDev == 0.0) {
            return new ShapiroWilkResult(1.0, 1.0);
        }
        
        double[] standardized = new double[n];
        for (int i = 0; i < n; i++) {
            standardized[i] = (data[i] - mean) / stdDev;
        }
        Arrays.sort(standardized);
        
        double adStatistic = 0.0;
        for (int i = 0; i < n; i++) {
            double zi = standardized[i];
            double phi = calculateNormalCDF(zi);
            
            if (phi > 0 && phi < 1) {
                adStatistic += (2 * i + 1) * Math.log(phi) + (2 * (n - i) - 1) * Math.log(1 - phi);
            }
        }
        adStatistic = -n - adStatistic / n;
        
        double adAdjusted = adStatistic * (1.0 + 0.75/n + 2.25/(n*n));
        
        double pValue;
        if (adAdjusted < 0.2) {
            pValue = 1.0 - Math.exp(-13.436 + 101.14 * adAdjusted - 223.73 * adAdjusted * adAdjusted);
        } else if (adAdjusted < 0.34) {
            pValue = 1.0 - Math.exp(-8.318 + 42.796 * adAdjusted - 59.938 * adAdjusted * adAdjusted);
        } else if (adAdjusted < 0.6) {
            pValue = Math.exp(0.9177 - 4.279 * adAdjusted - 1.38 * adAdjusted * adAdjusted);
        } else if (adAdjusted < 10) {
            pValue = Math.exp(1.2937 - 5.709 * adAdjusted + 0.0186 * adAdjusted * adAdjusted);
        } else {
            pValue = 3.7e-24;
        }
        
        pValue = Math.max(0.0, Math.min(1.0, pValue));
        
        return new ShapiroWilkResult(adStatistic, pValue);
    }
    
    private String generateWilcoxonVariabilityInterpretation(double eacoMAD, double epsoMAD, 
                                                              double eacoMedian, double epsoMedian, 
                                                              String metricName) {
        double eacoQCD = eacoMedian != 0.0 ? (eacoMAD / Math.abs(eacoMedian)) * 100 : 0.0;
        double epsoQCD = epsoMedian != 0.0 ? (epsoMAD / Math.abs(epsoMedian)) * 100 : 0.0;
        
        String eacoStability = categorizeStability(eacoQCD);
        String epsoStability = categorizeStability(epsoQCD);
        
        StringBuilder interpretation = new StringBuilder();
        
        interpretation.append(String.format(
            "Consistency (using median-based measurement): EACO varies by %.2f%% (%s), EPSO varies by %.2f%% (%s). ",
            eacoQCD, eacoStability,
            epsoQCD, epsoStability
        ));
        
        double qcdDifference = Math.abs(eacoQCD - epsoQCD);
        
        if (qcdDifference < 5.0) {
            interpretation.append("Both algorithms show similar consistency in their typical performance. ");
        } else if (eacoQCD < epsoQCD) {
            double improvement = ((epsoQCD - eacoQCD) / epsoQCD) * 100;
            interpretation.append(String.format(
                "EACO is %.1f%% more consistent than EPSO, giving you more predictable results in production. ",
                improvement
            ));
        } else {
            double improvement = ((eacoQCD - epsoQCD) / eacoQCD) * 100;
            interpretation.append(String.format(
                "EPSO is %.1f%% more consistent than EACO, giving you more predictable results in production. ",
                improvement
            ));
        }
        
        double avgQCD = (eacoQCD + epsoQCD) / 2.0;
        interpretation.append(getStabilityImplication(avgQCD, metricName));
        
        return interpretation.toString();
    }
}
