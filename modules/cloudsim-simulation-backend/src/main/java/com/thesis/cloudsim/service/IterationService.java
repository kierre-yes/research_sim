package com.thesis.cloudsim.service;

import com.thesis.cloudsim.algorithm.ISchedulingAlgorithm;
import com.thesis.cloudsim.dto.IterationResults;
import com.thesis.cloudsim.dto.SimulationRequest;
import com.thesis.cloudsim.metrics.SimulationResults;
import com.thesis.cloudsim.simulation.EnhancedSimulationManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

@Service
public class IterationService {
    
    private static final Logger logger = LoggerFactory.getLogger(IterationService.class);
    private final ExecutorService executorService = Executors.newFixedThreadPool(4);
    
    public IterationResults runIterations(ISchedulingAlgorithm algorithm, SimulationRequest request) {
        long startTime = System.currentTimeMillis();
        int iterations = Math.max(1, Math.min(100, request.getIterations())); // only to hundred
        
        logger.info("Starting {} iterations for {} algorithm", iterations, request.getOptimizationAlgorithm());
        
        List<SimulationResults> results = new ArrayList<>();
        List<CompletableFuture<SimulationResults>> futures = new ArrayList<>();
        
        // Run simulations in parallel (max 4 at a time)
        for (int i = 0; i < iterations; i++) {
            final int iteration = i;
            CompletableFuture<SimulationResults> future = CompletableFuture.supplyAsync(() -> {
                try {
                    logger.debug("Running iteration {} of {}", iteration + 1, iterations);
                    EnhancedSimulationManager manager = new EnhancedSimulationManager(algorithm, request);
                    return manager.run();
                } catch (Exception e) {
                    logger.error("Error in iteration {}: {}", iteration + 1, e.getMessage());
                    return null;
                }
            }, executorService);
            futures.add(future);
        }
        
        // Wait 
        CompletableFuture<Void> allFutures = CompletableFuture.allOf(
            futures.toArray(new CompletableFuture[0])
        );
        
        try {
            allFutures.join();
            
            // Collect 
            for (CompletableFuture<SimulationResults> future : futures) {
                SimulationResults result = future.get();
                if (result != null) {
                    results.add(result);
                }
            }
        } catch (Exception e) {
            logger.error("Error waiting for iterations to complete", e);
        }
        
        long totalExecutionTime = System.currentTimeMillis() - startTime;
        double successRate = (double) results.size() / iterations * 100;
        
        logger.info("Completed {} successful iterations out of {} ({}% success rate) in {} ms", 
                   results.size(), iterations, String.format("%.1f", successRate), totalExecutionTime);
        
        // Calculate 
        IterationResults iterationResults = new IterationResults();
        iterationResults.setTotalIterations(iterations);
        iterationResults.setAlgorithm(request.getOptimizationAlgorithm());
        iterationResults.setIndividualResults(results);
        iterationResults.setTotalExecutionTime(totalExecutionTime);
        iterationResults.setSuccessRate(successRate);
        
        if (!results.isEmpty()) {
            calculateStatistics(iterationResults, results);
        }

        iterationResults.setRunId(java.util.UUID.randomUUID().toString());
        iterationResults.setSeed(request.getSeed());
        iterationResults.setConfigSnapshot(buildConfigSnapshot(request));
        iterationResults.setDatasetId(computeDatasetId(request.getWorkloadPath()));
        
        return iterationResults;
    }
    
    private void calculateStatistics(IterationResults iterationResults, List<SimulationResults> results) {
        Map<String, List<Double>> metricsMap = new HashMap<>();
        
        for (SimulationResults result : results) {
            SimulationResults.Summary summary = result.getSummary();
            if (summary != null) {
                metricsMap.computeIfAbsent("makespan", k -> new ArrayList<>()).add(summary.getMakespan());
                metricsMap.computeIfAbsent("energyConsumption", k -> new ArrayList<>()).add(summary.getEnergyConsumption());
                metricsMap.computeIfAbsent("loadBalance", k -> new ArrayList<>()).add(summary.getLoadBalance());
                metricsMap.computeIfAbsent("resourceUtilization", k -> new ArrayList<>()).add(summary.getResourceUtilization());
                metricsMap.computeIfAbsent("responseTime", k -> new ArrayList<>()).add(summary.getResponseTime());
                metricsMap.computeIfAbsent("fitness", k -> new ArrayList<>()).add(summary.getFitness());
                metricsMap.computeIfAbsent("totalCost", k -> new ArrayList<>()).add(summary.getTotalCost());
                metricsMap.computeIfAbsent("costEfficiency", k -> new ArrayList<>()).add(summary.getCostEfficiency());
            }
        }
        
        // calculate 
        Map<String, Double> avgMetrics = new HashMap<>();
        Map<String, Double> minMetrics = new HashMap<>();
        Map<String, Double> maxMetrics = new HashMap<>();
        Map<String, Double> stdDevMetrics = new HashMap<>();
        
        for (Map.Entry<String, List<Double>> entry : metricsMap.entrySet()) {
            String metricName = entry.getKey();
            List<Double> values = entry.getValue();
            
            if (!values.isEmpty()) {
                // avg
                double avg = values.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
                avgMetrics.put(metricName, avg);
                
                // min
                double min = values.stream().mapToDouble(Double::doubleValue).min().orElse(0.0);
                minMetrics.put(metricName, min);
                
                // max
                double max = values.stream().mapToDouble(Double::doubleValue).max().orElse(0.0);
                maxMetrics.put(metricName, max);
                
                // standard deviation
                double variance = values.stream()
                    .mapToDouble(val -> Math.pow(val - avg, 2))
                    .average()
                    .orElse(0.0);
                double stdDev = Math.sqrt(variance);
                stdDevMetrics.put(metricName, stdDev);
            }
        }
        
        iterationResults.setAverageMetrics(avgMetrics);
        iterationResults.setMinMetrics(minMetrics);
        iterationResults.setMaxMetrics(maxMetrics);
        iterationResults.setStdDevMetrics(stdDevMetrics);
    }
    
    public void shutdown() {
        executorService.shutdown();
    }

    private Map<String, Object> buildConfigSnapshot(SimulationRequest r) {
        Map<String, Object> m = new HashMap<>();
        m.put("optimizationAlgorithm", r.getOptimizationAlgorithm());
        m.put("numHosts", r.getNumHosts());
        m.put("numVMs", r.getNumVMs());
        m.put("numPesPerHost", r.getNumPesPerHost());
        m.put("peMips", r.getPeMips());
        m.put("ramPerHost", r.getRamPerHost());
        m.put("bwPerHost", r.getBwPerHost());
        m.put("storagePerHost", r.getStoragePerHost());
        m.put("vmMips", r.getVmMips());
        m.put("vmPes", r.getVmPes());
        m.put("vmRam", r.getVmRam());
        m.put("vmBw", r.getVmBw());
        m.put("vmSize", r.getVmSize());
        m.put("vmScheduler", r.getVmScheduler());
        m.put("numCloudlets", r.getNumCloudlets());
        m.put("workloadType", r.getWorkloadType());
        m.put("useDefaultWorkload", r.isUseDefaultWorkload());
        m.put("useArrivalTimes", r.isUseArrivalTimes());
        m.put("iterations", r.getIterations());
        return m;
    }

    private String computeDatasetId(String path) {
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
