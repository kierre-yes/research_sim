package com.thesis.cloudsim.algorithm;

import org.cloudbus.cloudsim.Cloudlet;
import org.cloudbus.cloudsim.Vm;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import static com.thesis.cloudsim.algorithm.AlgorithmMetricUtils.*;


public class BaselineACO implements ISchedulingAlgorithm {
    
    private static final String ALGORITHM_NAME = "BACO";
    private final Map<String, Double> metrics;
    private final Random random;
    
    private List<Ant> ants;
    private double[][] pheromoneMatrix;
    private double[][] heuristicMatrix;
    private Map<Cloudlet, Vm> bestSolution;
    private double bestFitness;
    private int currentIteration;
    
    private double previousBestFitness;
    private int stagnationCounter;
    
    private List<Cloudlet> cloudlets;
    private List<Vm> vms;
    private AlgorithmParameters parameters;
    
    public BaselineACO() {
        this.metrics = new HashMap<>();
        this.random = new Random();
        this.ants = new ArrayList<>();
        this.bestFitness = Double.MAX_VALUE;
        this.currentIteration = 0;
        this.previousBestFitness = Double.MAX_VALUE;
        this.stagnationCounter = 0;
    }
    
    public BaselineACO(long seed) {
        this.metrics = new HashMap<>();
        this.random = new Random(seed);
        this.ants = new ArrayList<>();
        this.bestFitness = Double.MAX_VALUE;
        this.currentIteration = 0;
        this.previousBestFitness = Double.MAX_VALUE;
        this.stagnationCounter = 0;
    }
    
    @Override
    public Map<Cloudlet, Vm> schedule(List<Cloudlet> cloudlets, List<Vm> vms, AlgorithmParameters parameters) {
        this.cloudlets = new ArrayList<>(cloudlets);
        this.vms = new ArrayList<>(vms);
        this.parameters = parameters;
        
        initializeMatrices();
        initializeAnts();
        
        for (currentIteration = 0; currentIteration < parameters.getInt(AlgorithmParameters.MAX_ITERATIONS); currentIteration++) {
            constructSolutions();
            updateBestSolution();
            updatePheromones();
            
            if (shouldStopEarly()) {
                break;
            }
        }
        
        Map<Cloudlet, Vm> balancedSolution = applyLoadBalancing(bestSolution);
        calculateMetrics(balancedSolution);
        return new HashMap<>(balancedSolution);
    }
    
