package com.thesis.cloudsim.metrics;

import lombok.RequiredArgsConstructor;
import com.thesis.cloudsim.constants.SimulationConstants;
import org.cloudbus.cloudsim.Cloudlet;
import org.cloudbus.cloudsim.core.CloudSimTags;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.Datacenter;
import com.thesis.cloudsim.algorithm.AlgorithmMetricUtils;
import org.cloudbus.cloudsim.Vm;
import org.cloudbus.cloudsim.Host;
import org.cloudbus.cloudsim.power.PowerHost;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;

/**
 * Performance Metrics Calculator for CloudSim Simulations
 * 
 * This class calculates key performance metrics including makespan, resource utilization,
 * energy consumption, and load balance.
 * @author [Kier]
 * @version 1.0
 * @since 2025-07-11
 * 
 */
public class MetricsCalculator {
    
    private static final Logger logger = LoggerFactory.getLogger(MetricsCalculator.class);

    private final List<Vm> vms;
    private final Datacenter datacenter;
    private final List<Cloudlet> finishedCloudlets;
    private final String algorithmName;
    private final Map<Integer, Integer> vmToHostMapping; // VM ID to Host ID mapping
    
    public MetricsCalculator(List<Vm> vms, Datacenter datacenter, List<Cloudlet> finishedCloudlets) {
        this.vms = vms;
        this.datacenter = datacenter;
        this.finishedCloudlets = finishedCloudlets;
        this.algorithmName = "Unknown";
        this.vmToHostMapping = null;
    }
    
    public MetricsCalculator(List<Vm> vms, Datacenter datacenter, List<Cloudlet> finishedCloudlets, String algorithmName) {
        this.vms = vms;
        this.datacenter = datacenter;
        this.finishedCloudlets = finishedCloudlets;
        this.algorithmName = algorithmName;
        this.vmToHostMapping = null;
    }
    
    public MetricsCalculator(List<Vm> vms, Datacenter datacenter, List<Cloudlet> finishedCloudlets, String algorithmName, Map<Integer, Integer> vmToHostMapping) {
        this.vms = vms;
        this.datacenter = datacenter;
        this.finishedCloudlets = finishedCloudlets;
        this.algorithmName = algorithmName;
        this.vmToHostMapping = vmToHostMapping;
    }


    public double calculateAverageResponseTime() {
        if (finishedCloudlets.isEmpty()) return 0.0;
        
        double sum = 0.0;
        int validCount = 0;
        
        for (Cloudlet c : finishedCloudlets) {
            double responseTime = c.getActualCPUTime();
            if (responseTime > 0) {
                sum += responseTime;
                validCount++;
            }
        }
        
        // Return 0 if no cloudlets
        return validCount > 0 ? sum / validCount : 0.0;
    }

    public double calculateMakespan() {
        if (finishedCloudlets.isEmpty()) return 0.0;
        double maxFinishTime = -1.0;
        for (Cloudlet cloudlet : finishedCloudlets) {
            double finishTime = cloudlet.getExecFinishTime();
            if (finishTime > maxFinishTime) {
                maxFinishTime = finishTime;
            }
        }
        return maxFinishTime;
    }

public double calculateResourceUtilization() {
    if (vms.isEmpty() || datacenter == null) {
        return 0.0;
    }
    
    double makespan = calculateMakespan();
    if (makespan <= 0) {
        return 0.0;
    }
    
    // Build map of hosts to their VMs using preserved mappings
    Map<Host, List<Vm>> hostToVmsMap = new HashMap<>();
    int vmsWithHosts = 0;
    for (Vm vm : vms) {
        Host host = null;
        
        // First try to use preserved VM-to-host mapping if available
        if (vmToHostMapping != null && vmToHostMapping.containsKey(vm.getId())) {
            int hostId = vmToHostMapping.get(vm.getId());
            // Find the host with this ID
            for (Object hostObj : datacenter.getHostList()) {
                Host h = (Host) hostObj;
                if (h.getId() == hostId) {
                    host = h;
                    break;
                }
            }
        }
        
        // Fallback to vm.getHost() if mapping not available
        if (host == null && vm.getHost() != null && vm.getHost() instanceof Host) {
            host = (Host) vm.getHost();
        }
        
        if (host != null) {
            hostToVmsMap.computeIfAbsent(host, k -> new ArrayList<>()).add(vm);
            vmsWithHosts++;
        }
    }
    
    double totalUtilization = 0.0;
    int activeHosts = 0;  // Count only hosts with VMs
    
    // Calculate utilization for each host
    for (Object hostObj : datacenter.getHostList()) {
        Host host = (Host) hostObj;
        List<Vm> hostVms = hostToVmsMap.getOrDefault(host, new ArrayList<>());
        
        if (hostVms.isEmpty()) {
            // Host has no VMs, skip it in utilization calculation
            continue;
        }
        
        activeHosts++;  // Count this host as active
        double hostMips = host.getTotalMips();
        double hostUtilization = 0.0;
        
        // Calculate utilization for each VM on this host
        for (Vm vm : hostVms) {
            List<Cloudlet> vmCloudlets = cloudletsForVm(vm);
            double vmMips = vm.getMips() * vm.getNumberOfPes();
            double vmUtilization = 0.0;
            
            for (Cloudlet c : vmCloudlets) {
                double execTime = c.getActualCPUTime();
                double cloudletLength = c.getCloudletLength();
                if (execTime > 0) {
                    double cloudletMips = cloudletLength / execTime;
                    double contribution = (cloudletMips / vmMips) * (execTime / makespan);
                    vmUtilization += contribution;
                }
            }
            
            // Normalize VM utilization and add to host utilization
            vmUtilization = Math.min(1.0, vmUtilization);
            hostUtilization += vmUtilization * (vmMips / hostMips);
        }
        
        // Normalize host utilization (Uj in the formula)
        hostUtilization = Math.min(1.0, hostUtilization);
        totalUtilization += hostUtilization;
    }
    
    // Return average utilization across ACTIVE hosts only
    // Formula: Resource Utilization = (∑Uj) / m_active * 100
    if (activeHosts == 0) {
        return 0.0;
    }
    
    double result = (totalUtilization / activeHosts) * 100.0;
    return result;
}

