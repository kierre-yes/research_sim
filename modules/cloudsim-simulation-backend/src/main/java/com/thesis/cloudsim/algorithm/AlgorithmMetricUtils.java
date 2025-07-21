package com.thesis.cloudsim.algorithm;

import org.cloudbus.cloudsim.Cloudlet;
import org.cloudbus.cloudsim.Vm;
import org.cloudbus.cloudsim.core.CloudSim;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
/**
 * Utility class that centralises common scheduling-metric calculations so we
 * avoid code duplication across EPSO, EACO and any future algorithm.  All
 * methods are <strong>static</strong> and rely only on core Java collections.
 */
public final class AlgorithmMetricUtils {

    private AlgorithmMetricUtils() { /* utility – no instances */ }

    /** Calculate makespan: the longest cumulative execution time of any VM. */
    public static double makespan(Map<Cloudlet, Vm> schedule) {
        Map<Vm, Double> finish = new HashMap<>();
        for (Map.Entry<Cloudlet, Vm> e : schedule.entrySet()) {
            double exec = e.getKey().getCloudletLength() / e.getValue().getMips();
            finish.merge(e.getValue(), exec, Double::sum);
        }
        return finish.values().stream().mapToDouble(Double::doubleValue).max().orElse(0.0);
    }

    /** Rough monetary cost proportional to RAM capacity and exec time. */
    public static double cost(Map<Cloudlet, Vm> schedule) {
        double cost = 0.0;
        for (Map.Entry<Cloudlet, Vm> e : schedule.entrySet()) {
            double exec = e.getKey().getCloudletLength() / e.getValue().getMips();
            double hostRam = e.getValue().getHost().getGuestRamProvisioner().getRam();
            cost += exec * (hostRam * 0.001);
        }
        return cost;
    }
    
    /** Enhanced cost calculation with network-aware costs. */
    public static double enhancedCost(Map<Cloudlet, Vm> schedule, List<Cloudlet> cloudlets, List<Vm> vms) {
        double totalCost = 0.0;
        // Cost constants (adjust based on your pricing model)
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
    
    /** Calculate cost efficiency (performance per dollar). */
    public static double costEfficiency(Map<Cloudlet, Vm> schedule, List<Cloudlet> cloudlets, List<Vm> vms) {
        double cost = enhancedCost(schedule, cloudlets, vms);
        if (cost <= 0) return 0.0;
        
        double makespan = makespan(schedule);
        if (makespan <= 0) return 0.0;
        
        // Performance metric: completed tasks per time unit
        int finishedTasks = schedule.size();
        double performance = finishedTasks / makespan;
        
        return performance / cost;
    }

    /** Energy in Watt-seconds based on host power model and exec time. */
    public static double energy(Map<Cloudlet, Vm> schedule) {
        double energy = 0.0;
        for (Map.Entry<Cloudlet, Vm> e : schedule.entrySet()) {
            double exec = e.getKey().getCloudletLength() / e.getValue().getMips();
            double cpuUtil = e.getValue().getTotalUtilizationOfCpu(CloudSim.clock());
            // Simplified energy calculation for CloudSim 7.0
            energy += exec * 100.0; // Basic power consumption estimate
        }
        return energy;
    }

    /**
     * Load-balance metric = standard deviation of workloads across VMs.
     */
    public static double loadBalance(Map<Cloudlet, Vm> schedule) {
        Map<Vm, Double> loads = new HashMap<>();
        for (Map.Entry<Cloudlet, Vm> e : schedule.entrySet()) {
            double l = e.getKey().getCloudletLength() / e.getValue().getMips();
            loads.merge(e.getValue(), l, Double::sum);
        }
        double[] arr = loads.values().stream().mapToDouble(Double::doubleValue).toArray();
        double mean = Arrays.stream(arr).average().orElse(0.0);
        double variance = 0.0;
        for (double v : arr) {
            variance += (v - mean) * (v - mean);
        }
        variance /= arr.length > 0 ? arr.length : 1;
        return Math.sqrt(variance);
    }

    /**
     * Linear normalisation to [0,1] using rough upper bounds.
     */
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
                // Use a higher max for enhanced cost as it includes network costs
                max = cloudlets.stream().mapToDouble(Cloudlet::getCloudletLength).sum() * 0.2;
                break;
            case "costEfficiency":
                // Cost efficiency is already normalized (performance/cost ratio)
                max = 10.0;
                break;
            case "energy":
                max = cloudlets.stream().mapToDouble(Cloudlet::getCloudletLength).sum() * 100.0;
                break;
            case "loadBalance":
                max = cloudlets.stream().mapToDouble(Cloudlet::getCloudletLength).sum();
                break;
        }
        return Math.max(0.0, Math.min(1.0, value / max));
    }
}

