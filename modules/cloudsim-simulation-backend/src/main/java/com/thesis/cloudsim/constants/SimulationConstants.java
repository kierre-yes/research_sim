package com.thesis.cloudsim.constants;

// Simulation constants used across the application
public final class SimulationConstants {
    
    // Prevent instantiation
    private SimulationConstants() {
        throw new AssertionError("Constants class should not be instantiated");
    }
    
    // Cost constants
    public static final double COST_PER_HOUR = 0.1;
    
    // Workload ranges
    public static final int MIN_CLOUDLET_LENGTH = 1000;
    public static final int MAX_CLOUDLET_LENGTH = 10000;
    public static final int MIN_CLOUDLET_PES = 1;
    public static final int MAX_CLOUDLET_PES = 2;
    public static final int MIN_FILE_SIZE = 300;
    public static final int MAX_FILE_SIZE = 500;
}
