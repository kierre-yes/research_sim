package com.thesis.cloudsim.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SimulationRequest {
    private String algorithm;           // "EPSO" or "EACO"
    private int vms;                    // Number of virtual machines
    private int tasks;                  // Number of tasks (optional, may be unused)
    private int hosts;                  // Number of hosts
    private String workloadPath;        // Path to the CSV workload file

    // Example algorithm-specific parameters (optional)
    private double inertiaWeightStart;  // PSO parameter
    private double alpha;               // ACO parameter
} 