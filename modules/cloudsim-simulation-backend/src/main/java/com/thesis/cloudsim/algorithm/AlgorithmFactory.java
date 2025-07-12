package com.thesis.cloudsim.algorithm;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Factory class for creating scheduling algorithm instances
 * Provides a centralized way to instantiate and configure algorithms
 */
public class AlgorithmFactory {
    private static final Map<String, Class<? extends ISchedulingAlgorithm>> algorithmRegistry = new HashMap<>();

    static {
        // Register available algorithms
        algorithmRegistry.put("EPSO", EnhancedPSO.class);
        algorithmRegistry.put("EnhancedPSO", EnhancedPSO.class);
        algorithmRegistry.put("EACO", EnhancedACO.class);
        algorithmRegistry.put("EnhancedACO", EnhancedACO.class);
    }

    /**
     * Create an algorithm instance by name
     * @param algorithmName Name of the algorithm
     * @return Algorithm instance
     * @throws IllegalArgumentException if algorithm is not found
     */
    public static ISchedulingAlgorithm createAlgorithm(String algorithmName) {
        Class<? extends ISchedulingAlgorithm> algorithmClass = algorithmRegistry.get(algorithmName);
        if (algorithmClass == null) {
            throw new IllegalArgumentException("Unknown algorithm: " + algorithmName +
                    ". Available algorithms: " + algorithmRegistry.keySet());
        }
        try {
            return algorithmClass.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            throw new RuntimeException("Failed to create algorithm instance: " + algorithmName, e);
        }
    }

    /**
     * Get all available algorithm names
     * @return Set of algorithm names
     */
    public static Set<String> getAvailableAlgorithms() {
        return algorithmRegistry.keySet();
    }

    /**
     * Create algorithm parameters with default values for specific algorithm
     * @param algorithmName Name of the algorithm
     * @return Configured parameters
     */
    public static AlgorithmParameters createDefaultParameters(String algorithmName) {
        AlgorithmParameters params = new AlgorithmParameters();
        switch (algorithmName.toUpperCase()) {
            case "EPSO":
            case "ENHANCEDPSO":
                // PSO-specific tuning
                params.setParameter(AlgorithmParameters.MAX_ITERATIONS, 150);
                params.setParameter(AlgorithmParameters.POPULATION_SIZE, 30);
                params.setParameter(AlgorithmParameters.INERTIA_WEIGHT, 0.9);
                params.setParameter(AlgorithmParameters.COGNITIVE_COEFFICIENT, 1.5);
                params.setParameter(AlgorithmParameters.SOCIAL_COEFFICIENT, 1.5);
                params.setParameter(AlgorithmParameters.MAX_VELOCITY, 6.0);
                params.setParameter(AlgorithmParameters.MIN_VELOCITY, -6.0);
                break;
            case "EACO":
            case "ENHANCEDACO":
                // ACO-specific tuning
                params.setParameter(AlgorithmParameters.MAX_ITERATIONS, 120);
                params.setParameter(AlgorithmParameters.POPULATION_SIZE, 25);
                params.setParameter(AlgorithmParameters.PHEROMONE_DECAY, 0.6);
                params.setParameter(AlgorithmParameters.ALPHA, 1.2);
                params.setParameter(AlgorithmParameters.BETA, 2.5);
                params.setParameter(AlgorithmParameters.INITIAL_PHEROMONE, 0.1);
                params.setParameter(AlgorithmParameters.MIN_PHEROMONE, 0.005);
                params.setParameter(AlgorithmParameters.MAX_PHEROMONE, 2.0);
                break;
            default:
                throw new IllegalArgumentException("Unknown algorithm for default parameters: " + algorithmName);
        }
        // Common multi-objective weights (equal by default)
        params.setParameter(AlgorithmParameters.MAKESPAN_WEIGHT, 0.25);
        params.setParameter(AlgorithmParameters.COST_WEIGHT, 0.25);
        params.setParameter(AlgorithmParameters.ENERGY_WEIGHT, 0.25);
        params.setParameter(AlgorithmParameters.LOAD_BALANCE_WEIGHT, 0.25);
        return params;
    }

    /**
     * Create algorithm parameters for multi-objective optimization
     * @param makespanWeight Weight for makespan objective
     * @param costWeight Weight for cost objective
     * @param energyWeight Weight for energy objective
     * @param loadBalanceWeight Weight for load balance objective
     * @return Configured parameters
     */
    public static AlgorithmParameters createMultiObjectiveParameters(
            double makespanWeight, double costWeight, double energyWeight, double loadBalanceWeight) {
        // Normalize weights
        double totalWeight = makespanWeight + costWeight + energyWeight + loadBalanceWeight;
        if (totalWeight <= 0) {
            throw new IllegalArgumentException("Total weight must be positive");
        }
        AlgorithmParameters params = new AlgorithmParameters();
        params.setParameter(AlgorithmParameters.MAKESPAN_WEIGHT, makespanWeight / totalWeight);
        params.setParameter(AlgorithmParameters.COST_WEIGHT, costWeight / totalWeight);
        params.setParameter(AlgorithmParameters.ENERGY_WEIGHT, energyWeight / totalWeight);
        params.setParameter(AlgorithmParameters.LOAD_BALANCE_WEIGHT, loadBalanceWeight / totalWeight);
        return params;
    }

    /**
     * Validate algorithm parameters
     * @param parameters Parameters to validate
     * @param algorithmName Algorithm name for specific validation
     * @return true if parameters are valid
     */
    public static boolean validateParameters(AlgorithmParameters parameters, String algorithmName) {
        try {
            // Check required common parameters
            if (parameters.getInt(AlgorithmParameters.MAX_ITERATIONS) <= 0) {
                return false;
            }
            if (parameters.getInt(AlgorithmParameters.POPULATION_SIZE) <= 0) {
                return false;
            }
            // Check weight parameters
            double totalWeight = parameters.getDouble(AlgorithmParameters.MAKESPAN_WEIGHT) +
                                 parameters.getDouble(AlgorithmParameters.COST_WEIGHT) +
                                 parameters.getDouble(AlgorithmParameters.ENERGY_WEIGHT) +
                                 parameters.getDouble(AlgorithmParameters.LOAD_BALANCE_WEIGHT);
            if (Math.abs(totalWeight - 1.0) > 1e-6) {
                return false;
            }
            // Algorithm-specific validation
            switch (algorithmName.toUpperCase()) {
                case "EPSO":
                case "ENHANCEDPSO":
                    if (parameters.getDouble(AlgorithmParameters.COGNITIVE_COEFFICIENT) < 0 ||
                        parameters.getDouble(AlgorithmParameters.SOCIAL_COEFFICIENT) < 0) {
                        return false;
                    }
                    break;
                case "EACO":
                case "ENHANCEDACO":
                    if (parameters.getDouble(AlgorithmParameters.ALPHA) < 0 ||
                        parameters.getDouble(AlgorithmParameters.BETA) < 0 ||
                        parameters.getDouble(AlgorithmParameters.PHEROMONE_DECAY) < 0 ||
                        parameters.getDouble(AlgorithmParameters.PHEROMONE_DECAY) > 1) {
                        return false;
                    }
                    break;
            }
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
