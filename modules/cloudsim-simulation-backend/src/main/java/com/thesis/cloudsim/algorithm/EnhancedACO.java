package com.thesis.cloudsim.algorithm;

import org.cloudbus.cloudsim.Cloudlet;
import org.cloudbus.cloudsim.Vm;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import static com.thesis.cloudsim.algorithm.AlgorithmMetricUtils.*;

// Enhanced Ant Colony Optimization for task scheduling
public class EnhancedACO implements ISchedulingAlgorithm {
    
    private static final String ALGORITHM_NAME = "EACO";
    private final Map<String, Double> metrics;
    private final Random random;
    
// Ant Colony Optimization components
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
        
        // System.out.println("[EACO] Starting with " + cloudlets.size() + " cloudlets, " + vms.size() + " VMs");
        
        initializeMatrices();
        initializeAnts();
        
        // Main loop for ant colony optimization
        for (currentIteration = 0; currentIteration < parameters.getInt(AlgorithmParameters.MAX_ITERATIONS); currentIteration++) {
            constructSolutions();
            updateBestSolution();
            updatePheromones();
            
// Debug output example removed for clarity
        }
        
        calculateMetrics(bestSolution);
        
        return new HashMap<>(bestSolution);
    }
    
    private void initializeMatrices() {
        int cloudletCount = cloudlets.size();
        int vmCount = vms.size();
        
        pheromoneMatrix = new double[cloudletCount][vmCount];
        double initialPheromone = parameters.getDouble(AlgorithmParameters.INITIAL_PHEROMONE);
        
        // Initialize matrices with random variation
        for (int i = 0; i < cloudletCount; i++) {
            for (int j = 0; j < vmCount; j++) {
                // Apply random variation
                double variation = 0.95 + (random.nextDouble() * 0.1);
                pheromoneMatrix[i][j] = initialPheromone * variation;
            }
        }
        
        heuristicMatrix = new double[cloudletCount][vmCount];
        calculateHeuristicMatrix();
    }
    
    private void calculateHeuristicMatrix() {
        for (int i = 0; i < cloudlets.size(); i++) {
            Cloudlet cloudlet = cloudlets.get(i);
            for (int j = 0; j < vms.size(); j++) {
                Vm vm = vms.get(j);
                
                double executionTime = cloudlet.getCloudletLength() / vm.getMips();
                
                // Assumes host is always available
                double resourceRatio = 1.0;
                if (vm.getHost() != null) {
                    double vmRamCapacity = vm.getRam();
                    double hostRamCapacity = vm.getHost().getGuestRamProvisioner().getRam();
                    resourceRatio = vmRamCapacity / hostRamCapacity;
                }
                
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
        // Calculate pheromone evaporation adaptively
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
        // Adaptive pheromone evaporation formula based on fitness
        
        double rhoMin = parameters.getDouble(AlgorithmParameters.EVAPORATION_MIN);
        double rhoMax = parameters.getDouble(AlgorithmParameters.EVAPORATION_MAX);
        
        // Calculate average fitness of current iteration
        double avgFitness = calculateAverageFitness();
        double bestFitnessValue = bestFitness;
        
        // Avoid division by zero
        if (bestFitnessValue <= 0) {
            return rhoMax; // Maximum evaporation if best fitness is 0 or negative
        }
        
        // Calculate adaptive rate
        double adaptiveRate = rhoMin + (rhoMax - rhoMin) * 
                             ((bestFitnessValue - avgFitness) / bestFitnessValue);
        
        // Ensure the rate is within bounds
        return Math.max(rhoMin, Math.min(rhoMax, adaptiveRate));
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
        
        // Load-based reinforcement
        
        // Calculate VM loads
        Map<Vm, Double> vmLoads = new HashMap<>();
        for (Map.Entry<Cloudlet, Vm> entry : bestSolution.entrySet()) {
            Cloudlet cloudlet = entry.getKey();
            Vm vm = entry.getValue();
            double load = cloudlet.getCloudletLength() / vm.getMips();
            vmLoads.put(vm, vmLoads.getOrDefault(vm, 0.0) + load);
        }
        
        // Apply load-based reinforcement
        for (int i = 0; i < cloudlets.size(); i++) {
            Cloudlet cloudlet = cloudlets.get(i);
            Vm assignedVm = bestSolution.get(cloudlet);
            
            if (assignedVm != null) {
                int vmIndex = vms.indexOf(assignedVm);
                if (vmIndex >= 0) {
                    double vmLoad = vmLoads.getOrDefault(assignedVm, 0.0);
                    double reinforcement = 1.0 / (1.0 + vmLoad);
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
        // Multi-objective fitness
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
            double powerConsumption = 100.0;
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
        
        // Calculate and store the fitness value
        double fitness = calculateFitness(schedule);
        metrics.put("fitness", fitness);
        
        // Debug output
        System.out.println("[EACO] Final Metrics:");
        System.out.println("  Makespan: " + metrics.get("makespan"));
        System.out.println("  Cost: " + metrics.get("cost"));
        System.out.println("  Energy: " + metrics.get("energy"));
        System.out.println("  Load Balance: " + metrics.get("loadBalance"));
        System.out.println("  Fitness: " + fitness);
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
