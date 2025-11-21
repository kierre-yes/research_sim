package com.thesis.cloudsim.algorithm;

/*
* i centralize now the metric utils in order to avvoid duplication and inconsistencies.
*/
import org.cloudbus.cloudsim.Cloudlet;
import org.cloudbus.cloudsim.Vm;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import static com.thesis.cloudsim.algorithm.AlgorithmMetricUtils.*;

/**
 * based scheduler for aco to enable algo to algo comparison
 */
public class EnhancedACO implements ISchedulingAlgorithm {
    
    private static final String ALGORITHM_NAME = "EACO"; //i set in uppercase the constant identifier
    private final Map<String, Double> metrics; //avoid mutating the internal state
    //drives stochastic parts
    private final Random random; // for stochastic decisions
    
    // main aco components
    private List<Ant> ants;
    private double[][] pheromoneMatrix;  // for learning preferences, I store pheromone levels for each cloudlet-VM pair
    private double[][] heuristicMatrix;  // this will store heuristic values based on execution time and resources
    private Map<Cloudlet, Vm> bestSolution;
    private double bestFitness;
    private int currentIteration; //sched solution
    
    //convergence metrics so that we can stop early when the algorithm stabilizes
    private double previousBestFitness;
    private int stagnationCounter;
    private double previousPheromoneConvergence;
    private final Map<Integer, Double> fitnessCache;
    
    // entities also to detect if converge in similar values
    private List<Cloudlet> cloudlets;
    private List<Vm> vms;
    private AlgorithmParameters parameters;
    
    public EnhancedACO() {
        this.metrics = new HashMap<>();
        /*
         * apply the new random instance
         */
        this.random = new Random();
        this.ants = new ArrayList<>(); //unseeded
        this.bestFitness = Double.MAX_VALUE;
        this.currentIteration = 0;
        this.previousBestFitness = Double.MAX_VALUE;
        this.stagnationCounter = 0;
        this.previousPheromoneConvergence = Double.MAX_VALUE;
        this.fitnessCache = new ConcurrentHashMap<>();
    }
    
    /*
     * I provide a constructor with seed for reproducible results when needed
     */
    public EnhancedACO(long seed) {
        this.metrics = new HashMap<>();
        this.random = new Random(seed); //seeded
        this.ants = new ArrayList<>();
        this.bestFitness = Double.MAX_VALUE;
        this.currentIteration = 0;
        this.previousBestFitness = Double.MAX_VALUE;
        this.stagnationCounter = 0;
        this.previousPheromoneConvergence = Double.MAX_VALUE;
        this.fitnessCache = new ConcurrentHashMap<>();
    }
    
    @Override
    public Map<Cloudlet, Vm> schedule(List<Cloudlet> cloudlets, List<Vm> vms, AlgorithmParameters parameters) {
        this.cloudlets = new ArrayList<>(cloudlets);
        this.vms = new ArrayList<>(vms);
        this.parameters = parameters;
        
        initializeMatrices();
        initializeAnts();
        
        for (currentIteration = 0; currentIteration < parameters.getInt(AlgorithmParameters.MAX_ITERATIONS); currentIteration++) {
            if (com.thesis.cloudsim.simulation.EnhancedSimulationManager.isCancellationRequested()) {
                throw new RuntimeException("Simulation cancelled during ACO optimization");
            }
            
            constructSolutions();
            updateBestSolution();
            updatePheromones();
            
            // I check for early stopping based on fitness stagnation or pheromone convergence
            if (shouldStopEarly()) {
                if (shouldDebug()) {
                    System.out.println("[EACO] Early stopping triggered at iteration " + currentIteration + 
                                     " - Reason: " + getStoppingReason());
                }
                break;
            }
        }
        Map<Cloudlet, Vm> balancedSolution = applyLoadBalancing(bestSolution);
        calculateMetrics(balancedSolution);
        //copy to avoid external mutation
        return new HashMap<>(balancedSolution);
    }
    //to init   
    private void initializeMatrices() {
        //sizes
        int cloudletCount = cloudlets.size();
        int vmCount = vms.size();
        //i and j represents pheromone
        pheromoneMatrix = new double[cloudletCount][vmCount];
        double initialPheromone = parameters.getDouble(AlgorithmParameters.INITIAL_PHEROMONE);
        
        // I initialize pheromones with small random variations so that ants don't all
        // follow the same path initially, promoting exploration
        for (int i = 0; i < cloudletCount; i++) {
            for (int j = 0; j < vmCount; j++) {
                // I apply 5% random variation to break symmetry in initial pheromone levels
                double variation = 0.95 + (random.nextDouble() * 0.1);
                pheromoneMatrix[i][j] = initialPheromone * variation;
            }
        }
        
        heuristicMatrix = new double[cloudletCount][vmCount];
        calculateHeuristicMatrix();
    }
    
