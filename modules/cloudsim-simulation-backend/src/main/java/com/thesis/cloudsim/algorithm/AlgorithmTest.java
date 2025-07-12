package com.thesis.cloudsim.algorithm;

import org.cloudbus.cloudsim.cloudlets.Cloudlet;
import org.cloudbus.cloudsim.cloudlets.CloudletSimple;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.hosts.Host;
import org.cloudbus.cloudsim.hosts.HostSimple;
import org.cloudbus.cloudsim.vms.Vm;
import org.cloudbus.cloudsim.vms.VmSimple;
import org.cloudbus.cloudsim.resources.Pe;
import org.cloudbus.cloudsim.resources.PeSimple;
import org.cloudbus.cloudsim.provisioners.ResourceProvisionerSimple;
import org.cloudbus.cloudsim.schedulers.cloudlet.CloudletSchedulerTimeShared;
import org.cloudbus.cloudsim.schedulers.vm.VmSchedulerTimeShared;
import org.cloudbus.cloudsim.utilizationmodels.UtilizationModelFull;
import org.cloudbus.cloudsim.power.models.PowerModel;
import org.cloudbus.cloudsim.power.models.PowerModelLinear;

import java.util.*;

/**
 * Test class demonstrating the usage of Enhanced PSO and ACO algorithms
 * This class shows how to create, configure, and run the algorithms
 */
public class AlgorithmTest {
    
    public static void main(String[] args) {
        System.out.println("=== Enhanced PSO and ACO Algorithm Test ===");
        
        // Create sample CloudSim entities
        List<Cloudlet> cloudlets = createSampleCloudlets();
        List<Vm> vms = createSampleVMs();
        
        // Test Enhanced PSO
        System.out.println("\n--- Testing Enhanced PSO ---");
        testAlgorithm("EPSO", cloudlets, vms);
        
        // Test Enhanced ACO
        System.out.println("\n--- Testing Enhanced ACO ---");
        testAlgorithm("EACO", cloudlets, vms);
        
        // Test multi-objective parameters
        System.out.println("\n--- Testing Multi-Objective Configuration ---");
        testMultiObjectiveConfiguration(cloudlets, vms);
        
        System.out.println("\n=== Test Complete ===");
    }
    
