package com.thesis.cloudsim.service;

import com.thesis.cloudsim.algorithm.ISchedulingAlgorithm;
import com.thesis.cloudsim.dto.IterationResults;
import com.thesis.cloudsim.dto.SimulationRequest;
import com.thesis.cloudsim.metrics.SimulationResults;
import com.thesis.cloudsim.simulation.EnhancedSimulationManager;
import com.thesis.cloudsim.util.SimulationProgressHolder;
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
    private final ExecutorService executorService = Executors.newFixedThreadPool(8);
    private static volatile boolean isCancelled = false;
    
    public IterationResults runIterations(ISchedulingAlgorithm algorithm, SimulationRequest request) {
        isCancelled = false;
        
        long startTime = System.currentTimeMillis();
        int iterations = Math.max(1, Math.min(100, request.getIterations())); 
        
        String algorithmName = request.getOptimizationAlgorithm();
        
        //since iter resets it, use the current in progressholder
        if (iterations > 1) {
            SimulationProgressHolder.setCurrentIteration(0, iterations, algorithmName + " - Initializing");
        }
        
        logger.info("Starting {} iterations for {} algorithm", iterations, algorithmName);
        
        List<SimulationResults> results = new ArrayList<>();
        List<CompletableFuture<SimulationResults>> futures = new ArrayList<>();
        
        for (int i = 0; i < iterations; i++) {
            // check cancellation before starting new iteration
            if (isCancelled) {
                logger.info("Iterations cancelled before iteration {}", i + 1);
                break;
            }
            
            if (iterations > 1) {
                SimulationProgressHolder.setCurrentIteration(i + 1, iterations, algorithmName + " - Running");
            }
            
            final int iteration = i;
            CompletableFuture<SimulationResults> future = CompletableFuture.supplyAsync(() -> {
                try {
                    if (isCancelled) {
                        logger.debug("Iteration {} cancelled", iteration + 1);
                        return null;
                    }
                    
                    logger.debug("Running iteration {} of {}", iteration + 1, iterations);
                    EnhancedSimulationManager manager = new EnhancedSimulationManager(algorithm, request);
                    return manager.run();
                } catch (Exception e) {
                    if (isCancelled) {
                        logger.debug("Iteration {} cancelled during execution", iteration + 1);
                        return null;
                    }
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
            
            // Check for cancellation before collecting results
            if (isCancelled) {
                logger.info("Iterations cancelled, cancelling remaining futures");
                for (CompletableFuture<SimulationResults> future : futures) {
                    future.cancel(true);
                }
                SimulationProgressHolder.reset(); // Reset progress on cancellation
                throw new RuntimeException("Iterations cancelled by user");
            }
            
            // Collect 
            for (CompletableFuture<SimulationResults> future : futures) {
                if (isCancelled) break; // Final check during collection
                
                SimulationResults result = future.get();
                if (result != null) {
                    results.add(result);
                }
            }
        } catch (Exception e) {
            if (isCancelled) {
                logger.info("Iterations cancelled during execution");
                SimulationProgressHolder.reset(); // Reset progress on cancellation
                throw new RuntimeException("Iterations cancelled by user");
            }
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
        
        
        if (iterations > 1) {
            SimulationProgressHolder.setStage(algorithmName + " - Completed");
        }
        
        return iterationResults;
    }
    
    
    public IterationResults runIterationsWithOffset(ISchedulingAlgorithm algorithm, SimulationRequest request, 
                                                   int iterationOffset, int totalIterations) {
        isCancelled = false;
        
        long startTime = System.currentTimeMillis();
        int iterations = Math.max(1, Math.min(100, request.getIterations())); // only to hundred
        
        String algorithmName = request.getOptimizationAlgorithm();
        logger.info("Starting {} iterations for {} algorithm (offset: {})", iterations, algorithmName, iterationOffset);
        
        List<SimulationResults> results = new ArrayList<>();
        List<CompletableFuture<SimulationResults>> futures = new ArrayList<>();
        
        for (int i = 0; i < iterations; i++) {
            if (isCancelled) {
                logger.info("Iterations cancelled before iteration {}", i + 1);
                break;
            }
            
            int globalIteration = iterationOffset + i + 1;
            SimulationProgressHolder.setCurrentIteration(globalIteration, totalIterations, algorithmName + " - Running");
            
            final int iteration = i;
            CompletableFuture<SimulationResults> future = CompletableFuture.supplyAsync(() -> {
                try {
                    if (isCancelled) {
                        logger.debug("Iteration {} cancelled", iteration + 1);
                        return null;
                    }
                    
                    logger.debug("Running iteration {} of {} (global: {} of {})", iteration + 1, iterations, globalIteration, totalIterations);
                    EnhancedSimulationManager manager = new EnhancedSimulationManager(algorithm, request);
                    return manager.run();
                } catch (Exception e) {
                    if (isCancelled) {
                        logger.debug("Iteration {} cancelled during execution", iteration + 1);
                        return null;
                    }
                    logger.error("Error in iteration {}: {}", iteration + 1, e.getMessage());
                    return null;
                }
            }, executorService);
            futures.add(future);
        }
        
        CompletableFuture<Void> allFutures = CompletableFuture.allOf(
            futures.toArray(new CompletableFuture[0])
        );
        
        try {
            allFutures.join();
            
            if (isCancelled) {
                logger.info("Iterations cancelled, cancelling remaining futures");
                for (CompletableFuture<SimulationResults> future : futures) {
                    future.cancel(true);
                }
                throw new RuntimeException("Iterations cancelled by user");
            }
            
            for (CompletableFuture<SimulationResults> future : futures) {
                if (isCancelled) break; 
                
                SimulationResults result = future.get();
                if (result != null) {
                    results.add(result);
                }
            }
        } catch (Exception e) {
            if (isCancelled) {
                logger.info("Iterations cancelled during execution");
                throw new RuntimeException("Iterations cancelled by user");
            }
            logger.error("Error waiting for iterations to complete", e);
        }
        
        long totalExecutionTime = System.currentTimeMillis() - startTime;
        double successRate = (double) results.size() / iterations * 100;
        
        logger.info("Completed {} successful iterations out of {} ({}% success rate) in {} ms", 
                   results.size(), iterations, String.format("%.1f", successRate), totalExecutionTime);
        
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
        
        SimulationProgressHolder.setStage(algorithmName + " - Completed");
        java.util.concurrent.CompletableFuture.runAsync(() -> {
            try {
                Thread.sleep(2000); 
                SimulationProgressHolder.reset();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        
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
                metricsMap.computeIfAbsent("loadImbalance", k -> new ArrayList<>()).add(summary.getLoadImbalance());
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
    
    /**
     * Request cancellation of ongoing iterations
     */
    public static void requestCancellation() {
        isCancelled = true;
        logger.info("Cancellation requested for IterationService");
    }

    private Map<String, Object> buildConfigSnapshot(SimulationRequest r) {
        // Use centralized utility to avoid duplication
        return com.thesis.cloudsim.util.ConfigurationSnapshotUtil.createDetailedSnapshot(r);
    }

    private String computeDatasetId(String path) {
        // Delegate to utility class to avoid duplication
        return com.thesis.cloudsim.util.ConfigurationSnapshotUtil.computeDatasetId(path);
    }
}
