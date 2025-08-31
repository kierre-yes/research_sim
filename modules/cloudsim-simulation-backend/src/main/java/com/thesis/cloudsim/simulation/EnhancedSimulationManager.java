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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/*
 * I manage the CloudSim simulation lifecycle
 */
public class EnhancedSimulationManager {
    
    private static final Logger logger = LoggerFactory.getLogger(EnhancedSimulationManager.class);
    private static final Object SIMULATION_LOCK = new Object();
    private static volatile boolean isCancelled = false;
    private final ISchedulingAlgorithm algorithm;
    private final SimulationRequest request;

    public EnhancedSimulationManager(ISchedulingAlgorithm algorithm, SimulationRequest request) {
        this.algorithm = algorithm;
        this.request = request;
    }

    public SimulationResults run() throws IOException {
        synchronized (SIMULATION_LOCK) {
            isCancelled = false; 
            
            resetPreviousSimulation();
            checkCancellation();
            
            initializeCloudSim();
            checkCancellation();
            
            CustomSchedulingBroker broker = createBroker();
            int brokerId = broker.getId();
            checkCancellation();
            
            Datacenter datacenter = setupDatacenter();
            List<Vm> vmList = createVirtualMachines(brokerId);
            checkCancellation();
            
            logDebugInfo(datacenter, brokerId);
            
            List<Cloudlet> cloudlets = prepareWorkload(brokerId);
            checkCancellation();
            
            submitResources(broker, vmList, cloudlets);
            checkCancellation();
            
            CloudSim.startSimulation();
            checkCancellation();
            
            return collectResults(broker, vmList, datacenter, cloudlets);
        }
    }
    
    /**
     * Static method to cancel any running simulation
     */
    public static void cancelSimulation() {
        isCancelled = true;
        logger.info("Simulation cancellation requested");
        try {
            CloudSim.terminateSimulation();
        } catch (Exception e) {
            logger.debug("Error terminating simulation: {}", e.getMessage());
        }
    }
    
    /**
     * Check if simulation has been cancelled and throw exception if so
     */
    private void checkCancellation() throws IOException {
        if (isCancelled) {
            logger.info("Simulation cancelled by user");
            throw new IOException("Simulation cancelled by user");
        }
    }
    
