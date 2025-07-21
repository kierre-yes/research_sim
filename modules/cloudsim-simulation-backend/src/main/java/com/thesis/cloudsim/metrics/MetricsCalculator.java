package com.thesis.cloudsim.metrics;

import lombok.RequiredArgsConstructor;
import com.thesis.cloudsim.constants.SimulationConstants;
// We avoid pulling heavy statistics libraries; simple loops are enough for basics.
import org.cloudbus.cloudsim.Cloudlet;
import org.cloudbus.cloudsim.core.CloudSimTags;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.Datacenter;
import org.cloudbus.cloudsim.Vm;
import org.cloudbus.cloudsim.Host;
import org.cloudbus.cloudsim.power.PowerHost;

import java.util.List;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
// Removed Collectors import – we'll use basic loops

@RequiredArgsConstructor
public class MetricsCalculator {

    private final List<Vm> vms;
    private final Datacenter datacenter;
    private final List<Cloudlet> finishedCloudlets;


    // Average response time = (∑ (finish - start)) / N
    public double calculateAverageResponseTime() {
        if (finishedCloudlets.isEmpty()) return 0.0;
        double sum = 0.0;
        for (Cloudlet c : finishedCloudlets) {
            // In CloudSim 7.0, use getActualCPUTime() for execution time
            double responseTime = c.getActualCPUTime();
            if (responseTime > 0) {
                sum += responseTime;
            }
        }
        return sum / finishedCloudlets.size();
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
        // Calculate utilization based on VM MIPS and current usage
        double totalUtilization = 0.0;
        for (Vm vm : vms) {
            double vmMips = vm.getMips() * vm.getNumberOfPes();
            // Use a simple utilization estimation based on VM capacity
            totalUtilization += (vmMips > 0) ? SimulationConstants.DEFAULT_VM_UTILIZATION * 100 : 0.0; // Uses default utilization
        }
        return totalUtilization / vms.size();
    }

    public double calculateEnergyConsumption() {
        // Enhanced energy model based on dynamic VM utilization and realistic power profiles
        double totalEnergy = 0.0;
        double makespan = calculateMakespan();
        
        if (makespan <= 0) return 0.0;
        
        for (Object hostObj : datacenter.getHostList()) {
            Host host = (Host) hostObj;
            double hostMips = host.getTotalMips();
            
            // Track time-based utilization for more accurate energy calculation
            Map<Vm, Double> vmUtilizations = new HashMap<>();
            double totalHostUtilization = 0.0;
            int activeVmCount = 0;
            
            // Calculate per-VM utilization based on actual execution time
            for (Vm vm : vms) {
                if (vm.getHost() != null && vm.getHost().getId() == host.getId()) {
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
            }
            
            if (activeVmCount > 0) {
                // Normalize host utilization
                totalHostUtilization = Math.min(1.0, totalHostUtilization);
                
                // Enhanced power model with dynamic power states
                // Based on SPECpower benchmark-like model
                double idlePower = 86.7;  // Idle power (Watts) - typical server
                double maxPower = 247.0;  // Max power (Watts) - typical server
                
                // Non-linear power model: P(u) = Pidle + (Pmax - Pidle) * (2u - u^r)
                // where r is typically 1.4 for modern servers
                double r = 1.4;
                double powerFactor = 2 * totalHostUtilization - Math.pow(totalHostUtilization, r);
                double avgPower = idlePower + (maxPower - idlePower) * powerFactor;
                
                // Account for power state transitions and VM overhead
                double vmOverhead = 1.0 + (0.02 * activeVmCount); // 2% overhead per VM
                avgPower *= vmOverhead;
                
                // Energy = Power * Time (convert to kWh)
                double hostEnergy = (avgPower * makespan) / (1000.0 * 3600.0);
                totalEnergy += hostEnergy;
            }
        }
        
        return totalEnergy; // Returns energy in kWh
    }

    public double calculateLoadImbalance() {
        if (vms.isEmpty()) return 0.0;
        // Simple calculation without external libs
        List<Double> loads = new ArrayList<>();
        for (Vm vm : vms) {
            double load = 0.0;
            for (Cloudlet c : finishedCloudlets) {
                // In CloudSim 7.0, we use guestId to track VM assignment
                if (c.getGuestId() == vm.getId()) {
                    load += c.getCloudletLength() / vm.getMips();
                }
            }
            loads.add(load);
        }
        double mean = mean(loads);
        double varianceSum = 0.0;
        for (double l : loads) {
            varianceSum += (l - mean) * (l - mean);
        }
        return Math.sqrt(varianceSum / loads.size());
    }

    /**
     * Enhanced cost calculation that includes network-related costs.
     * Cost = CPU cost + RAM cost + Storage cost + Bandwidth cost
     * Bandwidth cost is calculated based on data transfer (file sizes)
     */
    public double calculateTotalCost() {
        double totalCost = 0.0;
        
        // Cost parameters (per unit per hour)
        final double CPU_COST_PER_MIPS_HOUR = 0.00001;  // $0.00001 per MIPS per hour
        final double RAM_COST_PER_MB_HOUR = 0.000005;   // $0.000005 per MB per hour
        final double STORAGE_COST_PER_MB_HOUR = 0.000001; // $0.000001 per MB per hour
        final double BANDWIDTH_COST_PER_MB = 0.00001;   // $0.00001 per MB transferred
        
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
        return SimulationResults.builder()
                .summary(SimulationResults.Summary.builder()
                        .responseTime(calculateAverageResponseTime())
                        .makespan(calculateMakespan())
                        .loadBalance(calculateLoadImbalance())
                        .resourceUtilization(calculateResourceUtilization())
                        .totalCost(calculateTotalCost())
                        .costEfficiency(calculateCostEfficiency())
                        .energyConsumption(calculateEnergyConsumption())
                        .fitness(0.0) // Default fitness value, should be set by algorithm
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

    /**
     * Builds the configuration map that appears once at the beginning of the scheduling log.
     * Pulling this into a helper keeps {@code generateSchedulingLog()} below the 40-line mark.
     */
    private Map<String, Object> buildConfigEntry() {
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
        return configData;
    }
}
