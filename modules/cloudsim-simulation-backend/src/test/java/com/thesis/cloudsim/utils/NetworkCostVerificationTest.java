package com.thesis.cloudsim.utils;

import com.thesis.cloudsim.algorithm.AlgorithmMetricUtils;
import org.cloudbus.cloudsim.Cloudlet;
import org.cloudbus.cloudsim.Vm;
import org.cloudbus.cloudsim.CloudletSchedulerTimeShared;
import org.cloudbus.cloudsim.UtilizationModelFull;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class NetworkCostVerificationTest {
    
    public static void main(String[] args) {
        System.out.println("=== Network Cost Verification Test ===\n");
        
        // Create a sample VM
        Vm vm = new Vm(0, 0, 1000, 2, 2048, 1000, 10000, "Xen", new CloudletSchedulerTimeShared());
        
        // List of VMs
        List<Vm> vms = Arrays.asList(vm);
        
        // Define test cases
        List<Cloudlet> cloudlets1 = Arrays.asList(
            new Cloudlet(1, 10000, 1, 0, 0,
                new UtilizationModelFull(), new UtilizationModelFull(), new UtilizationModelFull())
        );
        cloudlets1.get(0).setVmId(vm.getId());
        
        List<Cloudlet> cloudlets2 = Arrays.asList(
            new Cloudlet(2, 10000, 1, 100*1024*1024, 50*1024*1024,
                new UtilizationModelFull(), new UtilizationModelFull(), new UtilizationModelFull())
        );
        cloudlets2.get(0).setVmId(vm.getId());
        
        List<Cloudlet> cloudlets3 = Arrays.asList(
            new Cloudlet(3, 10000, 1, 1024*1024*1024, 500*1024*1024,
                new UtilizationModelFull(), new UtilizationModelFull(), new UtilizationModelFull())
        );
        cloudlets3.get(0).setVmId(vm.getId());
        
        // Utility to compute and print cost for a given set of cloudlets
        computeAndPrintCost("Case 1: No network data", cloudlets1, vms, vm);
        computeAndPrintCost("Case 2: 100MB file, 50MB output", cloudlets2, vms, vm);
        computeAndPrintCost("Case 3: 1GB file, 500MB output", cloudlets3, vms, vm);
        
        // Test Case 4: Normalized dataset values
        double normalizedFileSize = 0.3;
        double normalizedOutputSize = 0.15;
        long fileSize = (long)(normalizedFileSize * 1024 * 1024 * 1024);
        long outputSize = (long)(normalizedOutputSize * 1024 * 1024 * 1024);
        Cloudlet cloudlet4 = new Cloudlet(4, 10000, 1, fileSize, outputSize,
                new UtilizationModelFull(), new UtilizationModelFull(), new UtilizationModelFull());
        cloudlet4.setVmId(vm.getId());
        List<Cloudlet> cloudlets4 = Arrays.asList(cloudlet4);
        computeAndPrintCost("Case 4: Normalized (0.3, 0.15)", cloudlets4, vms, vm);
        
        System.out.println("\n=== Test Complete ===");
    }
    
    private static void computeAndPrintCost(
            String label,
            List<Cloudlet> cloudlets,
            List<Vm> vms,
            Vm vm) {
        // Build schedule: map each cloudlet to its VM
        Map<Cloudlet, Vm> schedule = new HashMap<>();
        for (Cloudlet c : cloudlets) {
            schedule.put(c, vm);
        }
        
        double cost = AlgorithmMetricUtils.enhancedCost(schedule, cloudlets, vms);
        System.out.printf("%s%nTotal Cost: $%.4f%n%n", label, cost);
    }
}
