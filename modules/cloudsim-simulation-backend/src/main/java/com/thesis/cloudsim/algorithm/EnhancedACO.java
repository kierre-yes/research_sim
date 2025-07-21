package com.thesis.cloudsim.algorithm;

import org.cloudbus.cloudsim.Cloudlet;
import org.cloudbus.cloudsim.Vm;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
// IntStream removed – basic loops are easier for beginners
import static com.thesis.cloudsim.algorithm.AlgorithmMetricUtils.*;

/**
 * Enhanced Ant Colony Optimization (EACO).
 * ----------------------------------------
 * Maintains full algorithmic detail but is heavily commented so a newcomer
 * understands the flow:
 *   • initialiseMatrices()  – create pheromone & heuristic tables
 *   • main loop            – constructSolutions → evaluate → updatePheromones
 *   • bestSolution map     – final Cloudlet→VM assignment
 *
 * Complex maths (pheromone evaporation, diversity measure) is left intact to
 * preserve research accuracy; rewriting them in simpler constructs would
 * flatten the optimisation capability. Instead we guide the reader with
 * comments and keep imports minimal.
 */
public class EnhancedACO implements ISchedulingAlgorithm {
    
    private static final String ALGORITHM_NAME = "Enhanced ACO";
    private final Map<String, Double> metrics;
    private final Random random;
    
    // ACO components
    private List<Ant> ants;
    private double[][] pheromoneMatrix;
    private double[][] heuristicMatrix;
    private Map<Cloudlet, Vm> bestSolution;
    private double bestFitness;
    private int currentIteration;
    
    // CloudSim entities
    private List<Cloudlet> cloudlets;
    private List<Vm> vms;
    private AlgorithmParameters parameters;
    
    public EnhancedACO() {
        this.metrics = new HashMap<>();
        this.random = ThreadLocalRandom.current();
        this.ants = new ArrayList<>();
        this.bestFitness = Double.MAX_VALUE;
        this.currentIteration = 0;
    }
    
    @Override
    public Map<Cloudlet, Vm> schedule(List<Cloudlet> cloudlets, List<Vm> vms, AlgorithmParameters parameters) {
        this.cloudlets = new ArrayList<>(cloudlets);
        this.vms = new ArrayList<>(vms);
        this.parameters = parameters;
        
        // Initialize ACO components
        initializeMatrices();
        initializeAnts();
        
        // ACO main loop
        for (currentIteration = 0; currentIteration < parameters.getInt(AlgorithmParameters.MAX_ITERATIONS); currentIteration++) {
            // Construct solutions
            constructSolutions();
            
            // Update best solution
            updateBestSolution();
            
            // Update pheromone trails
            updatePheromones();
        }
        
        // Calculate and store metrics
        calculateMetrics(bestSolution);
        
        return new HashMap<>(bestSolution);
    }
    
    private void initializeMatrices() {
        int cloudletCount = cloudlets.size();
        int vmCount = vms.size();
        
        // Initialize pheromone matrix
        pheromoneMatrix = new double[cloudletCount][vmCount];
        double initialPheromone = parameters.getDouble(AlgorithmParameters.INITIAL_PHEROMONE);
        
        for (int i = 0; i < cloudletCount; i++) {
            for (int j = 0; j < vmCount; j++) {
                pheromoneMatrix[i][j] = initialPheromone;
            }
        }
        
        // Initialize heuristic matrix
        heuristicMatrix = new double[cloudletCount][vmCount];
        calculateHeuristicMatrix();
    }
    
    private void calculateHeuristicMatrix() {
        for (int i = 0; i < cloudlets.size(); i++) {
            Cloudlet cloudlet = cloudlets.get(i);
            for (int j = 0; j < vms.size(); j++) {
                Vm vm = vms.get(j);
                
                // Heuristic based on execution time and resource availability
                double executionTime = cloudlet.getCloudletLength() / vm.getMips();
                double vmRamCapacity = vm.getRam();
                double hostRamCapacity = vm.getHost().getGuestRamProvisioner().getRam();
                double resourceRatio = vmRamCapacity / hostRamCapacity;
                
                // Inverse of execution time with resource consideration
                heuristicMatrix[i][j] = (1.0 / executionTime) * resourceRatio;
            }
        }
    }
    
