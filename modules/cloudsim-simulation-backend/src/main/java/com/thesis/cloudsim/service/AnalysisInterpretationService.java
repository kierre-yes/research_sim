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
        
        // Only include plot interpretations if we have actual plots from MATLAB
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
            "With 95%% confidence (α = %.2f), we can determine which performance differences are statistically significant.",
            tTestResults.getAlpha()
        ));
        
        return interpretation;
    }
    
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
        
        int taskCount = results.getSchedulingLog() != null ? 
            (int) results.getSchedulingLog().stream().filter(entry -> "assignment".equals(entry.getType())).count() : 1;
        double energyPerTask = taskCount > 0 ? summary.getEnergyConsumption() / taskCount : summary.getEnergyConsumption();
        
        interpretations.put("energyConsumption", String.format(
            "Total energy consumption of %.2f Wh reflects %s energy efficiency. " +
            "This translates to approximately %.4f Wh per task (%d tasks completed).",
            summary.getEnergyConsumption(),
            categorizeEnergyEfficiency(summary.getEnergyConsumption(), summary.getMakespan()),
            energyPerTask, taskCount
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
    
    private Map<String, String> generateEfficiencyAnalysis(SimulationResults results) {
        Map<String, String> analysis = new HashMap<>();
        SimulationResults.Summary summary = results.getSummary();
        
        int taskCount = results.getSchedulingLog() != null ? 
            (int) results.getSchedulingLog().stream().filter(entry -> "assignment".equals(entry.getType())).count() : 1000;
        
        double throughput = taskCount / summary.getMakespan();
        double energyPerTask = taskCount > 0 ? summary.getEnergyConsumption() / taskCount : summary.getEnergyConsumption();
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
    
    private double calculatePerformanceScore(SimulationResults.Summary summary) {
        
        // typical range 10-1000 seconds for standard workloads
        double makespanScore = Math.max(0, Math.min(1, 1 - (Math.log10(summary.getMakespan() + 1) / 3)));
        
        // Utilization: already in percentage, optimal is 60-80%
        double utilization = summary.getResourceUtilization();
        double utilizationScore = utilization < 60 ? utilization / 60 : 
                                 utilization > 80 ? 1 - (utilization - 80) / 20 : 1.0;
        
        // use logarithmic scale for better distribution
        double energyScore = Math.max(0, Math.min(1, 1 - (Math.log10(summary.getEnergyConsumption() + 1) / 4)));
        
        //  typical range 1-100 seconds
        double responseScore = Math.max(0, Math.min(1, 1 - (Math.log10(summary.getResponseTime() + 1) / 2)));
        
        //  lower is better, 0 is perfect
        double balanceScore = Math.max(0, 1 - Math.min(1, summary.getLoadBalance()));
        
        // Equal weighting for transparency (can be adjusted based on user preference)
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
        if (score >= 0.93) return "A+";  // Top 7%
        if (score >= 0.87) return "A";   // 87-93%
        if (score >= 0.80) return "A-";  // 80-87%
        if (score >= 0.73) return "B+";  // 73-80%
        if (score >= 0.67) return "B";   // 67-73%
        if (score >= 0.60) return "B-";  // 60-67%
        if (score >= 0.53) return "C+";  // 53-60%
        if (score >= 0.47) return "C";   // 47-53%
        if (score >= 0.40) return "C-";  // 40-47%
        if (score >= 0.33) return "D+";  // 33-40%
        if (score >= 0.27) return "D";   // 27-33%
        if (score >= 0.20) return "D-";  // 20-27%
        return "F";                       // Below 20%
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
            case "loadBalance": return "load balance";
            default: return metricName;
        }
    }
}
