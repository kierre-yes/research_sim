package com.thesis.cloudsim.broker;

import com.thesis.cloudsim.algorithm.AlgorithmFactory;
import com.thesis.cloudsim.algorithm.AlgorithmParameters;
import com.thesis.cloudsim.algorithm.ISchedulingAlgorithm;
import org.cloudbus.cloudsim.Cloudlet;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.core.CloudActionTags;
import org.cloudbus.cloudsim.core.SimEvent;
import org.cloudbus.cloudsim.Vm;
import org.cloudbus.cloudsim.DatacenterBroker;
import org.cloudbus.cloudsim.lists.VmList;
import org.cloudbus.cloudsim.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import org.cloudbus.cloudsim.Host;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Custom Scheduling Broker
 * 
 * Implements a scheduling broker that applies custom algorithms for cloudlet-to-VM mapping 
 * using the EPSO and EACO algorithms.
 * 
 * I extend DatacenterBroker so that I can intercept the cloudlet submission process
 * and apply our optimization algorithms before tasks are sent to the datacenter
 * 
 * 
 */
public class CustomSchedulingBroker extends DatacenterBroker {

    private static final Logger logger = LoggerFactory.getLogger(CustomSchedulingBroker.class);
    
    private String algorithmName;
    private AlgorithmParameters parameters;
    private ISchedulingAlgorithm lastUsedAlgorithm;  // I keep reference for metric extraction
    
    // I maintain VM-to-Host mapping so that metrics can be calculated correctly
    // CloudSim loses this information after simulation, so I capture it during submission
    private Map<Integer, Integer> vmToHostMapping = new HashMap<>();

    public CustomSchedulingBroker(String name, String algorithmName, AlgorithmParameters parameters) throws Exception {
        super(name);
        this.algorithmName = algorithmName;
        this.parameters = parameters;
    }


    /**
     * Maps cloudlets to VMs using the selected optimization algorithm
     * 
     * I create a new algorithm instance for each scheduling batch so that
     * there's no state pollution between different simulation runs
     */
    private Map<Cloudlet, Vm> customCloudletMapper(List<Cloudlet> cloudletList, List<Vm> vmList) {
        // I use the factory to create the appropriate algorithm instance
        ISchedulingAlgorithm algorithm = AlgorithmFactory.createAlgorithm(algorithmName);
        this.lastUsedAlgorithm = algorithm;

        // I execute the optimization algorithm to get the optimal mapping
        Map<Cloudlet, Vm> schedule = algorithm.schedule(cloudletList, vmList, parameters);

        // I log metrics during debug so that algorithm performance can be monitored
        if (logger.isDebugEnabled()) {
            logger.debug("Metrics for {}:", algorithm.getAlgorithmName());
            for (java.util.Map.Entry<String, Double> entry : algorithm.getMetrics().entrySet()) {
                logger.debug("{}: {}", entry.getKey(), String.format("%.4f", entry.getValue()));
            }
        }
        return schedule;
    }

