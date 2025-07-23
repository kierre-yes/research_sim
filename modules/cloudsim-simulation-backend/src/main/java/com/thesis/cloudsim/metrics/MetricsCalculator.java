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

import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;

/**
 * Calculates various performance metrics from simulation results
 * 
 * @author Kier M.
 * 
 * Cost model includes compute, storage, and network components
 * 
 * TODO: Add SLA violation metrics (optional)
 * TODO: Implement carbon footprint calculation
 */
public class MetricsCalculator {

    private final List<Vm> vms;
    private final Datacenter datacenter;
    private final List<Cloudlet> finishedCloudlets;
    private final String algorithmName;
    
    public MetricsCalculator(List<Vm> vms, Datacenter datacenter, List<Cloudlet> finishedCloudlets) {
        this.vms = vms;
        this.datacenter = datacenter;
        this.finishedCloudlets = finishedCloudlets;
        this.algorithmName = "Unknown";
    }
    
    public MetricsCalculator(List<Vm> vms, Datacenter datacenter, List<Cloudlet> finishedCloudlets, String algorithmName) {
        this.vms = vms;
        this.datacenter = datacenter;
        this.finishedCloudlets = finishedCloudlets;
        this.algorithmName = algorithmName;
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
        
        // Return 0 if no valid cloudlets
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
        if (vms.isEmpty()) return 0.0;
        
        // FIXME: This is a simplified calculation - should use actual runtime data
        // For now using default utilization from constants
        double totalUtilization = 0.0;
        for (Vm vm : vms) {
            double vmMips = vm.getMips() * vm.getNumberOfPes();
            // Assume default utilization for demo purposes
            totalUtilization += (vmMips > 0) ? SimulationConstants.DEFAULT_VM_UTILIZATION * 100 : 0.0;
        }
        return totalUtilization / vms.size();
    }

    public double calculateEnergyConsumption() {
        // Enhanced energy model based on dynamic VM utilization and realistic power profiles
        double totalEnergy = 0.0;
        double makespan = calculateMakespan();
        
        if (makespan <= 0) {
            System.out.println("[DEBUG] Energy calc: makespan is 0 or negative");
            return 0.0;
        }
        
        System.out.println("[DEBUG] Energy calc: makespan = " + makespan + " seconds");
        System.out.println("[DEBUG] Energy calc: number of hosts = " + datacenter.getHostList().size());
        
        // Since VM.getHost() is null after simulation, we'll use a different approach
        // Calculate based on actual cloudlet execution assuming round-robin VM-to-host mapping
        int hostsCount = datacenter.getHostList().size();
        if (hostsCount == 0) return 0.0;
        
        // Map VMs to hosts using round-robin (same as VmAllocationPolicySimple)
        Map<Integer, List<Vm>> hostToVmsMap = new HashMap<>();
        for (int i = 0; i < vms.size(); i++) {
            int hostIndex = i % hostsCount;
            hostToVmsMap.computeIfAbsent(hostIndex, k -> new ArrayList<>()).add(vms.get(i));
        }
        
        int hostIndex = 0;
        for (Object hostObj : datacenter.getHostList()) {
            Host host = (Host) hostObj;
            double hostMips = host.getTotalMips();
            List<Vm> hostVms = hostToVmsMap.getOrDefault(hostIndex, new ArrayList<>());
            
            // Track time-based utilization for more accurate energy calculation
            Map<Vm, Double> vmUtilizations = new HashMap<>();
            double totalHostUtilization = 0.0;
            int activeVmCount = 0;
            
            // Calculate per-VM utilization based on actual execution time
            for (Vm vm : hostVms) {
                    List<Cloudlet> vmCloudlets = cloudletsForVm(vm);
                    double vmUtilization = 0.0;
                    double vmMips = vm.getMips() * vm.getNumberOfPes();
                    
                    for (Cloudlet c : vmCloudlets) {
                        // Calculate actual CPU utilization based on cloudlet execution
                        double execTime = c.getActualCPUTime();
                        double cloudletMips = c.getCloudletLength() / execTime;
                        vmUtilization += (cloudletMips / vmMips) * (execTime / makespan);
                    }
                    
                    vmUtilization = Math.min(1.0, vmUtilization);
                    if (vmUtilization > 0) {
                        vmUtilizations.put(vm, vmUtilization);
                        totalHostUtilization += vmUtilization * (vmMips / hostMips);
                        activeVmCount++;
                    }
            }
            
            if (activeVmCount > 0) {
                // Normalize host utilization
                totalHostUtilization = Math.min(1.0, totalHostUtilization);
                
                // Power model based on real server measurements
                // Dell PowerEdge R740 specs (from our lab)
                double idlePower = 86.7;  // Watts at idle
                double maxPower = 247.0;  // Watts at 100% load
                
                // Non-linear power model from Fan et al. (2007)
                // P(u) = Pidle + (Pmax - Pidle) * (2u - u^r)
                double r = 1.4;  // Calibrated from our measurements
                double powerFactor = 2 * totalHostUtilization - Math.pow(totalHostUtilization, r);
                double avgPower = idlePower + (maxPower - idlePower) * powerFactor;
                
                // Account for power state transitions and VM overhead
                double vmOverhead = 1.0 + (0.02 * activeVmCount); // 2% overhead per VM
                avgPower *= vmOverhead;
                
                // Energy = Power * Time (convert to kWh)
                double hostEnergy = (avgPower * makespan) / (1000.0 * 3600.0);
                totalEnergy += hostEnergy;
                
                System.out.println("[DEBUG] Host " + hostIndex + " energy: " + hostEnergy + " kWh");
            }
            hostIndex++;
        }
        
        return totalEnergy; // Returns energy in kWh
    }

    /**
     * Calculate load imbalance using standard deviation of VM loads
     * Lower values indicate better load distribution
     */
    public double calculateLoadImbalance() {
        if (vms.isEmpty()) return 0.0;
        
        List<Double> loads = new ArrayList<>();
        for (Vm vm : vms) {
            double load = 0.0;
            for (Cloudlet c : finishedCloudlets) {
                if (c.getGuestId() == vm.getId()) {
                    load += c.getCloudletLength() / vm.getMips();
                }
            }
            loads.add(load);
        }
        
        // Calculate standard deviation
        double mean = mean(loads);
        double varianceSum = 0.0;
        for (double l : loads) {
            varianceSum += (l - mean) * (l - mean);
        }
        
        // Return coefficient of variation for normalized metric
        double stdDev = Math.sqrt(varianceSum / loads.size());
        return mean > 0 ? stdDev / mean : 0.0;
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

    /**
     * Helper to compute mean of a List<Double> using simple loop.
     */
    private double mean(List<Double> values) {
        if (values.isEmpty()) return 0.0;
        double sum = 0.0;
        for (double v : values) {
            sum += v;
        }
        return sum / values.size();
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
    
    public SimulationResults buildResults(double fitness) {
        return SimulationResults.builder()
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
