package com.thesis.cloudsim.config;

import com.thesis.cloudsim.algorithm.AlgorithmParameters;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

/**
 * Utility class for loading algorithm configuration from properties files
 * Compatible with CloudSim 7.0.0-alpha parameter handling.
 */
public class ParameterConfig {

    /**
     * Load parameters from a properties file.
     * @param filePath Path to the config file (e.g., "config.properties")
     * @return Configured AlgorithmParameters
     * @throws IOException if file loading fails
     */
    public static AlgorithmParameters loadFromFile(String filePath) throws IOException {
        Properties props = new Properties();
        try (FileInputStream fis = new FileInputStream(filePath)) {
            props.load(fis);
        }

        AlgorithmParameters params = new AlgorithmParameters();

        // Load common parameters
        if (props.containsKey(AlgorithmParameters.MAX_ITERATIONS)) {
            params.setParameter(AlgorithmParameters.MAX_ITERATIONS, Integer.parseInt(props.getProperty(AlgorithmParameters.MAX_ITERATIONS)));
        }
        if (props.containsKey(AlgorithmParameters.POPULATION_SIZE)) {
            params.setParameter(AlgorithmParameters.POPULATION_SIZE, Integer.parseInt(props.getProperty(AlgorithmParameters.POPULATION_SIZE)));
        }
        // Load weights
        double makespanWeight = Double.parseDouble(props.getProperty(AlgorithmParameters.MAKESPAN_WEIGHT, "0.25"));
        double costWeight = Double.parseDouble(props.getProperty(AlgorithmParameters.COST_WEIGHT, "0.25"));
        double energyWeight = Double.parseDouble(props.getProperty(AlgorithmParameters.ENERGY_WEIGHT, "0.25"));
        double loadBalanceWeight = Double.parseDouble(props.getProperty(AlgorithmParameters.LOAD_BALANCE_WEIGHT, "0.25"));
        params.setParameter(AlgorithmParameters.MAKESPAN_WEIGHT, makespanWeight);
        params.setParameter(AlgorithmParameters.COST_WEIGHT, costWeight);
        params.setParameter(AlgorithmParameters.ENERGY_WEIGHT, energyWeight);
        params.setParameter(AlgorithmParameters.LOAD_BALANCE_WEIGHT, loadBalanceWeight);

        // Load PSO-specific if present
        if (props.containsKey(AlgorithmParameters.INERTIA_WEIGHT)) {
            params.setParameter(AlgorithmParameters.INERTIA_WEIGHT, Double.parseDouble(props.getProperty(AlgorithmParameters.INERTIA_WEIGHT)));
        }
        // Load ACO-specific if present
        if (props.containsKey(AlgorithmParameters.PHEROMONE_DECAY)) {
            params.setParameter(AlgorithmParameters.PHEROMONE_DECAY, Double.parseDouble(props.getProperty(AlgorithmParameters.PHEROMONE_DECAY)));
        }
        // Add more as needed based on AlgorithmParameters

        return params;
    }

}
