package com.thesis.cloudsim.algorithm;

import org.cloudbus.cloudsim.cloudlets.Cloudlet;
import org.cloudbus.cloudsim.vms.Vm;
import java.util.List;
import java.util.Map;

/**
 * Interface for scheduling algorithms in CloudSim 7.0 G
 * Provides contract for task scheduling optimization algorithms
 */
public interface ISchedulingAlgorithm {
    
    /**
     * Schedule cloudlets to VMs using the algorithm's optimization strategy
     * @param cloudlets List of cloudlets to be scheduled
     * @param vms List of available VMs
     * @param parameters Algorithm-specific parameters
     * @return Map of cloudlet to VM assignments
     */
    Map<Cloudlet, Vm> schedule(List<Cloudlet> cloudlets, List<Vm> vms, AlgorithmParameters parameters);
    
    /**
     * Get the name of the algorithm
     * @return Algorithm name
     */
    String getAlgorithmName();
    
    /**
     * Get algorithm-specific metrics after scheduling
     * @return Map of metric names to values
     */
    Map<String, Double> getMetrics();
    
    /**
     * Reset algorithm state for new scheduling session
     */
    void reset();
}
