package com.thesis.cloudsim.service;

import com.thesis.cloudsim.dto.PlotMetadata;
import com.thesis.cloudsim.dto.PlotMetadata.PlotInterpretation;
import com.thesis.cloudsim.dto.PlotMetadata.PerformanceGrade;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import java.util.*;

/**
 * I generate plot-specific interpretations based on plot type and data
 * This ensures each visualization has contextually relevant explanations
 * that help users understand what they're seeing in the MATLAB plots
 */
@Service
public class PlotInterpretationService {
    
    private static final Logger logger = LoggerFactory.getLogger(PlotInterpretationService.class);
    
    /**
     * Generate interpretation for a specific plot based on its type and data
     */
    public PlotInterpretation interpretPlot(PlotMetadata.PlotType plotType, 
                                           Map<String, Object> dataPoints,
                                           String algorithmName) {
        
        logger.debug("Generating interpretation for {} plot with algorithm {}", plotType.getCode(), algorithmName);
        
        return switch (plotType) {
            case PERFORMANCE_METRICS -> interpretPerformanceMetrics(dataPoints, algorithmName);
            case DETAILED_ANALYSIS -> interpretDetailedAnalysis(dataPoints, algorithmName);
            case VM_UTILIZATION -> interpretVMUtilization(dataPoints, algorithmName);
            case ENERGY_ANALYSIS -> interpretEnergyAnalysis(dataPoints, algorithmName);
            case RADAR_CHART -> interpretRadarChart(dataPoints, algorithmName);
            case TIME_METRICS -> interpretTimeMetrics(dataPoints, algorithmName);
            case EFFICIENCY_METRICS -> interpretEfficiencyMetrics(dataPoints, algorithmName);
            case LOAD_DISTRIBUTION -> interpretLoadDistribution(dataPoints, algorithmName);
        };
    }
    
    /**
     * Interpret the main performance metrics bar chart (Plot 1)
     * This chart shows 5 key metrics: Makespan, Response Time, Resource Util., Energy, Load Balance
     */
    private PlotInterpretation interpretPerformanceMetrics(Map<String, Object> data, String algorithm) {
        double makespan = getDoubleValue(data, "makespan");
        double responseTime = getDoubleValue(data, "responseTime");
        double utilization = getDoubleValue(data, "resourceUtilization");
        double energy = getDoubleValue(data, "energyConsumption");
        double loadBalance = getDoubleValue(data, "loadBalance");
        
        // Determine which metric is dominant
        Map<String, Double> metrics = Map.of(
            "Makespan", makespan,
            "Response Time", responseTime,
            "Resource Utilization", utilization,
            "Energy Consumption", energy / 1000, // Convert to comparable scale
            "Load Balance", loadBalance
        );
        
        String dominantMetric = findDominantMetric(metrics);
        String weakestMetric = findWeakestMetric(metrics);
        
        // Generate key findings based on metric relationships
        List<String> findings = new ArrayList<>();
        
        // Analyze makespan and response time relationship
        if (makespan > responseTime * 2) {
            findings.add("High makespan relative to response time suggests parallel execution opportunities.");
        } else if (Math.abs(makespan - responseTime) < 1) {
            findings.add("Similar makespan and response time indicates sequential task execution.");
        }
        
        // Analyze resource utilization
        if (utilization > 80) {
            findings.add("Excellent resource utilization shows efficient VM usage.");
        } else if (utilization < 50) {
            findings.add("Low resource utilization indicates over-provisioning or inefficient scheduling.");
        }
        
        // Analyze energy efficiency
        double energyPerSecond = energy / makespan;
        if (energyPerSecond < 0.01) {
            findings.add("Energy-efficient execution with low power consumption per second.");
        } else if (energyPerSecond > 0.05) {
            findings.add("High energy consumption suggests optimization opportunities.");
        }
        
        // Create metric explanations
        Map<String, String> explanations = new HashMap<>();
        explanations.put("dominantMetric", String.format("%s (%.2f) is the highest value, indicating primary performance characteristic.", 
            dominantMetric, metrics.get(dominantMetric)));
        explanations.put("weakestMetric", String.format("%s needs attention for optimization.", weakestMetric));
        explanations.put("barHeightPattern", analyzeBarHeightPattern(metrics));
        
        // Generate recommendations
        String recommendations = generatePerformanceRecommendations(makespan, utilization, energy, loadBalance);
        
        // Determine performance grade
        PerformanceGrade grade = calculatePerformanceGrade(makespan, utilization, energy, loadBalance);
        
        return PlotInterpretation.builder()
            .summary(String.format(
                "The performance metrics bar chart for %s algorithm shows %s overall performance. " +
                "The chart displays 5 key metrics with %s as the dominant factor.",
                algorithm, grade.getLabel().toLowerCase(), dominantMetric
            ))
            .keyFindings(String.join(" ", findings))
            .recommendations(recommendations)
            .metricExplanations(explanations)
            .visualPattern(String.format("Bar heights show %s distribution across metrics.", 
                detectDistributionPattern(metrics)))
            .performanceGrade(grade)
            .build();
    }
    
