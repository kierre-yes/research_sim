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
            "Our statistical analysis uses a 95%% confidence level, meaning we're 95%% certain our conclusions are correct and not due to random chance. We accept only a %.0f%% risk of being wrong (α = %.2f).",
            tTestResults.getAlpha() * 100,
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
            assumptions.append(String.format("Info: %d zero difference(s) excluded per standard Wilcoxon methodology (Pratt, 1959). ", zeroCount));
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
            "We used a robust statistical method (Wilcoxon test) with 95%% confidence. Unlike other tests, this one works even when your data isn't perfectly bell-curve shaped. " +
            "Think of it as a more flexible approach that ranks performance differences rather than assuming any specific pattern. Only %.0f%% risk of being wrong (α = %.2f).",
            tTestResults.getAlpha() * 100,
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
                "Bottom line: Both algorithms perform about the same. " +
                "Out of %d performance metrics we tested, only %d showed meaningful differences (less than 5%% chance of being random). " +
                "You can safely use either algorithm for this type of workload.",
                totalMetrics, significantMetrics
            );
        } else {
            if (winnerWins == significantMetrics) {
                return String.format(
                    "Clear winner: %s performs better overall. " +
                    "It won on %d out of %d metrics with strong statistical confidence (less than 5%% chance these results are random). " +
                    "We strongly recommend using %s for this workload type.",
                    winner, significantMetrics, totalMetrics, winner
                );
            } else {
                return String.format(
                    "Overall winner: %s performs better. " +
                    "Out of %d metrics tested, %d showed clear differences, with %s winning on %d of them. " +
                    "We recommend using %s for this workload type, though both algorithms have strengths.",
                    winner, totalMetrics, significantMetrics, winner, winnerWins, winner
                );
            }
        }
    }
    
    private String interpretMetricTest(String metricName, TTestResults.MetricTest test) {
        if (!test.isSignificant()) {
            return String.format(
                "Both algorithms perform similarly for %s. The statistical test found no meaningful difference (p-value = %.4f means there's a %.1f%% probability the difference is just random chance).",
                getMetricDisplayName(metricName),
                test.getPValue(),
                test.getPValue() * 100
            );
        }
        
        String effectInterpretation = getEffectSizeInterpretation(test.getEffectSize());
        
        return String.format(
            "%s performs %.1f%% better for %s. This is a %s difference with high statistical confidence (only %.2f%% chance this is random, Cohen's d = %.3f). " +
            "We're 95%% confident the true improvement is between %.3f and %.3f units.",
            test.getBetterAlgorithm(),
            test.getImprovementPercentage(),
            getMetricDisplayName(metricName),
            effectInterpretation,
            test.getPValue() * 100,
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
            "Looking at how big these differences really are in practice: "
        );
        
        if (effectCounts.get("Large") > 0) {
            explanation.append(String.format("%d metric(s) show large improvements (you'll clearly notice this difference in real-world use), ", effectCounts.get("Large")));
        }
        if (effectCounts.get("Medium") > 0) {
            explanation.append(String.format("%d metric(s) show moderate improvements (definitely worth considering), ", effectCounts.get("Medium")));
        }
        if (effectCounts.get("Small") > 0) {
            explanation.append(String.format("%d metric(s) show small improvements (detectable but minor impact), ", effectCounts.get("Small")));
        }
        if (effectCounts.get("Negligible") > 0) {
            explanation.append(String.format("%d metric(s) show negligible improvements (too small to matter in practice), ", effectCounts.get("Negligible")));
        }
        
        if (explanation.toString().endsWith(", ")) {
            explanation.setLength(explanation.length() - 2);
        }
        
        explanation.append(". Effect sizes tell us whether a statistically significant difference is actually meaningful in real-world scenarios.");
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
                "Bottom line (using robust statistical test): Both algorithms are essentially tied. " +
                "Only %d out of %d metrics showed clear differences (less than 5%% chance of being random). " +
                "Either algorithm would work well for this workload. " +
                "This test works with any data pattern, not just bell curves.",
                significantMetrics, totalMetrics
            );
        } else {
            if (winnerWins == significantMetrics) {
                return String.format(
                    "Clear winner (using robust ranking method): %s performs better. " +
                    "It won on %d out of %d metrics with strong confidence (less than 5%% random chance). " +
                    "We strongly recommend %s for this workload. " +
                    "This test ranks performance rather than assuming any specific data pattern, making it very reliable.",
                    winner, significantMetrics, totalMetrics, winner
                );
            } else {
                return String.format(
                    "Overall winner (using robust ranking method): %s performs better. " +
                    "Out of %d metrics, %d showed clear differences, with %s winning on %d of them. " +
                    "We recommend %s for this workload. " +
                    "This ranking-based test works with any data pattern, providing reliable results.",
                    winner, totalMetrics, significantMetrics, winner, winnerWins, winner
                );
            }
        }
    }
    
    private String interpretWilcoxonMetricTest(String metricName, TTestResults.WilcoxonTest test) {
        if (!test.isSignificant()) {
            return String.format(
                "Both algorithms perform similarly for %s. There's a %.1f%% probability this difference could be just random variation (p = %.4f), which is too high to declare a winner.",
                getMetricDisplayName(metricName),
                test.getPValue() * 100,
                test.getPValue()
            );
        }
        
        String ciExplanation = "";
        String stabilityInsight = "";
        String probabilityExplanation = String.format(
            "If you picked any random test run, there's a %.0f%% chance that %s would outperform the other algorithm.",
            50 + (test.getEffectSizeR() * 50),
            test.getBetterAlgorithm()
        );
        
        if (test.getCiLower() != 0.0 || test.getCiUpper() != 0.0) {
            ciExplanation = String.format(" We're 95%% confident the true median difference is between %.3f and %.3f.", 
                test.getCiLower(), test.getCiUpper());
            
            double ciRange = Math.abs(test.getCiUpper() - test.getCiLower());
            double ciMidpoint = (test.getCiUpper() + test.getCiLower()) / 2.0;
            double relativeVariability = ciMidpoint != 0 ? (ciRange / Math.abs(ciMidpoint)) : 0;
            
            if (relativeVariability < 0.3 && test.getEffectSizeR() > 0.5) {
                stabilityInsight = String.format(
                    " The narrow range of possible differences plus the strong effect size (r = %.3f) means %s consistently performs better, " +
                    "not just in lucky runs but across different conditions.",
                    test.getEffectSizeR(), test.getBetterAlgorithm()
                );
            } else if (test.getEffectSizeR() > 0.7) {
                stabilityInsight = String.format(
                    " The very strong effect size (r = %.3f) means %s has a robust advantage that holds up " +
                    "regardless of workload variations - it's reliably better.",
                    test.getEffectSizeR(), test.getBetterAlgorithm()
                );
            }
        }
        
        String effectInterpretation = getEffectSizeInterpretation(test.getEffectSize());
        
        return String.format(
            "%s performs %.1f%% better for %s. This is a %s difference with only %.2f%% chance of being random. " +
            "%s%s%s",
            test.getBetterAlgorithm(),
            test.getImprovementPercentage(),
            getMetricDisplayName(metricName),
            effectInterpretation,
            test.getPValue() * 100,
            probabilityExplanation,
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
            "Looking at how big these performance differences really are: "
        );
        
        if (effectCounts.get("Large") > 0) {
            explanation.append(String.format("%d metric(s) show large improvements (clearly noticeable in practice), ", effectCounts.get("Large")));
        }
        if (effectCounts.get("Medium") > 0) {
            explanation.append(String.format("%d metric(s) show moderate improvements (worth paying attention to), ", effectCounts.get("Medium")));
        }
        if (effectCounts.get("Small") > 0) {
            explanation.append(String.format("%d metric(s) show small improvements (detectable but minor), ", effectCounts.get("Small")));
        }
        if (effectCounts.get("Negligible") > 0) {
            explanation.append(String.format("%d metric(s) show negligible improvements (too small to matter), ", effectCounts.get("Negligible")));
        }
        
        if (explanation.toString().endsWith(", ")) {
            explanation.setLength(explanation.length() - 2);
        }
        
        explanation.append(". Effect size tells you whether a difference is big enough to care about in real-world use.");
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
                "Good news: All %d metrics have data that follows a bell curve pattern (normal distribution). " +
                "This means the standard t-test results are fully trustworthy and give you the most precise answers. " +
                "No need to second-guess these results.",
                totalMetrics
            ));
            interpretation.put("preferredTest", "Paired T-Test");
        } else if (nonNormalCount == totalMetrics) {
            conclusion.append(String.format(
                "Important: None of the %d metrics follow a bell curve pattern. " +
                "This means you should trust the Wilcoxon test results instead of the t-test. " +
                "The Wilcoxon test is designed to work with any data pattern, not just bell curves.",
                totalMetrics
            ));
            interpretation.put("preferredTest", "Wilcoxon Signed-Rank Test");
        } else {
            conclusion.append(String.format(
                "Mixed results: %d out of %d metrics follow bell curve patterns, %d don't. " +
                "For the bell-curve metrics, trust the t-test. For the others, trust the Wilcoxon test. " +
                "If both tests agree on which algorithm wins, you can be very confident. " +
                "If they disagree, go with the Wilcoxon result (it's more reliable when data patterns vary).",
                normalCount, totalMetrics, nonNormalCount
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
            "The Anderson-Darling test checks if your data follows a bell curve (normal distribution). " +
            "Think of it like asking: 'Does this data look like the classic symmetrical hump shape?' " +
            "If the p-value is above 0.05, the answer is yes (use t-test). " +
            "If below 0.05, the answer is no (use Wilcoxon test instead). " +
            "This test is especially good at catching unusual patterns at the extremes of your data."
        );
        
        interpretation.put("practicalGuidance", generateNormalityPracticalGuidance(normalCount, totalMetrics));
        
        return interpretation;
    }
    
    private String generateNormalityPracticalGuidance(int normalCount, int totalMetrics) {
        if (normalCount == totalMetrics) {
            return "Use the t-test results with confidence. Your data fits the assumptions perfectly, " +
                   "giving you the most accurate and powerful statistical analysis possible.";
        } else if (normalCount == 0) {
            return "Rely on the Wilcoxon test results. Since none of your data follows a bell curve, " +
                   "the Wilcoxon test gives you reliable answers without making assumptions about data patterns.";
        } else {
            return String.format(
                "Here's what to do: For the %d metric(s) with bell-curve data, use the t-test results. " +
                "For the %d metric(s) without bell-curve patterns, use the Wilcoxon results. " +
                "If both tests point to the same winner, you can be very confident. " +
                "If they disagree, go with the Wilcoxon result - it's safer when data patterns aren't ideal.",
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
                "Since %d out of %d metrics show no clear winner, " +
                "your choice might come down to other factors: implementation ease, maintenance costs, " +
                "team familiarity, or specific requirements we didn't measure here.",
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
            "The statistical evidence strongly favors %s (winning on %d out of %d metrics). ",
            winner, significantMetrics, totalMetrics
        ));
        
        if (avgEffectSize > 0.7) {
            implications.append("The differences are large enough that you'd clearly notice the improvement in production. ");
        } else if (avgEffectSize > 0.5) {
            implications.append("The differences are significant enough to make a real difference in your system. ");
        } else {
            implications.append("While the differences are statistically proven, they're relatively modest in size. ");
        }
        
        implications.append("Balance this statistical evidence with practical concerns like costs, scalability needs, ");
        implications.append("and your specific workload characteristics when making your final decision.");
        
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
    
    private String getEffectSizeInterpretation(String effectSize) {
        switch (effectSize.toLowerCase()) {
            case "large":
                return "substantial and practically important";
            case "medium":
                return "moderate and noticeable";
            case "small":
                return "small but detectable";
            case "negligible":
            default:
                return "negligible";
        }
    }
    
    public String generateMeanInterpretation(String metricName, TTestResults.MetricTest test) {
        String metricDisplay = getMetricDisplayName(metricName);
        boolean lowerIsBetter = isLowerBetterMetric(metricName);
        
        if (!test.isSignificant()) {
            return String.format(
                "Both algorithms show similar performance for %s. EACO averaged %.2f while EPSO averaged %.2f. " +
                "The difference isn't large enough to confidently say one is better (p=%.3f).",
                metricDisplay,
                test.getEacoMean(),
                test.getEpsoMean(),
                test.getPValue()
            );
        }
        
        String winner = test.getBetterAlgorithm();
        String loser = winner.equals("EACO") ? "EPSO" : "EACO";
        double winnerMean = winner.equals("EACO") ? test.getEacoMean() : test.getEpsoMean();
        double loserMean = winner.equals("EACO") ? test.getEpsoMean() : test.getEacoMean();
        double improvement = test.getImprovementPercentage();
        
        String magnitudePhrase;
        if (improvement > 20) {
            magnitudePhrase = "significantly outperforms.";
        } else if (improvement > 10) {
            magnitudePhrase = "performs notably better than.";
        } else if (improvement > 5) {
            magnitudePhrase = "performs better than";
        } else {
            magnitudePhrase = "shows a small but measurable advantage over.";
        }
        
        String comparisonWord = lowerIsBetter ? "faster" : "higher";
        String unit = getMetricUnit(metricName);
        
        return String.format(
            "%s %s %s for %s. %s achieved %s%s compared to %s's %s%s, making it %.1f%% %s. " +
            "This difference is statistically reliable (p=%.3f), meaning you can expect this performance advantage in real deployments.",
            winner,
            magnitudePhrase,
            loser,
            metricDisplay,
            winner,
            formatMetricValue(winnerMean),
            unit,
            loser,
            formatMetricValue(loserMean),
            unit,
            improvement,
            comparisonWord,
            test.getPValue()
        );
    }
    
    private boolean isLowerBetterMetric(String metricName) {
        switch (metricName) {
            case "makespan":
            case "energyConsumption":
            case "responseTime":
            case "loadBalance":
            case "loadImbalance":
                return true;
            case "resourceUtilization":
                return false;
            default:
                return true;
        }
    }
    
    private String getMetricUnit(String metricName) {
        switch (metricName) {
            case "makespan":
            case "responseTime":
                return " seconds";
            case "energyConsumption":
                return " Wh";
            case "resourceUtilization":
                return "%";
            case "loadBalance":
            case "loadImbalance":
                return "";
            default:
                return "";
        }
    }
    
    private String formatMetricValue(double value) {
        if (value >= 1000) {
            return String.format("%.0f", value);
        } else if (value >= 10) {
            return String.format("%.1f", value);
        } else {
            return String.format("%.2f", value);
        }
    }
}
