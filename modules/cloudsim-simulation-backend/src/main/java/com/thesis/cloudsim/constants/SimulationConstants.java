package com.thesis.cloudsim.constants;

/*
 * I centralize all simulation constants here so i can update it in an instant
 */
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
    
    /*
     * I add an inner class for energy model constants based on typical server specifications.
     * These values are from Dell PowerEdge R740 server specifications which is commonly
     */
    public static final class EnergyModel {
        // 100 util, i based on the manuscript
        public static final double POWER_MAX_WATTS = 215.0;
        
        // idle
        public static final double POWER_IDLE_WATTS = 162.0;
        
        // Non-linear power scaling 
        public static final double SCALING_FACTOR = 1.4;
        
        // i add this for my debugging so its stops the exec then raise the error
        private EnergyModel() {
            throw new AssertionError("EnergyModel constants class should not be instantiated");
        }
    }
    
    /*
     * I add cost model constants for detailed cost calculation.
     * These are based on typical cloud provider pricing models converted
     * to per-unit costs for simulation purposes.
     */
    public static final class CostModel {
        // CPU cost per MIPS per hour
        public static final double CPU_COST_PER_MIPS_HOUR = 0.00001;
        
        // RAM cost per MB per hour
        public static final double RAM_COST_PER_MB_HOUR = 0.000005;
        
        // Storage cost per MB per hour
        public static final double STORAGE_COST_PER_MB_HOUR = 0.000001;
        
        // Network bandwidth cost per MB transferred
        public static final double BANDWIDTH_COST_PER_MB = 0.00001;
        
        // Prevent instantiation of inner class
        private CostModel() {
            throw new AssertionError("CostModel constants class should not be instantiated");
        }
    }
}
