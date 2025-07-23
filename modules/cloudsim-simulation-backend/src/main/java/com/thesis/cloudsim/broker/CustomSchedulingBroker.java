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

/**
 * Custom broker that integrates enhanced scheduling algorithms (e.g., EPSO, EACO)
 * for assigning cloudlets to VMs in CloudSim 7.0.0-alpha.
 */
public class CustomSchedulingBroker extends DatacenterBroker {

    private String algorithmName;
    private AlgorithmParameters parameters;
    private ISchedulingAlgorithm lastUsedAlgorithm;

    public CustomSchedulingBroker(String name, String algorithmName, AlgorithmParameters parameters) throws Exception {
        super(name);
        this.algorithmName = algorithmName;
        this.parameters = parameters;
    }

    /**
     * Setter included to align with the class diagram. Allows switching the scheduling
     * algorithm at runtime without recreating the broker. Only updates the internal
     * reference to the algorithm name; parameter set should be adjusted separately via
     * {@link #updateParameters(AlgorithmParameters)}.
     */
    public void setSchedulingAlgorithm(ISchedulingAlgorithm algorithm) {
        if (algorithm == null) {
            throw new IllegalArgumentException("algorithm must not be null");
        }
        this.algorithmName = algorithm.getAlgorithmName();
    }

    // Custom method that applies the selected optimisation algorithm to map
    // cloudlets → VMs. It is invoked by our override of defaultCloudletToVmMapping().
    private Map<Cloudlet, Vm> customCloudletMapper(List<Cloudlet> cloudletList, List<Vm> vmList) {
        // 1) Create an algorithm instance each time we need a fresh scheduling run
        ISchedulingAlgorithm algorithm = AlgorithmFactory.createAlgorithm(algorithmName);
        this.lastUsedAlgorithm = algorithm; // Store for later access

        // 2) Execute the optimisation algorithm to obtain a mapping
        Map<Cloudlet, Vm> schedule = algorithm.schedule(cloudletList, vmList, parameters);

        // 3) (Optional) print basic metrics for quick debugging
        System.out.println("Metrics for " + algorithm.getAlgorithmName() + ":");
        for (java.util.Map.Entry<String, Double> entry : algorithm.getMetrics().entrySet()) {
            System.out.printf("%s: %.4f%n", entry.getKey(), entry.getValue());
        }
        return schedule;
    }

    /**
     * Override the method that submits cloudlets to VMs.
     * This is called after VMs are created.
     */
    @Override
    protected void submitCloudlets() {
        List<Vm> vmList = getGuestsCreatedList();
        List<Cloudlet> cloudletList = new ArrayList<>(getCloudletList());
        
        System.out.println("[DEBUG] CustomSchedulingBroker.submitCloudlets() - Broker ID: " + getId());
        System.out.println("[DEBUG] VMs available: " + vmList.size());
        System.out.println("[DEBUG] Cloudlets to submit: " + cloudletList.size());
        
        if (!vmList.isEmpty() && !cloudletList.isEmpty()) {
            // Apply custom scheduling algorithm
            Map<Cloudlet, Vm> schedule = customCloudletMapper(cloudletList, vmList);
            
            // Submit cloudlets based on the schedule
            List<Cloudlet> successfullySubmitted = new ArrayList<>();
            
            for (Map.Entry<Cloudlet, Vm> entry : schedule.entrySet()) {
                Cloudlet cloudlet = entry.getKey();
                Vm vm = entry.getValue();
                
                if (vm != null) {
                    // Set the VM assignment
                    cloudlet.setGuestId(vm.getId());
                    
                    // Debug VM host assignment
                    System.out.println("[DEBUG] VM " + vm.getId() + " - User ID: " + vm.getUserId() + 
                                     " - Host: " + (vm.getHost() != null ? vm.getHost().getId() : "null"));
                    
                    if (!Log.isDisabled()) {
                        Log.printlnConcat(CloudSim.clock(), ": ", getName(), ": Sending ", 
                                cloudlet.getClass().getSimpleName(), " #", cloudlet.getCloudletId(), 
                                " to " + vm.getClassName() + " #", vm.getId());
                    }
                    
                    // Send cloudlet to datacenter
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
            
            // Remove submitted cloudlets from waiting list
            getCloudletList().removeAll(successfullySubmitted);
        }
    }

    // Method to update parameters dynamically
    public void updateParameters(AlgorithmParameters newParams) {
        this.parameters = newParams;
    }
    
    // Additional methods to match CloudSim 7.0 API
    public void setVmDestructionDelay(double delay) {
        // CloudSim 7.0 doesn't have this feature, so we'll ignore it
        // VMs are destroyed when the simulation ends
    }
    
    public List<Cloudlet> getCloudletFinishedList() {
        return getCloudletReceivedList();
    }
    
    /**
     * Get the last used algorithm instance for accessing metrics
     */
    public ISchedulingAlgorithm getLastUsedAlgorithm() {
        return lastUsedAlgorithm;
    }
}
