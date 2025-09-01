package com.thesis.cloudsim.metrics;

import com.thesis.cloudsim.constants.SimulationConstants;
import org.cloudbus.cloudsim.Cloudlet;
import org.cloudbus.cloudsim.Host;
import org.cloudbus.cloudsim.Vm;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * I extracted energy calculation logic from MetricsCalculator to follow SRP.
 * This class has a single responsibility: calculating energy consumption metrics.
 * This separation makes the code more maintainable and testable.
 */
public class EnergyCalculator {
    
    private static final Logger logger = LoggerFactory.getLogger(EnergyCalculator.class);
    
    private final List<Vm> vms;
    private final List<Host> hosts;
    private final List<Cloudlet> finishedCloudlets;
    private final Map<Integer, Integer> vmToHostMapping;
    private final double makespan;
    
    public EnergyCalculator(List<Vm> vms, List<Host> hosts, List<Cloudlet> finishedCloudlets, 
                           Map<Integer, Integer> vmToHostMapping, double makespan) {
        this.vms = vms;
        this.hosts = hosts;
        this.finishedCloudlets = finishedCloudlets;
        this.vmToHostMapping = vmToHostMapping;
        this.makespan = makespan;
    }
    
    /**
     * I calculate total energy consumption across all hosts.
     * This method orchestrates the energy calculation process.
     */
    public double calculateTotalEnergy() {
        if (makespan <= 0) {
            return 0.0;
        }
        
        Map<Host, List<Vm>> hostToVmsMap = buildHostToVmMapping();
        double totalEnergy = 0.0;
        
        for (Host host : hosts) {
            double hostEnergy = calculateHostEnergy(host, hostToVmsMap.get(host));
            totalEnergy += hostEnergy;
        }
        
        logger.debug("Total energy consumption: {} Wh", totalEnergy);
        return totalEnergy;
    }
    
    /**
     * I build the mapping of hosts to their VMs using preserved mappings.
     * This method encapsulates the complex VM-to-host resolution logic.
     */
    private Map<Host, List<Vm>> buildHostToVmMapping() {
        Map<Host, List<Vm>> hostToVmsMap = new HashMap<>();
        
        for (Vm vm : vms) {
            Host host = findHostForVm(vm);
            if (host != null) {
                hostToVmsMap.computeIfAbsent(host, k -> new ArrayList<>()).add(vm);
                logger.debug("VM {} is on Host {}", vm.getId(), host.getId());
            } else {
                logger.debug("VM {} has no host assignment", vm.getId());
            }
        }
        
        return hostToVmsMap;
    }
    
    /**
     * I find the host for a given VM using the mapping or fallback.
     */
    private Host findHostForVm(Vm vm) {
        // I first try the preserved mapping
        if (vmToHostMapping != null && vmToHostMapping.containsKey(vm.getId())) {
            int hostId = vmToHostMapping.get(vm.getId());
            return findHostById(hostId);
        }
        
        // I fallback to vm.getHost() if mapping not available
        if (vm.getHost() != null && vm.getHost() instanceof Host) {
            return (Host) vm.getHost();
        }
        
        return null;
    }
    
    /**
     * I find a host by its ID from the hosts list.
     */
    private Host findHostById(int hostId) {
        for (Host host : hosts) {
            if (host.getId() == hostId) {
                return host;
            }
        }
        return null;
    }
    
    /**
     * I calculate energy consumption for a single host.
     * This method encapsulates the energy model calculations.
     */
    private double calculateHostEnergy(Host host, List<Vm> hostVms) {
        if (hostVms == null || hostVms.isEmpty()) {
            return 0.0;
        }
        
        double hostUtilization = calculateHostUtilization(host, hostVms);
        double avgPower = calculateAveragePower(hostUtilization, hostVms.size());
        
        double hostEnergy = (avgPower * makespan) / 3600.0;
        
        logger.debug("Host {} energy: {} Wh (utilization: {}, power: {} W)", 
                    host.getId(), hostEnergy, hostUtilization, avgPower);
        
        return hostEnergy;
    }
    
    /**
     * I calculate the utilization of a host based on its VMs' workload.
     */
    private double calculateHostUtilization(Host host, List<Vm> hostVms) {
        double hostMips = host.getTotalMips();
        double totalHostUtilization = 0.0;
        
        for (Vm vm : hostVms) {
            double vmUtilization = calculateVmUtilization(vm);
            double vmMips = vm.getMips() * vm.getNumberOfPes();
            totalHostUtilization += vmUtilization * (vmMips / hostMips);
        }
        
        return Math.min(1.0, totalHostUtilization);
    }
    
    /**
     * I calculate the utilization of a single VM.
     */
    private double calculateVmUtilization(Vm vm) {
        double vmMips = vm.getMips() * vm.getNumberOfPes();
        double vmUtilization = 0.0;
        
        for (Cloudlet cloudlet : getCloudletsForVm(vm)) {
            double execTime = cloudlet.getActualCPUTime();
            if (execTime > 0) {
                double cloudletMips = cloudlet.getCloudletLength() / execTime;
                double contribution = (cloudletMips / vmMips) * (execTime / makespan);
                vmUtilization += contribution;
            }
        }
        
        return Math.min(1.0, vmUtilization);
    }
    
    /**
     * I get all cloudlets assigned to a specific VM.
     */
    private List<Cloudlet> getCloudletsForVm(Vm vm) {
        List<Cloudlet> vmCloudlets = new ArrayList<>();
        for (Cloudlet cloudlet : finishedCloudlets) {
            if (cloudlet.getGuestId() == vm.getId()) {
                vmCloudlets.add(cloudlet);
            }
        }
        return vmCloudlets;
    }
    
    /**
     * I calculate average power consumption using the energy model.
     */
    private double calculateAveragePower(double utilization, int activeVmCount) {
        double idlePower = SimulationConstants.EnergyModel.POWER_IDLE_WATTS;
        double busyPower = SimulationConstants.EnergyModel.POWER_MAX_WATTS;
        double alpha = SimulationConstants.EnergyModel.SCALING_FACTOR;
        
        double avgPower;
        if (utilization > 0) {
            // I apply the power model: P = (P_max - P_idle) × U^α + P_idle
            avgPower = (busyPower - idlePower) * Math.pow(utilization, alpha) + idlePower;
        } else {
            avgPower = 0.0;
        }
        
        // I add VM overhead
        double vmOverhead = 1.0 + (0.02 * activeVmCount);
        avgPower *= vmOverhead;
        
        return avgPower;
    }
}