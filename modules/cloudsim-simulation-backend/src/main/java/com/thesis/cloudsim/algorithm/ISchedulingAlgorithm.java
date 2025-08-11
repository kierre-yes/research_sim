package com.thesis.cloudsim.algorithm;

import org.cloudbus.cloudsim.Cloudlet;
import org.cloudbus.cloudsim.Vm;
import java.util.List;
import java.util.Map;

/** 
 * I define this interface so that EPSO and EACO algorithms can be swapped interchangeably
 * without modifying the broker or simulation manager code
 */
public interface ISchedulingAlgorithm {
    
    /**
     * 
     * I return a Map here so that each cloudlet has exactly one VM assignment,
     * preventing duplicate assignments and making the mapping explicit
     */
    Map<Cloudlet, Vm> schedule(List<Cloudlet> cloudlets, List<Vm> vms, AlgorithmParameters parameters);
    
    /**
     * Get the name of the algorithm
     * @return Algorithm name
     */
    String getAlgorithmName();
    
    /**
     * 
     * I use a Map<String, Double> here so that algorithms can report custom metrics
     * without changing the interface (e.g., EACO can report pheromone convergence)
     * 
     */
    Map<String, Double> getMetrics();
    
    /**
     * Reset algorithm state for new scheduling session
     * I include this method so that algorithm instances can be reused across multiple
     * simulations without state pollution from previous runs
     */
    void reset();
}