    /*
     * I reset the previous simulation state to ensure clean runs.
     * This prevents state pollution between consecutive simulations.
     */
    private void resetPreviousSimulation() throws IOException {
        try {
            CloudSim.terminateSimulation();
            Thread.sleep(200);
        } catch (Exception ignored) {
            /* I ignore exceptions here as they indicate first run */
        }
        
        System.gc();
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while resetting simulation", e);
        }
    }
    
    /*
     * I initialize CloudSim with the required parameters.
     */
    private void initializeCloudSim() throws IOException {
        int num_user = 1;
        java.util.Calendar calendar = java.util.Calendar.getInstance();
        boolean trace_flag = false;
        
        try {
            CloudSim.init(num_user, calendar, trace_flag);
        } catch (Exception e) {
            throw new IOException("Failed to initialize CloudSim", e);
        }
    }
    
    /*
     * I create the broker with the appropriate algorithm and parameters.
     */
    private CustomSchedulingBroker createBroker() throws IOException {
        try {
            AlgorithmParameters params = buildAlgorithmParameters(request);
            String algorithmType = request.getOptimizationAlgorithm();
            return new CustomSchedulingBroker("Broker", algorithmType, params);
        } catch (Exception e) {
            throw new IOException("Failed to create broker", e);
        }
    }
    
    /*
     * I setup the datacenter with the user's configuration from the frontend.
     */
    private Datacenter setupDatacenter() throws IOException {
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
        
        String datacenterName = "DC_" + System.currentTimeMillis();
        return configurator.configureDatacenter(datacenterName);
    }
    
    /*
     * I create virtual machines based on user's VM configuration.
     */
    private List<Vm> createVirtualMachines(int brokerId) {
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
        
        return configurator.createVms(brokerId);
    }
    
    /*
     * I log debug information about the created resources.
     */
    private void logDebugInfo(Datacenter datacenter, int brokerId) {
        logger.debug("Datacenter created with ID: {}", datacenter.getId());
        logger.debug("Broker created with ID: {}", brokerId);
    }
    
    /*
     * I prepare the workload by loading cloudlets and setting their user ID.
     */
    private List<Cloudlet> prepareWorkload(int brokerId) throws IOException {
        List<Cloudlet> cloudlets = loadCloudlets();
        logger.debug("Created {} cloudlets", cloudlets.size());
        
        for (Cloudlet cloudlet : cloudlets) {
            cloudlet.setUserId(brokerId);
        }
        
        return cloudlets;
    }
    
    /*
     * I submit resources to the broker and handle staged submission if needed.
     */
    private void submitResources(CustomSchedulingBroker broker, List<Vm> vmList, List<Cloudlet> cloudlets) {
        broker.submitGuestList(vmList);
        
        if (request.isUseArrivalTimes() && request.getArrivalTimes() != null && !request.getArrivalTimes().isEmpty()) {
            logger.info("Using staged submission with arrival times");
            submitCloudletsWithTiming(broker, cloudlets, request.getArrivalTimes());
        } else {
            logger.info("Using batch submission (all cloudlets at t=0)");
            broker.submitCloudletList(cloudlets);
        }
    }
    
    /*
     * I collect simulation results and calculate metrics to send back to the frontend.
     */
    private SimulationResults collectResults(CustomSchedulingBroker broker, List<Vm> vmList, 
                                            Datacenter datacenter, List<Cloudlet> cloudlets) {
        Map<Integer, Integer> vmToHostMap = broker.getVmToHostMapping();
        logger.debug("Retrieved VM-to-host mappings from broker: {} entries", vmToHostMap.size());
        for (Map.Entry<Integer, Integer> entry : vmToHostMap.entrySet()) {
            logger.debug("VM {} is on Host {}", entry.getKey(), entry.getValue());
        }
        
        List<Cloudlet> finished = broker.getCloudletReceivedList();
        
        if (finished.size() != cloudlets.size()) {
            logger.warn("Not all cloudlets finished: {} of {}", finished.size(), cloudlets.size());
        }
        
        MetricsCalculator calculator = new MetricsCalculator(vmList, datacenter, finished, 
                                                            request.getOptimizationAlgorithm(), vmToHostMap);
        
        double fitness = extractFitnessValue(broker);
        return calculator.buildResults(fitness);
    }
    
    /*
     * I extract the fitness value from the algorithm used by the broker.
     */
    private double extractFitnessValue(CustomSchedulingBroker broker) {
        ISchedulingAlgorithm brokerAlgorithm = broker.getLastUsedAlgorithm();
        if (brokerAlgorithm != null && brokerAlgorithm.getMetrics() != null) {
            Double fitnessValue = brokerAlgorithm.getMetrics().get("fitness");
            if (fitnessValue != null) {
                return fitnessValue;
            }
        }
        return 0.0;
    }

    // Load cloudlets from file or generate synthetic workload
    private List<Cloudlet> loadCloudlets() throws IOException {
        if (request.getWorkloadPath() != null && !request.isUseDefaultWorkload()) {
            // Try enhanced loader first for arrival time support
            try {
                DatasetUtils.WorkloadData workloadData = new DatasetUtils()
                    .loadWorkloadWithTiming(request.getWorkloadPath(), request.getNumCloudlets());
                
                // Store arrival times if present (for future staged submission)
                if (workloadData.hasArrivalTimes()) {
                    logger.debug("Workload has arrival times - will use staged submission");
                    // Store in request for later use
                    request.setArrivalTimes(workloadData.arrivalTimes);
                }
                
                return workloadData.cloudlets;
            } catch (Exception e) {
                // Fallback to legacy loader if enhanced fails
                logger.debug("Enhanced loader failed, fallback to legacy loader: {}", e.getMessage());
                return new DatasetUtils().loadWorkload(request.getWorkloadPath(), request.getNumCloudlets());
            }
        }

        return generateSyntheticWorkload();
    }


    private List<Cloudlet> generateSyntheticWorkload() {
        List<Cloudlet> cloudlets = new ArrayList<>();
        long seed = request.getSeed() != null ? request.getSeed() : 42L;
        Random random = new Random(seed);

        for (int i = 0; i < request.getNumCloudlets(); i++) {

            long length;
            if (random.nextDouble() < 0.8) {

                length = SimulationConstants.MIN_CLOUDLET_LENGTH + 
                        random.nextInt(5000);
            } else {

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
    
    /**
     * Submit cloudlets with timing - groups by time windows and schedules submission
     */
    private void submitCloudletsWithTiming(CustomSchedulingBroker broker, List<Cloudlet> cloudlets, List<Double> arrivalTimes) {
        logger.debug("Submitting cloudlets with arrival times");
        
        // Group cloudlets by arrival time windows (1 second buckets)
        Map<Integer, List<Cloudlet>> timeWindows = new java.util.TreeMap<>();
        
        for (int i = 0; i < cloudlets.size() && i < arrivalTimes.size(); i++) {
            Double arrivalTime = arrivalTimes.get(i);
            if (arrivalTime == null) arrivalTime = 0.0;
            
            // Round to nearest second for bucketing
            int timeWindow = (int) Math.round(arrivalTime);
            
            timeWindows.computeIfAbsent(timeWindow, k -> new ArrayList<>())
                      .add(cloudlets.get(i));
        }
        
        logger.debug("Grouped cloudlets into {} time windows", timeWindows.size());
        if (!timeWindows.isEmpty()) {
            logger.debug("Time windows range from {}s to {}s", 
                        timeWindows.keySet().iterator().next(),
                        ((java.util.TreeMap<Integer, List<Cloudlet>>)timeWindows).lastKey());
        }
        
        // Schedule batch submissions based on arrival times
        for (Map.Entry<Integer, List<Cloudlet>> entry : timeWindows.entrySet()) {
            double submitTime = entry.getKey();
            List<Cloudlet> batch = entry.getValue();
            
            if (submitTime <= 0) {
                // Submit immediately for time 0
                broker.submitCloudletList(batch);
                logger.debug("Submitted {} cloudlets immediately (t=0)", batch.size());
            } else {
                // Schedule for later submission
                broker.queueBatchForSubmission(batch, submitTime);
                logger.debug("Scheduled {} cloudlets for submission at t={}s", batch.size(), submitTime);
            }
        }
        
        logger.debug("Staged submission configured with {} time windows", timeWindows.size());
    }

    /*
     * I refactored this to use AlgorithmFactory to eliminate code duplication.
     * This follows DRY principle - algorithm parameters are now centralized
     * in AlgorithmFactory where they belong, avoiding maintenance issues
     * from having the same logic in multiple places.
     */
    private AlgorithmParameters buildAlgorithmParameters(SimulationRequest request) {
        // I delegate to AlgorithmFactory for default parameters to avoid duplication
        AlgorithmParameters params = com.thesis.cloudsim.algorithm.AlgorithmFactory
            .createDefaultParameters(request.getOptimizationAlgorithm());
        
        // I override with user-specified weights for multi-objective optimization
        params.setParameter(AlgorithmParameters.MAKESPAN_WEIGHT, request.getMakespanWeight());
        params.setParameter(AlgorithmParameters.COST_WEIGHT, request.getCostWeight());
        params.setParameter(AlgorithmParameters.ENERGY_WEIGHT, request.getEnergyWeight());
        params.setParameter(AlgorithmParameters.LOAD_BALANCE_WEIGHT, request.getLoadBalanceWeight());
        
        return params;
    }
}

