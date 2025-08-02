package com.thesis.cloudsim.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

// DTO = simple data carrier; Lombok generates getters/setters for brevity
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SimulationRequest {
    // data center config
    private String optimizationAlgorithm;  // epso/eaco
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
    
    // workload config
    private int numCloudlets;
    private String workloadType;           // csv/rand
    private boolean useDefaultWorkload;
    private String workloadPath;           // path if csv
    
    // Algorithm-specific parameters (optional)
    private double inertiaWeightStart;     // pso param
    private double alpha;                  // aco param
    
    // multi-objective opti weights
    private double makespanWeight = 0.25;
    private double costWeight = 0.25;
    private double energyWeight = 0.25;
    private double loadBalanceWeight = 0.25;
    
    // iteration 
    private int iterations = 1;  // n times default 
}
