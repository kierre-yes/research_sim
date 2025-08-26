package com.thesis.cloudsim.util;

import com.thesis.cloudsim.dto.SimulationRequest;
import java.util.HashMap;
import java.util.Map;

/**
 * I centralize configuration snapshot creation to eliminate duplication
 * across controllers and services (DRY principle)
 */
public class ConfigurationSnapshotUtil {
    
    /**
     * I create a basic snapshot with essential parameters
     * for simple tracking needs
     */
    public static Map<String, Object> createBasicSnapshot(SimulationRequest request) {
        Map<String, Object> snapshot = new HashMap<>();
        snapshot.put("algorithm", request.getOptimizationAlgorithm());
        snapshot.put("numHosts", request.getNumHosts());
        snapshot.put("numVMs", request.getNumVMs());
        snapshot.put("numCloudlets", request.getNumCloudlets());
        snapshot.put("workloadType", request.getWorkloadType());
        snapshot.put("vmScheduler", request.getVmScheduler());
        snapshot.put("iterations", request.getIterations());
        return snapshot;
    }
    
    /**
     * I create a detailed snapshot with all configuration parameters
     * for comprehensive tracking (e.g., comparison service)
     */
    public static Map<String, Object> createDetailedSnapshot(SimulationRequest request) {
        Map<String, Object> snapshot = new HashMap<>();
        
        snapshot.put("optimizationAlgorithm", request.getOptimizationAlgorithm());
        snapshot.put("numHosts", request.getNumHosts());
        snapshot.put("numVMs", request.getNumVMs());
        snapshot.put("numCloudlets", request.getNumCloudlets());
        snapshot.put("workloadType", request.getWorkloadType());
        snapshot.put("vmScheduler", request.getVmScheduler());
        snapshot.put("iterations", request.getIterations());
        snapshot.put("numPesPerHost", request.getNumPesPerHost());
        snapshot.put("peMips", request.getPeMips());
        snapshot.put("ramPerHost", request.getRamPerHost());
        snapshot.put("bwPerHost", request.getBwPerHost());
        snapshot.put("storagePerHost", request.getStoragePerHost());
        snapshot.put("vmMips", request.getVmMips());
        snapshot.put("vmPes", request.getVmPes());
        snapshot.put("vmRam", request.getVmRam());
        snapshot.put("vmBw", request.getVmBw());
        snapshot.put("vmSize", request.getVmSize());
        snapshot.put("useDefaultWorkload", request.isUseDefaultWorkload());
        snapshot.put("useArrivalTimes", request.isUseArrivalTimes());
        
        return snapshot;
    }
    
    /**
     * I create a custom snapshot with selective field inclusion
     * for flexible configuration tracking
     */
    public static Map<String, Object> createCustomSnapshot(
            SimulationRequest request,
            boolean includeHostDetails,
            boolean includeVmDetails) {
        
        Map<String, Object> snapshot = createBasicSnapshot(request);
        
        if (includeHostDetails) {
            snapshot.put("numPesPerHost", request.getNumPesPerHost());
            snapshot.put("peMips", request.getPeMips());
            snapshot.put("ramPerHost", request.getRamPerHost());
            snapshot.put("bwPerHost", request.getBwPerHost());
            snapshot.put("storagePerHost", request.getStoragePerHost());
        }
        
        if (includeVmDetails) {
            snapshot.put("vmMips", request.getVmMips());
            snapshot.put("vmPes", request.getVmPes());
            snapshot.put("vmRam", request.getVmRam());
            snapshot.put("vmBw", request.getVmBw());
            snapshot.put("vmSize", request.getVmSize());
        }
        
        return snapshot;
    }
    
    /**
     * Compute a unique dataset ID from file path
     * Centralized to avoid duplication across services
     */
    public static String computeDatasetId(String path) {
        if (path == null || path.isBlank()) return "RANDOM";
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] bytes = java.nio.file.Files.readAllBytes(java.nio.file.Paths.get(path));
            byte[] hash = md.digest(bytes);
            StringBuilder sb = new StringBuilder();
            for (byte b : hash) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            // Fallback to filename if hashing fails
            return new java.io.File(path).getName();
        }
    }
}
