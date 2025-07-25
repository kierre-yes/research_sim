package com.thesis.cloudsim.algorithm;

import org.cloudbus.cloudsim.Cloudlet;
import org.cloudbus.cloudsim.Vm;
import org.cloudbus.cloudsim.core.CloudSim;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
// Utility class for scheduling-metric calculations
public final class AlgorithmMetricUtils {

    private AlgorithmMetricUtils() { }

    // Calculate makespan: the longest cumulative execution time of any VM.
    public static double makespan(Map<Cloudlet, Vm> schedule) {
        Map<Vm, Double> finish = new HashMap<>();
        for (Map.Entry<Cloudlet, Vm> e : schedule.entrySet()) {
            double exec = e.getKey().getCloudletLength() / e.getValue().getMips();
            finish.merge(e.getValue(), exec, Double::sum);
        }
        return finish.values().stream().mapToDouble(Double::doubleValue).max().orElse(0.0);
    }

    // Calculate rough monetary cost based on RAM and execution time.
    public static double cost(Map<Cloudlet, Vm> schedule) {
        double cost = 0.0;
        for (Map.Entry<Cloudlet, Vm> e : schedule.entrySet()) {
            double exec = e.getKey().getCloudletLength() / e.getValue().getMips();
            double hostRam = e.getValue().getHost().getGuestRamProvisioner().getRam();
            cost += exec * (hostRam * 0.001);
        }
        return cost;
    }
    
    // Enhanced cost calculation considering network costs.
    public static double enhancedCost(Map<Cloudlet, Vm> schedule, List<Cloudlet> cloudlets, List<Vm> vms) {
        double totalCost = 0.0;
        // Cost constants for pricing model
        final double CPU_COST_PER_MIPS_SEC = 0.00001;
        final double RAM_COST_PER_MB_SEC = 0.000005;
        final double STORAGE_COST_PER_MB_SEC = 0.000001;
        final double BANDWIDTH_COST_PER_MB = 0.00001;
        
        for (Map.Entry<Cloudlet, Vm> entry : schedule.entrySet()) {
            Cloudlet c = entry.getKey();
            Vm v = entry.getValue();
            double execTime = c.getCloudletLength() / v.getMips();
            
            // CPU cost
            double cpuCost = v.getMips() * execTime * CPU_COST_PER_MIPS_SEC;
            
            // RAM cost
            double ramCost = v.getRam() * execTime * RAM_COST_PER_MB_SEC;
            
            // Storage cost
            double storageCost = v.getSize() * execTime * STORAGE_COST_PER_MB_SEC;
            
            // Network cost (file input + output)
            double networkCost = (c.getCloudletFileSize() + c.getCloudletOutputSize()) * BANDWIDTH_COST_PER_MB;
            
            totalCost += cpuCost + ramCost + storageCost + networkCost;
        }
        
        return totalCost;
    }
    
    // Calculate cost efficiency (performance per dollar).
    public static double costEfficiency(Map<Cloudlet, Vm> schedule, List<Cloudlet> cloudlets, List<Vm> vms) {
        double cost = enhancedCost(schedule, cloudlets, vms);
        if (cost <= 0) return 0.0;
        
        double makespan = makespan(schedule);
        if (makespan <= 0) return 0.0;
        
        // Performance per task per time unit
        int finishedTasks = schedule.size();
        double performance = finishedTasks / makespan;
        
        return performance / cost;
    }

    // Calculate energy consumption in Watt-seconds.
    public static double energy(Map<Cloudlet, Vm> schedule) {
        double energy = 0.0;
        for (Map.Entry<Cloudlet, Vm> e : schedule.entrySet()) {
            double exec = e.getKey().getCloudletLength() / e.getValue().getMips();
            double cpuUtil = e.getValue().getTotalUtilizationOfCpu(CloudSim.clock());
            energy += exec * 100.0;
        }
        return energy;
    }

    // Calculate Degree of Imbalance for load distribution.
    public static double loadBalance(Map<Cloudlet, Vm> schedule) {
        Map<Vm, Double> vmCompletionTimes = new HashMap<>();
        
        // Completion time for each VM
        for (Map.Entry<Cloudlet, Vm> e : schedule.entrySet()) {
            double execTime = e.getKey().getCloudletLength() / e.getValue().getMips();
            vmCompletionTimes.merge(e.getValue(), execTime, Double::sum);
        }
        
        // Return 0 if no load on VMs
        if (vmCompletionTimes.isEmpty() || vmCompletionTimes.values().stream().allMatch(t -> t == 0.0)) {
            return 0.0;
        }
        
        // Calculate MaxTime, MinTime, and AverageTime
        double maxTime = vmCompletionTimes.values().stream()
            .mapToDouble(Double::doubleValue)
            .max()
            .orElse(0.0);
        
        double minTime = vmCompletionTimes.values().stream()
            .mapToDouble(Double::doubleValue)
            .min()
            .orElse(0.0);
        
        double averageTime = vmCompletionTimes.values().stream()
            .mapToDouble(Double::doubleValue)
            .average()
            .orElse(0.0);
        
        // Use DI formula for imbalance
        if (averageTime <= 0.0) {
            return 0.0;
        }
        
        return (maxTime - minTime) / averageTime;
    }

    // Linear normalization of metrics.
    public static double normalise(String type, double value, List<Cloudlet> cloudlets, List<Vm> vms) {
        double max = 1.0;
        switch (type) {
            case "makespan":
                max = cloudlets.stream().mapToDouble(Cloudlet::getCloudletLength).sum() /
                      vms.stream().mapToDouble(Vm::getMips).min().orElse(1.0);
                break;
            case "cost":
                max = cloudlets.stream().mapToDouble(Cloudlet::getCloudletLength).sum() * 0.1;
                break;
            case "enhancedCost":
                max = cloudlets.stream().mapToDouble(Cloudlet::getCloudletLength).sum() * 0.2;
                break;
            case "costEfficiency":
                max = 10.0;
                break;
            case "energy":
                max = cloudlets.stream().mapToDouble(Cloudlet::getCloudletLength).sum() * 100.0;
                break;
            case "loadBalance":
                max = 1.0;
                break;
        }
        return Math.max(0.0, Math.min(1.0, value / max));
    }
}