    private void calculateHeuristicMatrix() {
        // I calculate heuristic values that guide ants towards efficient VM assignments
        for (int i = 0; i < cloudlets.size(); i++) {
            Cloudlet cloudlet = cloudlets.get(i);
            for (int j = 0; j < vms.size(); j++) {
                Vm vm = vms.get(j);
                
                // I use execution time as the primary heuristic which for shorter is better
                double executionTime = cloudlet.getCloudletLength() / vm.getMips();
                
                // I factor in resource availability so that ants prefer VMs with more available resources
                double resourceRatio = 1.0;
                if (vm.getHost() != null) {
                    double vmRamCapacity = vm.getRam();
                    double hostRamCapacity = vm.getHost().getGuestRamProvisioner().getRam();
                    resourceRatio = vmRamCapacity / hostRamCapacity;
                }
                
                // I combine execution time and resource ratio to create the heuristic value
                // higher values indicate more desirable assignments
                heuristicMatrix[i][j] = (1.0 / executionTime) * resourceRatio;
            }
        }
    }
    
    private void initializeAnts() {
        int populationSize = parameters.getInt(AlgorithmParameters.POPULATION_SIZE); //colony size
        //i clear previous runs 
        ants.clear();
        
        
        // I create a colony of ants, each capable of building a complete solution
        for (int i = 0; i < populationSize; i++) {
            ants.add(new Ant(cloudlets.size()));
        }
    }
    
    private void constructSolutions() {
        // I let each ant build its solution probabilistically based on pheromones and heuristics
        for (Ant ant : ants) {
            ant.constructSolution();
            
            // I evaluate each ant's solution so that we can identify good paths to reinforce
            Map<Cloudlet, Vm> solution = ant.getSolution();
            double fitness = calculateFitness(solution);
            ant.setFitness(fitness);
        }
    }
    
    private void updateBestSolution() {
        double oldBestFitness = bestFitness; //store curr best
        
        // I check all ants to see if any found a better solution than our current best
        for (Ant ant : ants) {
            if (ant.getFitness() < bestFitness) {
                bestFitness = ant.getFitness();
                // I create a copy so that the best solution is preserved independently
                bestSolution = new HashMap<>(ant.getSolution());
            }
        }
        
        // I track stagnation so that we can stop early if the algorithm has converged
        if (parameters.getBoolean(AlgorithmParameters.ENABLE_EARLY_STOPPING)) {
            double improvement = oldBestFitness - bestFitness;
            double threshold = parameters.getDouble(AlgorithmParameters.FITNESS_IMPROVEMENT_THRESHOLD);
            
            if (improvement < threshold) {
                stagnationCounter++;
            } else {
                // I reset the counter when improvement occurs so that temporary plateaus don't trigger early stop
                stagnationCounter = 0;
            }
        }
    }
    
