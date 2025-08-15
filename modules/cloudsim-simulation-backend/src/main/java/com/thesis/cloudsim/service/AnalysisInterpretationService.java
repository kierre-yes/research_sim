package com.thesis.cloudsim.service;

import com.thesis.cloudsim.dto.TTestResults;
import com.thesis.cloudsim.metrics.SimulationResults;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * I created this service to generate detailed analysis and interpretations of simulation results.
 * This provides contextual, data-driven explanations instead of generic placeholders
 * that the frontend currently displays. The service centralizes all interpretation logic
 * so that accurate insights are generated based on actual simulation data.
 */
@Service
public class AnalysisInterpretationService {
    
    private static final Logger logger = LoggerFactory.getLogger(AnalysisInterpretationService.class);
    
    /**
     * I generate comprehensive analysis for simulation results including overall performance,
     * metric-specific interpretations, efficiency analysis, recommendations, and plot interpretations.
     * This ensures the frontend receives meaningful data instead of placeholders.
     */
    public Map<String, Object> generateCompleteAnalysis(SimulationResults results, String algorithmName) {
        Map<String, Object> analysis = new HashMap<>();
        
        analysis.put("overallPerformance", generateOverallAnalysis(results, algorithmName));
        analysis.put("metricInterpretations", generateMetricInterpretations(results));
        analysis.put("efficiencyAnalysis", generateEfficiencyAnalysis(results));
        analysis.put("recommendations", generateRecommendations(results, algorithmName));
        analysis.put("plotInterpretations", generatePlotInterpretations(results));
        
        return analysis;
    }
    
