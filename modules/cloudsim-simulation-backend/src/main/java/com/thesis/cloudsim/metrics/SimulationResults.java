package com.thesis.cloudsim.metrics;

import lombok.Builder;
import lombok.Data;
import java.util.List;
import java.util.Map;

@Data
@Builder
public class SimulationResults {
    // Run metadata for reproducibility
    private String runId;
    private Long seed;
    private Map<String, Object> configSnapshot;
    private String datasetId;
    
    // Existing fields
    private Summary summary;
    private List<VmUtilization> vmUtilization;
    private List<SchedulingLogEntry> schedulingLog;
    private double energyConsumption;
    
    @Data
    @Builder
    public static class Summary {
        private double makespan;
        private double energyConsumption;
        private double loadBalance; 
        private double loadImbalance; 
        private double resourceUtilization;
        private double responseTime;
        private double fitness;
        private double totalCost;
        private double costEfficiency;
    }
    
    @Data
    @Builder
    public static class VmUtilization {
        private int vmId;
        private double cpuUtilization;
        private double ramUtilization;
        private int numAPECloudlets;
    }
    
    @Data
    @Builder
    public static class SchedulingLogEntry {
        private String type;
        private String description;
        private Double vmId;
        private Double cloudletId;
        private Double submissionTime;
        private Object data; // For configuration entries
    }
}
