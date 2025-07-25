package com.thesis.cloudsim.algorithm;

import java.util.HashMap;
import java.util.Map;

// Configuration parameters for scheduling algorithms
public class AlgorithmParameters {
    
    private final Map<String, Object> parameters;
    
    // Common parameters
    public static final String MAX_ITERATIONS = "maxIterations";
    public static final String POPULATION_SIZE = "populationSize";
    public static final String MAKESPAN_WEIGHT = "makespanWeight";
    public static final String COST_WEIGHT = "costWeight";
    public static final String ENERGY_WEIGHT = "energyWeight";
    public static final String LOAD_BALANCE_WEIGHT = "loadBalanceWeight";
    
    // PSO parameters
    public static final String INERTIA_WEIGHT = "inertiaWeight";
    public static final String INERTIA_WEIGHT_MAX = "inertiaWeightMax";
    public static final String INERTIA_WEIGHT_MIN = "inertiaWeightMin";
    public static final String COGNITIVE_COEFFICIENT = "cognitiveCoefficient";
    public static final String SOCIAL_COEFFICIENT = "socialCoefficient";
    public static final String MAX_VELOCITY = "maxVelocity";
    public static final String MIN_VELOCITY = "minVelocity";
    public static final String MAX_VELOCITY_INITIAL = "maxVelocityInitial";
    public static final String MAX_VELOCITY_FINAL = "maxVelocityFinal";
    
    // ACO parameters
    public static final String PHEROMONE_DECAY = "pheromoneDecay";
    public static final String ALPHA = "alpha";
    public static final String BETA = "beta";
    public static final String INITIAL_PHEROMONE = "initialPheromone";
    public static final String MIN_PHEROMONE = "minPheromone";
    public static final String MAX_PHEROMONE = "maxPheromone";
    public static final String EVAPORATION_MIN = "evaporationMin";
    public static final String EVAPORATION_MAX = "evaporationMax";
    
    public AlgorithmParameters() {
        this.parameters = new HashMap<>();
        setDefaultParameters();
    }
    
    public AlgorithmParameters(Map<String, Object> parameters) {
        this.parameters = new HashMap<>();
        setDefaultParameters();
        this.parameters.putAll(parameters);
    }
    
    private void setDefaultParameters() {
        // Set common defaults
        parameters.put(MAX_ITERATIONS, 100);
        parameters.put(POPULATION_SIZE, 50);
        parameters.put(MAKESPAN_WEIGHT, 0.25);
        parameters.put(COST_WEIGHT, 0.25);
        parameters.put(ENERGY_WEIGHT, 0.25);
        parameters.put(LOAD_BALANCE_WEIGHT, 0.25);
        
        // Set PSO defaults
        parameters.put(INERTIA_WEIGHT, 0.9);
        parameters.put(INERTIA_WEIGHT_MAX, 0.9);
        parameters.put(INERTIA_WEIGHT_MIN, 0.4);
        parameters.put(COGNITIVE_COEFFICIENT, 2.0);
        parameters.put(SOCIAL_COEFFICIENT, 2.0);
        parameters.put(MAX_VELOCITY, 10.0);
        parameters.put(MIN_VELOCITY, -10.0);
        parameters.put(MAX_VELOCITY_INITIAL, 6.0);
        parameters.put(MAX_VELOCITY_FINAL, 1.0);
        
        // Set ACO defaults
        parameters.put(PHEROMONE_DECAY, 0.5);
        parameters.put(ALPHA, 1.0);
        parameters.put(BETA, 2.0);
        parameters.put(INITIAL_PHEROMONE, 0.1);
        parameters.put(MIN_PHEROMONE, 0.01);
        parameters.put(MAX_PHEROMONE, 1.0);
        parameters.put(EVAPORATION_MIN, 0.1);
        parameters.put(EVAPORATION_MAX, 0.9);
    }
    
    public <T> T getParameter(String key, Class<T> type) {
        Object value = parameters.get(key);
        return type.cast(value);
    }
    
    public void setParameter(String key, Object value) {
        parameters.put(key, value);
    }
    
    public double getDouble(String key) {
        return getParameter(key, Double.class);
    }
    
    public int getInt(String key) {
        return getParameter(key, Integer.class);
    }
    
    public boolean getBoolean(String key) {
        return getParameter(key, Boolean.class);
    }
    
    public String getString(String key) {
        return getParameter(key, String.class);
    }
    
    public Map<String, Object> getAllParameters() {
        return new HashMap<>(parameters);
    }
    
    public boolean hasParameter(String key) {
        return parameters.containsKey(key);
    }
}
