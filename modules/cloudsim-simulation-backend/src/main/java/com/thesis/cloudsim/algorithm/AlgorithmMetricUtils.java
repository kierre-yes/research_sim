package com.thesis.cloudsim.algorithm;

import org.cloudbus.cloudsim.Cloudlet;
import org.cloudbus.cloudsim.Vm;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
/**
 * Utility class for scheduling metric calculations
 * 
 * I centralize all metric calculations here so that both algorithms use
 * identical formulas, ensuring fair comparison between EPSO and EACO
 */
public final class AlgorithmMetricUtils {

    // I make constructor private so that this utility class can't be instantiated
    private AlgorithmMetricUtils() { }

    /**
     * Calculate makespan: the longest cumulative execution time of any VM
     * 
     * I calculate makespan as the maximum completion time across all VMs
     * This represents when the entire workload finishes execution
     */
    public static double makespan(Map<Cloudlet, Vm> schedule) {
        Map<Vm, Double> finish = new HashMap<>();
        
        // I accumulate execution time for each VM
        for (Map.Entry<Cloudlet, Vm> e : schedule.entrySet()) {
            double exec = e.getKey().getCloudletLength() / e.getValue().getMips();
            // I use merge so that multiple cloudlets on the same VM are summed
            finish.merge(e.getValue(), exec, Double::sum);
        }
        
        // I return the maximum completion time among all VMs
        return finish.values().stream().mapToDouble(Double::doubleValue).max().orElse(0.0);
    }

    /**
     * Calculate rough monetary cost based on RAM and execution time
     * 
     * I use a simplified cost model for quick calculations
     * For more accurate cost, use enhancedCost() method
     */
    public static double cost(Map<Cloudlet, Vm> schedule) {
        double cost = 0.0;
        for (Map.Entry<Cloudlet, Vm> e : schedule.entrySet()) {
            double exec = e.getKey().getCloudletLength() / e.getValue().getMips();
            double hostRam = e.getValue().getHost().getGuestRamProvisioner().getRam();
            // I multiply RAM by 0.001 as a simple cost factor
            cost += exec * (hostRam * 0.001);
        }
        return cost;
    }
    
    /**
     * Enhanced cost calculation considering all resource types
     * 
     * I implement a comprehensive cost model that considers CPU, RAM,
     * storage, and network costs based on cloud pricing models
     */
    public static double enhancedCost(Map<Cloudlet, Vm> schedule, List<Cloudlet> cloudlets, List<Vm> vms) {
        double totalCost = 0.0;
        
        // I define cost constants based on typical cloud pricing
        // These can be adjusted to match specific cloud provider rates
        final double CPU_COST_PER_MIPS_SEC = 0.00001;
        final double RAM_COST_PER_MB_SEC = 0.000005;
        final double STORAGE_COST_PER_MB_SEC = 0.000001;
        final double BANDWIDTH_COST_PER_MB = 0.00001;
        
        for (Map.Entry<Cloudlet, Vm> entry : schedule.entrySet()) {
            Cloudlet c = entry.getKey();
            Vm v = entry.getValue();
            double execTime = c.getCloudletLength() / v.getMips();
            
            // I calculate CPU cost based on processing power used over time
            double cpuCost = v.getMips() * execTime * CPU_COST_PER_MIPS_SEC;
            
            // I calculate RAM cost based on memory reserved during execution
            double ramCost = v.getRam() * execTime * RAM_COST_PER_MB_SEC;
            
            // I calculate storage cost based on disk space used
            double storageCost = v.getSize() * execTime * STORAGE_COST_PER_MB_SEC;
            
            // I calculate network cost based on data transfer (input + output)
            double networkCost = (c.getCloudletFileSize() + c.getCloudletOutputSize()) * BANDWIDTH_COST_PER_MB;
            
            totalCost += cpuCost + ramCost + storageCost + networkCost;
        }
        
        return totalCost;
    }
    
