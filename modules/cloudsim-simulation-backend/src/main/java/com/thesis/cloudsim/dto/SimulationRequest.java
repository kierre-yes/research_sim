package com.thesis.cloudsim.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

// DTO = simple data carrier; Lombok generates getters/setters for brevity
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SimulationRequest {
    // Data Center Configuration
    private String optimizationAlgorithm;  // "EPSO" or "EACO"
    private int numHosts;
    private int numVMs;
    private int numPesPerHost;
    private int peMips;
    private int ramPerHost;
    private int bwPerHost;
    private int storagePerHost;
    private int vmMips;
    private int vmPes;
    private int vmRam;
    private int vmBw;
    private int vmSize;
    private String vmScheduler;
    
    // Workload Configuration
    private int numCloudlets;
    private String workloadType;           // "CSV" or "Random"
    private boolean useDefaultWorkload;
    private String workloadPath;           // Path to the CSV workload file
    
    // Algorithm-specific parameters (optional)
    private double inertiaWeightStart;     // PSO parameter
    private double alpha;                  // ACO parameter
    
    // Multi-objective optimization weights
    private double makespanWeight = 0.25;
    private double costWeight = 0.25;
    private double energyWeight = 0.25;
    private double loadBalanceWeight = 0.25;
}