    /**
     * Submit cloudlets to VMs after applying optimization
     * 
     * I override this method so that cloudlets are scheduled using our algorithms
     * instead of CloudSim's default round-robin or space-shared policies
     */
    @Override
    protected void submitCloudlets() {
        List<Vm> vmList = getGuestsCreatedList();
        // I create a copy so that we don't modify the original cloudlet list
        List<Cloudlet> cloudletList = new ArrayList<>(getCloudletList());
        
        logger.debug("CustomSchedulingBroker.submitCloudlets() - Broker ID: {}", getId());
        logger.debug("VMs available: {}", vmList.size());
        logger.debug("Cloudlets to submit: {}", cloudletList.size());
        
        if (!vmList.isEmpty() && !cloudletList.isEmpty()) {
            // I apply the optimization algorithm to get the optimal schedule
            Map<Cloudlet, Vm> schedule = customCloudletMapper(cloudletList, vmList);
            
            // I process each cloudlet-VM assignment from the optimized schedule
            List<Cloudlet> successfullySubmitted = new ArrayList<>();
            
            for (Map.Entry<Cloudlet, Vm> entry : schedule.entrySet()) {
                Cloudlet cloudlet = entry.getKey();
                Vm vm = entry.getValue();
                
                if (vm != null) {
                    // I assign the VM ID to the cloudlet so CloudSim knows where to run it
                    cloudlet.setGuestId(vm.getId());
                    
                    logger.debug("VM {} - User ID: {} - Host: {}", vm.getId(), vm.getUserId(), 
                                     (vm.getHost() != null ? vm.getHost().getId() : "null"));
                    
                    // I capture VM-to-Host mapping here because CloudSim loses this information
                    // after simulation, but we need it for accurate metric calculation
                    if (vm.getHost() != null) {
                        vmToHostMapping.put(vm.getId(), vm.getHost().getId());
                        logger.debug("Captured VM {} -> Host {} mapping", vm.getId(), vm.getHost().getId());
                    }
                    
                    if (!Log.isDisabled()) {
                        Log.printlnConcat(CloudSim.clock(), ": ", getName(), ": Sending ", 
                                cloudlet.getClass().getSimpleName(), " #", cloudlet.getCloudletId(), 
                                " to " + vm.getClassName() + " #", vm.getId());
                    }
                    
                    // I send the cloudlet to the datacenter that hosts the assigned VM
                    Integer datacenterId = getVmsToDatacentersMap().get(vm.getId());
                    if (datacenterId == null) {
                        logger.error("No datacenter mapping found for VM {}", vm.getId());
                        continue;
                    }
                    logger.debug("Sending cloudlet {} to datacenter {} for VM {}", 
                                cloudlet.getCloudletId(), datacenterId, vm.getId());
                    
                    // I use sendNow so that the cloudlet is immediately submitted to the datacenter
                    sendNow(datacenterId, CloudActionTags.CLOUDLET_SUBMIT, cloudlet);
                    
                    cloudletsSubmitted++;
                    getCloudletSubmittedList().add(cloudlet);
                    successfullySubmitted.add(cloudlet);
                }
            }
            
            // I remove submitted cloudlets from the waiting list so they aren't resubmitted
            getCloudletList().removeAll(successfullySubmitted);
        }
    }

    @Override
    protected void processCloudletReturn(org.cloudbus.cloudsim.core.SimEvent ev) {
        Cloudlet cloudlet = (Cloudlet) ev.getData();
        if (!Log.isDisabled()) {
            Log.printlnConcat(CloudSim.clock(), ": ", getName(), ": Cloudlet ", cloudlet.getCloudletId(), " received");
        }
        
        // I finalize the cloudlet to record its completion time and resource usage
        cloudlet.finalizeCloudlet();
        getCloudletReceivedList().add(cloudlet);

        cloudletsSubmitted--;

        // I check if all cloudlets have completed so that we can terminate the simulation
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
    
    /**
     * Get the algorithm instance for metrics extraction
     * 
     * I expose this so that the simulation manager can retrieve algorithm-specific
     * metrics like convergence status and iteration count
     */
    public ISchedulingAlgorithm getLastUsedAlgorithm() {
        return lastUsedAlgorithm;
    }
    
    /**
     * Get the VM-to-Host mapping captured during cloudlet submission
     * 
     * I return a copy so that the internal mapping can't be modified externally
     * mapping is crucial for calculating energy and cost metrics accurately
     */
    public Map<Integer, Integer> getVmToHostMapping() {
        return new HashMap<>(vmToHostMapping);  
    }
    
    /**
     * Queue a batch of cloudlets for delayed submission
     * Used for implementing arrival-time-based submission
     */
    public void queueBatchForSubmission(List<Cloudlet> batch, double delaySeconds) {
        if (batch == null || batch.isEmpty()) {
            return;
        }
        
        logger.debug("Scheduling {} cloudlets for submission at time {}", 
                    batch.size(), CloudSim.clock() + delaySeconds);
        
        // Store cloudlets to be submitted later
        for (Cloudlet cloudlet : batch) {
            cloudlet.setUserId(getId()); // Ensure broker ID is set
            getCloudletList().add(cloudlet); // Add to broker's list
        }
        
        // Schedule a self-event to submit this batch after the delay
        // We'll schedule to ourselves to trigger submission
        schedule(getId(), delaySeconds, CloudActionTags.CLOUDLET_SUBMIT);
    }
    
    /**
     * Process events including staged batch submissions
     */
    @Override
    public void processEvent(SimEvent ev) {
        // Check if this is a delayed submission event
        if (ev.getTag() == CloudActionTags.CLOUDLET_SUBMIT && 
            ev.getSource() == getId() && 
            CloudSim.clock() > 0) {
            // This is a self-scheduled event for delayed submission
            logger.debug("Processing staged submission at time {}", CloudSim.clock());
            submitCloudlets();
        } else {
            // Process normally
            super.processEvent(ev);
        }
    }
}
