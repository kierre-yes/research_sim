package com.thesis.cloudsim.constants;

// Simulation constants used across the application
public final class SimulationConstants {
    
    // Prevent instantiation
    private SimulationConstants() {
        throw new AssertionError("Constants class should not be instantiated");
    }
    
    // Cost constants
    public static final double COST_PER_RAM_UNIT = 0.001;
    public static final double COST_PER_HOUR = 0.1;
    
    // Utilization defaults
    public static final double DEFAULT_VM_UTILIZATION = 0.5;
    public static final double DEFAULT_HOST_UTILIZATION = 0.5;
    
    // Simulation timing
    public static final double DEFAULT_VM_DESTRUCTION_DELAY = 100.0;
    public static final double WARMUP_RETRY_INTERVAL = 5.0;
    
    // Workload ranges
    public static final int MIN_CLOUDLET_LENGTH = 1000;
    public static final int MAX_CLOUDLET_LENGTH = 10000;
    public static final int MIN_CLOUDLET_PES = 1;
    public static final int MAX_CLOUDLET_PES = 2;
    public static final int MIN_FILE_SIZE = 300;
    public static final int MAX_FILE_SIZE = 500;
    
    // Algorithm convergence
    public static final double CONVERGENCE_THRESHOLD = 1e-6;
    
    // Energy calculation
    public static final double IDLE_POWER_PERCENTAGE = 0.7;
    public static final double MAX_POWER_PERCENTAGE = 1.0;
}