    /**
     * Interpret the detailed analysis plot with 4 subplots (Plot 2)
     */
    private PlotInterpretation interpretDetailedAnalysis(Map<String, Object> data, String algorithm) {
        return PlotInterpretation.builder()
            .summary(String.format(
                "The detailed analysis for %s provides four perspectives: Time Metrics, Efficiency Metrics, " +
                "Energy Analysis, and Load Distribution. Each subplot reveals different performance aspects.",
                algorithm
            ))
            .keyFindings("This composite view enables multi-dimensional performance assessment. " +
                "Time metrics show execution characteristics, efficiency metrics reveal resource usage, " +
                "energy analysis tracks power consumption, and load distribution indicates balance.")
            .recommendations("Examine each subplot for specific optimization opportunities. " +
                "Cross-reference patterns between subplots to identify systemic issues.")
            .visualPattern("Four-quadrant layout provides comprehensive performance overview.")
            .build();
    }
    
    /**
     * Interpret VM utilization bar chart (Plot 3)
     */
    private PlotInterpretation interpretVMUtilization(Map<String, Object> data, String algorithm) {
        double avgCpuUtil = getDoubleValue(data, "avgCpuUtilization");
        double avgRamUtil = getDoubleValue(data, "avgRamUtilization");
        
        List<String> findings = new ArrayList<>();
        
        // Analyze CPU vs RAM balance
        double utilizationRatio = avgCpuUtil / (avgRamUtil + 0.01); // Avoid division by zero
        if (utilizationRatio > 1.5) {
            findings.add("CPU-bound workload detected with higher CPU than RAM utilization.");
        } else if (utilizationRatio < 0.67) {
            findings.add("Memory-intensive workload with RAM utilization exceeding CPU.");
        } else {
            findings.add("Balanced CPU and RAM utilization indicates well-matched resource allocation.");
        }
        
        // Check for underutilization
        if (avgCpuUtil < 30 && avgRamUtil < 30) {
            findings.add("Significant underutilization suggests too many VMs for the workload.");
        }
        
        // Check for hotspots
        double cpuVariance = getDoubleValue(data, "cpuVariance", 0);
        if (cpuVariance > 20) {
            findings.add("High variance in CPU utilization indicates uneven load distribution.");
        }
        
        String recommendations;
        if (avgCpuUtil < 50) {
            recommendations = "Consider reducing the number of VMs to improve resource efficiency.";
        } else if (avgCpuUtil > 85) {
            recommendations = "VMs are near capacity. Consider adding more VMs or upgrading VM specifications.";
        } else {
            recommendations = "Current VM configuration is well-suited for the workload.";
        }
        
        return PlotInterpretation.builder()
            .summary(String.format(
                "VM utilization chart for %s shows average CPU utilization of %.1f%% and RAM utilization of %.1f%%. " +
                "The grouped bars compare resource usage across all VMs.",
                algorithm, avgCpuUtil, avgRamUtil
            ))
            .keyFindings(String.join(" ", findings))
            .recommendations(recommendations)
            .metricExplanations(Map.of(
                "cpuBars", "Blue bars represent CPU utilization percentage for each VM",
                "ramBars", "Orange bars show RAM utilization percentage",
                "pattern", String.format("Utilization ratio of %.2f indicates %s workload",
                    utilizationRatio, utilizationRatio > 1.5 ? "compute-intensive" : "balanced")
            ))
            .visualPattern("Grouped bar chart enables side-by-side resource comparison.")
            .build();
    }
    
