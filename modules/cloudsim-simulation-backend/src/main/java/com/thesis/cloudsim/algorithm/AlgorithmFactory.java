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
                // EPSO parameters - needs more exploration time
                params.setParameter(AlgorithmParameters.MAX_ITERATIONS, 150);
                params.setParameter(AlgorithmParameters.POPULATION_SIZE, 30);
                // Adaptive inertia weight (0.9 to 0.4) for broad exploration then convergence
                params.setParameter(AlgorithmParameters.INERTIA_WEIGHT, 0.9);
                params.setParameter(AlgorithmParameters.INERTIA_WEIGHT_MAX, 0.9);
                params.setParameter(AlgorithmParameters.INERTIA_WEIGHT_MIN, 0.4);
                // Balanced exploration between personal and global best
                params.setParameter(AlgorithmParameters.COGNITIVE_COEFFICIENT, 1.5);
                params.setParameter(AlgorithmParameters.SOCIAL_COEFFICIENT, 1.5);
                // Adaptive velocity limits for finer adjustments near convergence
                params.setParameter(AlgorithmParameters.MAX_VELOCITY, 6.0);
                params.setParameter(AlgorithmParameters.MIN_VELOCITY, -6.0);
                params.setParameter(AlgorithmParameters.MAX_VELOCITY_INITIAL, 6.0);
                params.setParameter(AlgorithmParameters.MAX_VELOCITY_FINAL, 1.0);
            }
            case "EACO", "ENHANCEDACO" -> {
                // ACO parameters - faster convergence due to pheromone trails
                params.setParameter(AlgorithmParameters.MAX_ITERATIONS, 120);
                params.setParameter(AlgorithmParameters.POPULATION_SIZE, 25);
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
     * Weights are normalized to sum to 1.0 for comparable fitness values
     */
    public static AlgorithmParameters createMultiObjectiveParameters(
            double makespanWeight, double costWeight, double energyWeight, double loadBalanceWeight) {
        
        double totalWeight = makespanWeight + costWeight + energyWeight + loadBalanceWeight;
        if (totalWeight <= 0) {
            throw new IllegalArgumentException("Total weight must be positive");
        }
        
        AlgorithmParameters params = new AlgorithmParameters();
        // Normalize weights to sum to 1.0
        params.setParameter(AlgorithmParameters.MAKESPAN_WEIGHT, makespanWeight / totalWeight);
        params.setParameter(AlgorithmParameters.COST_WEIGHT, costWeight / totalWeight);
        params.setParameter(AlgorithmParameters.ENERGY_WEIGHT, energyWeight / totalWeight);
        params.setParameter(AlgorithmParameters.LOAD_BALANCE_WEIGHT, loadBalanceWeight / totalWeight);
        return params;
    }

}
