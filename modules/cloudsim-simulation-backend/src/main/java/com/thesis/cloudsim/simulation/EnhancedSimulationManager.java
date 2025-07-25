package com.thesis.cloudsim.simulation;

import com.thesis.cloudsim.algorithm.AlgorithmParameters;
import com.thesis.cloudsim.algorithm.ISchedulingAlgorithm;
import com.thesis.cloudsim.broker.CustomSchedulingBroker;
import com.thesis.cloudsim.configurator.DataCenterConfigurator;
import com.thesis.cloudsim.constants.SimulationConstants;
import com.thesis.cloudsim.dto.SimulationRequest;
import com.thesis.cloudsim.metrics.MetricsCalculator;
import com.thesis.cloudsim.metrics.SimulationResults;
import com.thesis.cloudsim.utils.DatasetUtils;
import org.cloudbus.cloudsim.Cloudlet;
import org.cloudbus.cloudsim.UtilizationModelFull;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.Datacenter;
import org.cloudbus.cloudsim.Vm;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

// Manages CloudSim simulation lifecycle
public class EnhancedSimulationManager {

    private static final Object SIMULATION_LOCK = new Object();
    private final ISchedulingAlgorithm algorithm;
    private final SimulationRequest request;

    public EnhancedSimulationManager(ISchedulingAlgorithm algorithm, SimulationRequest request) {
        this.algorithm = algorithm;
        this.request = request;
    }

    // Runs the simulation and returns metrics
    public SimulationResults run() throws IOException {
        synchronized (SIMULATION_LOCK) {
        // Initialize CloudSim
        int num_user = 1;
        java.util.Calendar calendar = java.util.Calendar.getInstance();
        boolean trace_flag = false;
        
        try {
            // Reset CloudSim state between runs
            try {
                CloudSim.terminateSimulation();
                Thread.sleep(200);
            } catch (Exception ignored) {
                // First run
            }
            
            // Clean up previous simulation
            System.gc();
            Thread.sleep(100);
            
            CloudSim.init(num_user, calendar, trace_flag);
        } catch (Exception e) {
            throw new IOException("Failed to initialize CloudSim", e);
        }

        // Create broker
        CustomSchedulingBroker broker = null;
        try {
            // Set algorithm parameters
            AlgorithmParameters params = new AlgorithmParameters();
            params.setParameter(AlgorithmParameters.MAKESPAN_WEIGHT, request.getMakespanWeight());
            params.setParameter(AlgorithmParameters.COST_WEIGHT, request.getCostWeight());
            params.setParameter(AlgorithmParameters.ENERGY_WEIGHT, request.getEnergyWeight());
            params.setParameter(AlgorithmParameters.LOAD_BALANCE_WEIGHT, request.getLoadBalanceWeight());
            
            // Configure algorithm-specific parameters
            if ("EPSO".equalsIgnoreCase(request.getOptimizationAlgorithm())) {
                // PSO parameters
                params.setParameter(AlgorithmParameters.MAX_ITERATIONS, 100);
                params.setParameter(AlgorithmParameters.POPULATION_SIZE, 30);
                params.setParameter(AlgorithmParameters.INERTIA_WEIGHT, 0.9);
                params.setParameter(AlgorithmParameters.COGNITIVE_COEFFICIENT, 2.0);
                params.setParameter(AlgorithmParameters.SOCIAL_COEFFICIENT, 2.0);
                params.setParameter(AlgorithmParameters.MAX_VELOCITY, 10.0);
                params.setParameter(AlgorithmParameters.MIN_VELOCITY, -10.0);
            } else {
                // ACO parameters
                params.setParameter(AlgorithmParameters.MAX_ITERATIONS, 100);
                params.setParameter(AlgorithmParameters.POPULATION_SIZE, 30);
                params.setParameter(AlgorithmParameters.PHEROMONE_DECAY, 0.5);
                params.setParameter(AlgorithmParameters.ALPHA, 1.0);
                params.setParameter(AlgorithmParameters.BETA, 2.0);
                params.setParameter(AlgorithmParameters.INITIAL_PHEROMONE, 0.1);
                params.setParameter(AlgorithmParameters.MIN_PHEROMONE, 0.01);
                params.setParameter(AlgorithmParameters.MAX_PHEROMONE, 1.0);
            }
            
            // Get algorithm type
            String algorithmType = request.getOptimizationAlgorithm();
            broker = new CustomSchedulingBroker("Broker", algorithmType, params);
        } catch (Exception e) {
            throw new IOException("Failed to create broker", e);
        }
        
        int brokerId = broker.getId();
        
        // Create datacenter
        DataCenterConfigurator configurator = new DataCenterConfigurator(
                request.getNumHosts(),
                request.getNumPesPerHost(),
                request.getPeMips(),
                request.getRamPerHost(),
                request.getBwPerHost(),
                request.getStoragePerHost(),
                request.getNumVMs(),
                request.getVmMips(),
                request.getVmPes(),
                request.getVmRam(),
                request.getVmBw(),
                request.getVmSize());

        // Generate unique datacenter name
        String datacenterName = "DC_" + System.currentTimeMillis();
        Datacenter datacenter = configurator.configureDatacenter(datacenterName);
        
        // Create VMs
        List<Vm> vmList = configurator.createVms(brokerId);
        
        // Debug output
        System.out.println("[DEBUG] Datacenter created with ID: " + datacenter.getId());
        System.out.println("[DEBUG] Broker created with ID: " + brokerId);

        // Load workload
        List<Cloudlet> cloudlets = loadCloudlets();
        System.out.println("[DEBUG] Created " + cloudlets.size() + " cloudlets");
        
        // Set broker ID for cloudlets
        for (Cloudlet cloudlet : cloudlets) {
            cloudlet.setUserId(brokerId);
        }
        
        // Submit resources to broker
        broker.submitGuestList(vmList);
        broker.submitCloudletList(cloudlets);

        // Start simulation
        
        CloudSim.startSimulation();

        // Get completed cloudlets
        List<Cloudlet> finished = broker.getCloudletReceivedList();
        
        // Check if all cloudlets completed
        if (finished.size() != cloudlets.size()) {
            System.err.println("[WARNING] Not all cloudlets finished: " + 
                             finished.size() + " of " + cloudlets.size());
        }
        
        MetricsCalculator calculator = new MetricsCalculator(vmList, datacenter, finished, request.getOptimizationAlgorithm());
        
        // Get fitness value from algorithm
        double fitness = 0.0;
        ISchedulingAlgorithm brokerAlgorithm = broker.getLastUsedAlgorithm();
        if (brokerAlgorithm != null && brokerAlgorithm.getMetrics() != null) {
            Double fitnessValue = brokerAlgorithm.getMetrics().get("fitness");
            if (fitnessValue != null) {
                fitness = fitnessValue;
            }
        }
        
        return calculator.buildResults(fitness);
        }
    }

