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
    private static final double DEFAULT_ALPHA = 1.0;
    private static final double DEFAULT_BETA = 5.0;
    private static final double DEFAULT_EVAPORATION = 0.02;
    private static final double Q_CONSTANT = 1.0;
    private static final double PROB_BEST = 0.05;
    private final Map<String, Double> metrics;
    private final Random random;
    
    private List<Ant> ants;
    private double[][] pheromoneMatrix;
    private double[][] heuristicMatrix;
    private double tauMax;
    private double tauMin;
    private Map<Cloudlet, Vm> bestSolution;
    private double bestFitness;
    private int currentIteration;
    private Ant iterationBestAnt;
    
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
            iterationBestAnt = null;
            constructSolutions();
            updateBestSolution();
            updatePheromones(iterationBestAnt);
            
            
        }
        
        Map<Cloudlet, Vm> result = bestSolution == null ? new HashMap<>() : new HashMap<>(bestSolution);
        calculateMetrics(result);
        return result;
    }
    
    private void initializeMatrices() {
        int cloudletCount = cloudlets.size();
        int vmCount = vms.size();
        
        pheromoneMatrix = new double[cloudletCount][vmCount];
        double initialPheromone = initializePheromoneBounds();
        
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
            
            if (iterationBestAnt == null || fitness < iterationBestAnt.getFitness()) {
                iterationBestAnt = ant;
            }
        }
    }
    
    private void updateBestSolution() {
        double oldBestFitness = bestFitness;
        
        if (iterationBestAnt != null && iterationBestAnt.getFitness() < bestFitness) {
            bestFitness = iterationBestAnt.getFitness();
            bestSolution = new HashMap<>(iterationBestAnt.getSolution());
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
    
    private void updatePheromones(Ant iterationBest) {
        // FIXED evaporation rate (baseline - no adaptation)
        double evaporationRate = parameters.hasParameter(AlgorithmParameters.EVAPORATION_MIN)
                ? parameters.getDouble(AlgorithmParameters.EVAPORATION_MIN)
                : DEFAULT_EVAPORATION;
        
        // Evaporate existing pheromones
        for (int i = 0; i < pheromoneMatrix.length; i++) {
            for (int j = 0; j < pheromoneMatrix[i].length; j++) {
                pheromoneMatrix[i][j] *= (1.0 - evaporationRate);
            }
        }
        
        if (iterationBest != null) {
            double pheromoneDeposit = Q_CONSTANT / (1.0 + iterationBest.getFitness());
            Map<Cloudlet, Vm> solution = iterationBest.getSolution();
            
            // Each ant deposits pheromones
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
        
        limitPheromoneLevel();
    }
    
    private void limitPheromoneLevel() {
        for (int i = 0; i < pheromoneMatrix.length; i++) {
            for (int j = 0; j < pheromoneMatrix[i].length; j++) {
                if (pheromoneMatrix[i][j] > tauMax) {
                    pheromoneMatrix[i][j] = tauMax;
                } else if (pheromoneMatrix[i][j] < tauMin) {
                    pheromoneMatrix[i][j] = tauMin;
                }
            }
        }
    }

    private double initializePheromoneBounds() {
        double estimatedCost = calculateNearestNeighborCost();
        double evaporationRate = parameters.hasParameter(AlgorithmParameters.EVAPORATION_MIN)
                ? parameters.getDouble(AlgorithmParameters.EVAPORATION_MIN)
                : DEFAULT_EVAPORATION;
        
        if (estimatedCost <= 0) {
            tauMax = 1.0;
            tauMin = 1.0;
            return tauMax;
        }
        
        tauMax = 1.0 / (evaporationRate * estimatedCost);
        double pRoot = Math.pow(PROB_BEST, 1.0 / Math.max(1, cloudlets.size()));
        double denominator = (cloudlets.size() / 2.0) * pRoot;
        if (denominator <= 0) {
            tauMin = tauMax;
        } else {
            tauMin = (tauMax * (1.0 - pRoot)) / denominator;
        }
        return tauMax;
    }

    private double calculateNearestNeighborCost() {
        double total = 0.0;
        for (Cloudlet cloudlet : cloudlets) {
            double minTime = Double.MAX_VALUE;
            for (Vm vm : vms) {
                double execTime = cloudlet.getCloudletLength() / vm.getMips();
                if (execTime < minTime) {
                    minTime = execTime;
                }
            }
            if (minTime < Double.MAX_VALUE) {
                total += minTime;
            }
        }
        return total;
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
        iterationBestAnt = null;
        metrics.clear();
        previousBestFitness = Double.MAX_VALUE;
        stagnationCounter = 0;
        tauMax = 0.0;
        tauMin = 0.0;
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
            double alpha = parameters.hasParameter(AlgorithmParameters.ALPHA) ?
                    parameters.getDouble(AlgorithmParameters.ALPHA) : DEFAULT_ALPHA;
            double beta = parameters.hasParameter(AlgorithmParameters.BETA) ?
                    parameters.getDouble(AlgorithmParameters.BETA) : DEFAULT_BETA;
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
}