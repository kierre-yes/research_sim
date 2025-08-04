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
import java.util.HashMap;
import org.cloudbus.cloudsim.Host;

/**
 * Custom Scheduling Broker
 * 
 * Implements a scheduling broker that applies custom algorithms for cloudlet-to-VM mapping 
 * using the EPSO and EACO algorithms.
 * 
 * @author [Kier]
 * @version 1.0
 * @since 2025-07-10
 * 
 */
public class CustomSchedulingBroker extends DatacenterBroker {

    private String algorithmName;
    private AlgorithmParameters parameters;
    private ISchedulingAlgorithm lastUsedAlgorithm;
    private Map<Integer, Integer> vmToHostMapping = new HashMap<>();

    public CustomSchedulingBroker(String name, String algorithmName, AlgorithmParameters parameters) throws Exception {
        super(name);
        this.algorithmName = algorithmName;
        this.parameters = parameters;
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
                    
                    // Capture VM-to-Host mapping while the host is still valid
                    if (vm.getHost() != null) {
                        vmToHostMapping.put(vm.getId(), vm.getHost().getId());
                        System.out.println("[DEBUG] Captured VM " + vm.getId() + " -> Host " + vm.getHost().getId() + " mapping");
                    }
                    
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

    @Override
    protected void processCloudletReturn(org.cloudbus.cloudsim.core.SimEvent ev) {
        Cloudlet cloudlet = (Cloudlet) ev.getData();
        if (!Log.isDisabled()) {
            Log.printlnConcat(CloudSim.clock(), ": ", getName(), ": Cloudlet ", cloudlet.getCloudletId(), " received");
        }
        cloudlet.finalizeCloudlet();
        getCloudletReceivedList().add(cloudlet);

        cloudletsSubmitted--;

        if (getCloudletList().isEmpty() && cloudletsSubmitted == 0) {
            if (!Log.isDisabled()) {
                Log.printlnConcat(CloudSim.clock(), ": ", getName(), ": All Cloudlets executed. Finishing...");
            }
            clearDatacenters();
            finishExecution();
        }
    }

    @Override
    public void shutdownEntity() {
        super.shutdownEntity();
    }

    
    public List<Cloudlet> getCloudletFinishedList() {
        return getCloudletReceivedList();
    }
    
    // Get the algorithm instance for metrics
    public ISchedulingAlgorithm getLastUsedAlgorithm() {
        return lastUsedAlgorithm;
    }
    
    // Get the VM-to-Host mapping captured during cloudlet submission
    public Map<Integer, Integer> getVmToHostMapping() {
        return new HashMap<>(vmToHostMapping);  
    }
}
