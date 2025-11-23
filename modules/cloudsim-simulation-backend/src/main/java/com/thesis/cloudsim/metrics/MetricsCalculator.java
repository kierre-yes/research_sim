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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MetricsCalculator {

    private static final Logger logger = LoggerFactory.getLogger(MetricsCalculator.class);

    private final List<Vm> vms;
    private final Datacenter datacenter;
    private final List<Cloudlet> finishedCloudlets;
    private final String algorithmName;
    private final Map<Integer, Integer> vmToHostMapping; // VM ID to Host ID mapping
    private final Map<String, Double> algorithmMetrics;

    public MetricsCalculator(List<Vm> vms, Datacenter datacenter, List<Cloudlet> finishedCloudlets) {
        this.vms = vms;
        this.datacenter = datacenter;
        this.finishedCloudlets = finishedCloudlets;
        this.algorithmName = "Unknown";
        this.vmToHostMapping = null;
        this.algorithmMetrics = null;
    }

    public MetricsCalculator(List<Vm> vms, Datacenter datacenter, List<Cloudlet> finishedCloudlets,
            String algorithmName) {
        this.vms = vms;
        this.datacenter = datacenter;
        this.finishedCloudlets = finishedCloudlets;
        this.algorithmName = algorithmName;
        this.vmToHostMapping = null;
        this.algorithmMetrics = null;
    }

    public MetricsCalculator(List<Vm> vms, Datacenter datacenter, List<Cloudlet> finishedCloudlets,
            String algorithmName, Map<Integer, Integer> vmToHostMapping) {
        this.vms = vms;
        this.datacenter = datacenter;
        this.finishedCloudlets = finishedCloudlets;
        this.algorithmName = algorithmName;
        this.vmToHostMapping = vmToHostMapping;
        this.algorithmMetrics = null;
    }

    /*
     * I add a private constructor for the Builder pattern to use.
     * allows cleaner construction while keeping existing constructors.
     */
    private MetricsCalculator(Builder builder) {
        this.vms = builder.vms;
        this.datacenter = builder.datacenter;
        this.finishedCloudlets = builder.finishedCloudlets;
        this.algorithmName = builder.algorithmName;
        this.vmToHostMapping = builder.vmToHostMapping;
        this.algorithmMetrics = builder.algorithmMetrics != null ? new HashMap<>(builder.algorithmMetrics) : null;
    }

    /*
     * I implement the Builder pattern to provide a cleaner way to construct
     * MetricsCalculator instances with optional parameters saves for overload
     * constructors i guess when i revise again.
     */
    public static class Builder {
        // Required
        private final List<Vm> vms;
        private final Datacenter datacenter;
        private final List<Cloudlet> finishedCloudlets;

        // Optional
        private String algorithmName = "Unknown";
        private Map<Integer, Integer> vmToHostMapping = null;
        private Map<String, Double> algorithmMetrics = null;

        public Builder(List<Vm> vms, Datacenter datacenter, List<Cloudlet> finishedCloudlets) {
            this.vms = vms;
            this.datacenter = datacenter;
            this.finishedCloudlets = finishedCloudlets;
        }

        public Builder withAlgorithmName(String algorithmName) {
            this.algorithmName = algorithmName;
            return this;
        }

        public Builder withVmToHostMapping(Map<Integer, Integer> vmToHostMapping) {
            this.vmToHostMapping = vmToHostMapping;
            return this;
        }

        public Builder withAlgorithmMetrics(Map<String, Double> algorithmMetrics) {
            this.algorithmMetrics = algorithmMetrics;
            return this;
        }

        public MetricsCalculator build() {
            return new MetricsCalculator(this);
        }
    }

    public double calculateAverageResponseTime() {
        if (finishedCloudlets.isEmpty())
            return 0.0;

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
        if (finishedCloudlets.isEmpty())
            return 0.0;
        double maxFinishTime = -1.0;
        for (Cloudlet cloudlet : finishedCloudlets) {
            double finishTime = cloudlet.getExecFinishTime();
            if (finishTime > maxFinishTime) {
                maxFinishTime = finishTime;
            }
        }
        return maxFinishTime;
    }

    /*
     * I extracted the resource utilization calculation into a separate method
     * to improve readability. The logic remains the same but is now more modular.
     */
    public double calculateResourceUtilization() {
        if (vms.isEmpty() || datacenter == null) {
            return 0.0;
        }

        double makespan = calculateMakespan();
        if (makespan <= 0) {
            return 0.0;
        }

        Map<Host, List<Vm>> hostToVmsMap = buildHostToVmMapping();
        return calculateAverageHostUtilization(hostToVmsMap, makespan);
    }

    /*
     * I extracted host-to-VM mapping logic to reduce duplication.
     * This method is now reusable across different calculations.
     */
    private Map<Host, List<Vm>> buildHostToVmMapping() {
        Map<Host, List<Vm>> hostToVmsMap = new HashMap<>();

        for (Vm vm : vms) {
            Host host = findHostForVm(vm);
            if (host != null) {
                hostToVmsMap.computeIfAbsent(host, k -> new ArrayList<>()).add(vm);
            }
        }

        return hostToVmsMap;
    }

    /*
     * I extracted host finding logic to a separate method for clarity.
     */
    private Host findHostForVm(Vm vm) {
        // I first try the preserved mapping
        if (vmToHostMapping != null && vmToHostMapping.containsKey(vm.getId())) {
            int hostId = vmToHostMapping.get(vm.getId());
            for (Object hostObj : datacenter.getHostList()) {
                Host h = (Host) hostObj;
                if (h.getId() == hostId) {
                    return h;
                }
            }
        }

        // I fallback to vm.getHost() if mapping not available
        if (vm.getHost() != null && vm.getHost() instanceof Host) {
            return (Host) vm.getHost();
        }

        return null;
    }

    /*
     * I extracted the utilization calculation logic for better modularity.
     */
    private double calculateAverageHostUtilization(Map<Host, List<Vm>> hostToVmsMap, double makespan) {
        double totalUtilization = 0.0;
        int activeHosts = 0;

        for (Object hostObj : datacenter.getHostList()) {
            Host host = (Host) hostObj;
            List<Vm> hostVms = hostToVmsMap.getOrDefault(host, new ArrayList<>());

            if (hostVms.isEmpty()) {
                continue; // Skip hosts without VMs
            }

            activeHosts++;
            double hostUtilization = calculateSingleHostUtilization(host, hostVms, makespan);
            totalUtilization += hostUtilization;
        }

        if (activeHosts == 0) {
            return 0.0;
        }

        return (totalUtilization / activeHosts) * 100.0;
    }

    /*
     * I extracted single host utilization calculation for clarity.
     */
    private double calculateSingleHostUtilization(Host host, List<Vm> hostVms, double makespan) {
        double hostMips = host.getTotalMips();
        double hostUtilization = 0.0;

        for (Vm vm : hostVms) {
            double vmMips = vm.getMips() * vm.getNumberOfPes();
            double vmUtilization = calculateVmUtilization(vm, makespan);
            hostUtilization += vmUtilization * (vmMips / hostMips);
        }

        return hostUtilization;
    }

    /*
     * I extracted VM utilization calculation to reduce nesting.
     */
    private double calculateVmUtilization(Vm vm, double makespan) {
        List<Cloudlet> vmCloudlets = cloudletsForVm(vm);
        double vmMips = vm.getMips() * vm.getNumberOfPes();
        double vmUtilization = 0.0;

        for (Cloudlet c : vmCloudlets) {
            double execTime = c.getActualCPUTime();
            if (execTime > 0) {
                double cloudletMips = c.getCloudletLength() / execTime;
                double contribution = (cloudletMips / vmMips) * (execTime / makespan);
                vmUtilization += contribution;
            }
        }

        return vmUtilization;
    }

    /*
     * I refactored energy calculation to use the new EnergyCalculator class.
     * This follows SRP - MetricsCalculator now delegates energy calculations
     * to a specialized class, reducing its responsibilities and complexity.
     * The original method was 123 lines; now it's just 10 lines.
     */
    public double calculateEnergyConsumption() {
        double makespan = calculateMakespan();
        if (makespan <= 0) {
            return 0.0;
        }

        List<Host> hosts = new ArrayList<>();
        for (Object hostObj : datacenter.getHostList()) {
            hosts.add((Host) hostObj);
        }

        EnergyCalculator energyCalculator = new EnergyCalculator(
                vms, hosts, finishedCloudlets, vmToHostMapping, makespan);

        return energyCalculator.calculateTotalEnergy();
    }

    // Calculate degree of load imbalance
    public double calculateLoadImbalance() {
        if (vms.isEmpty())
            return 0.0;

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

        /*
         * I use centralized cost constants from SimulationConstants.CostModel
         * instead of hard-coding them here. This makes the cost model more
         * maintainable and easier to adjust for different pricing scenarios.
         */
        final double CPU_COST_PER_MIPS_HOUR = SimulationConstants.CostModel.CPU_COST_PER_MIPS_HOUR;
        final double RAM_COST_PER_MB_HOUR = SimulationConstants.CostModel.RAM_COST_PER_MB_HOUR;
        final double STORAGE_COST_PER_MB_HOUR = SimulationConstants.CostModel.STORAGE_COST_PER_MB_HOUR;
        final double BANDWIDTH_COST_PER_MB = SimulationConstants.CostModel.BANDWIDTH_COST_PER_MB;

        double makespan = calculateMakespan();
        double makespanHours = makespan / 3600.0; // Convert seconds to hours

        if (makespanHours <= 0)
            return 0.0;

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
                // Get file sizes from cloudlet (assuming these are set during cloudlet
                // creation)
                // These should correspond to file_size and output_size from Google Cluster
                // dataset
                double inputFileSize = cloudlet.getCloudletFileSize(); // Input data size in MB
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
        if (totalCost <= 0)
            return 0.0;

        // Performance metric: completed tasks per time unit
        double makespan = calculateMakespan();
        if (makespan <= 0)
            return 0.0;

        double performance = finishedCloudlets.size() / makespan;
        return performance / totalCost;
    }

    private List<SimulationResults.VmUtilization> calculateVmUtilization() {
        List<SimulationResults.VmUtilization> vmUtilizations = new ArrayList<>();

        double makespan = calculateMakespan();

        for (Vm vm : vms) {
            List<Cloudlet> vmCloudlets = cloudletsForVm(vm);
            double vmMips = vm.getMips() * vm.getNumberOfPes();
            double vmUtilization = 0.0;

            for (Cloudlet c : vmCloudlets) {
                double execTime = c.getActualCPUTime();
                if (execTime > 0) {
                    double cloudletMips = c.getCloudletLength() / execTime;
                    double contribution = (cloudletMips / vmMips) * (execTime / makespan);
                    vmUtilization += contribution;
                }
            }

            double cpuUtilizationPercent = Math.min(100.0, vmUtilization * 100.0);

            vmUtilizations.add(SimulationResults.VmUtilization.builder()
                    .vmId((int) vm.getId())
                    .cpuUtilization(cpuUtilizationPercent)
                    .ramUtilization(vm.getRam())
                    .numAPECloudlets(vmCloudlets.size())
                    .build());
        }

        return vmUtilizations;
    }

    private List<SimulationResults.SchedulingLogEntry> generateSchedulingLog() {
        List<SimulationResults.SchedulingLogEntry> schedulingLog = new ArrayList<>();
        String algoDescription = buildAlgorithmDescription();

        // Add configuration entry
        schedulingLog.add(SimulationResults.SchedulingLogEntry.builder()
                .type("configuration")
                .data(buildConfigEntry())
                .description("System configuration initialized")
                .algoDescription(algoDescription)
                .build());

        Map<Long, Integer> vmTaskCount = new HashMap<>();
        Map<Long, Double> vmUtilization = new HashMap<>();

        for (Cloudlet cloudlet : finishedCloudlets) {
            if (cloudlet.getGuestId() >= 0) {
                long vmId = cloudlet.getGuestId();

                // Assignment event
                schedulingLog.add(SimulationResults.SchedulingLogEntry.builder()
                        .type("assignment")
                        .vmId((double) vmId)
                        .cloudletId((double) cloudlet.getCloudletId())
                        .submissionTime(cloudlet.getSubmissionTime())
                        .description(String.format("Cloudlet %d assigned to VM %d",
                                cloudlet.getCloudletId(), vmId))
                        .build());

                schedulingLog.add(SimulationResults.SchedulingLogEntry.builder()
                        .type("start")
                        .vmId((double) vmId)
                        .cloudletId((double) cloudlet.getCloudletId())
                        .submissionTime(cloudlet.getExecStartTime())
                        .description(String.format("Cloudlet %d started execution on VM %d",
                                cloudlet.getCloudletId(), vmId))
                        .build());

                // Task complete event
                schedulingLog.add(SimulationResults.SchedulingLogEntry.builder()
                        .type("complete")
                        .vmId((double) vmId)
                        .cloudletId((double) cloudlet.getCloudletId())
                        .submissionTime(cloudlet.getFinishTime())
                        .description(String.format("Cloudlet %d completed on VM %d (execution time: %.2f seconds)",
                                cloudlet.getCloudletId(), vmId, cloudlet.getActualCPUTime()))
                        .build());

                // Track VM utilization
                vmTaskCount.put(vmId, vmTaskCount.getOrDefault(vmId, 0) + 1);
                vmUtilization.put(vmId, vmUtilization.getOrDefault(vmId, 0.0) + cloudlet.getActualCPUTime());
            }
        }

        double avgTasksPerVm = finishedCloudlets.size() / (double) vms.size();
        double makespan = calculateMakespan();

        for (Vm vm : vms) {
            long vmId = vm.getId();
            int taskCount = vmTaskCount.getOrDefault(vmId, 0);
            double totalExecTime = vmUtilization.getOrDefault(vmId, 0.0);

            double cpuUtilization = 0.0;
            if (makespan > 0) {
                // Calculate correct utilization based on MIPS
                double vmMips = vm.getMips() * vm.getNumberOfPes();
                double vmUtilizationRatio = 0.0;

                List<Cloudlet> vmCloudlets = cloudletsForVm(vm);
                for (Cloudlet c : vmCloudlets) {
                    double execTime = c.getActualCPUTime();
                    if (execTime > 0) {
                        double cloudletMips = c.getCloudletLength() / execTime;
                        double contribution = (cloudletMips / vmMips) * (execTime / makespan);
                        vmUtilizationRatio += contribution;
                    }
                }
                cpuUtilization = Math.min(100.0, vmUtilizationRatio * 100.0);
            }

            boolean taskOverload = taskCount > avgTasksPerVm * 1.5;
            boolean cpuOverload = cpuUtilization > 90.0;

            if (taskOverload || cpuOverload) {
                String overloadReason;
                if (taskOverload && cpuOverload) {
                    overloadReason = String.format("VM %d overloaded: %d tasks (avg: %.1f), CPU: %.1f%%",
                            vmId, taskCount, avgTasksPerVm, cpuUtilization);
                } else if (taskOverload) {
                    overloadReason = String.format("VM %d task overloaded: %d tasks (avg: %.1f), CPU: %.1f%%",
                            vmId, taskCount, avgTasksPerVm, cpuUtilization);
                } else {
                    overloadReason = String.format("VM %d CPU overloaded: %.1f%% utilization, %d tasks",
                            vmId, cpuUtilization, taskCount);
                }

                schedulingLog.add(SimulationResults.SchedulingLogEntry.builder()
                        .type("overload")
                        .vmId((double) vmId)
                        .submissionTime(calculateMakespan() * 0.5) // Approximate midpoint
                        .description(overloadReason)
                        .build());
            }
        }

        schedulingLog.sort((a, b) -> {
            if (a.getSubmissionTime() == null && b.getSubmissionTime() == null)
                return 0;
            if (a.getSubmissionTime() == null)
                return -1;
            if (b.getSubmissionTime() == null)
                return 1;
            return Double.compare(a.getSubmissionTime(), b.getSubmissionTime());
        });

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

    public SimulationResults buildResults(double fitness, String runId, Long seed, Map<String, Object> configSnapshot,
            String datasetId) {
        double rawLoadImbalance = calculateLoadImbalance();
        double energyWh = calculateEnergyConsumption();

        return SimulationResults.builder()
                .runId(runId)
                .seed(seed)
                .configSnapshot(configSnapshot)
                .datasetId(datasetId)
                .summary(SimulationResults.Summary.builder()
                        .responseTime(calculateAverageResponseTime())
                        .makespan(calculateMakespan())
                        .loadBalance(
                                AlgorithmMetricUtils.normalise("loadBalance", rawLoadImbalance, finishedCloudlets, vms))
                        .loadImbalance(rawLoadImbalance)
                        .resourceUtilization(calculateResourceUtilization())
                        .totalCost(calculateTotalCost())
                        .costEfficiency(calculateCostEfficiency())
                        .energyConsumption(energyWh)
                        .fitness(fitness)
                        .build())
                .vmUtilization(calculateVmUtilization())
                .schedulingLog(generateSchedulingLog())
                .energyConsumption(energyWh)
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

    private String buildAlgorithmDescription() {
        String base;
        if (algorithmName == null) {
            base = "Task-to-VM mapping generated by the configured scheduling algorithm.";
        } else {
            String upper = algorithmName.toUpperCase();
            if (upper.contains("EPSO")) {
                base = "EPSO used non-linear inertia weight reduction and adaptive velocity limits to balance exploration and exploitation in this mapping.";
            } else if (upper.contains("EACO")) {
                base = "EACO used adaptive pheromone evaporation and heuristic load reinforcement to favor well-balanced VMs in this mapping.";
            } else {
                base = "Task-to-VM mapping generated by scheduling algorithm: " + algorithmName;
            }
        }

        if (algorithmMetrics == null || algorithmMetrics.isEmpty()) {
            return base;
        }

        String upperName = algorithmName != null ? algorithmName.toUpperCase() : "";
        StringBuilder sb = new StringBuilder(base);

        if (upperName.contains("EPSO")) {
            Double inertia = algorithmMetrics.get("inertiaWeight");
            Double vMax = algorithmMetrics.get("velocityLimit");
            Double clampedRatio = algorithmMetrics.get("velocityClampedRatio");
            StringBuilder details = new StringBuilder();
            if (inertia != null) {
                details.append(String.format("inertiaWeight=%.4f", inertia));
            }
            if (vMax != null) {
                if (details.length() > 0) {
                    details.append(", ");
                }
                details.append(String.format("maxVelocity=%.4f", vMax));
            }
            if (clampedRatio != null) {
                if (details.length() > 0) {
                    details.append(", ");
                }
                details.append(String.format("velocityClampedRatio=%.4f", clampedRatio));
            }
            if (details.length() > 0) {
                sb.append(" In this run, ").append(details).append(".");
            }
        } else if (upperName.contains("EACO")) {
            Double evaporation = algorithmMetrics.get("evaporationRate");
            Double convergence = algorithmMetrics.get("pheromoneConvergence");
            StringBuilder details = new StringBuilder();
            if (evaporation != null) {
                details.append(String.format("evaporationRate=%.4f", evaporation));
            }
            if (convergence != null) {
                if (details.length() > 0) {
                    details.append(", ");
                }
                details.append(String.format("pheromoneConvergence=%.6f", convergence));
            }
            if (details.length() > 0) {
                sb.append(" In this run, ").append(details).append(".");
            }
        }

        return sb.toString();
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