    private void updatePheromones() {
        // I calculate adaptive evaporation rate based on solution quality diversity
        double evaporationRate = calculateAdaptiveEvaporation();
        
        // I evaporate existing pheromones to forget old, potentially suboptimal paths
        for (int i = 0; i < pheromoneMatrix.length; i++) {
            for (int j = 0; j < pheromoneMatrix[i].length; j++) {
                pheromoneMatrix[i][j] *= (1.0 - evaporationRate);
                
                // I maintain minimum pheromone so that all paths remain explorable
                double minPheromone = parameters.getDouble(AlgorithmParameters.MIN_PHEROMONE);
                pheromoneMatrix[i][j] = Math.max(pheromoneMatrix[i][j], minPheromone);
            }
        }
        
        // I let each ant deposit pheromones proportional to its solution quality
        for (Ant ant : ants) {
            depositPheromones(ant);
        }
        
        // I apply extra reinforcement to the best solution to guide future ants
        reinforceBestSolution();
        
        // I cap pheromone levels to prevent any single path from dominating
        limitPheromoneLevel();
    }
    
    private double calculateAdaptiveEvaporation() {
        // I adjust evaporation rate based on solution diversity so that exploration
        // increases when ants are finding similar solutions (low diversity)
        
        double rhoMin = parameters.getDouble(AlgorithmParameters.EVAPORATION_MIN);
        double rhoMax = parameters.getDouble(AlgorithmParameters.EVAPORATION_MAX);
        
        // I calculate average fitness to measure solution diversity
        double avgFitness = calculateAverageFitness();
        double bestFitnessValue = bestFitness;
        
        // I handle edge case where best fitness is non-positive
        if (bestFitnessValue <= 0) {
            return rhoMax; // Maximum evaporation for maximum exploration
        }
        
        // I increase evaporation when solutions are diverse (large fitness gap)
        // and decrease it when solutions are similar (small fitness gap) to converge
        double adaptiveRate = rhoMin + (rhoMax - rhoMin) * 
                             ((avgFitness - bestFitnessValue) / bestFitnessValue);
        
        // I ensure the rate stays within configured bounds
        return Math.max(rhoMin, Math.min(rhoMax, adaptiveRate));
    }
    
    
    
    private void depositPheromones(Ant ant) {
        // I calculate pheromone amount inversely proportional to fitness
        // Better solutions (lower fitness) deposit more pheromone
        double pheromoneDeposit = 1.0 / (1.0 + ant.getFitness());
        Map<Cloudlet, Vm> solution = ant.getSolution();
        
        // I reinforce the paths taken by this ant
        for (int i = 0; i < cloudlets.size(); i++) {
            Cloudlet cloudlet = cloudlets.get(i);
            Vm assignedVm = solution.get(cloudlet);
            
            if (assignedVm != null) {
                int vmIndex = vms.indexOf(assignedVm);
                if (vmIndex >= 0) {
                    // I add pheromone to the cloudlet-VM pair used in this solution
                    pheromoneMatrix[i][vmIndex] += pheromoneDeposit;
                }
            }
        }
    }
    
    private void reinforceBestSolution() {
        if (bestSolution == null) return;
        
        // I implement load-based reinforcement so that well-balanced solutions
        // get extra pheromone reinforcement
        
        // I calculate the load on each VM in the best solution
        Map<Vm, Double> vmLoads = new HashMap<>();
        for (Map.Entry<Cloudlet, Vm> entry : bestSolution.entrySet()) {
            Cloudlet cloudlet = entry.getKey();
            Vm vm = entry.getValue();
            double load = cloudlet.getCloudletLength() / vm.getMips();
            vmLoads.put(vm, vmLoads.getOrDefault(vm, 0.0) + load);
        }
        
        // I apply reinforcement inversely proportional to VM load
        // This encourages balanced load distribution in future solutions
        for (int i = 0; i < cloudlets.size(); i++) {
            Cloudlet cloudlet = cloudlets.get(i);
            Vm assignedVm = bestSolution.get(cloudlet);
            
            if (assignedVm != null) {
                int vmIndex = vms.indexOf(assignedVm);
                if (vmIndex >= 0) {
                    double vmLoad = vmLoads.getOrDefault(assignedVm, 0.0);
                    // I give more reinforcement to assignments that use less loaded VMs
                    double reinforcement = 1.0 / (1.0 + vmLoad);
                    pheromoneMatrix[i][vmIndex] += reinforcement;
                }
            }
        }
    }
    