    // Load cloudlets from file or generate synthetic workload
    private List<Cloudlet> loadCloudlets() throws IOException {
        if (request.getWorkloadPath() != null && !request.isUseDefaultWorkload()) {
            // Load from CSV file
            return new DatasetUtils().loadWorkload(request.getWorkloadPath());
        }
        // Otherwise fall back to synthetic workload
        return generateSyntheticWorkload();
    }

    // Generate synthetic workload
    private List<Cloudlet> generateSyntheticWorkload() {
        List<Cloudlet> cloudlets = new ArrayList<>();
        Random random = new Random(42);

        for (int i = 0; i < request.getNumCloudlets(); i++) {
            // 80% short tasks, 20% long tasks
            long length;
            if (random.nextDouble() < 0.8) {
                // Short task
                length = SimulationConstants.MIN_CLOUDLET_LENGTH + 
                        random.nextInt(5000);
            } else {
                // Long task
                length = 10000 + random.nextInt(20000);
            }
            
            int pes = SimulationConstants.MIN_CLOUDLET_PES + 
                     random.nextInt(SimulationConstants.MAX_CLOUDLET_PES - SimulationConstants.MIN_CLOUDLET_PES + 1);

            long fileSize = SimulationConstants.MIN_FILE_SIZE + random.nextInt(SimulationConstants.MAX_FILE_SIZE - SimulationConstants.MIN_FILE_SIZE);
            long outputSize = SimulationConstants.MIN_FILE_SIZE + random.nextInt(SimulationConstants.MAX_FILE_SIZE - SimulationConstants.MIN_FILE_SIZE);
            
            Cloudlet cloudlet = new Cloudlet(i, length, pes, fileSize, outputSize,
                      new UtilizationModelFull(),
                      new UtilizationModelFull(),
                      new UtilizationModelFull());
            cloudlet.setUserId(-1);
            cloudlets.add(cloudlet);
        }
        return cloudlets;
    }
}