    /**
     * Interpret energy analysis plots (Plot 4)
     */
    private PlotInterpretation interpretEnergyAnalysis(Map<String, Object> data, String algorithm) {
        double totalEnergy = getDoubleValue(data, "energyConsumption");
        double makespan = getDoubleValue(data, "makespan");
        double throughput = getDoubleValue(data, "throughput");
        
        double energyPerSecond = totalEnergy / makespan;
        double tasksCompleted = throughput * makespan;
        double energyPerTask = totalEnergy / tasksCompleted;
        
        List<String> findings = new ArrayList<>();
        
        // Categorize energy efficiency
        if (energyPerTask < 0.001) {
            findings.add("Excellent energy efficiency with minimal consumption per task.");
        } else if (energyPerTask < 0.01) {
            findings.add("Good energy efficiency within acceptable ranges.");
        } else {
            findings.add("High energy consumption per task suggests optimization potential.");
        }
        
        // Analyze energy patterns
        if (energyPerSecond > 0.1) {
            findings.add("High power draw during execution may impact operational costs.");
        }
        
        // Compare with throughput
        double efficiencyScore = throughput / (totalEnergy * 100);
        findings.add(String.format("Energy efficiency score of %.2f tasks per Wh.", efficiencyScore));
        
        String recommendations;
        if (totalEnergy > 0.5) {
            recommendations = "Consider energy-aware scheduling algorithms or VM consolidation to reduce consumption.";
        } else if (energyPerTask > 0.01) {
            recommendations = "Optimize task allocation to more energy-efficient VMs.";
        } else {
            recommendations = "Energy consumption is within optimal range for this workload.";
        }
        
        return PlotInterpretation.builder()
            .summary(String.format(
                "Energy analysis for %s shows total consumption of %.3f Wh with %.4f mWh per task. " +
                "The dual bar charts display absolute consumption and efficiency metrics.",
                algorithm, totalEnergy, energyPerTask * 1000
            ))
            .keyFindings(String.join(" ", findings))
            .recommendations(recommendations)
            .metricExplanations(Map.of(
                "leftChart", "Total energy and per-second consumption in mWh",
                "rightChart", "Energy efficiency measured as tasks/Wh and mWh/task",
                "efficiency", String.format("%.2f tasks completed per Wh of energy", efficiencyScore)
            ))
            .visualPattern("Side-by-side bar charts contrast consumption vs efficiency.")
            .build();
    }
    
