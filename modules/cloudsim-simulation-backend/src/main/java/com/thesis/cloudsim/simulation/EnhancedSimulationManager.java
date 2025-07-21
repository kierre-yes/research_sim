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

/**
 * EnhancedSimulationManager orchestrates a full CloudSim run using user–supplied
 * {@link SimulationRequest} parameters.  The logic is intentionally kept simple and
 * linear (basic loops, no complex Streams) so developers with ~6 months of Java can
 * trace every step.
 */
public class EnhancedSimulationManager {

    private static final Object SIMULATION_LOCK = new Object();
    private final ISchedulingAlgorithm algorithm;
    private final SimulationRequest request;

    public EnhancedSimulationManager(ISchedulingAlgorithm algorithm, SimulationRequest request) {
        this.algorithm = algorithm;
        this.request = request;
    }

    /**
     * Runs the simulation end-to-end and returns calculated metrics.
     */
    public SimulationResults run() throws IOException {
        synchronized (SIMULATION_LOCK) {
        // 1) Bootstrap CloudSim runtime
        int num_user = 1; // number of cloud users
        java.util.Calendar calendar = java.util.Calendar.getInstance();
        boolean trace_flag = false; // trace events
        
        try {
            // Reset CloudSim state for clean simulation
            try {
                CloudSim.terminateSimulation();
                Thread.sleep(200); // Give time for cleanup
            } catch (Exception ignored) {
                // Ignore if CloudSim wasn't initialized
            }
            
            // Force garbage collection to clean up any lingering references
            System.gc();
            Thread.sleep(100);
            
            // Initialize fresh CloudSim instance
            CloudSim.init(num_user, calendar, trace_flag);
        } catch (Exception e) {
            throw new IOException("Failed to initialize CloudSim", e);
        }

        // 2) Build infrastructure (Datacenter + VMs) according to user config
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

        // Use unique datacenter name to avoid conflicts
        String datacenterName = "Datacenter_" + System.currentTimeMillis();
        Datacenter datacenter = configurator.configureDatacenter(datacenterName);
        List<Vm> vmList = configurator.createVms();
        
        System.out.println("[DEBUG] Datacenter created with ID: " + datacenter.getId());

        // 3) Obtain workload
        List<Cloudlet> cloudlets = loadCloudlets();
        System.out.println("[DEBUG] Created " + cloudlets.size() + " cloudlets");

        // 4) Create broker wired with the selected optimisation algorithm
        CustomSchedulingBroker broker = null;
        try {
            // Create algorithm parameters
            AlgorithmParameters params = new AlgorithmParameters();
            params.setParameter(AlgorithmParameters.MAKESPAN_WEIGHT, request.getMakespanWeight());
            params.setParameter(AlgorithmParameters.COST_WEIGHT, request.getCostWeight());
            params.setParameter(AlgorithmParameters.ENERGY_WEIGHT, request.getEnergyWeight());
            params.setParameter(AlgorithmParameters.LOAD_BALANCE_WEIGHT, request.getLoadBalanceWeight());
            
            // Add algorithm-specific parameters
            if ("EPSO".equalsIgnoreCase(request.getOptimizationAlgorithm())) {
                params.setParameter(AlgorithmParameters.MAX_ITERATIONS, 100);
                params.setParameter(AlgorithmParameters.POPULATION_SIZE, 30);
                params.setParameter(AlgorithmParameters.INERTIA_WEIGHT, 0.9);
                params.setParameter(AlgorithmParameters.COGNITIVE_COEFFICIENT, 2.0);
                params.setParameter(AlgorithmParameters.SOCIAL_COEFFICIENT, 2.0);
                params.setParameter(AlgorithmParameters.MAX_VELOCITY, 10.0);
                params.setParameter(AlgorithmParameters.MIN_VELOCITY, -10.0);
            } else {
                params.setParameter(AlgorithmParameters.MAX_ITERATIONS, 100);
                params.setParameter(AlgorithmParameters.POPULATION_SIZE, 30);
                params.setParameter(AlgorithmParameters.PHEROMONE_DECAY, 0.5);
                params.setParameter(AlgorithmParameters.ALPHA, 1.0);
                params.setParameter(AlgorithmParameters.BETA, 2.0);
                params.setParameter(AlgorithmParameters.INITIAL_PHEROMONE, 0.1);
                params.setParameter(AlgorithmParameters.MIN_PHEROMONE, 0.01);
                params.setParameter(AlgorithmParameters.MAX_PHEROMONE, 1.0);
            }
            
            // Pass the request's algorithm type (EPSO/EACO) not the full name
            String algorithmType = request.getOptimizationAlgorithm();
            broker = new CustomSchedulingBroker("Broker", algorithmType, params);
        } catch (Exception e) {
            throw new IOException("Failed to create broker", e);
        }
        
        int brokerId = broker.getId();
        
        // Set broker ID for VMs
        for (Vm vm : vmList) {
            vm.setUserId(brokerId);
            System.out.println("[DEBUG] Set VM " + vm.getId() + " user ID to: " + vm.getUserId());
        }
        
        // Set broker ID for cloudlets to ensure proper VM lookup
        for (Cloudlet cloudlet : cloudlets) {
            cloudlet.setUserId(brokerId);
        }
        
        // Submit VMs and cloudlets to broker
        broker.submitGuestList(vmList);
        broker.submitCloudletList(cloudlets);

        // 5) Run!
        System.out.println("[DEBUG] Broker ID before simulation: " + brokerId);
        System.out.println("[DEBUG] Number of VMs: " + vmList.size());
        System.out.println("[DEBUG] Number of Cloudlets: " + cloudlets.size());
        
        CloudSim.startSimulation();

        // 6) Collect results and calculate higher-level metrics
        List<Cloudlet> finished = broker.getCloudletReceivedList();
        System.out.println("[DEBUG] Finished cloudlets: " + finished.size());
        
        // Debug cloudlet states
        for (Cloudlet cloudlet : finished) {
            System.out.println("[DEBUG] Cloudlet " + cloudlet.getCloudletId() + 
                              " - Status: " + cloudlet.getCloudletStatusString() +
                              " - ExecFinishTime: " + cloudlet.getExecFinishTime() +
                              " - ExecStartTime: " + cloudlet.getExecStartTime() +
                              " - ActualCPUTime: " + cloudlet.getActualCPUTime());
        }
        
        MetricsCalculator calculator = new MetricsCalculator(vmList, datacenter, finished);
        return calculator.buildResults();
        } // end synchronized block
    }

