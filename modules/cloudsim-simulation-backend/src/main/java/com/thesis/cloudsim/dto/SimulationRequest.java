package com.thesis.cloudsim.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.validation.constraints.*;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

// simple data carrier; Lombok generates getters/setters
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SimulationRequest {

    public Map<String, Object> toBasicMap() {
        Map<String, Object> snapshot = new HashMap<>();
        snapshot.put("algorithm", getOptimizationAlgorithm());
        snapshot.put("numHosts", getNumHosts());
        snapshot.put("numVMs", getNumVMs());
        snapshot.put("numCloudlets", getNumCloudlets());
        snapshot.put("workloadType", getWorkloadType());
        snapshot.put("vmScheduler", getVmScheduler());
        snapshot.put("iterations", getIterations());
        return snapshot;
    }

    public Map<String, Object> toCustomMap(boolean includeHostDetails, boolean includeVmDetails) {
        Map<String, Object> snapshot = toBasicMap();
        
        if (includeHostDetails) {
            snapshot.put("numPesPerHost", getNumPesPerHost());
            snapshot.put("peMips", getPeMips());
            snapshot.put("ramPerHost", getRamPerHost());
            snapshot.put("bwPerHost", getBwPerHost());
            snapshot.put("storagePerHost", getStoragePerHost());
        }
        
        if (includeVmDetails) {
            snapshot.put("vmMips", getVmMips());
            snapshot.put("vmPes", getVmPes());
            snapshot.put("vmRam", getVmRam());
            snapshot.put("vmBw", getVmBw());
            snapshot.put("vmSize", getVmSize());
        }
        
        return snapshot;
    }
    // data center config
    @NotBlank(message = "Optimization algorithm is required")
    @Pattern(
            regexp = "^(?i)(EPSO|EACO|BPSO|BACO|BASELINEPSO|BASELINEACO)$",
            message = "Algorithm must be EPSO, EACO, BPSO, BACO, BASELINEPSO, or BASELINEACO"
    )
    private String optimizationAlgorithm;
    
    @Min(value = 1, message = "Number of hosts must be at least 1")
    @Max(value = 1000, message = "Number of hosts cannot exceed 1000")
    private int numHosts;
    
    @Min(value = 1, message = "Number of VMs must be at least 1")
    @Max(value = 10000, message = "Number of VMs cannot exceed 10000")
    private int numVMs;
    
    @Min(value = 1, message = "PEs per host must be at least 1")
    private int numPesPerHost;
    
    @Min(value = 100, message = "PE MIPS must be at least 100")
    private int peMips;
    
    @Min(value = 512, message = "RAM per host must be at least 512 MB")
    private int ramPerHost;
    
    @Min(value = 1, message = "Bandwidth must be positive")
    private int bwPerHost;
    
    @Min(value = 1, message = "Storage must be positive")
    private int storagePerHost;
    
    @Min(value = 100, message = "VM MIPS must be at least 100")
    private int vmMips;
    
    @Min(value = 1, message = "VM PEs must be at least 1")
    private int vmPes;
    
    @Min(value = 128, message = "VM RAM must be at least 128 MB")
    private int vmRam;
    
    @Min(value = 1, message = "VM bandwidth must be positive")
    private int vmBw;
    
    @Min(value = 1, message = "VM size must be positive")
    private int vmSize;
    
    private String vmScheduler;
    
    // workload config
    @Min(value = 1, message = "Number of cloudlets must be at least 1")
    @Max(value = 100000, message = "Number of cloudlets cannot exceed 100000")
    private int numCloudlets;
    
    @Pattern(regexp = "^(csv|rand|random|CSV|RAND|Random)$", message = "Workload type must be 'csv', 'rand', or 'random'")
    private String workloadType;           // csv/rand/random
    
    private boolean useDefaultWorkload;
    private String workloadPath;           // path if csv

    // reproducibility
    private Long seed;                     // optional deterministic seed
    
    // Algorithm-specific parameters (optional)
    private double inertiaWeightStart;     // pso param
    private double alpha;                  // aco param
    
    // multi-objective opti weights
    @DecimalMin(value = "0.0", message = "Makespan weight must be non-negative")
    @DecimalMax(value = "1.0", message = "Makespan weight cannot exceed 1.0")
    private double makespanWeight = 0.25;
    
    @DecimalMin(value = "0.0", message = "Cost weight must be non-negative")
    @DecimalMax(value = "1.0", message = "Cost weight cannot exceed 1.0")
    private double costWeight = 0.25;
    
    @DecimalMin(value = "0.0", message = "Energy weight must be non-negative")
    @DecimalMax(value = "1.0", message = "Energy weight cannot exceed 1.0")
    private double energyWeight = 0.25;
    
    @DecimalMin(value = "0.0", message = "Load balance weight must be non-negative")
    @DecimalMax(value = "1.0", message = "Load balance weight cannot exceed 1.0")
    private double loadBalanceWeight = 0.25;
    
    // Optional arrival times for staged submission (populated by DatasetUtils if present in CSV)
    @JsonIgnore
    private transient List<Double> arrivalTimes;
    
    // Control whether to use arrival times for staged submission (default: batch mode)
    private boolean useArrivalTimes = false;
    
    // iteration 
    @Min(value = 1, message = "Iterations must be at least 1")
    @Max(value = 1000, message = "Iterations cannot exceed 1000")
    private int iterations = 1;  // n times default
}