    /**
     * I generate statistical test interpretation to provide meaningful explanations
     * of t-test results instead of just raw numbers. This helps users understand
     * what the statistical significance actually means for their use case.
     */
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
            "With 95%% confidence (α = %.2f), we can determine which performance differences are statistically significant.",
            tTestResults.getAlpha()
        ));
        
        return interpretation;
    }
    
    /**
     * I generate interpretations for MATLAB plots based on actual metric values.
     * Each plot type gets a specific interpretation that explains what the visualization
     * shows and what it means for system performance.
     */
    public Map<String, String> generatePlotInterpretations(SimulationResults results) {
        Map<String, String> plotInterpretations = new HashMap<>();
        
        SimulationResults.Summary summary = results.getSummary();
        
        plotInterpretations.put("metrics", String.format(
            "The metrics comparison shows overall performance characteristics. " +
            "Makespan of %.2f seconds indicates %s task completion time. " +
            "Resource utilization at %.1f%% suggests %s efficiency.",
            summary.getMakespan(),
            categorizeMakespan(summary.getMakespan()),
            summary.getResourceUtilization(),
            categorizeUtilization(summary.getResourceUtilization())
        ));
        
        plotInterpretations.put("vm_utilization", String.format(
            "VM utilization patterns reveal resource distribution efficiency. " +
            "Average utilization of %.1f%% with load balance index of %.3f indicates %s distribution.",
            summary.getResourceUtilization(),
            summary.getLoadBalance(),
            categorizeLoadBalance(summary.getLoadBalance())
        ));
        
        plotInterpretations.put("energy", String.format(
            "Energy consumption totals %.2f Wh. This represents %s energy efficiency " +
            "considering the workload size and execution time.",
            summary.getEnergyConsumption(),
            categorizeEnergyEfficiency(summary.getEnergyConsumption(), summary.getMakespan())
        ));
        
        plotInterpretations.put("timeline", String.format(
            "The execution timeline shows task scheduling patterns over %.2f seconds. " +
            "Response time averaging %.2f seconds indicates %s user experience.",
            summary.getMakespan(),
            summary.getResponseTime(),
            categorizeResponseTime(summary.getResponseTime())
        ));
        
        plotInterpretations.put("radar", 
            "The radar chart provides a multi-dimensional view of algorithm performance. " +
            "Larger area coverage indicates better overall performance across all metrics. " +
            "Shape symmetry reveals balanced optimization across different objectives."
        );
        
        return plotInterpretations;
    }
    
    /**
     * I analyze overall performance and generate a summary with grade, strengths, and weaknesses.
     * The performance score is calculated using weighted metrics to provide a holistic view.
     */
    private Map<String, String> generateOverallAnalysis(SimulationResults results, String algorithmName) {
        Map<String, String> analysis = new HashMap<>();
        SimulationResults.Summary summary = results.getSummary();
        
        double performanceScore = calculatePerformanceScore(summary);
        String grade = getPerformanceGrade(performanceScore);
        
        analysis.put("grade", grade);
        analysis.put("summary", String.format(
            "%s algorithm achieved a %s performance grade with makespan of %.2f seconds, " +
            "%.1f%% resource utilization, and %.2f Wh energy consumption.",
            algorithmName, grade, summary.getMakespan(), 
            summary.getResourceUtilization(), summary.getEnergyConsumption()
        ));
        
        analysis.put("strengths", identifyStrengths(summary));
        analysis.put("weaknesses", identifyWeaknesses(summary));
        
        return analysis;
    }
    
    /**
     * I generate specific interpretations for each metric, explaining what the values mean
     * in practical terms and providing context for decision-making.
     */
    private Map<String, String> generateMetricInterpretations(SimulationResults results) {
        Map<String, String> interpretations = new HashMap<>();
        SimulationResults.Summary summary = results.getSummary();
        
        interpretations.put("makespan", String.format(
            "Makespan of %.2f seconds represents the total time to complete all tasks. " +
            "This is %s for the given workload size.",
            summary.getMakespan(),
            categorizeMakespan(summary.getMakespan())
        ));
        
        interpretations.put("resourceUtilization", String.format(
            "Resource utilization at %.1f%% indicates that system resources are %s. " +
            "%s",
            summary.getResourceUtilization(),
            categorizeUtilization(summary.getResourceUtilization()),
            getUtilizationRecommendation(summary.getResourceUtilization())
        ));
        
        interpretations.put("energyConsumption", String.format(
            "Total energy consumption of %.2f Wh reflects %s energy efficiency. " +
            "This translates to approximately %.4f Wh per task.",
            summary.getEnergyConsumption(),
            categorizeEnergyEfficiency(summary.getEnergyConsumption(), summary.getMakespan()),
            summary.getEnergyConsumption() / 1000.0
        ));
        
        interpretations.put("responseTime", String.format(
            "Average response time of %.2f seconds means users wait %s for task completion. " +
            "This impacts user experience %s.",
            summary.getResponseTime(),
            categorizeResponseTime(summary.getResponseTime()),
            getResponseTimeImpact(summary.getResponseTime())
        ));
        
        interpretations.put("loadBalance", String.format(
            "Load balance degree of %.3f indicates %s distribution across resources. " +
            "%s",
            summary.getLoadBalance(),
            categorizeLoadBalance(summary.getLoadBalance()),
            getLoadBalanceImplication(summary.getLoadBalance())
        ));
        
        return interpretations;
    }
    
    /**
     * I calculate and analyze efficiency metrics including throughput, energy per task,
     * and overall efficiency score to provide a complete efficiency profile.
     */
    private Map<String, String> generateEfficiencyAnalysis(SimulationResults results) {
        Map<String, String> analysis = new HashMap<>();
        SimulationResults.Summary summary = results.getSummary();
        
        double throughput = 1000.0 / summary.getMakespan();
        double energyPerTask = summary.getEnergyConsumption() / 1000.0;
        double utilizationEfficiency = summary.getResourceUtilization() / 100.0;
        
        analysis.put("throughput", String.format(
            "System throughput: %.2f tasks/second",
            throughput
        ));
        
        analysis.put("energyEfficiency", String.format(
            "Energy efficiency: %.4f Wh per task",
            energyPerTask
        ));
        
        analysis.put("overallEfficiency", String.format(
            "Overall efficiency score: %.1f%% (based on utilization, energy, and throughput)",
            calculateEfficiencyScore(summary) * 100
        ));
        
        return analysis;
    }
    
    /**
     * I generate actionable recommendations based on the simulation results.
     * These recommendations are specific to the observed performance patterns
     * and the algorithm being used.
     */
    private List<String> generateRecommendations(SimulationResults results, String algorithmName) {
        List<String> recommendations = new ArrayList<>();
        SimulationResults.Summary summary = results.getSummary();
        
        if (summary.getResourceUtilization() < 60) {
            recommendations.add("Consider reducing the number of VMs to improve resource efficiency and reduce costs.");
        } else if (summary.getResourceUtilization() > 90) {
            recommendations.add("System is near capacity. Consider adding more resources to prevent bottlenecks.");
        }
        
        if (summary.getLoadBalance() > 0.3) {
            recommendations.add("Load distribution is uneven. Consider implementing better load balancing strategies.");
        }
        
        double energyPerSecond = summary.getEnergyConsumption() / summary.getMakespan();
        if (energyPerSecond > 0.5) {
            recommendations.add("High energy consumption detected. Consider optimizing task scheduling for energy efficiency.");
        }
        
        if (summary.getResponseTime() > 10) {
            recommendations.add("Response times are high. Consider prioritizing time-sensitive tasks or adding more processing power.");
        }
        
        if ("EACO".equals(algorithmName) && summary.getLoadBalance() > 0.2) {
            recommendations.add("EACO can be tuned with different pheromone parameters to improve load distribution.");
        } else if ("EPSO".equals(algorithmName) && summary.getResourceUtilization() < 70) {
            recommendations.add("EPSO particle parameters can be adjusted to explore better resource allocation solutions.");
        }
        
        if (recommendations.isEmpty()) {
            recommendations.add("System is performing well with current configuration. Continue monitoring for changes in workload patterns.");
        }
        
        return recommendations;
    }
    
    /**
     * I create a comprehensive statistical conclusion that summarizes the t-test results
     * in plain language, making it accessible to users without statistical background.
     */
    private String generateStatisticalConclusion(TTestResults results) {
        int significantMetrics = results.getSignificantDifferences();
        int totalMetrics = results.getMetricTests().size();
        String winner = results.getOverallWinner();
        
        if ("No clear winner".equals(winner)) {
            return String.format(
                "Statistical analysis shows no clear winner between algorithms. " +
                "Out of %d metrics analyzed, %d showed significant differences (p < 0.05). " +
                "Both algorithms perform comparably under current conditions.",
                totalMetrics, significantMetrics
            );
        } else {
            return String.format(
                "%s demonstrates statistically superior performance. " +
                "Significant improvements observed in %d out of %d metrics (p < 0.05). " +
                "The evidence strongly supports using %s for this workload type.",
                winner, significantMetrics, totalMetrics, winner
            );
        }
    }
    
    /**
     * I interpret individual metric test results, explaining the statistical significance,
     * effect size, and practical implications of the differences observed.
     */
    private String interpretMetricTest(String metricName, TTestResults.MetricTest test) {
        if (!test.isSignificant()) {
            return String.format(
                "No significant difference detected (p = %.4f). Both algorithms perform similarly for %s.",
                test.getPValue(), getMetricDisplayName(metricName)
            );
        }
        
        return String.format(
            "%s shows significant advantage in %s (p = %.4f). " +
            "Performance improvement of %.1f%% with %s effect size (Cohen's d = %.3f). " +
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
    
    /**
     * I generate an explanation of effect sizes across all metrics to help users
     * understand the practical significance beyond statistical significance.
     */
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
        
        StringBuilder explanation = new StringBuilder("Effect sizes indicate practical significance: ");
        
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
    
    /**
     * I categorize metrics into qualitative descriptions to make the data more accessible.
     * These helper methods convert numerical values into meaningful categories.
     */
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
    
    /**
     * I provide specific recommendations based on utilization levels to help
     * optimize resource usage and costs.
     */
    private String getUtilizationRecommendation(double utilization) {
        if (utilization < 40) {
            return "Consider consolidating workloads to fewer resources.";
        } else if (utilization > 90) {
            return "System is near capacity, consider scaling up.";
        }
        return "Utilization is within optimal range.";
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
        return "Uneven distribution may lead to bottlenecks and inefficiency.";
    }
    
    /**
     * I calculate a composite performance score using weighted metrics.
     * This provides a single score that represents overall system performance.
     */
    private double calculatePerformanceScore(SimulationResults.Summary summary) {
        double makespanScore = Math.max(0, 1 - (summary.getMakespan() / 100));
        double utilizationScore = summary.getResourceUtilization() / 100;
        double energyScore = Math.max(0, 1 - (summary.getEnergyConsumption() / 1000));
        double responseScore = Math.max(0, 1 - (summary.getResponseTime() / 20));
        double balanceScore = Math.max(0, 1 - summary.getLoadBalance());
        
        return (makespanScore * 0.3 + 
                utilizationScore * 0.2 + 
                energyScore * 0.2 + 
                responseScore * 0.2 + 
                balanceScore * 0.1);
    }
    
    private double calculateEfficiencyScore(SimulationResults.Summary summary) {
        double throughput = 1000.0 / summary.getMakespan();
        double throughputScore = Math.min(1, throughput / 100);
        double utilizationScore = summary.getResourceUtilization() / 100;
        double energyScore = Math.max(0, 1 - (summary.getEnergyConsumption() / 1000));
        
        return (throughputScore + utilizationScore + energyScore) / 3;
    }
    
    /**
     * I convert performance scores to letter grades for intuitive understanding.
     * This familiar grading system makes it easy to assess performance at a glance.
     */
    private String getPerformanceGrade(double score) {
        if (score >= 0.9) return "A+";
        if (score >= 0.85) return "A";
        if (score >= 0.8) return "A-";
        if (score >= 0.75) return "B+";
        if (score >= 0.7) return "B";
        if (score >= 0.65) return "B-";
        if (score >= 0.6) return "C+";
        if (score >= 0.55) return "C";
        if (score >= 0.5) return "C-";
        return "D";
    }
    
    /**
     * I identify system strengths based on metric thresholds.
     * This helps users understand what aspects of their system are performing well.
     */
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
    
    /**
     * I identify system weaknesses that need attention.
     * This helps prioritize optimization efforts.
     */
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
            case "loadBalance": return "load balance";
            default: return metricName;
        }
    }
}