    private void initializeAnts() {
        int populationSize = parameters.getInt(AlgorithmParameters.POPULATION_SIZE);
        ants.clear();
        
        for (int i = 0; i < populationSize; i++) {
            ants.add(new Ant(cloudlets.size()));
        }
    }
    
    private void constructSolutions() {
        for (Ant ant : ants) {
            ant.constructSolution();
            
            // Evaluate ant's solution
            Map<Cloudlet, Vm> solution = ant.getSolution();
            double fitness = calculateFitness(solution);
            ant.setFitness(fitness);
        }
    }
    
    private void updateBestSolution() {
        for (Ant ant : ants) {
            if (ant.getFitness() < bestFitness) {
                bestFitness = ant.getFitness();
                bestSolution = new HashMap<>(ant.getSolution());
            }
        }
    }
    
    private void updatePheromones() {
        // Adaptive pheromone evaporation
        double evaporationRate = calculateAdaptiveEvaporation();
        
        // Evaporate pheromones
        for (int i = 0; i < pheromoneMatrix.length; i++) {
            for (int j = 0; j < pheromoneMatrix[i].length; j++) {
                pheromoneMatrix[i][j] *= (1.0 - evaporationRate);
                
                // Ensure minimum pheromone level
                double minPheromone = parameters.getDouble(AlgorithmParameters.MIN_PHEROMONE);
                pheromoneMatrix[i][j] = Math.max(pheromoneMatrix[i][j], minPheromone);
            }
        }
        
        // Deposit pheromones from ants
        for (Ant ant : ants) {
            depositPheromones(ant);
        }
        
        // Load-based reinforcement for best solution
        reinforceBestSolution();
        
        // Ensure maximum pheromone level
        limitPheromoneLevel();
    }
    
    private double calculateAdaptiveEvaporation() {
        // Adaptive pheromone evaporation based on iteration progress and diversity
        double baseEvaporation = parameters.getDouble(AlgorithmParameters.PHEROMONE_DECAY);
        double maxIter = parameters.getInt(AlgorithmParameters.MAX_ITERATIONS);
        double progress = (double) currentIteration / maxIter;
        
        // Calculate diversity of current solutions
        double diversity = calculateSolutionDiversity();
        
        // Adaptive formula: higher evaporation early, lower when converging
        double adaptiveFactor = 0.5 + 0.5 * Math.exp(-diversity * 2.0);
        double timeDecay = 1.0 - Math.exp(-progress * 3.0);
        
        return baseEvaporation * adaptiveFactor * (1.0 - timeDecay * 0.5);
    }
    
    private double calculateSolutionDiversity() {
        if (ants.size() <= 1) return 1.0;
        
        double totalDifference = 0.0;
        int comparisons = 0;
        
        for (int i = 0; i < ants.size(); i++) {
            for (int j = i + 1; j < ants.size(); j++) {
                totalDifference += calculateSolutionDifference(ants.get(i), ants.get(j));
                comparisons++;
            }
        }
        
        return comparisons > 0 ? totalDifference / comparisons : 1.0;
    }
    
    private double calculateSolutionDifference(Ant ant1, Ant ant2) {
        Map<Cloudlet, Vm> solution1 = ant1.getSolution();
        Map<Cloudlet, Vm> solution2 = ant2.getSolution();
        
        int differences = 0;
        for (Cloudlet cloudlet : cloudlets) {
            Vm vm1 = solution1.get(cloudlet);
            Vm vm2 = solution2.get(cloudlet);
            if (vm1 != null && vm2 != null && !vm1.equals(vm2)) {
                differences++;
            }
        }
        
        return (double) differences / cloudlets.size();
    }
    