    private void limitPheromoneLevel() {
        double maxPheromone = parameters.getDouble(AlgorithmParameters.MAX_PHEROMONE);
        
        // I cap pheromone levels so that no single path becomes too dominant
        // This maintains exploration capability throughout the optimization
        for (int i = 0; i < pheromoneMatrix.length; i++) {
            for (int j = 0; j < pheromoneMatrix[i].length; j++) {
                pheromoneMatrix[i][j] = Math.min(pheromoneMatrix[i][j], maxPheromone);
            }
        }
    }
    
    private double calculateFitness(Map<Cloudlet, Vm> schedule) {
        int scheduleHash = schedule.hashCode();
        Double cachedFitness = fitnessCache.get(scheduleHash);
        if (cachedFitness != null) {
            return cachedFitness;
        }
        
        double fitness = AlgorithmMetricUtils.calculateFitness(schedule, cloudlets, vms, parameters);
        fitnessCache.put(scheduleHash, fitness);
        return fitness;
    }
    
    // these methods were  planned to be removed as they duplicate functionality in AlgorithmMetricUtils
    // I switch now to AlgorithmMetricUtils.makespan(), cost(), energy(), loadBalance() instead
    
    private void calculateMetrics(Map<Cloudlet, Vm> schedule) {
        metrics.clear();
        metrics.put("makespan", AlgorithmMetricUtils.makespan(schedule));
        metrics.put("cost", AlgorithmMetricUtils.cost(schedule));
        metrics.put("energy", AlgorithmMetricUtils.energy(schedule));
        metrics.put("degreeOfImbalance", AlgorithmMetricUtils.degreeOfImbalance(schedule));
        metrics.put("responseTime", AlgorithmMetricUtils.responseTime(schedule));
        metrics.put("iterations", (double) currentIteration);
        metrics.put("converged", currentIteration < parameters.getInt(AlgorithmParameters.MAX_ITERATIONS) ? 1.0 : 0.0);
        metrics.put("pheromoneConvergence", calculatePheromoneConvergence());
        double evaporationRate = calculateAdaptiveEvaporation();
        metrics.put("evaporationRate", evaporationRate);
        
        // Calculate and store the fitness value
        double fitness = calculateFitness(schedule);
        metrics.put("fitness", fitness);
        
        // Debug output - should be controlled by configuration
        if (shouldDebug()) {
            System.out.println("[EACO] Final Metrics:");
            System.out.println("  Makespan: " + metrics.get("makespan"));
            System.out.println("  Cost: " + metrics.get("cost"));
            System.out.println("  Energy: " + metrics.get("energy"));
            System.out.println("  Load Balance: " + metrics.get("loadBalance"));
            System.out.println("  Response Time: " + metrics.get("responseTime"));
            System.out.println("  Fitness: " + fitness);
        }
    }
    
    private boolean shouldDebug() {
        // For now, return false since we dont need that much debugging in the epso
        return false;
    }
    
    private double calculatePheromoneConvergence() {
        if (pheromoneMatrix == null) return 0.0;
        
        double totalVariance = 0.0;
        int count = 0;
        
        for (int i = 0; i < pheromoneMatrix.length; i++) {
            double[] row = pheromoneMatrix[i];
            double mean = Arrays.stream(row).average().orElse(0.0);
            double variance = Arrays.stream(row).map(x -> Math.pow(x - mean, 2)).average().orElse(0.0);
            totalVariance += variance;
            count++;
        }
        
        double convergence = count > 0 ? totalVariance / count : 0.0;
        
        // Store for early stopping check
        if (parameters.getBoolean(AlgorithmParameters.ENABLE_EARLY_STOPPING)) {
            previousPheromoneConvergence = convergence;
        }
        
        return convergence;
    }
    
