package com.thesis.cloudsim.metrics;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class SimulationResults {
    private double averageResponseTime;
    private double makespan;
    private double resourceUtilization;
    private double energyConsumption;
    private double loadImbalance;
} 