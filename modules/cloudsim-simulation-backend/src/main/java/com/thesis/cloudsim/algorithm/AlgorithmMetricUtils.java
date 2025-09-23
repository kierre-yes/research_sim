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
    
    // Cost model constants based on AWS/Azure pricing models (USD)
    private static final double CPU_COST_PER_MIPS_SEC = 0.000001;    // $1e-6 per MIPS-second
    private static final double RAM_COST_PER_MB_SEC = 0.0000005;     // $5e-7 per MB-second  
    private static final double STORAGE_COST_PER_MB_SEC = 0.000001;  // $1e-6 per MB-second
    private static final double BANDWIDTH_COST_PER_MB = 0.00001;     // $1e-5 per MB transfer
    
    // Server power model constants from "The Case for Energy-Proportional Computing"
    private static final double SERVER_MAX_POWER_WATTS = 215.0;      // Maximum server power consumption
    private static final double SERVER_IDLE_POWER_WATTS = 162.0;     // Idle power consumption
    private static final double POWER_SCALING_FACTOR = 1.4;          // Non-linear power scaling exponent
    

    public static final double PHEROMONE_VARIATION_MIN = 0.95;       
    public static final double PHEROMONE_VARIATION_RANGE = 0.1;      

    /**
     * Enhanced cost calculation considering all resource types
     * 
     * I implement a comprehensive cost model that considers CPU, RAM,
     * storage, and network costs based on cloud pricing models
     */
    public static double enhancedCost(Map<Cloudlet, Vm> schedule, List<Cloudlet> cloudlets, List<Vm> vms) {
        double totalCost = 0.0;
        
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
        // I use power values based on empirical server measurements
        
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
                power = (SERVER_MAX_POWER_WATTS - SERVER_IDLE_POWER_WATTS) * 
                       Math.pow(utilization, POWER_SCALING_FACTOR) + SERVER_IDLE_POWER_WATTS;
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
        Map<Vm, List<Cloudlet>> vmQueues = new HashMap<>();
        
        // Group cloudlets by their assigned VMs 
        for (Map.Entry<Cloudlet, Vm> entry : schedule.entrySet()) {
            vmQueues.computeIfAbsent(entry.getValue(), k -> new ArrayList<>())
                    .add(entry.getKey());
        }
        
        // process each VM's queue in the order they were scheduled
        for (Map.Entry<Vm, List<Cloudlet>> vmEntry : vmQueues.entrySet()) {
            Vm vm = vmEntry.getKey();
            List<Cloudlet> cloudlets = vmEntry.getValue();
            
            double vmCurrentTime = 0.0;
            for (Cloudlet cloudlet : cloudlets) {

                double waitingTime = vmCurrentTime;
                

                double executionTime = cloudlet.getCloudletLength() / vm.getMips();
                

                double responseTime = waitingTime + executionTime;
                totalResponseTime += responseTime;
                
                // update VM's current time for next cloudlet
                vmCurrentTime += executionTime;
            }
        }
        
        // return average response time across all cloudlets
        return totalResponseTime / schedule.size();
    }
    
    
    public static double degreeOfImbalance(Map<Cloudlet, Vm> schedule) {
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
     * @deprecated alternative solution muna
     */
    @Deprecated
    public static double loadBalance(Map<Cloudlet, Vm> schedule) {
        return degreeOfImbalance(schedule);
    }

    /**
     * calculate multi-objective fitness value for a scheduling solution
     * 
     * I centralize fitness calculation to ensure consistency between EPSO and EACO
     * this eliminates code duplication and ensures fair algorithm comparison
     */
    public static double calculateFitness(Map<Cloudlet, Vm> schedule, 
                                         List<Cloudlet> cloudlets, 
                                         List<Vm> vms,
                                         AlgorithmParameters parameters) {
        // Calculate raw metrics
        double makespan = makespan(schedule);
        double cost = enhancedCost(schedule, cloudlets, vms);
        double energy = energy(schedule);
        double loadBalance = degreeOfImbalance(schedule);
        
        // Normalize metrics to [0,1] range
        makespan = normalise("makespan", makespan, cloudlets, vms);
        cost = normalise("enhancedCost", cost, cloudlets, vms);
        energy = normalise("energy", energy, cloudlets, vms);
        loadBalance = normalise("loadBalance", loadBalance, cloudlets, vms);
        
        // Calculate weighted sum (lower is better)
        return parameters.getDouble(AlgorithmParameters.MAKESPAN_WEIGHT) * makespan +
               parameters.getDouble(AlgorithmParameters.COST_WEIGHT) * cost +
               parameters.getDouble(AlgorithmParameters.ENERGY_WEIGHT) * energy +
               parameters.getDouble(AlgorithmParameters.LOAD_BALANCE_WEIGHT) * loadBalance;
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
                double totalWork = cloudlets.stream().mapToDouble(Cloudlet::getCloudletLength).sum();
                double slowestExec = totalWork / vms.stream().mapToDouble(Vm::getMips).min().orElse(1.0);
                double maxRam = vms.stream().mapToDouble(Vm::getRam).max().orElse(1.0);
                max = slowestExec * (maxRam * 0.001); // Based on actual cost formula
                break;
            case "enhancedCost":
                double totalCloudletWork = cloudlets.stream().mapToDouble(Cloudlet::getCloudletLength).sum();
                double worstExecTime = totalCloudletWork / vms.stream().mapToDouble(Vm::getMips).min().orElse(1.0);
                double maxVmMips = vms.stream().mapToDouble(Vm::getMips).max().orElse(1.0);
                double maxVmRam = vms.stream().mapToDouble(Vm::getRam).max().orElse(1.0);
                double maxStorage = vms.stream().mapToDouble(Vm::getSize).max().orElse(1.0);
                double maxFileSize = cloudlets.stream().mapToDouble(c -> c.getCloudletFileSize() + c.getCloudletOutputSize()).max().orElse(1.0);
                max = (maxVmMips * worstExecTime * 0.00001) + 
                      (maxVmRam * worstExecTime * 0.000005) + 
                      (maxStorage * worstExecTime * 0.000001) + 
                      (maxFileSize * 0.00001);
                break;
            case "costEfficiency":
                max = cloudlets.size() / 0.001; // Maximum tasks per minimum cost
                break;
            case "energy":
                // Use actual power constants from energy calculation
                double totalExecTime = cloudlets.stream().mapToDouble(Cloudlet::getCloudletLength).sum() / 
                                       vms.stream().mapToDouble(Vm::getMips).min().orElse(1.0);
                max = SERVER_MAX_POWER_WATTS * totalExecTime * vms.size(); // Maximum power * time * all VMs
                break;
            case "loadBalance":
                // Theoretical maximum imbalance: all work on one VM
                max = vms.size() - 1.0; // (maxTime - 0) / avgTime where avgTime = maxTime/n
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

