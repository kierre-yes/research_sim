package com.thesis.cloudsim.algorithm;

/**
 * 
 * I implement the Factory pattern here so that algorithm creation is centralized
 * and the rest of the codebase doesn't need to know about specific algorithm classes
 */
public class AlgorithmFactory {
    
    /**
     * Creates an algorithm instance based on the given name
     * Supports aliases for flexibility (EPSO/EnhancedPSO, EACO/EnhancedACO)
     */
    public static ISchedulingAlgorithm createAlgorithm(String algorithmName) {
        if (algorithmName == null) {
            throw new IllegalArgumentException("Algorithm name must not be null");
        }
        
        return switch (algorithmName.toUpperCase()) {
            case "EPSO", "ENHANCEDPSO" -> new EnhancedPSO();
            case "EACO", "ENHANCEDACO" -> new EnhancedACO();
            // baseline
            case "BPSO", "BASELINEPSO" -> new BaselinePSO();
            case "BACO", "BASELINEACO" -> new BaselineACO();
            default -> throw new IllegalArgumentException("Unknown algorithm: " + algorithmName);
        };
    }


    /**
     * Creates default parameters for the specified algorithm
     * Parameters are optimized based on experimental results
     */
    public static AlgorithmParameters createDefaultParameters(String algorithmName) {
        if (algorithmName == null) {
            throw new IllegalArgumentException("Algorithm name must not be null");
        }
        
        AlgorithmParameters params = new AlgorithmParameters();
        
        switch (algorithmName.toUpperCase()) {
            case "EPSO", "ENHANCEDPSO" -> {
                params.setParameter(AlgorithmParameters.MAX_ITERATIONS, 100);
                params.setParameter(AlgorithmParameters.POPULATION_SIZE, 30);
                // Adaptive inertia weight (0.9 to 0.4) for broad exploration then convergence
                params.setParameter(AlgorithmParameters.INERTIA_WEIGHT, 0.9);
                params.setParameter(AlgorithmParameters.INERTIA_WEIGHT_MAX, 0.9);
                params.setParameter(AlgorithmParameters.INERTIA_WEIGHT_MIN, 0.4);
                // Balanced exploration between personal and global best
                params.setParameter(AlgorithmParameters.COGNITIVE_COEFFICIENT, 1.5);
                params.setParameter(AlgorithmParameters.SOCIAL_COEFFICIENT, 1.5);
                params.setParameter(AlgorithmParameters.MAX_VELOCITY, 6.0);
                params.setParameter(AlgorithmParameters.MIN_VELOCITY, -6.0);
                params.setParameter(AlgorithmParameters.MAX_VELOCITY_INITIAL, 6.0);
                params.setParameter(AlgorithmParameters.MAX_VELOCITY_FINAL, 1.0);
                params.setParameter(AlgorithmParameters.ENABLE_EARLY_STOPPING, true);
            }
            case "EACO", "ENHANCEDACO" -> {
                params.setParameter(AlgorithmParameters.MAX_ITERATIONS, 100);
                params.setParameter(AlgorithmParameters.POPULATION_SIZE, 30);
                // Pheromone persistence vs exploration balance
                params.setParameter(AlgorithmParameters.PHEROMONE_DECAY, 0.6);
                // Heuristic (beta) has more influence than pheromone (alpha)
                params.setParameter(AlgorithmParameters.ALPHA, 1.2);
                params.setParameter(AlgorithmParameters.BETA, 2.5);
                // Pheromone bounds to prevent premature convergence
                params.setParameter(AlgorithmParameters.INITIAL_PHEROMONE, 0.1);
                params.setParameter(AlgorithmParameters.MIN_PHEROMONE, 0.005);
                params.setParameter(AlgorithmParameters.MAX_PHEROMONE, 2.0);
                // Adaptive evaporation rates
                params.setParameter(AlgorithmParameters.EVAPORATION_MIN, 0.1);
                params.setParameter(AlgorithmParameters.EVAPORATION_MAX, 0.9);
                params.setParameter(AlgorithmParameters.ENABLE_EARLY_STOPPING, true);
            }
            case "BPSO", "BASELINEPSO" -> {
                params.setParameter(AlgorithmParameters.MAX_ITERATIONS, 100);
                params.setParameter(AlgorithmParameters.POPULATION_SIZE, 30);
                params.setParameter(AlgorithmParameters.INERTIA_WEIGHT_MAX, 0.9);
                params.setParameter(AlgorithmParameters.INERTIA_WEIGHT_MIN, 0.4);
                params.setParameter(AlgorithmParameters.COGNITIVE_COEFFICIENT, 2.0);
                params.setParameter(AlgorithmParameters.SOCIAL_COEFFICIENT, 2.0);
            }
            case "BACO", "BASELINEACO" -> {
                params.setParameter(AlgorithmParameters.MAX_ITERATIONS, 100);
                params.setParameter(AlgorithmParameters.POPULATION_SIZE, 30);
                params.setParameter(AlgorithmParameters.ALPHA, 1.0);
                params.setParameter(AlgorithmParameters.BETA, 5.0);
                params.setParameter(AlgorithmParameters.EVAPORATION_MIN, 0.02);
            }
            default -> throw new IllegalArgumentException("Unknown algorithm: " + algorithmName);
        }
        
        // Equal weights for all objectives by default
        params.setParameter(AlgorithmParameters.MAKESPAN_WEIGHT, 0.25);
        params.setParameter(AlgorithmParameters.COST_WEIGHT, 0.25);
        params.setParameter(AlgorithmParameters.ENERGY_WEIGHT, 0.25);
        params.setParameter(AlgorithmParameters.LOAD_BALANCE_WEIGHT, 0.25);
        return params;
    }

    /**
     * Creates parameters with custom weights for multi-objective optimization
     * uses raw weights to preserve user preferences without forced normalization
     */
    public static AlgorithmParameters createMultiObjectiveParameters(
            double makespanWeight, double costWeight, double energyWeight, double loadBalanceWeight) {
        
        // Validate at least one weight is positive
        if (makespanWeight < 0 || costWeight < 0 || energyWeight < 0 || loadBalanceWeight < 0) {
            throw new IllegalArgumentException("Weights must be non-negative");
        }
        
        if (makespanWeight + costWeight + energyWeight + loadBalanceWeight <= 0) {
            throw new IllegalArgumentException("At least one weight must be positive");
        }
        
        AlgorithmParameters params = new AlgorithmParameters();
        // If user wants makespan=100 and others=1, respect that strong preference
        params.setParameter(AlgorithmParameters.MAKESPAN_WEIGHT, makespanWeight);
        params.setParameter(AlgorithmParameters.COST_WEIGHT, costWeight);
        params.setParameter(AlgorithmParameters.ENERGY_WEIGHT, energyWeight);
        params.setParameter(AlgorithmParameters.LOAD_BALANCE_WEIGHT, loadBalanceWeight);
        return params;
    }

}