    /**
     * Interpret radar chart (Plot 5)
     */
    private PlotInterpretation interpretRadarChart(Map<String, Object> data, String algorithm) {
        // Radar chart has 5 dimensions, all normalized to 0-1
        double makespanScore = getDoubleValue(data, "makespanNormalized", 0.5);
        double responseScore = getDoubleValue(data, "responseTimeNormalized", 0.5);
        double utilizationScore = getDoubleValue(data, "utilizationNormalized", 0.5);
        double energyScore = getDoubleValue(data, "energyEfficiencyNormalized", 0.5);
        double balanceScore = getDoubleValue(data, "loadBalanceNormalized", 0.5);
        
        // Calculate overall area (pentagon area)
        double overallScore = (makespanScore + responseScore + utilizationScore + energyScore + balanceScore) / 5;
        
        List<String> findings = new ArrayList<>();
        
        // Identify strongest and weakest dimensions
        Map<String, Double> dimensions = Map.of(
            "Makespan", makespanScore,
            "Response Time", responseScore,
            "Resource Utilization", utilizationScore,
            "Energy Efficiency", energyScore,
            "Load Balance", balanceScore
        );
        
        String strongest = dimensions.entrySet().stream()
            .max(Map.Entry.comparingByValue())
            .map(Map.Entry::getKey)
            .orElse("Unknown");
            
        String weakest = dimensions.entrySet().stream()
            .min(Map.Entry.comparingByValue())
            .map(Map.Entry::getKey)
            .orElse("Unknown");
        
        findings.add(String.format("Strongest performance in %s (%.0f%%).", strongest, dimensions.get(strongest) * 100));
        findings.add(String.format("Weakest performance in %s (%.0f%%).", weakest, dimensions.get(weakest) * 100));
        
        // Analyze shape symmetry
        double variance = calculateVariance(dimensions.values());
        if (variance < 0.05) {
            findings.add("Symmetric radar shape indicates balanced performance across all dimensions.");
        } else if (variance > 0.15) {
            findings.add("Asymmetric radar shape reveals significant performance variations.");
        }
        
        // Overall assessment
        String overallAssessment;
        if (overallScore > 0.8) {
            overallAssessment = "Excellent overall performance with large radar coverage.";
        } else if (overallScore > 0.6) {
            overallAssessment = "Good performance with moderate radar coverage.";
        } else if (overallScore > 0.4) {
            overallAssessment = "Average performance with room for improvement.";
        } else {
            overallAssessment = "Performance needs significant optimization.";
        }
        
        return PlotInterpretation.builder()
            .summary(String.format(
                "The radar chart for %s visualizes multi-dimensional performance with %.0f%% overall coverage. " +
                "%s", algorithm, overallScore * 100, overallAssessment
            ))
            .keyFindings(String.join(" ", findings))
            .recommendations(String.format("Focus optimization efforts on %s while maintaining strength in %s.",
                weakest, strongest))
            .metricExplanations(Map.of(
                "coverage", String.format("Radar area coverage of %.0f%% indicates overall performance", overallScore * 100),
                "shape", variance < 0.05 ? "Symmetric shape shows balanced optimization" : "Asymmetric shape reveals optimization priorities",
                "interpretation", "Larger area and more symmetric shape indicate better overall performance"
            ))
            .visualPattern(String.format("Pentagon shape with %.0f%% coverage and %s distribution.",
                overallScore * 100, variance < 0.05 ? "balanced" : "uneven"))
            .performanceGrade(calculateGradeFromScore(overallScore))
            .build();
    }
    
    /**
     * Interpret time metrics subplot
     */
    private PlotInterpretation interpretTimeMetrics(Map<String, Object> data, String algorithm) {
        double responseTime = getDoubleValue(data, "responseTime");
        double makespan = getDoubleValue(data, "makespan");
        
        String relationship;
        double ratio = makespan / (responseTime + 0.01);
        if (ratio > 2) {
            relationship = "Significant gap between response time and makespan indicates high parallelism.";
        } else if (ratio > 1.5) {
            relationship = "Moderate parallelism with some concurrent task execution.";
        } else {
            relationship = "Near-sequential execution with minimal parallelism.";
        }
        
        return PlotInterpretation.builder()
            .summary(String.format(
                "Time metrics subplot shows response time of %.2fs and makespan of %.2fs. %s",
                responseTime, makespan, relationship
            ))
            .keyFindings(relationship)
            .recommendations(ratio < 1.5 ? "Consider enabling parallel task execution to reduce makespan." : 
                "Good parallelization achieved.")
            .build();
    }
    