    /**
     * Calculate cost efficiency (performance per dollar)
     * 
     * I measure how many tasks are completed per unit time per dollar spent
     * Higher values indicate better cost-performance ratio
     */
    public static double costEfficiency(Map<Cloudlet, Vm> schedule, List<Cloudlet> cloudlets, List<Vm> vms) {
        double cost = enhancedCost(schedule, cloudlets, vms);
        // I check for zero cost to avoid division by zero
        if (cost <= 0) return 0.0;
        
        double makespan = makespan(schedule);
        // I check for zero makespan to avoid division by zero
        if (makespan <= 0) return 0.0;
        
        // I calculate throughput as tasks completed per time unit
        int finishedTasks = schedule.size();
        double performance = finishedTasks / makespan;
        
        // I divide performance by cost to get efficiency metric
        return performance / cost;
    }


    /**
     * Calculate energy consumption using non-linear power model
     * 
     * I implement the energy model from "The Case for Energy-Proportional Computing"
     * which shows servers consume significant idle power
     */
    public static double energy(Map<Cloudlet, Vm> schedule) {
        // I use power values based on typical server specifications
        // These values come from empirical measurements in data center research
        final double P_MAX = 215.0;    // Maximum power at 100% utilization (Watts)
        final double P_IDLE = 162.0;   // Power when server is idle but on (Watts)
        final double ALPHA = 1.4;      // Non-linear scaling factor from research
        
        double totalEnergy = 0.0;
        Map<Vm, Double> vmWorkloads = new HashMap<>();
        
        // I first calculate the total workload time for each VM
        for (Map.Entry<Cloudlet, Vm> entry : schedule.entrySet()) {
            Cloudlet cloudlet = entry.getKey();
            Vm vm = entry.getValue();
            double execTime = cloudlet.getCloudletLength() / vm.getMips();
            vmWorkloads.merge(vm, execTime, Double::sum);
        }
        
        // I precompute makespan once to avoid recalculating for each VM
        double makespanValue = makespan(schedule);
        
        // I calculate energy for each VM based on its utilization
        for (Map.Entry<Vm, Double> entry : vmWorkloads.entrySet()) {
            Vm vm = entry.getKey();
            double workloadTime = entry.getValue();
            
            // I calculate utilization as the fraction of time VM is busy
            double utilization = Math.min(1.0, workloadTime / makespanValue);
            
            // I apply non-linear power model: P = (P_max - P_idle) * U^α + P_idle
            // This reflects that power doesn't scale linearly with utilization
            double power;
            if (utilization > 0) {
                power = (P_MAX - P_IDLE) * Math.pow(utilization, ALPHA) + P_IDLE;
            } else {
                power = 0; // I assume VM can be turned off if not used
            }
            
            // I multiply power by time to get energy (Watts * seconds = Joules)
            totalEnergy += power * workloadTime;
        }
        
        return totalEnergy;
    }


    /**
     * Calculate average response time for all cloudlets
     * 
     * I calculate response time as waiting time + execution time
     * This metric shows how quickly tasks complete from submission
     */
    public static double responseTime(Map<Cloudlet, Vm> schedule) {
        if (schedule.isEmpty()) return 0.0;
        
        double totalResponseTime = 0.0;
        Map<Vm, Double> vmCurrentTime = new HashMap<>();
        
        // I sort cloudlets by ID to simulate FIFO submission order
        List<Cloudlet> orderedCloudlets = new ArrayList<>(schedule.keySet());
        orderedCloudlets.sort((c1, c2) -> Long.compare(c1.getCloudletId(), c2.getCloudletId()));
        
        for (Cloudlet cloudlet : orderedCloudlets) {
            Vm vm = schedule.get(cloudlet);
            
            // I track the current time for each VM to calculate waiting time
            double vmTime = vmCurrentTime.getOrDefault(vm, 0.0);
            
            // I calculate waiting time as the time cloudlet waits before execution starts
            double waitingTime = vmTime;
            
            // I calculate execution time based on cloudlet length and VM speed
            double executionTime = cloudlet.getCloudletLength() / vm.getMips();

            // I sum waiting and execution time to get total response time
            double responseTime = waitingTime + executionTime;
            totalResponseTime += responseTime;
            
            // I update VM's current time for next cloudlet in queue
            vmCurrentTime.put(vm, vmTime + executionTime);
        }
        
        // I return average response time across all cloudlets
        return totalResponseTime / schedule.size();
    }
    