    private static void testAlgorithm(String algorithmName, List<Cloudlet> cloudlets, List<Vm> vms) {
        try {
            // Create algorithm instance
            ISchedulingAlgorithm algorithm = AlgorithmFactory.createAlgorithm(algorithmName);
            
            // Create parameters
            AlgorithmParameters parameters = AlgorithmFactory.createDefaultParameters(algorithmName);
            
            // Validate parameters
            if (!AlgorithmFactory.validateParameters(parameters, algorithmName)) {
                System.err.println("Invalid parameters for " + algorithmName);
                return;
            }
            
            // Run algorithm
            System.out.println("Running " + algorithm.getAlgorithmName() + " with " + 
                             cloudlets.size() + " cloudlets and " + vms.size() + " VMs");
            
            long startTime = System.currentTimeMillis();
            Map<Cloudlet, Vm> schedule = algorithm.schedule(cloudlets, vms, parameters);
            long endTime = System.currentTimeMillis();
            
            // Display results
            System.out.println("Execution time: " + (endTime - startTime) + " ms");
            System.out.println("Scheduled assignments: " + schedule.size());
            
            // Display metrics
            Map<String, Double> metrics = algorithm.getMetrics();
            System.out.println("Performance metrics:");
            for (Map.Entry<String, Double> entry : metrics.entrySet()) {
                System.out.printf("  %s: %.4f%n", entry.getKey(), entry.getValue());
            }
            
            // Display schedule summary
            displayScheduleSummary(schedule);
            
        } catch (Exception e) {
            System.err.println("Error testing " + algorithmName + ": " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    private static void testMultiObjectiveConfiguration(List<Cloudlet> cloudlets, List<Vm> vms) {
        System.out.println("Testing different objective weight configurations:");
        
        // Test different weight configurations
        double[][] weightConfigs = {
            {1.0, 0.0, 0.0, 0.0}, // Makespan only
            {0.0, 1.0, 0.0, 0.0}, // Cost only
            {0.0, 0.0, 1.0, 0.0}, // Energy only
            {0.0, 0.0, 0.0, 1.0}, // Load balance only
            {0.25, 0.25, 0.25, 0.25}, // Balanced
            {0.4, 0.3, 0.2, 0.1}  // Makespan-focused
        };
        
        String[] configNames = {
            "Makespan-only", "Cost-only", "Energy-only", "Load-balance-only", "Balanced", "Makespan-focused"
        };
        
        for (int i = 0; i < weightConfigs.length; i++) {
            System.out.println("\nConfiguration: " + configNames[i]);
            
            try {
                AlgorithmParameters params = AlgorithmFactory.createMultiObjectiveParameters(
                    weightConfigs[i][0], weightConfigs[i][1], weightConfigs[i][2], weightConfigs[i][3]);
                
                // Test with PSO
                ISchedulingAlgorithm pso = AlgorithmFactory.createAlgorithm("EPSO");
                Map<Cloudlet, Vm> schedule = pso.schedule(cloudlets, vms, params);
                
                Map<String, Double> metrics = pso.getMetrics();
                System.out.printf("  PSO Results - Makespan: %.4f, Cost: %.4f, Energy: %.4f, Load Balance: %.4f%n",
                    metrics.get("makespan"), metrics.get("cost"), metrics.get("energy"), metrics.get("loadBalance"));
                
            } catch (Exception e) {
                System.err.println("  Error in configuration " + configNames[i] + ": " + e.getMessage());
            }
        }
    }
    
    private static void displayScheduleSummary(Map<Cloudlet, Vm> schedule) {
        Map<Vm, Integer> vmAssignments = new HashMap<>();
        
        for (Vm vm : schedule.values()) {
            vmAssignments.put(vm, vmAssignments.getOrDefault(vm, 0) + 1);
        }
        
        System.out.println("VM assignment summary:");
        for (Map.Entry<Vm, Integer> entry : vmAssignments.entrySet()) {
            System.out.printf("  VM %d: %d cloudlets%n", entry.getKey().getId(), entry.getValue());
        }
    }
    
    private static List<Cloudlet> createSampleCloudlets() {
        List<Cloudlet> cloudlets = new ArrayList<>();
        
        // Create 20 sample cloudlets with varying characteristics
        for (int i = 0; i < 20; i++) {
            long length = 1000 + (long)(Math.random() * 9000); // 1K to 10K MI
            long fileSize = 100 + (long)(Math.random() * 900); // 100 to 1000 bytes
            long outputSize = 50 + (long)(Math.random() * 450); // 50 to 500 bytes
            
            Cloudlet cloudlet = new CloudletSimple(i, length, 1);
            cloudlet.setFileSize(fileSize);
            cloudlet.setOutputSize(outputSize);
            cloudlet.setUtilizationModel(new UtilizationModelFull());
            
            cloudlets.add(cloudlet);
        }
        
        return cloudlets;
    }
    
    private static List<Vm> createSampleVMs() {
        List<Vm> vms = new ArrayList<>();
        
        // Create 5 sample VMs with different capacities
        for (int i = 0; i < 5; i++) {
            long mips = 1000 + (long)(Math.random() * 2000); // 1000 to 3000 MIPS
            long ram = 512 + (long)(Math.random() * 1536); // 512 to 2048 MB
            long storage = 1000 + (long)(Math.random() * 9000); // 1GB to 10GB
            
            Vm vm = new VmSimple(i, mips, 1);
            vm.setRam(ram);
            vm.setBw(100); // 100 Mbps bandwidth
            vm.setSize(storage);
            vm.setCloudletScheduler(new CloudletSchedulerTimeShared());
            
            // Create a simple host for the VM
            Host host = createSampleHost(i);
            vm.setHost(host);
            
            vms.add(vm);
        }
        
        return vms;
    }
    
    private static Host createSampleHost(int id) {
        List<Pe> peList = new ArrayList<>();
        peList.add(new PeSimple(1000 + (long)(Math.random() * 2000))); // 1000 to 3000 MIPS
        
        Host host = new HostSimple(4096, 10000, 100000, peList);
        host.setId(id);
        host.setRamProvisioner(new ResourceProvisionerSimple());
        host.setBwProvisioner(new ResourceProvisionerSimple());
        host.setVmScheduler(new VmSchedulerTimeShared());
        
        // Set a simple power model
        PowerModel powerModel = new PowerModelLinear(100, 0.7); // 100W max, 70% min
        host.setPowerModel(powerModel);
        
        return host;
    }
}
