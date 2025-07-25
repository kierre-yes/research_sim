package com.thesis.cloudsim.broker;

import com.thesis.cloudsim.algorithm.AlgorithmFactory;
import com.thesis.cloudsim.algorithm.AlgorithmParameters;
import com.thesis.cloudsim.algorithm.ISchedulingAlgorithm;
import org.cloudbus.cloudsim.Cloudlet;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.core.CloudActionTags;
import org.cloudbus.cloudsim.Vm;
import org.cloudbus.cloudsim.DatacenterBroker;
import org.cloudbus.cloudsim.lists.VmList;
import org.cloudbus.cloudsim.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

// Custom broker for scheduling algorithms
public class CustomSchedulingBroker extends DatacenterBroker {

    private String algorithmName;
    private AlgorithmParameters parameters;
    private ISchedulingAlgorithm lastUsedAlgorithm;

    public CustomSchedulingBroker(String name, String algorithmName, AlgorithmParameters parameters) throws Exception {
        super(name);
        this.algorithmName = algorithmName;
        this.parameters = parameters;
    }

    // Allows switching algorithm at runtime
    public void setSchedulingAlgorithm(ISchedulingAlgorithm algorithm) {
        if (algorithm == null) {
            throw new IllegalArgumentException("algorithm must not be null");
        }
        this.algorithmName = algorithm.getAlgorithmName();
    }

    // Maps cloudlets to VMs using the selected algorithm
    private Map<Cloudlet, Vm> customCloudletMapper(List<Cloudlet> cloudletList, List<Vm> vmList) {
        // Create algorithm instance
        ISchedulingAlgorithm algorithm = AlgorithmFactory.createAlgorithm(algorithmName);
        this.lastUsedAlgorithm = algorithm;

        // Execute algorithm
        Map<Cloudlet, Vm> schedule = algorithm.schedule(cloudletList, vmList, parameters);

        // Print metrics
        System.out.println("Metrics for " + algorithm.getAlgorithmName() + ":");
        for (java.util.Map.Entry<String, Double> entry : algorithm.getMetrics().entrySet()) {
            System.out.printf("%s: %.4f%n", entry.getKey(), entry.getValue());
        }
        return schedule;
    }

    // Submit cloudlets to VMs
    @Override
    protected void submitCloudlets() {
        List<Vm> vmList = getGuestsCreatedList();
        List<Cloudlet> cloudletList = new ArrayList<>(getCloudletList());
        
        System.out.println("[DEBUG] CustomSchedulingBroker.submitCloudlets() - Broker ID: " + getId());
        System.out.println("[DEBUG] VMs available: " + vmList.size());
        System.out.println("[DEBUG] Cloudlets to submit: " + cloudletList.size());
        
        if (!vmList.isEmpty() && !cloudletList.isEmpty()) {
            // Apply scheduling algorithm
            Map<Cloudlet, Vm> schedule = customCloudletMapper(cloudletList, vmList);
            
            // Submit cloudlets
            List<Cloudlet> successfullySubmitted = new ArrayList<>();
            
            for (Map.Entry<Cloudlet, Vm> entry : schedule.entrySet()) {
                Cloudlet cloudlet = entry.getKey();
                Vm vm = entry.getValue();
                
                if (vm != null) {
                    // Assign VM
                    cloudlet.setGuestId(vm.getId());
                    
                    // Debug output
                    System.out.println("[DEBUG] VM " + vm.getId() + " - User ID: " + vm.getUserId() + 
                                     " - Host: " + (vm.getHost() != null ? vm.getHost().getId() : "null"));
                    
                    if (!Log.isDisabled()) {
                        Log.printlnConcat(CloudSim.clock(), ": ", getName(), ": Sending ", 
                                cloudlet.getClass().getSimpleName(), " #", cloudlet.getCloudletId(), 
                                " to " + vm.getClassName() + " #", vm.getId());
                    }
                    
                    // Send to datacenter
                    Integer datacenterId = getVmsToDatacentersMap().get(vm.getId());
                    if (datacenterId == null) {
                        System.err.println("[ERROR] No datacenter mapping found for VM " + vm.getId());
                        continue;
                    }
                    System.out.println("[DEBUG] Sending cloudlet " + cloudlet.getCloudletId() + 
                                     " to datacenter " + datacenterId + " for VM " + vm.getId());
                    sendNow(datacenterId, CloudActionTags.CLOUDLET_SUBMIT, cloudlet);
                    
                    cloudletsSubmitted++;
                    getCloudletSubmittedList().add(cloudlet);
                    successfullySubmitted.add(cloudlet);
                }
            }
            
            // Remove submitted cloudlets
            getCloudletList().removeAll(successfullySubmitted);
        }
    }

    // Update algorithm parameters
    public void updateParameters(AlgorithmParameters newParams) {
        this.parameters = newParams;
    }
    
    // CloudSim 7.0 compatibility methods
    public void setVmDestructionDelay(double delay) {
        // Not supported in CloudSim 7.0
    }
    
    public List<Cloudlet> getCloudletFinishedList() {
        return getCloudletReceivedList();
    }
    
    // Get the algorithm instance for metrics
    public ISchedulingAlgorithm getLastUsedAlgorithm() {
        return lastUsedAlgorithm;
    }
}