    /**
     * Calculate load balance using Degree of Imbalance (DI) metric
     * 
     * I measure how evenly work is distributed across VMs
     * Lower values indicate better load balance (0 = perfect balance)
     */
    public static double loadBalance(Map<Cloudlet, Vm> schedule) {
        Map<Vm, Double> vmCompletionTimes = new HashMap<>();
        
        // I calculate total execution time for each VM
        for (Map.Entry<Cloudlet, Vm> e : schedule.entrySet()) {
            double execTime = e.getKey().getCloudletLength() / e.getValue().getMips();
            vmCompletionTimes.merge(e.getValue(), execTime, Double::sum);
        }
        
        // I return 0 (perfect balance) if no load exists
        if (vmCompletionTimes.isEmpty() || vmCompletionTimes.values().stream().allMatch(t -> t == 0.0)) {
            return 0.0;
        }
        
        // I find the maximum completion time among all VMs
        double maxTime = vmCompletionTimes.values().stream()
            .mapToDouble(Double::doubleValue)
            .max()
            .orElse(0.0);
        
        // I find the minimum completion time among all VMs
        double minTime = vmCompletionTimes.values().stream()
            .mapToDouble(Double::doubleValue)
            .min()
            .orElse(0.0);
        
        // I calculate average completion time across all VMs
        double averageTime = vmCompletionTimes.values().stream()
            .mapToDouble(Double::doubleValue)
            .average()
            .orElse(0.0);
        
        // I apply the Degree of Imbalance formula: DI = (MaxTime - MinTime) / AverageTime
        // This normalizes the load difference by the average load
        if (averageTime <= 0.0) {
            return 0.0;
        }
        
        return (maxTime - minTime) / averageTime;
    }

    /**
     * Linear normalization of metrics to [0,1] range
     * 
     * I normalize metrics so that different units can be combined in multi-objective
     * optimization without one metric dominating due to scale differences
     */
    public static double normalise(String type, double value, List<Cloudlet> cloudlets, List<Vm> vms) {
        double max = 1.0;
        switch (type) {
            case "makespan":
                // I estimate worst-case makespan: all tasks on slowest VM
                max = cloudlets.stream().mapToDouble(Cloudlet::getCloudletLength).sum() /
                      vms.stream().mapToDouble(Vm::getMips).min().orElse(1.0);
                break;
            case "cost":
                // I use empirical factor for simple cost upper bound
                max = cloudlets.stream().mapToDouble(Cloudlet::getCloudletLength).sum() * 0.1;
                break;
            case "enhancedCost":
                // I use higher factor for enhanced cost due to additional components
                max = cloudlets.stream().mapToDouble(Cloudlet::getCloudletLength).sum() * 0.2;
                break;
            case "costEfficiency":
                // I set reasonable upper bound for efficiency metric
                max = 10.0;
                break;
            case "energy":
                // I estimate maximum energy based on total work and power consumption
                max = cloudlets.stream().mapToDouble(Cloudlet::getCloudletLength).sum() * 100.0;
                break;
            case "loadBalance":
                // I use 2.0 as typical maximum imbalance degree
                max = 2.0;  
                break;
            case "responseTime":
                // I calculate worst-case response time: sequential execution on slowest VM
                double totalLength = cloudlets.stream().mapToDouble(Cloudlet::getCloudletLength).sum();
                double slowestMips = vms.stream().mapToDouble(Vm::getMips).min().orElse(1.0);
                max = totalLength / slowestMips;
                break;
        }
        
        // I ensure the normalized value stays within [0,1] bounds
        return Math.max(0.0, Math.min(1.0, value / max));
    }
}

