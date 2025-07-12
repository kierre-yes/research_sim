package com.thesis.cloudsim.metrics;

import lombok.RequiredArgsConstructor;
import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;
import org.cloudbus.cloudsim.cloudlets.Cloudlet;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.datacenters.Datacenter;
import org.cloudbus.cloudsim.vms.Vm;

import java.util.List;

@RequiredArgsConstructor
public class MetricsCalculator {

    private final CloudSim sim;
    private final List<Vm> vms;
    private final Datacenter datacenter;
    private final List<Cloudlet> finishedCloudlets;

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

    public SimulationResults buildResults() {
        return SimulationResults.builder()
                .averageResponseTime(calculateAverageResponseTime())
                .makespan(calculateMakespan())
                .resourceUtilization(calculateResourceUtilization())
                .energyConsumption(calculateEnergyConsumption())
                .loadImbalance(calculateLoadImbalance())
                .build();
    }
}