    /**
     * Interpret efficiency metrics subplot
     */
    private PlotInterpretation interpretEfficiencyMetrics(Map<String, Object> data, String algorithm) {
        double utilization = getDoubleValue(data, "resourceUtilization");
        double throughput = getDoubleValue(data, "throughput");
        
        String efficiency;
        if (utilization > 70 && throughput > 50) {
            efficiency = "High efficiency with good resource utilization and throughput.";
        } else if (utilization < 50) {
            efficiency = "Low resource utilization indicates inefficient resource allocation.";
        } else if (throughput < 20) {
            efficiency = "Low throughput suggests processing bottlenecks.";
        } else {
            efficiency = "Moderate efficiency with optimization potential.";
        }
        
        return PlotInterpretation.builder()
            .summary(String.format(
                "Efficiency metrics show %.1f%% resource utilization and %.2f tasks/second throughput.",
                utilization, throughput
            ))
            .keyFindings(efficiency)
            .recommendations(utilization < 60 ? "Improve task distribution to increase resource utilization." :
                "Maintain current efficiency levels.")
            .build();
    }
    
    /**
     * Interpret load distribution pie chart
     */
    private PlotInterpretation interpretLoadDistribution(Map<String, Object> data, String algorithm) {
        double balanced = getDoubleValue(data, "balancedPercentage", 50);
        double imbalanced = 100 - balanced;
        
        String assessment;
        String recommendation;
        
        if (balanced > 85) {
            assessment = "Excellent load distribution with minimal imbalance.";
            recommendation = "Current load balancing strategy is highly effective.";
        } else if (balanced > 70) {
            assessment = "Good load distribution with acceptable imbalance levels.";
            recommendation = "Minor tuning could further improve distribution.";
        } else if (balanced > 50) {
            assessment = "Moderate load distribution with noticeable imbalance.";
            recommendation = "Consider implementing dynamic load balancing.";
        } else {
            assessment = "Poor load distribution with significant imbalance.";
            recommendation = "Urgent need for load balancing optimization.";
        }
        
        return PlotInterpretation.builder()
            .summary(String.format(
                "Load distribution pie chart shows %.1f%% balanced and %.1f%% imbalanced workload. %s",
                balanced, imbalanced, assessment
            ))
            .keyFindings(String.format(
                "The %s-colored segment (%.1f%%) represents balanced load, while the %s segment (%.1f%%) shows imbalance.",
                balanced > 70 ? "green" : "orange", balanced,
                balanced > 70 ? "small gray" : "large gray", imbalanced
            ))
            .recommendations(recommendation)
            .visualPattern(String.format("Pie chart with %.0f%% / %.0f%% split visually represents load balance quality.",
                balanced, imbalanced))
            .performanceGrade(balanced > 85 ? PerformanceGrade.EXCELLENT :
                balanced > 70 ? PerformanceGrade.GOOD :
                balanced > 50 ? PerformanceGrade.SATISFACTORY :
                PerformanceGrade.NEEDS_IMPROVEMENT)
            .build();
    }
    
    // Helper methods
    
    private double getDoubleValue(Map<String, Object> data, String key) {
        return getDoubleValue(data, key, 0.0);
    }
    
    private double getDoubleValue(Map<String, Object> data, String key, double defaultValue) {
        if (data == null || !data.containsKey(key)) {
            return defaultValue;
        }
        Object value = data.get(key);
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        return defaultValue;
    }
    
    private String findDominantMetric(Map<String, Double> metrics) {
        return metrics.entrySet().stream()
            .max(Map.Entry.comparingByValue())
            .map(Map.Entry::getKey)
            .orElse("Unknown");
    }
    
    private String findWeakestMetric(Map<String, Double> metrics) {
        // For some metrics, lower is better
        Map<String, Double> normalizedMetrics = new HashMap<>();
        for (Map.Entry<String, Double> entry : metrics.entrySet()) {
            String key = entry.getKey();
            double value = entry.getValue();
            if (key.equals("Resource Utilization") || key.equals("Load Balance")) {
                // Higher is better for these
                normalizedMetrics.put(key, value);
            } else {
                // Lower is better for makespan, response time, energy
                normalizedMetrics.put(key, 100 - value);
            }
        }
        return normalizedMetrics.entrySet().stream()
            .min(Map.Entry.comparingByValue())
            .map(Map.Entry::getKey)
            .orElse("Unknown");
    }
    