    private void depositPheromones(Ant ant) {
        double pheromoneDeposit = 1.0 / (1.0 + ant.getFitness());
        Map<Cloudlet, Vm> solution = ant.getSolution();
        
        for (int i = 0; i < cloudlets.size(); i++) {
            Cloudlet cloudlet = cloudlets.get(i);
            Vm assignedVm = solution.get(cloudlet);
            
            if (assignedVm != null) {
                int vmIndex = vms.indexOf(assignedVm);
                if (vmIndex >= 0) {
                    pheromoneMatrix[i][vmIndex] += pheromoneDeposit;
                }
            }
        }
    }
    
    private void reinforceBestSolution() {
        if (bestSolution == null) return;
        
        // Load-based reinforcement: stronger reinforcement for better load balance
        double loadBalanceScore = 1.0 / (1.0 + calculateLoadBalance(bestSolution));
        double reinforcement = loadBalanceScore * (1.0 / (1.0 + bestFitness));
        
        for (int i = 0; i < cloudlets.size(); i++) {
            Cloudlet cloudlet = cloudlets.get(i);
            Vm assignedVm = bestSolution.get(cloudlet);
            
            if (assignedVm != null) {
                int vmIndex = vms.indexOf(assignedVm);
                if (vmIndex >= 0) {
                    pheromoneMatrix[i][vmIndex] += reinforcement;
                }
            }
        }
    }
    
    private void limitPheromoneLevel() {
        double maxPheromone = parameters.getDouble(AlgorithmParameters.MAX_PHEROMONE);
        
        for (int i = 0; i < pheromoneMatrix.length; i++) {
            for (int j = 0; j < pheromoneMatrix[i].length; j++) {
                pheromoneMatrix[i][j] = Math.min(pheromoneMatrix[i][j], maxPheromone);
            }
        }
    }
    
    private double calculateFitness(Map<Cloudlet, Vm> schedule) {
        // Multi-objective fitness calculation (same as EPSO)
        double makespan = AlgorithmMetricUtils.makespan(schedule);
        double cost = AlgorithmMetricUtils.enhancedCost(schedule, cloudlets, vms);
        double energy = AlgorithmMetricUtils.energy(schedule);
        double loadBalance = AlgorithmMetricUtils.loadBalance(schedule);

        // Normalize metrics
        makespan = AlgorithmMetricUtils.normalise("makespan", makespan, cloudlets, vms);
        cost = AlgorithmMetricUtils.normalise("enhancedCost", cost, cloudlets, vms);
        energy = AlgorithmMetricUtils.normalise("energy", energy, cloudlets, vms);
        loadBalance = AlgorithmMetricUtils.normalise("loadBalance", loadBalance, cloudlets, vms);
        
        // Weighted sum calculation
        double fitness = parameters.getDouble(AlgorithmParameters.MAKESPAN_WEIGHT) * makespan +
                        parameters.getDouble(AlgorithmParameters.COST_WEIGHT) * cost +
                        parameters.getDouble(AlgorithmParameters.ENERGY_WEIGHT) * energy +
                        parameters.getDouble(AlgorithmParameters.LOAD_BALANCE_WEIGHT) * loadBalance;
        
        return fitness;
    }
    
    private double calculateMakespan(Map<Cloudlet, Vm> schedule) {
        Map<Vm, Double> vmFinishTimes = new HashMap<>();
        
        for (Map.Entry<Cloudlet, Vm> entry : schedule.entrySet()) {
            Cloudlet cloudlet = entry.getKey();
            Vm vm = entry.getValue();
            
            double executionTime = cloudlet.getCloudletLength() / vm.getMips();
            double currentFinish = vmFinishTimes.getOrDefault(vm, 0.0);
            vmFinishTimes.put(vm, currentFinish + executionTime);
        }
        
        return vmFinishTimes.values().stream().mapToDouble(Double::doubleValue).max().orElse(0.0);
    }
    
