package com.thesis.cloudsim.algorithm;

/**
 * 
 * I implement the Factory pattern here so that algorithm creation is centralized
 * and the rest of the codebase doesn't need to know about specific algorithm classes
 */
public class AlgorithmFactory {
    
    /**
     * 
     * I use an enum with aliases so that users can specify "EPSO" or "EnhancedPSO"
     * and both will work, improving API flexibility
     */
    public enum AlgorithmType {
        EPSO("EPSO", "ENHANCEDPSO"),
        EACO("EACO", "ENHANCEDACO");
        
        private final String[] aliases;
        
        AlgorithmType(String... aliases) {
            this.aliases = aliases;
        }
        
        public static AlgorithmType fromString(String name) {
            // I validate null early so that we get a clear error message instead of NPE
            if (name == null) {
                throw new IllegalArgumentException("Algorithm name must not be null");
            }
            
            // I convert to uppercase so that algorithm names are case-insensitive
            String upperName = name.toUpperCase();
            for (AlgorithmType type : values()) {
                for (String alias : type.aliases) {
                    if (alias.equals(upperName)) {
                        return type;
                    }
                }
            }
            throw new IllegalArgumentException("Unknown algorithm: " + name);
        }
    }

    /**
     * this creates an algorithm instance based on the given name
     * 
     * I use the enum validation here so that invalid algorithm names are caught early
     * with descriptive error messages rather than returning null or failing silently
     */
    public static ISchedulingAlgorithm createAlgorithm(String algorithmName) {
        AlgorithmType type = AlgorithmType.fromString(algorithmName);
        
        switch (type) {
            case EPSO:
                return new EnhancedPSO();
            case EACO:
                return new EnhancedACO();
            default:
                // This should never happen due to enum validation
                // I include this case so that if someone adds a new enum value but forgets
                // to add the case, we get a clear error instead of null pointer
                throw new IllegalArgumentException("Unknown algorithm type: " + type);
        }
    }


    /**
     * Creates default parameters for the specified algorithm
     * 
     * I separate default parameters by algorithm type so that each algorithm
     * gets optimal starting values based on our experimental results
     */
    public static AlgorithmParameters createDefaultParameters(String algorithmName) {
        AlgorithmType type = AlgorithmType.fromString(algorithmName);
        AlgorithmParameters params = new AlgorithmParameters();
        
        switch (type) {
            case EPSO:
                // I use 150 iterations for EPSO as it needs more exploration time
                params.setParameter(AlgorithmParameters.MAX_ITERATIONS, 150);
                params.setParameter(AlgorithmParameters.POPULATION_SIZE, 30);
                // I use adaptive inertia weight (0.9 to 0.4) so that the algorithm
                // explores broadly at first then converges to good solutions
                params.setParameter(AlgorithmParameters.INERTIA_WEIGHT, 0.9);
                params.setParameter(AlgorithmParameters.INERTIA_WEIGHT_MAX, 0.9);
                params.setParameter(AlgorithmParameters.INERTIA_WEIGHT_MIN, 0.4);
                
                // I set cognitive and social coefficients to 1.5 for balanced exploration
                // between personal best and global best positions
                params.setParameter(AlgorithmParameters.COGNITIVE_COEFFICIENT, 1.5);
                params.setParameter(AlgorithmParameters.SOCIAL_COEFFICIENT, 1.5);
                
                // I use adaptive velocity limits so that particles slow down over time
                // for finer adjustments near convergence
                params.setParameter(AlgorithmParameters.MAX_VELOCITY, 6.0);
                params.setParameter(AlgorithmParameters.MIN_VELOCITY, -6.0);
                params.setParameter(AlgorithmParameters.MAX_VELOCITY_INITIAL, 6.0);
                params.setParameter(AlgorithmParameters.MAX_VELOCITY_FINAL, 1.0);
                break;
            case EACO:
                // ACO parameters - Tuned for faster convergence than EPSO
                // I use fewer iterations (120) for EACO as it converges faster due to pheromone trails
                params.setParameter(AlgorithmParameters.MAX_ITERATIONS, 120);
                params.setParameter(AlgorithmParameters.POPULATION_SIZE, 25);
                
                // I set decay to 0.6 so that pheromone trails persist enough for learning
                // but still allow exploration of new paths
                params.setParameter(AlgorithmParameters.PHEROMONE_DECAY, 0.6);
                
                // I use alpha=1.2 and beta=2.5 so that heuristic information (beta)
                // has more influence than pheromone trails (alpha) for better initial exploration
                params.setParameter(AlgorithmParameters.ALPHA, 1.2);
                params.setParameter(AlgorithmParameters.BETA, 2.5);
                
                // I set pheromone bounds to prevent premature convergence (min)
                // and runaway pheromone accumulation (max)
                params.setParameter(AlgorithmParameters.INITIAL_PHEROMONE, 0.1);
                params.setParameter(AlgorithmParameters.MIN_PHEROMONE, 0.005);
                params.setParameter(AlgorithmParameters.MAX_PHEROMONE, 2.0);
                
                // I use adaptive evaporation rates so that the algorithm can adjust
                // exploration vs exploitation based on solution quality
                params.setParameter(AlgorithmParameters.EVAPORATION_MIN, 0.1);
                params.setParameter(AlgorithmParameters.EVAPORATION_MAX, 0.9);
                break;
            default:
                // This should never happen due to enum validation
                throw new IllegalArgumentException("Unknown algorithm type: " + type);
        }
        // I set equal weights (0.25 each) for all objectives so that the default
        // configuration treats all metrics as equally important
        params.setParameter(AlgorithmParameters.MAKESPAN_WEIGHT, 0.25);
        params.setParameter(AlgorithmParameters.COST_WEIGHT, 0.25);
        params.setParameter(AlgorithmParameters.ENERGY_WEIGHT, 0.25);
        params.setParameter(AlgorithmParameters.LOAD_BALANCE_WEIGHT, 0.25);
        return params;
    }

    /**
     * parameters with custom weights for multi-objective optimization
     * 
     * I normalize the weights to sum to 1.0 so that users can input any positive values
     * and the relative importance is preserved while keeping the fitness value bounded
     */
    public static AlgorithmParameters createMultiObjectiveParameters(
            double makespanWeight, double costWeight, double energyWeight, double loadBalanceWeight) {
        
        // I calculate the total weight first so that I can normalize all weights proportionally
        double totalWeight = makespanWeight + costWeight + energyWeight + loadBalanceWeight;
        if (totalWeight <= 0) {
            throw new IllegalArgumentException("Total weight must be positive");
        }
        
        AlgorithmParameters params = new AlgorithmParameters();
        // I divide each weight by totalWeight so that they sum to 1.0
        // This ensures fitness values remain comparable across different weight configurations
        params.setParameter(AlgorithmParameters.MAKESPAN_WEIGHT, makespanWeight / totalWeight);
        params.setParameter(AlgorithmParameters.COST_WEIGHT, costWeight / totalWeight);
        params.setParameter(AlgorithmParameters.ENERGY_WEIGHT, energyWeight / totalWeight);
        params.setParameter(AlgorithmParameters.LOAD_BALANCE_WEIGHT, loadBalanceWeight / totalWeight);
        return params;
    }

}