    private double calculateAverageFitness() {
        if (ants.isEmpty()) return Double.MAX_VALUE;
        
        double totalFitness = 0.0;
        for (Ant ant : ants) {
            totalFitness += ant.getFitness();
        }
        
        return totalFitness / ants.size();
    }
    
    @Override
    public String getAlgorithmName() {
        return ALGORITHM_NAME;
    }
    
    @Override
    public Map<String, Double> getMetrics() {
        return new HashMap<>(metrics);
    }
    
    @Override
    public void reset() {
        ants.clear();
        pheromoneMatrix = null;
        heuristicMatrix = null;
        bestSolution = null;
        bestFitness = Double.MAX_VALUE;
        currentIteration = 0;
        metrics.clear();
        previousBestFitness = Double.MAX_VALUE;
        stagnationCounter = 0;
        previousPheromoneConvergence = Double.MAX_VALUE;
        fitnessCache.clear();
    }
    
    // Ant class
    private class Ant {
        private final Map<Cloudlet, Vm> solution;
        private double fitness;
        
        public Ant(int cloudletCount) {
            this.solution = new HashMap<>();
            this.fitness = Double.MAX_VALUE;
        }
        
        public void constructSolution() {
            solution.clear();
            
            // Probabilistic solution
            for (int i = 0; i < cloudlets.size(); i++) {
                Cloudlet cloudlet = cloudlets.get(i);
                Vm selectedVm = selectVm(i);
                solution.put(cloudlet, selectedVm);
            }
        }
        
        private Vm selectVm(int cloudletIndex) {
            double[] probabilities = calculateProbabilities(cloudletIndex);
            double randomValue = random.nextDouble();
            double cumulativeProbability = 0.0;
            
            for (int j = 0; j < probabilities.length; j++) {
                cumulativeProbability += probabilities[j];
                if (randomValue <= cumulativeProbability) {
                    return vms.get(j);
                }
            }
            
            // Fallback to last VM
            return vms.get(vms.size() - 1);
        }
        
        private double[] calculateProbabilities(int cloudletIndex) {
            double alpha = parameters.getDouble(AlgorithmParameters.ALPHA);
            double beta = parameters.getDouble(AlgorithmParameters.BETA);
            double[] probabilities = new double[vms.size()];
            double totalProbability = 0.0;
            
            // this to calculate probabilities based on pheromone and heuristic information
            for (int j = 0; j < vms.size(); j++) {
                double pheromone = Math.pow(pheromoneMatrix[cloudletIndex][j], alpha);
                double heuristic = Math.pow(heuristicMatrix[cloudletIndex][j], beta);
                probabilities[j] = pheromone * heuristic;
                totalProbability += probabilities[j];
            }
            
            // Normalize probabilities
            if (totalProbability > 0) {
                for (int j = 0; j < probabilities.length; j++) {
                    probabilities[j] /= totalProbability;
                }
            } else {
                // Uniform distribution if no valid probability
                Arrays.fill(probabilities, 1.0 / vms.size());
            }
            
            return probabilities;
        }
        
        public Map<Cloudlet, Vm> getSolution() {
            return solution;
        }
        
        public double getFitness() {
            return fitness;
        }
        
        public void setFitness(double fitness) {
            this.fitness = fitness;
        }
    }
    
    private boolean shouldStopEarly() {
        if (!parameters.getBoolean(AlgorithmParameters.ENABLE_EARLY_STOPPING)) {
            return false;
        }
        
        // Check fitness stagnation
        int maxStagnation = parameters.getInt(AlgorithmParameters.STAGNATION_ITERATIONS);
        if (stagnationCounter >= maxStagnation) {
            return true;
        }
        
        // Check pheromone convergence (ACO-specific)
        double varianceThreshold = parameters.getDouble(AlgorithmParameters.PHEROMONE_VARIANCE_THRESHOLD);
        double currentConvergence = calculatePheromoneConvergence();
        if (currentConvergence < varianceThreshold && currentConvergence > 0) {
            return true;  // Pheromones have converged
        }
        
        return false;
    }
    