    private double calculateCost(Map<Cloudlet, Vm> schedule) {
        double totalCost = 0.0;
        
        for (Map.Entry<Cloudlet, Vm> entry : schedule.entrySet()) {
            Cloudlet cloudlet = entry.getKey();
            Vm vm = entry.getValue();
            
            double executionTime = cloudlet.getCloudletLength() / vm.getMips();
            double hostRamCapacity = vm.getHost().getGuestRamProvisioner().getRam();
            double vmCostPerSecond = hostRamCapacity * 0.001;
            totalCost += executionTime * vmCostPerSecond;
        }
        
        return totalCost;
    }
    
    private double calculateEnergy(Map<Cloudlet, Vm> schedule) {
        double totalEnergy = 0.0;
        
        for (Map.Entry<Cloudlet, Vm> entry : schedule.entrySet()) {
            Cloudlet cloudlet = entry.getKey();
            Vm vm = entry.getValue();
            
            double executionTime = cloudlet.getCloudletLength() / vm.getMips();
            // Simplified power consumption calculation
            double powerConsumption = 100.0; // Basic estimate
            totalEnergy += executionTime * powerConsumption;
        }
        
        return totalEnergy;
    }
    
    private double calculateLoadBalance(Map<Cloudlet, Vm> schedule) {
        Map<Vm, Double> vmLoads = new HashMap<>();
        
        for (Map.Entry<Cloudlet, Vm> entry : schedule.entrySet()) {
            Cloudlet cloudlet = entry.getKey();
            Vm vm = entry.getValue();
            
            double load = cloudlet.getCloudletLength() / vm.getMips();
            vmLoads.put(vm, vmLoads.getOrDefault(vm, 0.0) + load);
        }
        
        double[] loads = vmLoads.values().stream().mapToDouble(Double::doubleValue).toArray();
        double mean = Arrays.stream(loads).average().orElse(0.0);
        double variance = Arrays.stream(loads).map(x -> Math.pow(x - mean, 2)).average().orElse(0.0);
        
        return Math.sqrt(variance);
    }
    
    private double normalizeMetric(double value, String metricType) {
        double minValue = 0.0;
        double maxValue = 1.0;
        
        switch (metricType) {
            case "makespan":
                maxValue = cloudlets.stream().mapToDouble(Cloudlet::getCloudletLength).sum() / 
                          vms.stream().mapToDouble(Vm::getMips).min().orElse(1.0);
                break;
            case "cost":
                maxValue = cloudlets.stream().mapToDouble(Cloudlet::getCloudletLength).sum() * 0.1;
                break;
            case "energy":
                maxValue = cloudlets.stream().mapToDouble(Cloudlet::getCloudletLength).sum() * 100.0;
                break;
            case "loadBalance":
                maxValue = cloudlets.stream().mapToDouble(Cloudlet::getCloudletLength).sum();
                break;
        }
        
        return Math.max(0.0, Math.min(1.0, (value - minValue) / (maxValue - minValue)));
    }
    
    private void calculateMetrics(Map<Cloudlet, Vm> schedule) {
        metrics.clear();
        metrics.put("makespan", calculateMakespan(schedule));
        metrics.put("cost", calculateCost(schedule));
        metrics.put("energy", calculateEnergy(schedule));
        metrics.put("loadBalance", calculateLoadBalance(schedule));
        metrics.put("iterations", (double) currentIteration);
        metrics.put("converged", currentIteration < parameters.getInt(AlgorithmParameters.MAX_ITERATIONS) ? 1.0 : 0.0);
        metrics.put("pheromoneConvergence", calculatePheromoneConvergence());
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
        
        return count > 0 ? totalVariance / count : 0.0;
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
    }
    
    /**
     * Ant class representing a solution constructor in the ACO algorithm
     */
    private class Ant {
        private final Map<Cloudlet, Vm> solution;
        private double fitness;
        
        public Ant(int cloudletCount) {
            this.solution = new HashMap<>();
            this.fitness = Double.MAX_VALUE;
        }
        
        public void constructSolution() {
            solution.clear();
            
            // Probabilistic solution construction
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
            
            // Calculate probabilities based on pheromone and heuristic information
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
}