    /**
     * Decide whether to load a CSV workload supplied by the user or to generate a
     * synthetic workload on-the-fly.
     */
    private List<Cloudlet> loadCloudlets() throws IOException {
        if (request.getWorkloadPath() != null && !request.isUseDefaultWorkload()) {
            // User uploaded a CSV file
            return new DatasetUtils().loadWorkload(request.getWorkloadPath());
        }
        // Otherwise fall back to synthetic workload
        return generateSyntheticWorkload();
    }

    /**
     * Builds a very simple synthetic workload with random Cloudlet lengths and PEs.
     */
    private List<Cloudlet> generateSyntheticWorkload() {
        List<Cloudlet> cloudlets = new ArrayList<>();
        Random random = new Random();

        for (int i = 0; i < request.getNumCloudlets(); i++) {
            long length = SimulationConstants.MIN_CLOUDLET_LENGTH + 
                         random.nextInt(SimulationConstants.MAX_CLOUDLET_LENGTH - SimulationConstants.MIN_CLOUDLET_LENGTH);
            int pes = SimulationConstants.MIN_CLOUDLET_PES + 
                     random.nextInt(SimulationConstants.MAX_CLOUDLET_PES - SimulationConstants.MIN_CLOUDLET_PES + 1);

            long fileSize = SimulationConstants.MIN_FILE_SIZE + random.nextInt(SimulationConstants.MAX_FILE_SIZE - SimulationConstants.MIN_FILE_SIZE);
            long outputSize = SimulationConstants.MIN_FILE_SIZE + random.nextInt(SimulationConstants.MAX_FILE_SIZE - SimulationConstants.MIN_FILE_SIZE);
            
            Cloudlet cloudlet = new Cloudlet(i, length, pes, fileSize, outputSize,
                      new UtilizationModelFull(),
                      new UtilizationModelFull(),
                      new UtilizationModelFull());
            cloudlet.setUserId(-1); // Set user ID (-1 means it will be set by broker)
            cloudlets.add(cloudlet);
        }
        return cloudlets;
    }
}