    private void initializeMatrices() {
        int cloudletCount = cloudlets.size();
        int vmCount = vms.size();
        
        pheromoneMatrix = new double[cloudletCount][vmCount];
        double initialPheromone = parameters.getDouble(AlgorithmParameters.INITIAL_PHEROMONE);
        
        // Initialize pheromones uniformly (no random variation in baseline)
        for (int i = 0; i < cloudletCount; i++) {
            for (int j = 0; j < vmCount; j++) {
                pheromoneMatrix[i][j] = initialPheromone;
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
                
                // Simple heuristic without resource considerations
                heuristicMatrix[i][j] = 1.0 / executionTime;
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
        double oldBestFitness = bestFitness;
        
        for (Ant ant : ants) {
            if (ant.getFitness() < bestFitness) {
                bestFitness = ant.getFitness();
                bestSolution = new HashMap<>(ant.getSolution());
            }
        }
        
        if (parameters.getBoolean(AlgorithmParameters.ENABLE_EARLY_STOPPING)) {
            double improvement = oldBestFitness - bestFitness;
            double threshold = parameters.getDouble(AlgorithmParameters.FITNESS_IMPROVEMENT_THRESHOLD);
            
            if (improvement < threshold) {
                stagnationCounter++;
            } else {
                stagnationCounter = 0;
            }
        }
    }
    
    
    private void updatePheromones() {
        // FIXED evaporation rate (baseline - no adaptation)
        double evaporationRate = parameters.getDouble(AlgorithmParameters.EVAPORATION_MIN);
        
        // Evaporate existing pheromones
        for (int i = 0; i < pheromoneMatrix.length; i++) {
            for (int j = 0; j < pheromoneMatrix[i].length; j++) {
                pheromoneMatrix[i][j] *= (1.0 - evaporationRate);
                
                double minPheromone = parameters.getDouble(AlgorithmParameters.MIN_PHEROMONE);
                pheromoneMatrix[i][j] = Math.max(pheromoneMatrix[i][j], minPheromone);
            }
        }
        
        // Each ant deposits pheromones
        for (Ant ant : ants) {
            depositPheromones(ant);
        }
        
        // NO load-based reinforcement in baseline
        // Just simple best solution reinforcement
        simpleReinforceBestSolution();
        
        limitPheromoneLevel();
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
    
    
    private void simpleReinforceBestSolution() {
        if (bestSolution == null) return;
        
        // Simple uniform reinforcement (no load-based heuristic)
        double reinforcement = 1.0 / (1.0 + bestFitness);
        
        for (int i = 0; i < cloudlets.size(); i++) {
            Cloudlet cloudlet = cloudlets.get(i);
            Vm assignedVm = bestSolution.get(cloudlet);
            
            if (assignedVm != null) {
                int vmIndex = vms.indexOf(assignedVm);
                if (vmIndex >= 0) {
                    // Uniform reinforcement (no load consideration)
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
        return AlgorithmMetricUtils.calculateFitness(schedule, cloudlets, vms, parameters);
    }
    
    private void calculateMetrics(Map<Cloudlet, Vm> schedule) {
        metrics.clear();
        metrics.put("makespan", AlgorithmMetricUtils.makespan(schedule));
        metrics.put("cost", AlgorithmMetricUtils.cost(schedule));
        metrics.put("energy", AlgorithmMetricUtils.energy(schedule));
        metrics.put("degreeOfImbalance", AlgorithmMetricUtils.degreeOfImbalance(schedule));
        metrics.put("responseTime", AlgorithmMetricUtils.responseTime(schedule));
        metrics.put("iterations", (double) currentIteration);
        metrics.put("converged", currentIteration < parameters.getInt(AlgorithmParameters.MAX_ITERATIONS) ? 1.0 : 0.0);
        
        double fitness = calculateFitness(schedule);
        metrics.put("fitness", fitness);
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
    }
    
    private class Ant {
        private final Map<Cloudlet, Vm> solution;
        private double fitness;
        
        public Ant(int cloudletCount) {
            this.solution = new HashMap<>();
            this.fitness = Double.MAX_VALUE;
        }
        
        public void constructSolution() {
            solution.clear();
            
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
            
            return vms.get(vms.size() - 1);
        }
        
        private double[] calculateProbabilities(int cloudletIndex) {
            double alpha = parameters.getDouble(AlgorithmParameters.ALPHA);
            double beta = parameters.getDouble(AlgorithmParameters.BETA);
            double[] probabilities = new double[vms.size()];
            double totalProbability = 0.0;
            
            for (int j = 0; j < vms.size(); j++) {
                double pheromone = Math.pow(pheromoneMatrix[cloudletIndex][j], alpha);
                double heuristic = Math.pow(heuristicMatrix[cloudletIndex][j], beta);
                probabilities[j] = pheromone * heuristic;
                totalProbability += probabilities[j];
            }
            
            if (totalProbability > 0) {
                for (int j = 0; j < probabilities.length; j++) {
                    probabilities[j] /= totalProbability;
                }
            } else {
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
        
        int maxStagnation = parameters.getInt(AlgorithmParameters.STAGNATION_ITERATIONS);
        return stagnationCounter >= maxStagnation;
    }
    
    private Map<Cloudlet, Vm> applyLoadBalancing(Map<Cloudlet, Vm> originalSchedule) {
        if (originalSchedule == null) {
            return originalSchedule;
        }
        
        Map<Cloudlet, Vm> balancedSchedule = new HashMap<>(originalSchedule);
        LoadBalancer balancer = new LoadBalancer(balancedSchedule, vms, cloudlets);
        balancer.balance();
        
        return balancedSchedule;
    }
}