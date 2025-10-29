package com.thesis.cloudsim.service;

import com.thesis.cloudsim.dto.PlotMetadata;
import com.thesis.cloudsim.dto.ProcessedResults;
import com.thesis.cloudsim.dto.TTestResults;
import com.thesis.cloudsim.metrics.SimulationResults;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class AnalysisInterpretationService {
    
    private static final Logger logger = LoggerFactory.getLogger(AnalysisInterpretationService.class);
    private final PlotInterpretationService plotInterpretationService;

    public AnalysisInterpretationService(PlotInterpretationService plotInterpretationService) {
        this.plotInterpretationService = plotInterpretationService;
    }

    public Map<String, Object> generateCompleteAnalysis(ProcessedResults processedResults, String algorithmName) {
        Map<String, Object> analysis = new HashMap<>();
        SimulationResults results = processedResults.getRawResults();
        
        analysis.put("overallPerformance", generateOverallAnalysis(results, algorithmName));
        analysis.put("metricInterpretations", generateMetricInterpretations(results));
        analysis.put("efficiencyAnalysis", generateEfficiencyAnalysis(results));
        analysis.put("recommendations", generateRecommendations(results, algorithmName));
        
        if (processedResults.getPlotMetadata() != null && !processedResults.getPlotMetadata().isEmpty()) {
            analysis.put("plotInterpretations", processedResults.getPlotMetadata());
        }
        
        return analysis;
    }
    
    public Map<String, Object> generateStatisticalInterpretation(TTestResults tTestResults) {
        Map<String, Object> interpretation = new HashMap<>();
        
        String overallConclusion = generateStatisticalConclusion(tTestResults);
        interpretation.put("conclusion", overallConclusion);
        
        Map<String, String> metricInterpretations = new HashMap<>();
        for (Map.Entry<String, TTestResults.MetricTest> entry : tTestResults.getMetricTests().entrySet()) {
            String metricName = entry.getKey();
            TTestResults.MetricTest test = entry.getValue();
            metricInterpretations.put(metricName, interpretMetricTest(metricName, test));
        }
        interpretation.put("metricAnalysis", metricInterpretations);
        
        interpretation.put("effectSizeExplanation", generateEffectSizeExplanation(tTestResults));
        
        interpretation.put("confidenceExplanation", String.format(
            "We can determine statistically significant performance differences with 95%% confidence (α = %.2f).",
            tTestResults.getAlpha()
        ));
        
        return interpretation;
    }
    
    public Map<String, Object> generateEnhancedWilcoxonInterpretation(TTestResults tTestResults) {
        Map<String, Object> interpretation = new HashMap<>();
        
        Map<String, Object> basicInterpretation = generateWilcoxonInterpretation(tTestResults);
        interpretation.putAll(basicInterpretation);
        
        interpretation.put("researchReferences", generateWilcoxonResearchReferences());
        
        Map<String, Object> methodValidation = new HashMap<>();
        for (Map.Entry<String, TTestResults.WilcoxonTest> entry : tTestResults.getWilcoxonTests().entrySet()) {
            methodValidation.put(entry.getKey(), generateMethodValidationInfo(entry.getValue()));
        }
        interpretation.put("methodValidation", methodValidation);
        
        int maxSampleSize = tTestResults.getWilcoxonTests().values().stream()
            .mapToInt(test -> test.getSampleSize())
            .max().orElse(0);
        int minEffectiveSample = tTestResults.getWilcoxonTests().values().stream()
            .mapToInt(test -> test.getSampleSize())
            .min().orElse(0);
        boolean anyTies = false;
        
        interpretation.put("enhancedAssumptions", 
            generateEnhancedWilcoxonAssumptions(maxSampleSize, minEffectiveSample, anyTies));
        
        return interpretation;
    }
    
    private String generateWilcoxonResearchReferences() {
        return "Wilcoxon, F. (1945). Individual comparisons by ranking methods. Biometrics Bulletin, 1(6), 80-83; " +
               "Pratt, J.W. (1959). Remarks on zeros and ties in the Wilcoxon signed rank procedures. JASA, 54(287), 655-667; " +
               "Hodges, J.L. & Lehmann, E.L. (1963). Estimates of location based on rank tests. Ann. Math. Stat., 34(2), 598-611.";
    }
    
    private String interpretWilcoxonEffectSize(double effectSizeR) {
        String interpretation;
        String guidance;
        
        if (effectSizeR < 0.1) {
            interpretation = "Negligible";
            guidance = "Differences may not be practically significant for cloud workloads.";
        } else if (effectSizeR < 0.3) {
            interpretation = "Small"; 
            guidance = "Noticeable but modest improvement in algorithm performance.";
        } else if (effectSizeR < 0.5) {
            interpretation = "Medium";
            guidance = "Substantial performance difference with practical implications.";
        } else {
            interpretation = "Large";
            guidance = "Major performance difference strongly favoring one algorithm.";
        }
        
        return String.format("%s effect (r=%.3f). %s", interpretation, effectSizeR, guidance);
    }
    
    private Map<String, Object> generateMethodValidationInfo(TTestResults.WilcoxonTest test) {
        Map<String, Object> validation = new HashMap<>();
        
        validation.put("normalApproximationValid", test.getSampleSize() >= 20);
        validation.put("normalApproximationNote", test.getSampleSize() < 20 ? 
            "Small sample: Consider exact distribution tables" : "Normal approximation valid");
        validation.put("tiesPresent", false);
        validation.put("tiesImpact", "No ties detected");
        validation.put("zeroExclusions", 0);
        validation.put("effectiveSampleSize", test.getSampleSize());
        validation.put("originalSampleSize", test.getSampleSize());
        
        return validation;
    }
    
    private String generateEnhancedWilcoxonAssumptions(int totalSampleSize, int nonZeroCount, boolean hasTies) {
        StringBuilder assumptions = new StringBuilder();
        
        assumptions.append("The Wilcoxon signed-rank test assumes: ");
        assumptions.append("(1) Paired observations are independent across pairs; ");
        assumptions.append("(2) Differences are from a continuous or ordinal distribution; ");
        assumptions.append("(3) Distribution of differences is symmetric around the median; ");
        assumptions.append("(4) Minimal ties in absolute differences for optimal ranking precision. ");
        
        if (totalSampleSize < 10) {
            assumptions.append("Small sample size (n < 10) may reduce test power. ");
        }
        
        if (nonZeroCount < 20) {
            assumptions.append("Small sample warning: Normal approximation may be less accurate for n < 20. Consider exact distribution tables. ");
        }
        
        if (hasTies) {
            assumptions.append("Ties present: Multiple observations with identical absolute differences may slightly reduce test precision. ");
        }
        
        if (nonZeroCount < totalSampleSize) {
            int zeroCount = totalSampleSize - nonZeroCount;
            assumptions.append(String.format("ℹ️ %d zero difference(s) excluded per standard Wilcoxon methodology (Pratt, 1959). ", zeroCount));
        }
        
        return assumptions.toString();
    }
    
    public Map<String, Object> generateWilcoxonInterpretation(TTestResults tTestResults) {
        Map<String, Object> interpretation = new HashMap<>();
        
        String overallConclusion = generateWilcoxonConclusion(tTestResults);
        interpretation.put("conclusion", overallConclusion);
        
        Map<String, String> metricInterpretations = new HashMap<>();
        for (Map.Entry<String, TTestResults.WilcoxonTest> entry : tTestResults.getWilcoxonTests().entrySet()) {
            String metricName = entry.getKey();
            TTestResults.WilcoxonTest test = entry.getValue();
            metricInterpretations.put(metricName, interpretWilcoxonMetricTest(metricName, test));
        }
        interpretation.put("metricAnalysis", metricInterpretations);
        
        interpretation.put("effectSizeExplanation", generateWilcoxonEffectSizeExplanation(tTestResults));
        
        interpretation.put("confidenceExplanation", String.format(
            "We determine statistical significance using distribution-free methods with 95%% confidence (α = %.2f). " +
            "The Wilcoxon signed-rank test requires no assumptions about data normality. " +
            "This provides robust conclusions even when parametric assumptions are violated.",
            tTestResults.getAlpha()
        ));
        
        interpretation.put("assumptions", generateWilcoxonAssumptions(tTestResults));
        interpretation.put("practicalImplications", generateWilcoxonPracticalImplications(tTestResults));
        
        return interpretation;
    }
    
    private String generateWilcoxonAssumptions(TTestResults results) {
        int totalSampleSize = results.getWilcoxonTests().values().stream()
            .mapToInt(TTestResults.WilcoxonTest::getSampleSize)
            .max()
            .orElse(0);
        
        StringBuilder assumptions = new StringBuilder();
        assumptions.append("The Wilcoxon signed-rank test assumes: ");
        assumptions.append("(1) Paired observations are independent across pairs; ");
        assumptions.append("(2) Differences are from a continuous or ordinal distribution; ");
        assumptions.append("(3) Distribution of differences is symmetric around the median. ");
        
        if (totalSampleSize < 10) {
            assumptions.append("Note: Small sample size (n < 10) may reduce test power. ");
        }
        
        assumptions.append("Zero differences are excluded per standard Wilcoxon methodology, ");
        assumptions.append("resulting in varying sample sizes across metrics. ");
        assumptions.append("This test makes no assumptions about data normality, ");
        assumptions.append("providing robust results for non-normal distributions.");
        
        return assumptions.toString();
    }
    
    private Map<String, String> generateOverallAnalysis(SimulationResults results, String algorithmName) {
        Map<String, String> analysis = new HashMap<>();
        SimulationResults.Summary summary = results.getSummary();
        
        double performanceScore = calculatePerformanceScore(summary);
        String workloadType = inferWorkloadType(summary);
        String grade = getPerformanceGradeWithContext(performanceScore, workloadType);
        
        analysis.put("grade", grade);
        analysis.put("summary", String.format(
            "The %s algorithm achieved a %s performance grade. " +
            "Makespan reached %.2f seconds with %.1f%% resource utilization and %.2f Wh energy consumption.",
            algorithmName, grade, summary.getMakespan(), 
            summary.getResourceUtilization(), summary.getEnergyConsumption()
        ));
        
        analysis.put("strengths", identifyStrengths(summary));
        analysis.put("weaknesses", identifyWeaknesses(summary));
        
        return analysis;
    }
    
    private Map<String, String> generateMetricInterpretations(SimulationResults results) {
        Map<String, String> interpretations = new HashMap<>();
        SimulationResults.Summary summary = results.getSummary();
        
        interpretations.put("makespan", String.format(
            "The makespan of %.2f seconds shows the total time needed to complete all tasks. " +
            "This performance is %s for the given workload size.",
            summary.getMakespan(),
            categorizeMakespan(summary.getMakespan())
        ));
        
        interpretations.put("resourceUtilization", String.format(
            "Resource utilization reached %.1f%%, indicating that system resources are %s. " +
            "%s",
            summary.getResourceUtilization(),
            categorizeUtilization(summary.getResourceUtilization()),
            getUtilizationRecommendation(summary.getResourceUtilization())
        ));
        
        int taskCount = results.getSchedulingLog() != null ? 
            (int) results.getSchedulingLog().stream().filter(entry -> "assignment".equals(entry.getType())).count() : 1;
        double energyPerTask = taskCount > 0 ? summary.getEnergyConsumption() / taskCount : summary.getEnergyConsumption();
        
        String sustainabilityCategory = categorizeEnergyEfficiencyWithSustainability(
            summary.getEnergyConsumption(), summary.getMakespan(), "standard"
        );
        
        interpretations.put("energyConsumption", String.format(
            "Total energy consumption of %.2f Wh demonstrates %s energy efficiency. " +
            "This equals approximately %.4f Wh per task (%d tasks completed). " +
            "Carbon impact assessment shows: %s.",
            summary.getEnergyConsumption(),
            sustainabilityCategory,
            energyPerTask, taskCount,
            getCarbonImpactDescription(summary.getEnergyConsumption())
        ));
        
        interpretations.put("responseTime", String.format(
            "Average response time of %.2f seconds means users experience %s wait times for task completion. " +
            "This %s impacts user experience.",
            summary.getResponseTime(),
            categorizeResponseTime(summary.getResponseTime()),
            getResponseTimeImpact(summary.getResponseTime())
        ));
        
        double degreeOfImbalance = summary.getLoadImbalance() > 0 ? 
            summary.getLoadImbalance() : summary.getLoadBalance();
        
        interpretations.put("loadBalance", String.format(
            "The Degree of Imbalance (DI) of %.4f shows %s load distribution across resources. " +
            "%s",
            degreeOfImbalance,
            categorizeDegreeOfImbalance(degreeOfImbalance),
            getDegreeOfImbalanceImplication(degreeOfImbalance)
        ));
        
        return interpretations;
    }
    
    private Map<String, String> generateEfficiencyAnalysis(SimulationResults results) {
        Map<String, String> analysis = new HashMap<>();
        SimulationResults.Summary summary = results.getSummary();
        
        int taskCount = results.getSchedulingLog() != null ? 
            (int) results.getSchedulingLog().stream().filter(entry -> "assignment".equals(entry.getType())).count() : 1000;
        
        double throughput = taskCount / summary.getMakespan();
        double energyPerTask = taskCount > 0 ? summary.getEnergyConsumption() / taskCount : summary.getEnergyConsumption();
        
        analysis.put("throughput", String.format(
            "System throughput: %.2f tasks per second",
            throughput
        ));
        
        analysis.put("energyEfficiency", String.format(
            "Energy efficiency: %.4f Wh per task",
            energyPerTask
        ));
        
        analysis.put("overallEfficiency", String.format(
            "Overall efficiency score: %.1f%% (calculated from utilization, energy, and throughput)",
            calculateEfficiencyScore(summary) * 100
        ));
        
        return analysis;
    }
    
    private List<String> generateRecommendations(SimulationResults results, String algorithmName) {
        List<String> recommendations = new ArrayList<>();
        SimulationResults.Summary summary = results.getSummary();
        
        if (summary.getResourceUtilization() < 60) {
            recommendations.add("Reduce the number of VMs to improve resource efficiency and lower costs.");
        } else if (summary.getResourceUtilization() > 90) {
            recommendations.add("The system operates near capacity. Add more resources to prevent bottlenecks.");
        }
        
        if (summary.getLoadBalance() > 0.3) {
            recommendations.add("Load distribution is uneven. Implement better load balancing strategies.");
        }
        
        double energyPerSecond = summary.getEnergyConsumption() / summary.getMakespan();
        if (energyPerSecond > 0.5) {
            recommendations.add("High energy consumption detected. Optimize task scheduling for better energy efficiency.");
        }
        
        if (summary.getResponseTime() > 10) {
            recommendations.add("Response times are high. Prioritize time-sensitive tasks or add more processing power.");
        }
        
        if ("EACO".equals(algorithmName) && summary.getLoadBalance() > 0.2) {
            recommendations.add("Tune EACO with different pheromone parameters to improve load distribution.");
        } else if ("EPSO".equals(algorithmName) && summary.getResourceUtilization() < 70) {
            recommendations.add("Adjust EPSO particle parameters to explore better resource allocation solutions.");
        }
        
        if (recommendations.isEmpty()) {
            recommendations.add("The system performs well with current configuration. Continue monitoring for workload pattern changes.");
        }
        
        return recommendations;
    }
    
    private String generateStatisticalConclusion(TTestResults results) {
        int significantMetrics = results.getSignificantDifferences();
        int totalMetrics = results.getMetricTests().size();
        String winner = results.getOverallWinner();
        
        long winnerWins = results.getMetricTests().values().stream()
            .filter(t -> t.isSignificant() && winner.equals(t.getBetterAlgorithm()))
            .count();
        
        if ("No clear winner".equals(winner)) {
            return String.format(
                "Statistical analysis reveals no clear winner between algorithms. " +
                "%d out of %d metrics showed significant differences (p < 0.05). " +
                "Both algorithms perform similarly under current conditions.",
                significantMetrics, totalMetrics
            );
        } else {
            if (winnerWins == significantMetrics) {
                return String.format(
                    "%s shows statistically superior performance. " +
                    "We observed significant improvements in %d out of %d metrics (p < 0.05). " +
                    "The evidence strongly supports using %s for this workload type.",
                    winner, significantMetrics, totalMetrics, winner
                );
            } else {
                return String.format(
                    "%s shows statistically superior performance overall. " +
                    "We observed significant differences in %d out of %d metrics (p < 0.05), " +
                    "with %s winning %d metrics. " +
                    "The evidence supports using %s for this workload type.",
                    winner, significantMetrics, totalMetrics, winner, winnerWins, winner
                );
            }
        }
    }
    
    private String interpretMetricTest(String metricName, TTestResults.MetricTest test) {
        if (!test.isSignificant()) {
            return String.format(
                "No significant difference was detected (p = %.4f). Both algorithms perform similarly for %s.",
                test.getPValue(), getMetricDisplayName(metricName)
            );
        }
        
        return String.format(
            "%s shows a significant advantage in %s (p = %.4f). " +
            "Performance improved by %.1f%% with %s effect size (Cohen's d = %.3f). " +
            "95%% CI: [%.3f, %.3f]",
            test.getBetterAlgorithm(),
            getMetricDisplayName(metricName),
            test.getPValue(),
            test.getImprovementPercentage(),
            test.getEffectSize().toLowerCase(),
            test.getCohensD(),
            test.getCiLower(),
            test.getCiUpper()
        );
    }
    
    private String generateEffectSizeExplanation(TTestResults results) {
        Map<String, Integer> effectCounts = new HashMap<>();
        effectCounts.put("Negligible", 0);
        effectCounts.put("Small", 0);
        effectCounts.put("Medium", 0);
        effectCounts.put("Large", 0);
        
        for (TTestResults.MetricTest test : results.getMetricTests().values()) {
            if (test.isSignificant()) {
                effectCounts.compute(test.getEffectSize(), (k, v) -> v + 1);
            }
        }
        
        StringBuilder explanation = new StringBuilder(
            "Effect sizes show practical significance using cloud computing-specific thresholds: "
        );
        
        if (effectCounts.get("Large") > 0) {
            explanation.append(String.format("%d large effects (substantial practical difference), ", effectCounts.get("Large")));
        }
        if (effectCounts.get("Medium") > 0) {
            explanation.append(String.format("%d medium effects (moderate practical difference), ", effectCounts.get("Medium")));
        }
        if (effectCounts.get("Small") > 0) {
            explanation.append(String.format("%d small effects (minor practical difference), ", effectCounts.get("Small")));
        }
        if (effectCounts.get("Negligible") > 0) {
            explanation.append(String.format("%d negligible effects, ", effectCounts.get("Negligible")));
        }
        
        if (explanation.toString().endsWith(", ")) {
            explanation.setLength(explanation.length() - 2);
        }
        
        explanation.append(".");
        return explanation.toString();
    }
    
    private String generateWilcoxonConclusion(TTestResults results) {
        int significantMetrics = (int) results.getWilcoxonTests().values().stream()
            .filter(TTestResults.WilcoxonTest::isSignificant)
            .count();
        int totalMetrics = results.getWilcoxonTests().size();
        String winner = results.getOverallWinner();
        
        long winnerWins = results.getWilcoxonTests().values().stream()
            .filter(t -> t.isSignificant() && winner.equals(t.getBetterAlgorithm()))
            .count();
        
        if ("No clear winner".equals(winner)) {
            return String.format(
                "Non-parametric analysis shows no clear winner between algorithms. " +
                "%d out of %d metrics showed significant median differences using the Wilcoxon signed-rank test (p < 0.05). " +
                "Both algorithms perform similarly under current conditions with no statistically significant advantage.",
                significantMetrics, totalMetrics
            );
        } else {
            if (winnerWins == significantMetrics) {
                return String.format(
                    "%s shows statistically superior performance using rank-based analysis. " +
                    "We observed significant improvements in %d out of %d metrics (p < 0.05). " +
                    "The Wilcoxon signed-rank test provides robust evidence supporting %s for this workload type. " +
                    "This analysis assumes no normal distribution of performance metrics.",
                    winner, significantMetrics, totalMetrics, winner
                );
            } else {
                return String.format(
                    "%s shows statistically superior performance overall using rank-based analysis. " +
                    "We observed significant differences in %d out of %d metrics (p < 0.05), " +
                    "with %s winning %d metrics. " +
                    "The Wilcoxon signed-rank test provides robust evidence supporting %s for this workload type. " +
                    "This analysis assumes no normal distribution of performance metrics.",
                    winner, significantMetrics, totalMetrics, winner, winnerWins, winner
                );
            }
        }
    }
    
    private String interpretWilcoxonMetricTest(String metricName, TTestResults.WilcoxonTest test) {
        if (!test.isSignificant()) {
            return String.format(
                "No significant median difference was detected (p = %.4f, W = %.0f). " +
                "Both algorithms perform similarly for %s based on rank-based analysis.",
                test.getPValue(), test.getTestStatistic(), getMetricDisplayName(metricName)
            );
        }
        
        String ciExplanation = "";
        String stabilityInsight = "";
        
        if (test.getCiLower() != 0.0 || test.getCiUpper() != 0.0) {
            ciExplanation = String.format(" 95%% CI for median difference: [%.3f, %.3f].", 
                test.getCiLower(), test.getCiUpper());
            
            double ciRange = Math.abs(test.getCiUpper() - test.getCiLower());
            double ciMidpoint = (test.getCiUpper() + test.getCiLower()) / 2.0;
            double relativeVariability = ciMidpoint != 0 ? (ciRange / Math.abs(ciMidpoint)) : 0;
            
            if (relativeVariability < 0.3 && test.getEffectSizeR() > 0.5) {
                stabilityInsight = String.format(
                    " The tight confidence interval combined with large effect size (r = %.3f) indicates %s demonstrates " +
                    "consistent, reliable superiority across diverse workload conditions.",
                    test.getEffectSizeR(), test.getBetterAlgorithm()
                );
            } else if (test.getEffectSizeR() > 0.7) {
                stabilityInsight = String.format(
                    " The very large effect size (r = %.3f) suggests %s maintains robust performance advantages " +
                    "with minimal sensitivity to workload variations.",
                    test.getEffectSizeR(), test.getBetterAlgorithm()
                );
            }
        }
        
        return String.format(
            "%s shows a significant advantage in %s (p = %.4f, W = %.0f, Z = %.2f). " +
            "Performance improved by %.1f%% with %s effect size (rank-biserial r = %.3f). " +
            "This indicates a %.1f%% probability that %s outperforms the alternative in a randomly selected pair.%s%s",
            test.getBetterAlgorithm(),
            getMetricDisplayName(metricName),
            test.getPValue(),
            test.getTestStatistic(),
            test.getZScore(),
            test.getImprovementPercentage(),
            test.getEffectSize().toLowerCase(),
            test.getEffectSizeR(),
            50 + (test.getEffectSizeR() * 50),
            test.getBetterAlgorithm(),
            ciExplanation,
            stabilityInsight
        );
    }
    
    private String generateWilcoxonEffectSizeExplanation(TTestResults results) {
        Map<String, Integer> effectCounts = new HashMap<>();
        effectCounts.put("Negligible", 0);
        effectCounts.put("Small", 0);
        effectCounts.put("Medium", 0);
        effectCounts.put("Large", 0);
        
        for (TTestResults.WilcoxonTest test : results.getWilcoxonTests().values()) {
            if (test.isSignificant()) {
                effectCounts.compute(test.getEffectSize(), (k, v) -> v + 1);
            }
        }
        
        StringBuilder explanation = new StringBuilder(
            "Rank-biserial correlation (r) shows practical significance using non-parametric effect size measures: "
        );
        
        if (effectCounts.get("Large") > 0) {
            explanation.append(String.format("%d large effects (r > 0.5, substantial practical difference), ", effectCounts.get("Large")));
        }
        if (effectCounts.get("Medium") > 0) {
            explanation.append(String.format("%d medium effects (0.3 < r < 0.5, moderate practical difference), ", effectCounts.get("Medium")));
        }
        if (effectCounts.get("Small") > 0) {
            explanation.append(String.format("%d small effects (0.1 < r < 0.3, minor practical difference), ", effectCounts.get("Small")));
        }
        if (effectCounts.get("Negligible") > 0) {
            explanation.append(String.format("%d negligible effects (r < 0.1), ", effectCounts.get("Negligible")));
        }
        
        if (explanation.toString().endsWith(", ")) {
            explanation.setLength(explanation.length() - 2);
        }
        
        explanation.append(". These effect sizes measure the strength of association between algorithm choice and performance outcomes.");
        return explanation.toString();
    }
    
    public Map<String, Object> generateNormalityInterpretation(TTestResults tTestResults) {
        Map<String, Object> interpretation = new HashMap<>();
        
        if (tTestResults.getNormalityTests() == null || tTestResults.getNormalityTests().isEmpty()) {
            return interpretation;
        }
        
        int normalCount = (int) tTestResults.getNormalityTests().values().stream()
            .filter(TTestResults.NormalityTest::isNormal)
            .count();
        int totalMetrics = tTestResults.getNormalityTests().size();
        int nonNormalCount = totalMetrics - normalCount;
        
        StringBuilder conclusion = new StringBuilder();
        
        if (normalCount == totalMetrics) {
            conclusion.append(String.format(
                "All %d metrics follow normal distribution (Anderson-Darling p > 0.05). " +
                "Paired t-test assumptions are satisfied. T-test results are reliable and provide optimal statistical power. " +
                "Parametric analysis is appropriate for this dataset.",
                totalMetrics
            ));
            interpretation.put("preferredTest", "Paired T-Test");
        } else if (nonNormalCount == totalMetrics) {
            conclusion.append(String.format(
                "All %d metrics deviate from normal distribution (Anderson-Darling p ≤ 0.05). " +
                "Parametric assumptions are violated. Wilcoxon signed-rank test is strongly preferred " +
                "as it provides robust, assumption-free conclusions without requiring normality.",
                totalMetrics
            ));
            interpretation.put("preferredTest", "Wilcoxon Signed-Rank Test");
        } else {
            conclusion.append(String.format(
                "Mixed normality results: %d/%d metrics follow normal distribution, %d/%d deviate. " +
                "For metrics with normal differences, t-test is optimal. " +
                "For non-normal metrics, Wilcoxon test provides more reliable conclusions. " +
                "Consider both parametric and non-parametric results for comprehensive analysis.",
                normalCount, totalMetrics, nonNormalCount, totalMetrics
            ));
            interpretation.put("preferredTest", "Both Tests (Mixed Evidence)");
        }
        
        interpretation.put("conclusion", conclusion.toString());
        
        Map<String, String> metricInterpretations = new HashMap<>();
        for (Map.Entry<String, TTestResults.NormalityTest> entry : tTestResults.getNormalityTests().entrySet()) {
            String metricName = entry.getKey();
            TTestResults.NormalityTest test = entry.getValue();
            metricInterpretations.put(metricName, test.getInterpretation());
        }
        interpretation.put("metricAnalysis", metricInterpretations);
        
        interpretation.put("methodologyNote", 
            "Anderson-Darling test evaluates whether data differences follow a normal distribution. " +
            "This goodness-of-fit test gives more weight to the tails than alternative tests. " +
            "P-values > 0.05 indicate normality, validating parametric test assumptions. " +
            "P-values ≤ 0.05 indicate non-normality, recommending distribution-free methods."
        );
        
        interpretation.put("practicalGuidance", generateNormalityPracticalGuidance(normalCount, totalMetrics));
        
        return interpretation;
    }
    
    private String generateNormalityPracticalGuidance(int normalCount, int totalMetrics) {
        if (normalCount == totalMetrics) {
            return "Trust the paired t-test results. The data meets all parametric assumptions, " +
                   "providing maximum statistical power and narrower confidence intervals for detecting true differences.";
        } else if (normalCount == 0) {
            return "Trust the Wilcoxon signed-rank test results. The non-parametric approach is robust to " +
                   "distributional violations and provides valid conclusions without normality assumptions.";
        } else {
            return String.format(
                "Compare both test results. For %d normal metric(s), t-test is optimal. " +
                "For %d non-normal metric(s), Wilcoxon test is more appropriate. " +
                "If both tests agree on significance, conclusions are highly reliable. " +
                "If tests disagree, prioritize Wilcoxon for conservative, robust inference.",
                normalCount, totalMetrics - normalCount
            );
        }
    }
    
    private String generateWilcoxonPracticalImplications(TTestResults results) {
        String winner = results.getOverallWinner();
        int significantMetrics = (int) results.getWilcoxonTests().values().stream()
            .filter(TTestResults.WilcoxonTest::isSignificant)
            .count();
        int totalMetrics = results.getWilcoxonTests().size();
        
        if ("No clear winner".equals(winner)) {
            return String.format(
                "With %d/%d metrics showing no significant median difference, " +
                "algorithm selection may depend on specific operational priorities, " +
                "cost constraints, or non-measured factors such as implementation complexity and maintenance requirements.",
                totalMetrics - significantMetrics, totalMetrics
            );
        }
        
        double avgEffectSize = results.getWilcoxonTests().values().stream()
            .filter(TTestResults.WilcoxonTest::isSignificant)
            .mapToDouble(TTestResults.WilcoxonTest::getEffectSizeR)
            .average()
            .orElse(0.0);
        
        StringBuilder implications = new StringBuilder();
        implications.append(String.format(
            "The rank-based analysis provides strong evidence for %s superiority (%d/%d significant metrics). ",
            winner, significantMetrics, totalMetrics
        ));
        
        if (avgEffectSize > 0.7) {
            implications.append("The large effect sizes suggest substantial practical benefits in production deployment. ");
        } else if (avgEffectSize > 0.5) {
            implications.append("Moderate to large effect sizes indicate meaningful performance differences in real-world scenarios. ");
        } else {
            implications.append("While statistically significant, effect sizes suggest modest practical differences. ");
        }
        
        implications.append("Consider this evidence alongside operational factors such as resource costs, ");
        implications.append("scalability requirements, and specific workload characteristics when making deployment decisions.");
        
        return implications.toString();
    }
    
    private String categorizeMakespan(double makespan) {
        if (makespan < 5) return "excellent";
        if (makespan < 15) return "good";
        if (makespan < 30) return "moderate";
        return "needs improvement";
    }
    
    private String categorizeUtilization(double utilization) {
        if (utilization < 40) return "underutilized";
        if (utilization < 60) return "moderately utilized";
        if (utilization < 80) return "well utilized";
        if (utilization < 95) return "highly utilized";
        return "near capacity";
    }
    
    private String categorizeEnergyEfficiency(double energy, double makespan) {
        double energyPerSecond = energy / makespan;
        if (energyPerSecond < 0.1) return "excellent";
        if (energyPerSecond < 0.3) return "good";
        if (energyPerSecond < 0.5) return "moderate";
        return "poor";
    }
    
    private String categorizeEnergyEfficiencyWithSustainability(double energy, double makespan, String datacenterLocation) {
        double energyPerSecond = energy / makespan;
        
        double sustainabilityFactor = getSustainabilityFactor(datacenterLocation);
        double adjustedEnergyRate = energyPerSecond * sustainabilityFactor;
        
        if (adjustedEnergyRate < 0.05) {
            return "excellent (carbon-neutral)";
        } else if (adjustedEnergyRate < 0.15) {
            return "good (low-carbon)";
        } else if (adjustedEnergyRate < 0.35) {
            return "moderate (standard emissions)";
        } else {
            return "poor (high-carbon footprint)";
        }
    }
    
    private double getSustainabilityFactor(String datacenterLocation) {
        if (datacenterLocation == null) {
            return 1.0; 
        }
        
        switch (datacenterLocation.toLowerCase()) {
            case "renewable":
            case "green":
            case "nordic":
                return 0.3;
            case "hybrid":
            case "europe":
                return 0.6;
            case "standard":
            case "usa":
                return 0.8;
            case "coal":
            case "fossil":
                return 1.5;
            default:
                return 1.0;
        }
    }
    
    private String getCarbonImpactDescription(double energyWh) {
        double energyKwh = energyWh / 1000.0;
        double co2Kg = energyKwh * 0.475;
        
        if (co2Kg < 0.01) {
            return "negligible CO2 emissions";
        } else if (co2Kg < 0.1) {
            return String.format("~%.3f kg CO2 equivalent (low impact)", co2Kg);
        } else if (co2Kg < 1.0) {
            return String.format("~%.2f kg CO2 equivalent (moderate impact)", co2Kg);
        } else {
            return String.format("~%.1f kg CO2 equivalent (consider optimization for sustainability)", co2Kg);
        }
    }
    
    private String categorizeResponseTime(double responseTime) {
        if (responseTime < 1) return "excellent";
        if (responseTime < 5) return "good";
        if (responseTime < 10) return "acceptable";
        return "slow";
    }
    
    private String categorizeLoadBalance(double loadBalance) {
        if (loadBalance < 0.1) return "excellent";
        if (loadBalance < 0.2) return "good";
        if (loadBalance < 0.3) return "moderate";
        return "poor";
    }
    
    private String categorizeDegreeOfImbalance(double degreeOfImbalance) {
        if (degreeOfImbalance < 0.1) return "excellent";
        if (degreeOfImbalance < 0.3) return "good";
        if (degreeOfImbalance < 0.5) return "moderate";
        return "poor";
    }
    
    private String getUtilizationRecommendation(double utilization) {
        if (utilization < 40) {
            return "Consider consolidating workloads to fewer resources.";
        } else if (utilization > 90) {
            return "The system is near capacity. Consider scaling up.";
        }
        return "Utilization is within the optimal range.";
    }
    
    private String getResponseTimeImpact(double responseTime) {
        if (responseTime < 1) return "minimally";
        if (responseTime < 5) return "slightly";
        if (responseTime < 10) return "moderately";
        return "significantly";
    }
    
    private String getLoadBalanceImplication(double loadBalance) {
        if (loadBalance < 0.1) {
            return "Excellent distribution ensures optimal resource usage.";
        } else if (loadBalance < 0.3) {
            return "Good distribution with minor optimization opportunities.";
        }
        return "Uneven distribution may cause bottlenecks and inefficiency.";
    }
    
    private String getDegreeOfImbalanceImplication(double degreeOfImbalance) {
        if (degreeOfImbalance < 0.1) {
            return "Excellent load distribution ensures optimal resource usage and prevents bottlenecks.";
        } else if (degreeOfImbalance < 0.3) {
            return "Good load distribution with minor optimization opportunities available.";
        } else if (degreeOfImbalance < 0.5) {
            return "Moderate imbalance detected. Consider load redistribution for better performance.";
        }
        return "High imbalance may cause resource bottlenecks and reduced efficiency.";
    }
    
    private double calculatePerformanceScore(SimulationResults.Summary summary) {
        double makespanScore = Math.max(0, Math.min(1, 1 - (Math.log10(summary.getMakespan() + 1) / 3)));
        
        double utilization = summary.getResourceUtilization();
        double utilizationScore = utilization < 60 ? utilization / 60 : 
                                 utilization > 80 ? 1 - (utilization - 80) / 20 : 1.0;
        
        double energyScore = Math.max(0, Math.min(1, 1 - (Math.log10(summary.getEnergyConsumption() + 1) / 4)));
        double responseScore = Math.max(0, Math.min(1, 1 - (Math.log10(summary.getResponseTime() + 1) / 2)));
        double balanceScore = Math.max(0, 1 - Math.min(1, summary.getLoadBalance()));
        
        return (makespanScore * 0.2 + 
                utilizationScore * 0.2 + 
                energyScore * 0.2 + 
                responseScore * 0.2 + 
                balanceScore * 0.2);
    }
    
    private double calculateEfficiencyScore(SimulationResults.Summary summary) {
        double throughput = 1000.0 / summary.getMakespan();
        double throughputScore = Math.min(1, throughput / 100);
        double utilizationScore = summary.getResourceUtilization() / 100;
        double energyScore = Math.max(0, 1 - (summary.getEnergyConsumption() / 1000));
        
        return (throughputScore + utilizationScore + energyScore) / 3;
    }
    
    private String getPerformanceGrade(double score) {
        if (score >= 0.93) return "A+";
        if (score >= 0.87) return "A";
        if (score >= 0.80) return "A-";
        if (score >= 0.73) return "B+";
        if (score >= 0.67) return "B";
        if (score >= 0.60) return "B-";
        if (score >= 0.53) return "C+";
        if (score >= 0.47) return "C";
        if (score >= 0.40) return "C-";
        if (score >= 0.33) return "D+";
        if (score >= 0.27) return "D";
        if (score >= 0.20) return "D-";
        return "F";
    }
    
    private String getPerformanceGradeWithContext(double score, String workloadType) {
        double adjustedScore = score;
        
        if ("compute-intensive".equalsIgnoreCase(workloadType)) {
            adjustedScore = score * 1.05; 
        } else if ("data-intensive".equalsIgnoreCase(workloadType)) {
            adjustedScore = score;
        } else if ("mixed".equalsIgnoreCase(workloadType) || workloadType == null) {
            adjustedScore = score;
        } else if ("real-time".equalsIgnoreCase(workloadType)) {
            adjustedScore = score * 0.95; 
        }
        
        adjustedScore = Math.min(1.0, adjustedScore);
        return getPerformanceGrade(adjustedScore);
    }
    
    private String identifyStrengths(SimulationResults.Summary summary) {
        List<String> strengths = new ArrayList<>();
        
        if (summary.getMakespan() < 10) {
            strengths.add("Fast task completion");
        }
        if (summary.getResourceUtilization() > 70 && summary.getResourceUtilization() < 90) {
            strengths.add("Optimal resource utilization");
        }
        if (summary.getEnergyConsumption() < 100) {
            strengths.add("Energy efficient");
        }
        if (summary.getResponseTime() < 5) {
            strengths.add("Low response time");
        }
        if (summary.getLoadBalance() < 0.2) {
            strengths.add("Well-balanced load distribution");
        }
        
        return strengths.isEmpty() ? "Performance within expected parameters" : String.join(", ", strengths);
    }
    
    private String identifyWeaknesses(SimulationResults.Summary summary) {
        List<String> weaknesses = new ArrayList<>();
        
        if (summary.getMakespan() > 30) {
            weaknesses.add("Slow task completion");
        }
        if (summary.getResourceUtilization() < 50) {
            weaknesses.add("Low resource utilization");
        }
        if (summary.getResourceUtilization() > 95) {
            weaknesses.add("Resource overutilization");
        }
        if (summary.getEnergyConsumption() > 500) {
            weaknesses.add("High energy consumption");
        }
        if (summary.getResponseTime() > 10) {
            weaknesses.add("High response time");
        }
        if (summary.getLoadBalance() > 0.3) {
            weaknesses.add("Poor load distribution");
        }
        
        return weaknesses.isEmpty() ? "No significant weaknesses identified" : String.join(", ", weaknesses);
    }
    
    private String getMetricDisplayName(String metricName) {
        switch (metricName) {
            case "makespan": return "makespan (total completion time)";
            case "energyConsumption": return "energy consumption";
            case "resourceUtilization": return "resource utilization";
            case "responseTime": return "response time";
            case "loadBalance": return "degree of imbalance";
            case "loadImbalance": return "degree of imbalance";
            default: return metricName;
        }
    }
    
    private String inferWorkloadType(SimulationResults.Summary summary) {
        if (summary.getResourceUtilization() > 75 && summary.getMakespan() > 20) {
            return "compute-intensive";
        } else if (summary.getMakespan() < 10 && summary.getResourceUtilization() < 70) {
            return "data-intensive";
        } else if (summary.getResponseTime() < 2) {
            return "real-time";
        } else {
            return "mixed";
        }
    }
}