    public double calculateEnergyConsumption() {
        // Use standardized energy model matching AlgorithmMetricUtils
        final double P_MAX = 215.0;    // Maximum power at 100% utilization (Watts)
        final double P_IDLE = 162.0;   // Power when server is idle but on (Watts)
        final double ALPHA = 1.4;      // Non-linear scaling factor
        
        double totalEnergy = 0.0;
        double makespan = calculateMakespan();
        
        if (makespan <= 0) {
            return 0.0;
        }
        
        // Build map of hosts to their VMs using preserved mappings
        Map<Host, List<Vm>> hostToVmsMap = new HashMap<>();
        int vmsWithHostCount = 0;
        for (Vm vm : vms) {
            Host host = null;
            
            // First try to use preserved VM-to-host mapping if available
            if (vmToHostMapping != null && vmToHostMapping.containsKey(vm.getId())) {
                int hostId = vmToHostMapping.get(vm.getId());
                // Find the host with this ID
                for (Object hostObj : datacenter.getHostList()) {
                    Host h = (Host) hostObj;
                    if (h.getId() == hostId) {
                        host = h;
                        break;
                    }
                }
            }
            
            // Fallback to vm.getHost() if mapping not available
            if (host == null && vm.getHost() != null && vm.getHost() instanceof Host) {
                host = (Host) vm.getHost();
            }
            
            if (host != null) {
                hostToVmsMap.computeIfAbsent(host, k -> new ArrayList<>()).add(vm);
                vmsWithHostCount++;
                System.out.println("[DEBUG] VM " + vm.getId() + " is on Host " + host.getId());
            } else {
                System.out.println("[DEBUG] VM " + vm.getId() + " has null host!");
            }
        }
        System.out.println("[DEBUG] VMs with valid hosts: " + vmsWithHostCount);
        
        // Calculate energy for each host
        int hostIndex = 0;
        for (Object hostObj : datacenter.getHostList()) {
            Host host = (Host) hostObj;
            double hostMips = host.getTotalMips();
            List<Vm> hostVms = hostToVmsMap.getOrDefault(host, new ArrayList<>());
            System.out.println("[DEBUG] Processing Host " + host.getId() + " (index " + hostIndex + ") with " + hostVms.size() + " VMs");
            hostIndex++;
            
            // Calculate host utilization
            Map<Vm, Double> vmUtilizations = new HashMap<>();
            double totalHostUtilization = 0.0;
            int activeVmCount = 0;
            
            // Calculate VM utilization
            for (Vm vm : hostVms) {
                    List<Cloudlet> vmCloudlets = cloudletsForVm(vm);
                    double vmUtilization = 0.0;
                    double vmMips = vm.getMips() * vm.getNumberOfPes();
                    
                    for (Cloudlet c : vmCloudlets) {
                        // Calculate CPU utilization
                        double execTime = c.getActualCPUTime();
                        if (execTime > 0) {
                            double cloudletMips = c.getCloudletLength() / execTime;
                            double contrib = (cloudletMips / vmMips) * (execTime / makespan);
                            vmUtilization += contrib;
                            System.out.println("[DEBUG]   Cloudlet " + c.getCloudletId() + " contribution: " + contrib);
                        }
                    }
                    
                    vmUtilization = Math.min(1.0, vmUtilization);
                    if (vmUtilization > 0) {
                        vmUtilizations.put(vm, vmUtilization);
                        totalHostUtilization += vmUtilization * (vmMips / hostMips);
                        activeVmCount++;
                    }
            }
            
            if (activeVmCount > 0) {
                // Normalize utilization
                totalHostUtilization = Math.min(1.0, totalHostUtilization);
                
                // Apply power model (can be linear or non-linear)
                double idlePower = 162.0;  // Watts at idle
                double busyPower = 215.0;  // Watts at full load
                double alpha = 1.0; // Power scaling factor (1.0 for linear, 1.4 for non-linear)
                
                // Calculate average power
                double avgPower;
                if (totalHostUtilization > 0) {
                    // P = (P_max - P_idle) × U^α + P_idle
                    avgPower = (busyPower - idlePower) * Math.pow(totalHostUtilization, alpha) + idlePower;
                } else {
                    avgPower = 0.0;
                }
                
                // Add VM overhead
                double vmOverhead = 1.0 + (0.02 * activeVmCount);
                avgPower *= vmOverhead;
                
                // Convert to kWh
                double hostEnergy = (avgPower * makespan) / (1000.0 * 3600.0);
                totalEnergy += hostEnergy;
                
                System.out.println("[DEBUG] Host " + host.getId() + " calculations:");
                System.out.println("[DEBUG]   Total host utilization: " + totalHostUtilization);
                System.out.println("[DEBUG]   Active VMs: " + activeVmCount);
                System.out.println("[DEBUG]   Average power: " + avgPower + " W");
                System.out.println("[DEBUG]   Energy consumed: " + hostEnergy + " kWh");
            }
        }
        
        System.out.println("[DEBUG] Total energy consumption: " + totalEnergy + " kWh");
        return totalEnergy;
    }

