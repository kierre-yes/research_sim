package com.thesis.cloudsim.broker;

import com.thesis.cloudsim.algorithm.AlgorithmFactory;
import com.thesis.cloudsim.algorithm.AlgorithmParameters;
import com.thesis.cloudsim.algorithm.ISchedulingAlgorithm;
import org.cloudbus.cloudsim.cloudlets.Cloudlet;
import org.cloudbus.cloudsim.core.Simulation;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.vms.Vm;
import org.cloudbus.cloudsim.brokers.DatacenterBrokerSimple;

import java.util.List;
import java.util.Map;

/**
 * Custom broker that integrates enhanced scheduling algorithms (e.g., EPSO, EACO)
 * for assigning cloudlets to VMs in CloudSim 7.0.0-alpha.
 */
public class CustomSchedulingBroker extends DatacenterBrokerSimple {

    private final String algorithmName;
    private AlgorithmParameters parameters;

    public CustomSchedulingBroker(CloudSim simulation, String algorithmName, AlgorithmParameters parameters) {
        super(simulation);
        this.algorithmName = algorithmName;
        this.parameters = parameters;
    }

    // Custom method for cloudlet mapping (no invalid override)
    protected Map<Cloudlet, Vm> customCloudletMapper(List<Cloudlet> cloudletList, List<Vm> vmList) {
        // Create algorithm instance
        ISchedulingAlgorithm algorithm = AlgorithmFactory.createAlgorithm(algorithmName);
        
        // Run scheduling
        Map<Cloudlet, Vm> schedule = algorithm.schedule(cloudletList, vmList, parameters);
        
        // Display metrics for logging
        System.out.println("Metrics for " + algorithm.getAlgorithmName() + ":");
        algorithm.getMetrics().forEach((key, value) -> System.out.printf("%s: %.4f%n", key, value));
        
        return schedule;
    }

    // Method to update parameters dynamically
    public void updateParameters(AlgorithmParameters newParams) {
        this.parameters = newParams;
    }
}
