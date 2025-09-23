package com.thesis.cloudsim.algorithm;

import org.cloudbus.cloudsim.Cloudlet;
import org.cloudbus.cloudsim.Vm;
import java.util.List;
import java.util.Map;

public class LoadBalancer {
    private final Map<Cloudlet, Vm> schedule;
    private final List<Vm> vms;
    private final List<Cloudlet> cloudlets;
    private final int[] vmUsage;
    
    public LoadBalancer(Map<Cloudlet, Vm> schedule, List<Vm> vms, List<Cloudlet> cloudlets) {
        this.schedule = schedule;
        this.vms = vms;
        this.cloudlets = cloudlets;
        this.vmUsage = new int[vms.size()];
        recordCurrentAssignments();
    }
    
    private void recordCurrentAssignments() {
        for (Map.Entry<Cloudlet, Vm> entry : schedule.entrySet()) {
            int vmIndex = vms.indexOf(entry.getValue());
            if (vmIndex >= 0) {
                vmUsage[vmIndex]++;
            }
        }
    }
    
   
    public void balance() {
        if (cloudlets.size() <= vms.size()) {
            return; 
        }
        
        for (int vmIdx = 0; vmIdx < vms.size(); vmIdx++) {
            if (isUnderutilized(vmIdx)) {
                redistributeToVm(vmIdx);
            }
        }
    }
    
    private boolean isUnderutilized(int vmIdx) {
        return vmUsage[vmIdx] == 0;
    }
    
    private void redistributeToVm(int targetVmIdx) {
        int sourceVmIdx = findMostLoadedVm();
        
        // I use proportional threshold instead of hardcoded value
        int minTasksBeforeRedistribution = Math.max(1, cloudlets.size() / vms.size());
        if (vmUsage[sourceVmIdx] > minTasksBeforeRedistribution) {
            moveOneCloudlet(sourceVmIdx, targetVmIdx);
        }
    }
    
    private int findMostLoadedVm() {
        int maxLoadedVm = 0;
        int maxLoad = vmUsage[0];
        
        for (int i = 1; i < vms.size(); i++) {
            if (vmUsage[i] > maxLoad) {
                maxLoad = vmUsage[i];
                maxLoadedVm = i;
            }
        }
        
        return maxLoadedVm;
    }
    
    private void moveOneCloudlet(int fromVmIdx, int toVmIdx) {
        Vm sourceVm = vms.get(fromVmIdx);
        Vm targetVm = vms.get(toVmIdx);
        
        for (Map.Entry<Cloudlet, Vm> entry : schedule.entrySet()) {
            if (entry.getValue().equals(sourceVm)) {
                schedule.put(entry.getKey(), targetVm);
                vmUsage[fromVmIdx]--;
                vmUsage[toVmIdx]++;
                break; 
            }
        }
    }
}