    // Calculate degree of load imbalance
    public double calculateLoadImbalance() {
        if (vms.isEmpty()) return 0.0;
        
        // Get VM completion times
        Map<Vm, Double> vmCompletionTimes = new HashMap<>();
        for (Vm vm : vms) {
            double completionTime = 0.0;
            for (Cloudlet c : finishedCloudlets) {
                if (c.getGuestId() == vm.getId()) {
                    // Add execution time of each cloudlet on this VM
                    completionTime += c.getCloudletLength() / vm.getMips();
                }
            }
            vmCompletionTimes.put(vm, completionTime);
        }
        
        // If no VM has any load, return 0
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
        
        // Apply the DI formula: (MaxTime - MinTime) / AverageTime
        // Avoid division by zero
        if (averageTime <= 0.0) {
            return 0.0;
        }
        
        return (maxTime - minTime) / averageTime;
    }

    /**
     * Enhanced cost calculation that includes network-related costs.
     * Cost = CPU cost + RAM cost + Storage cost + Bandwidth cost
     * Bandwidth cost is calculated based on data transfer (file sizes)
     */
    public double calculateTotalCost() {
        double totalCost = 0.0;
        
        // Converted to per-unit costs for simulation
        final double CPU_COST_PER_MIPS_HOUR = 0.00001;   // Based on net
        final double RAM_COST_PER_MB_HOUR = 0.000005;    // RAM component
        final double STORAGE_COST_PER_MB_HOUR = 0.000001; // Based on net
        final double BANDWIDTH_COST_PER_MB = 0.00001;    // Data transfer cost
        
        double makespan = calculateMakespan();
        double makespanHours = makespan / 3600.0; // Convert seconds to hours
        
        if (makespanHours <= 0) return 0.0;
        
        // Calculate cost per VM
        for (Vm vm : vms) {
            // CPU cost based on VM MIPS capacity and utilization
            double vmMips = vm.getMips() * vm.getNumberOfPes();
            double cpuCost = vmMips * CPU_COST_PER_MIPS_HOUR * makespanHours;
            
            // RAM cost
            double ramCost = vm.getRam() * RAM_COST_PER_MB_HOUR * makespanHours;
            
            // Storage cost
            double storageCost = vm.getSize() * STORAGE_COST_PER_MB_HOUR * makespanHours;
            
            // Network bandwidth cost based on cloudlet data transfer
            double networkCost = 0.0;
            List<Cloudlet> vmCloudlets = cloudletsForVm(vm);
            
            for (Cloudlet cloudlet : vmCloudlets) {
                // Get file sizes from cloudlet (assuming these are set during cloudlet creation)
                // These should correspond to file_size and output_size from Google Cluster dataset
                double inputFileSize = cloudlet.getCloudletFileSize();    // Input data size in MB
                double outputFileSize = cloudlet.getCloudletOutputSize(); // Output data size in MB
                
                // Total data transfer for this cloudlet
                double dataTransfer = inputFileSize + outputFileSize;
                networkCost += dataTransfer * BANDWIDTH_COST_PER_MB;
            }
            
            // Aggregate costs for this VM
            double vmTotalCost = cpuCost + ramCost + storageCost + networkCost;
            totalCost += vmTotalCost;
        }
        
        return totalCost;
    }
    
