package com.thesis.cloudsim.algorithm;

// Factory class for creating scheduling algorithm instances
public class AlgorithmFactory {

    // Creates an algorithm instance based on the given name
    // Supports: EPSO/EnhancedPSO, EACO/EnhancedACO
    public static ISchedulingAlgorithm createAlgorithm(String algorithmName) {
        if (algorithmName == null) {
            throw new IllegalArgumentException("Algorithm name must not be null");
        }

        switch (algorithmName.toUpperCase()) {
            case "EPSO":
            case "ENHANCEDPSO":
                return new EnhancedPSO();
            case "EACO":
            case "ENHANCEDACO":
                return new EnhancedACO();
            default:
                throw new IllegalArgumentException("Unknown algorithm: " + algorithmName);
        }
    }


    // Creates default parameters for the specified algorithm
    public static AlgorithmParameters createDefaultParameters(String algorithmName) {
        AlgorithmParameters params = new AlgorithmParameters();
        switch (algorithmName.toUpperCase()) {
            case "EPSO":
            case "ENHANCEDPSO":
                // PSO parameters
                params.setParameter(AlgorithmParameters.MAX_ITERATIONS, 150);
                params.setParameter(AlgorithmParameters.POPULATION_SIZE, 30);
                params.setParameter(AlgorithmParameters.INERTIA_WEIGHT, 0.9);
                params.setParameter(AlgorithmParameters.INERTIA_WEIGHT_MAX, 0.9);
                params.setParameter(AlgorithmParameters.INERTIA_WEIGHT_MIN, 0.4);
                params.setParameter(AlgorithmParameters.COGNITIVE_COEFFICIENT, 1.5);
                params.setParameter(AlgorithmParameters.SOCIAL_COEFFICIENT, 1.5);
                params.setParameter(AlgorithmParameters.MAX_VELOCITY, 6.0);
                params.setParameter(AlgorithmParameters.MIN_VELOCITY, -6.0);
                params.setParameter(AlgorithmParameters.MAX_VELOCITY_INITIAL, 6.0);
                params.setParameter(AlgorithmParameters.MAX_VELOCITY_FINAL, 1.0);
                break;
            case "EACO":
            case "ENHANCEDACO":
                // ACO parameters
                params.setParameter(AlgorithmParameters.MAX_ITERATIONS, 120);
                params.setParameter(AlgorithmParameters.POPULATION_SIZE, 25);
                params.setParameter(AlgorithmParameters.PHEROMONE_DECAY, 0.6);
                params.setParameter(AlgorithmParameters.ALPHA, 1.2);
                params.setParameter(AlgorithmParameters.BETA, 2.5);
                params.setParameter(AlgorithmParameters.INITIAL_PHEROMONE, 0.1);
                params.setParameter(AlgorithmParameters.MIN_PHEROMONE, 0.005);
                params.setParameter(AlgorithmParameters.MAX_PHEROMONE, 2.0);
                params.setParameter(AlgorithmParameters.EVAPORATION_MIN, 0.1);
                params.setParameter(AlgorithmParameters.EVAPORATION_MAX, 0.9);
                break;
            default:
                throw new IllegalArgumentException("Unknown algorithm for default parameters: " + algorithmName);
        }
        // Set equal weights for all objectives
        params.setParameter(AlgorithmParameters.MAKESPAN_WEIGHT, 0.25);
        params.setParameter(AlgorithmParameters.COST_WEIGHT, 0.25);
        params.setParameter(AlgorithmParameters.ENERGY_WEIGHT, 0.25);
        params.setParameter(AlgorithmParameters.LOAD_BALANCE_WEIGHT, 0.25);
        return params;
    }

    // Creates parameters with custom weights for multi-objective optimization
    public static AlgorithmParameters createMultiObjectiveParameters(
            double makespanWeight, double costWeight, double energyWeight, double loadBalanceWeight) {
        // Normalize weights to sum to 1
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

}
