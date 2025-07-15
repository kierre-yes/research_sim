package com.thesis.cloudsim.metrics;

import lombok.Builder;
import lombok.Data;
import java.util.List;

@Data
@Builder
public class SimulationResults {
    private Summary summary;
    private List<VmUtilization> vmUtilization;
    private List<SchedulingLogEntry> schedulingLog;
    private double energyConsumption;
    
    @Data
    @Builder
    public static class Summary {
        private double averageResponseTime;
        private double makespan;
        private int totalCloudlets;
        private int finishedCloudlets;
        private double imbalanceDegree;
        private double resourceUtilization;
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