    /**
     * Calculates cost efficiency metric (performance per dollar).
     * Higher values indicate better cost efficiency.
     */
    public double calculateCostEfficiency() {
        double totalCost = calculateTotalCost();
        if (totalCost <= 0) return 0.0;
        
        // Performance metric: completed tasks per time unit
        double makespan = calculateMakespan();
        if (makespan <= 0) return 0.0;
        
        double performance = finishedCloudlets.size() / makespan;
        return performance / totalCost;
    }

    private List<SimulationResults.VmUtilization> calculateVmUtilization() {
        List<SimulationResults.VmUtilization> vmUtilizations = new ArrayList<>();
        
        double makespan = calculateMakespan();
        
        for (Vm vm : vms) {
            List<Cloudlet> vmCloudlets = cloudletsForVm(vm);
            
            double totalExecTime = 0.0;
            for (Cloudlet c : vmCloudlets) {
                totalExecTime += c.getActualCPUTime();
            }
            
            double cpuUtilization = 0.0;
            if (!vmCloudlets.isEmpty() && makespan > 0) {
                cpuUtilization = Math.min(100.0, (totalExecTime / makespan) * 100);
            }
            
            vmUtilizations.add(SimulationResults.VmUtilization.builder()
                    .vmId((int) vm.getId())
                    .cpuUtilization(cpuUtilization)
                    .ramUtilization(vm.getRam())
                    .numAPECloudlets(vmCloudlets.size())
                    .build());
        }
        
        return vmUtilizations;
    }


    private List<SimulationResults.SchedulingLogEntry> generateSchedulingLog() {
        List<SimulationResults.SchedulingLogEntry> schedulingLog = new ArrayList<>();
        
        // Add configuration entry
        schedulingLog.add(SimulationResults.SchedulingLogEntry.builder()
                .type("configuration")
                .data(buildConfigEntry())
                .build());
        
        // Add assignment entries
        for (Cloudlet cloudlet : finishedCloudlets) {
            if (cloudlet.getGuestId() >= 0) {
                schedulingLog.add(SimulationResults.SchedulingLogEntry.builder()
                        .type("assignment")
                        .vmId((double) cloudlet.getGuestId())
                        .cloudletId((double) cloudlet.getCloudletId())
                        .submissionTime(0.0) // Default submission time
                        .description(String.format("Cloudlet %d assigned to VM %d at submission time %.2f seconds",
                                cloudlet.getCloudletId(), cloudlet.getGuestId(), 0.0))
                        .build());
            }
        }
        
        return schedulingLog;
    }

    public SimulationResults buildResults() {
        return buildResults(0.0);
    }
    
    /**
     * I need to add overloaded methods to pass metadata
     */
    public SimulationResults buildResults(double fitness) {
        return buildResults(fitness, null, null, null, null);
    }
    
    public SimulationResults buildResults(double fitness, String runId, Long seed, Map<String, Object> configSnapshot, String datasetId) {
        return SimulationResults.builder()
                .runId(runId)
                .seed(seed)
                .configSnapshot(configSnapshot)
                .datasetId(datasetId)
                .summary(SimulationResults.Summary.builder()
                        .responseTime(calculateAverageResponseTime())
                        .makespan(calculateMakespan())
                        .loadBalance(AlgorithmMetricUtils.normalise("loadBalance", calculateLoadImbalance(), finishedCloudlets, vms))
                        .resourceUtilization(calculateResourceUtilization())
                        .totalCost(calculateTotalCost())
                        .costEfficiency(calculateCostEfficiency())
                        .energyConsumption(calculateEnergyConsumption())
                        .fitness(fitness)
                        .build())
                .vmUtilization(calculateVmUtilization())
                .schedulingLog(generateSchedulingLog())
                .energyConsumption(calculateEnergyConsumption())
                .build();
    }

    /**
     * Returns the list of cloudlets executed by the given VM.
     */
    private List<Cloudlet> cloudletsForVm(Vm vm) {
        List<Cloudlet> vmCloudlets = new ArrayList<>();
        for (Cloudlet c : finishedCloudlets) {
            if (c.getGuestId() == vm.getId()) {
                vmCloudlets.add(c);
            }
        }
        return vmCloudlets;
    }

    
    private Map<String, Object> buildConfigEntry() {
        Map<String, Object> configData = new HashMap<>();
        configData.put("optimizationAlgorithm", algorithmName);
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
        return configData;
    }
}