    private String getStoppingReason() {
        if (stagnationCounter >= parameters.getInt(AlgorithmParameters.STAGNATION_ITERATIONS)) {
            return "Fitness stagnation (" + stagnationCounter + " iterations without improvement)";
        }
        double currentConvergence = calculatePheromoneConvergence();
        if (currentConvergence < parameters.getDouble(AlgorithmParameters.PHEROMONE_VARIANCE_THRESHOLD)) {
            return "Pheromone convergence (variance=" + String.format("%.6f", currentConvergence) + ")";
        }
        return "Unknown";
    }
    

    private Map<Cloudlet, Vm> applyLoadBalancing(Map<Cloudlet, Vm> originalSchedule) {
        if (originalSchedule == null || cloudlets.size() <= vms.size()) {
            return originalSchedule;
        }
        
        Map<Cloudlet, Vm> balancedSchedule = new HashMap<>(originalSchedule);
        LoadBalancer balancer = new LoadBalancer(balancedSchedule, vms.size());
        
        // Record current assignments
        for (Map.Entry<Cloudlet, Vm> entry : balancedSchedule.entrySet()) {
            int vmIndex = vms.indexOf(entry.getValue());
            if (vmIndex >= 0) {
                balancer.recordAssignment(vmIndex);
            }
        }
        
        balancer.balance();
        
        return balancedSchedule;
    }
    
    /**
     * Load balancer implementation identical to EPSO for fair comparison
     * This ensures both algorithms have the same post-optimization processing
     */
    private class LoadBalancer {
        private final Map<Cloudlet, Vm> schedule;
        private final int[] vmUsage;
        private final int vmCount;
        
        public LoadBalancer(Map<Cloudlet, Vm> schedule, int vmCount) {
            this.schedule = schedule;
            this.vmCount = vmCount;
            this.vmUsage = new int[vmCount];
        }
        
        public void recordAssignment(int vmIndex) {
            vmUsage[vmIndex]++;
        }
        
        public void balance() {
            for (int vmIdx = 0; vmIdx < vmCount; vmIdx++) {
                if (isUnderutilized(vmIdx)) {
                    redistributeToVm(vmIdx);
                }
            }
        }
        
        private boolean isUnderutilized(int vmIdx) {
            return vmUsage[vmIdx] == 0;
        }
        
        private void redistributeToVm(int targetVmIdx) {
            int sourceVmIdx = findMostLoadedVm();
            
            // Use proportional threshold instead of hardcoded value
            int minTasksBeforeRedistribution = Math.max(1, cloudlets.size() / vms.size());
            if (vmUsage[sourceVmIdx] > minTasksBeforeRedistribution) {
                moveOneCloudlet(sourceVmIdx, targetVmIdx);
            }
        }
        
        private int findMostLoadedVm() {
            int maxLoadedVm = 0;
            int maxLoad = vmUsage[0];
            
            for (int i = 1; i < vmCount; i++) {
                if (vmUsage[i] > maxLoad) {
                    maxLoad = vmUsage[i];
                    maxLoadedVm = i;
                }
            }
            
            return maxLoadedVm;
        }
        
        private void moveOneCloudlet(int fromVmIdx, int toVmIdx) {
            Vm sourceVm = vms.get(fromVmIdx);
            Vm targetVm = vms.get(toVmIdx);
            
            for (Map.Entry<Cloudlet, Vm> entry : schedule.entrySet()) {
                if (entry.getValue().equals(sourceVm)) {
                    schedule.put(entry.getKey(), targetVm);
                    vmUsage[fromVmIdx]--;
                    vmUsage[toVmIdx]++;
                    break; // Only move one task at a time for stability
                }
            }
        }
    }
    
    // LoadBalancer functionality moved to shared utility class
}
