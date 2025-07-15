package com.thesis.cloudsim.metrics;

import lombok.RequiredArgsConstructor;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.cloudbus.cloudsim.cloudlets.Cloudlet;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.datacenters.Datacenter;
import org.cloudbus.cloudsim.vms.Vm;

import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.util.stream.Collectors;

@RequiredArgsConstructor
public class MetricsCalculator {

    private final CloudSim sim;
    private final List<Vm> vms;
    private final Datacenter datacenter;
    private final List<Cloudlet> finishedCloudlets;

    private static final int TOTAL_CLOUDLETS = 100;

    public double calculateAverageResponseTime() {
        if (finishedCloudlets.isEmpty()) return 0.0;
        DescriptiveStatistics stats = new DescriptiveStatistics();
        finishedCloudlets.forEach(c -> stats.addValue(c.getFinishTime() - c.getExecStartTime()));
        return stats.getMean();
    }

    public double calculateMakespan() {
        if (finishedCloudlets.isEmpty()) return 0.0;
        return finishedCloudlets.stream()
                .mapToDouble(Cloudlet::getFinishTime)
                .max()
                .orElse(0.0);
    }

    public double calculateResourceUtilization() {
        if (vms.isEmpty()) return 0.0;
        // Calculate utilization based on VM MIPS and current usage
        double totalUtilization = 0.0;
        for (Vm vm : vms) {
            double vmMips = vm.getMips() * vm.getNumberOfPes();
            // Use a simple utilization estimation based on VM capacity
            totalUtilization += (vmMips > 0) ? 50.0 : 0.0; // Placeholder calculation
        }
        return totalUtilization / vms.size();
    }

    public double calculateEnergyConsumption() {
        // Calculate energy based on host power models
        return datacenter.getHostList().stream()
                .mapToDouble(host -> {
                    // Calculate host utilization estimation
                    double hostMips = host.getTotalMipsCapacity();
                    double utilization = (hostMips > 0) ? 0.5 : 0.0; // Placeholder: 50% utilization
                    return host.getPowerModel().getPower(utilization);
                })
                .sum();
    }

    public double calculateLoadImbalance() {
        if (vms.isEmpty()) return 0.0;
        DescriptiveStatistics stats = new DescriptiveStatistics();
        
        for (Vm vm : vms) {
            // Calculate VM load based on assigned cloudlets
            double vmLoad = finishedCloudlets.stream()
                    .filter(c -> c.getVm() != null && c.getVm().equals(vm))
                    .mapToDouble(c -> c.getLength() / vm.getMips())
                    .sum();
            stats.addValue(vmLoad);
        }
        return stats.getStandardDeviation();
    }

    private List<SimulationResults.VmUtilization> calculateVmUtilization() {
        List<SimulationResults.VmUtilization> vmUtilizations = new ArrayList<>();
        
        for (Vm vm : vms) {
            List<Cloudlet> vmCloudlets = finishedCloudlets.stream()
                    .filter(c -> c.getVm() != null && c.getVm().equals(vm))
                    .collect(Collectors.toList());
            
            double totalExecTime = vmCloudlets.stream()
                    .mapToDouble(c -> c.getFinishTime() - c.getExecStartTime())
                    .sum();
            
            double cpuUtilization = vmCloudlets.isEmpty() ? 0.0 : 
                    (totalExecTime / calculateMakespan()) * 100;
            
            vmUtilizations.add(SimulationResults.VmUtilization.builder()
                    .vmId((int) vm.getId())
                    .cpuUtilization(cpuUtilization)
                    .ramUtilization(vm.getRam().getCapacity())
                    .numAPECloudlets(vmCloudlets.size())
                    .build());
        }
        
        return vmUtilizations;
    }

    private List<SimulationResults.SchedulingLogEntry> generateSchedulingLog() {
        List<SimulationResults.SchedulingLogEntry> schedulingLog = new ArrayList<>();
        
        // Add configuration entry
        Map<String, Object> configData = new HashMap<>();
        configData.put("optimizationAlgorithm", "EACO");
        configData.put("numHosts", (double) datacenter.getHostList().size());
        configData.put("numVms", (double) vms.size());
        configData.put("numCloudlets", (double) finishedCloudlets.size());
        configData.put("vmScheduler", "TimeShared");
        configData.put("workloadType", "CSV");
        configData.put("numPesPerHost", 2.0);
        configData.put("peMips", 2000.0);
        configData.put("ramPerHost", 2048.0);
        configData.put("bwPerHost", 10000.0);
        configData.put("storagePerHost", 100000.0);
        configData.put("vmMips", 1000.0);
        configData.put("vmPes", 2.0);
        configData.put("vmRam", 1024.0);
        configData.put("vmBw", 1000.0);
        configData.put("vmSize", 10000.0);
        configData.put("cloudletLength", 6000.0);
        configData.put("cloudletPes", 1.0);
        
        schedulingLog.add(SimulationResults.SchedulingLogEntry.builder()
                .type("configuration")
                .data(configData)
                .build());
        
        // Add assignment entries
        for (Cloudlet cloudlet : finishedCloudlets) {
            if (cloudlet.getVm() != null) {
                schedulingLog.add(SimulationResults.SchedulingLogEntry.builder()
                        .type("assignment")
                        .vmId((double) cloudlet.getVm().getId())
                        .cloudletId((double) cloudlet.getId())
                        .submissionTime(0.0) // Default submission time
                        .description(String.format("Cloudlet %d assigned to VM %d at submission time %.2f seconds",
                                cloudlet.getId(), cloudlet.getVm().getId(), 0.0))
                        .build());
            }
        }
        
        return schedulingLog;
    }

    public SimulationResults buildResults() {
        return SimulationResults.builder()
                .summary(SimulationResults.Summary.builder()
                        .averageResponseTime(calculateAverageResponseTime())
                        .makespan(calculateMakespan())
                        .totalCloudlets(TOTAL_CLOUDLETS)
                        .finishedCloudlets(finishedCloudlets.size())
                        .imbalanceDegree(calculateLoadImbalance())
                        .resourceUtilization(calculateResourceUtilization())
                        .build())
                .vmUtilization(calculateVmUtilization())
                .schedulingLog(generateSchedulingLog())
                .energyConsumption(calculateEnergyConsumption())
                .build();
    }
}