    private String analyzeBarHeightPattern(Map<String, Double> metrics) {
        List<Double> values = new ArrayList<>(metrics.values());
        Collections.sort(values);
        
        double range = values.get(values.size() - 1) - values.get(0);
        double avg = values.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        
        if (range < avg * 0.2) {
            return "Uniform bar heights indicate consistent performance across metrics";
        } else if (range > avg * 1.5) {
            return "Highly varied bar heights show significant performance differences";
        } else {
            return "Moderate variation in bar heights reflects typical performance distribution";
        }
    }
    
    private String detectDistributionPattern(Map<String, Double> metrics) {
        double variance = calculateVariance(metrics.values());
        if (variance < 10) {
            return "uniform";
        } else if (variance < 30) {
            return "moderate";
        } else {
            return "highly varied";
        }
    }
    
    private double calculateVariance(Collection<Double> values) {
        if (values.isEmpty()) return 0;
        double mean = values.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        return values.stream()
            .mapToDouble(v -> Math.pow(v - mean, 2))
            .average()
            .orElse(0);
    }
    
    private String generatePerformanceRecommendations(double makespan, double utilization, 
                                                     double energy, double loadBalance) {
        List<String> recommendations = new ArrayList<>();
        
        if (makespan > 30) {
            recommendations.add("Reduce makespan through parallel execution or faster VMs.");
        }
        if (utilization < 60) {
            recommendations.add("Improve resource utilization by consolidating VMs.");
        }
        if (energy > 0.5) {
            recommendations.add("Implement energy-aware scheduling to reduce consumption.");
        }
        if (loadBalance < 70) {
            recommendations.add("Enhance load balancing algorithm for better distribution.");
        }
        
        if (recommendations.isEmpty()) {
            return "Performance is within optimal ranges. Continue monitoring for changes.";
        }
        
        return String.join(" ", recommendations);
    }
    
    private PerformanceGrade calculatePerformanceGrade(double makespan, double utilization, 
                                                       double energy, double loadBalance) {
        double score = 0;
        
        // Score based on makespan (lower is better)
        if (makespan < 10) score += 25;
        else if (makespan < 20) score += 20;
        else if (makespan < 30) score += 15;
        else if (makespan < 50) score += 10;
        else score += 5;
        
        // Score based on utilization (higher is better)
        if (utilization > 80) score += 25;
        else if (utilization > 70) score += 20;
        else if (utilization > 60) score += 15;
        else if (utilization > 50) score += 10;
        else score += 5;
        
        // Score based on energy (lower is better)
        if (energy < 0.1) score += 25;
        else if (energy < 0.3) score += 20;
        else if (energy < 0.5) score += 15;
        else if (energy < 1.0) score += 10;
        else score += 5;
        
        // Score based on load balance (higher is better)
        if (loadBalance > 90) score += 25;
        else if (loadBalance > 80) score += 20;
        else if (loadBalance > 70) score += 15;
        else if (loadBalance > 60) score += 10;
        else score += 5;
        
        return calculateGradeFromScore(score / 100.0);
    }
    
    private PerformanceGrade calculateGradeFromScore(double score) {
        if (score >= 0.9) return PerformanceGrade.EXCELLENT;
        if (score >= 0.8) return PerformanceGrade.VERY_GOOD;
        if (score >= 0.7) return PerformanceGrade.GOOD;
        if (score >= 0.6) return PerformanceGrade.SATISFACTORY;
        if (score >= 0.5) return PerformanceGrade.NEEDS_IMPROVEMENT;
        return PerformanceGrade.POOR;
    }
}
