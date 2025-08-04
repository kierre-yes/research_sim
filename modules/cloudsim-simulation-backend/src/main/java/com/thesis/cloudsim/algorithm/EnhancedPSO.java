package com.thesis.cloudsim.algorithm;

import org.cloudbus.cloudsim.Cloudlet;
import org.cloudbus.cloudsim.Vm;
import com.thesis.cloudsim.config.AlgorithmDebugConfig;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;
import static com.thesis.cloudsim.algorithm.AlgorithmMetricUtils.*;

public class EnhancedPSO implements ISchedulingAlgorithm {

    private static final String ALGORITHM_NAME = "EPSO";
    private final Map<String, Double> metrics;
    private final Random random;
    private List<Particle> particles;
    private Particle globalBest;
    private int currentIteration;
    private List<Cloudlet> cloudlets;
    private List<Vm> vms;
    private AlgorithmParameters parameters;
    
    @Autowired(required = false)
    private AlgorithmDebugConfig debugConfig;

    public EnhancedPSO() {
        this.metrics = new HashMap<>();
        this.random = ThreadLocalRandom.current();
        this.particles = new ArrayList<>();
        this.currentIteration = 0;
    }

    @Override
    public Map<Cloudlet, Vm> schedule(List<Cloudlet> cloudlets, List<Vm> vms, AlgorithmParameters parameters) {
        this.cloudlets = new ArrayList<>(cloudlets);
        this.vms = new ArrayList<>(vms);
        this.parameters = parameters;
        
        initializeParticles();
        
        // Main optimization loop
        for (currentIteration = 0; currentIteration < parameters.getInt(AlgorithmParameters.MAX_ITERATIONS); currentIteration++) {
            updateParticles();
            updateGlobalBest();
            
            // Convergence check disabled
            
            // Progress output
            if (isDebugEnabled() && currentIteration % getDebugIterationInterval() == 0) {
                System.out.println("[EPSO] Iteration " + currentIteration + 
                                 ", Best fitness: " + globalBest.getFitness());
            }
        }
        
        Map<Cloudlet, Vm> schedule = convertToScheduleMap(globalBest.getPosition());
        calculateMetrics(schedule);
        return schedule;
    }

    private void initializeParticles() {
        int popSize = parameters.getInt(AlgorithmParameters.POPULATION_SIZE);
        particles.clear();
        for (int i = 0; i < popSize; i++) {
            Particle p = new Particle(cloudlets.size(), vms.size());
            p.setFitness(calculateFitness(p.getPosition()));
            particles.add(p);
            if (globalBest == null || p.getFitness() < globalBest.getFitness()) {
                globalBest = new Particle(p);
            }
        }
    }

    private void updateParticles() {
        double w = calculateInertiaWeight();
        double c1 = parameters.getDouble(AlgorithmParameters.COGNITIVE_COEFFICIENT);
        double c2 = parameters.getDouble(AlgorithmParameters.SOCIAL_COEFFICIENT);
        for (Particle p : particles) {
            updateVelocity(p, w, c1, c2);
            updatePosition(p);
            double fit = calculateFitness(p.getPosition());
            p.setFitness(fit);
            if (fit < p.getPersonalBestFitness()) {
                p.setPersonalBest(p.getPosition().clone());
                p.setPersonalBestFitness(fit);
            }
        }
    }

    private double calculateInertiaWeight() {
        // Calculate non-linear inertia weight
        
        double wMax = parameters.getDouble(AlgorithmParameters.INERTIA_WEIGHT_MAX);
        double wMin = parameters.getDouble(AlgorithmParameters.INERTIA_WEIGHT_MIN);
        double maxIterations = parameters.getInt(AlgorithmParameters.MAX_ITERATIONS);
        
        // Apply quadratic decay
        double progress = (double) currentIteration / maxIterations;
        double quadraticProgress = progress * progress;
        
        return wMax - (wMax - wMin) * quadraticProgress;
    }

    private void updateVelocity(Particle p, double w, double c1, double c2) {
        double[] vel = p.getVelocity(), pos = p.getPosition(), pb = p.getPersonalBest(), gb = globalBest.getPosition();
        double maxVel = calculateAdaptiveVelocityLimit();
        for (int i = 0; i < vel.length; i++) {
            double r1 = random.nextDouble(), r2 = random.nextDouble();
            vel[i] = w * vel[i] + c1 * r1 * (pb[i] - pos[i]) + c2 * r2 * (gb[i] - pos[i]);
            vel[i] = Math.max(-maxVel, Math.min(maxVel, vel[i]));
        }
    }

    private double calculateAdaptiveVelocityLimit() {
        // Calculate adaptive velocity limit
        
        double vMaxInitial = parameters.getDouble(AlgorithmParameters.MAX_VELOCITY_INITIAL);
        double vMaxFinal = parameters.getDouble(AlgorithmParameters.MAX_VELOCITY_FINAL);
        double maxIterations = parameters.getInt(AlgorithmParameters.MAX_ITERATIONS);
        
        // Apply quadratic decay
        double progress = (double) currentIteration / maxIterations;
        double quadraticProgress = progress * progress;
        
        return vMaxInitial - (vMaxInitial - vMaxFinal) * quadraticProgress;
    }

    private void updatePosition(Particle p) {
        double[] pos = p.getPosition(), vel = p.getVelocity();
        for (int i = 0; i < pos.length; i++) {
            pos[i] += vel[i];
            
            // Apply boundary constraints
            pos[i] = Math.max(0, Math.min(vms.size() - 1, pos[i]));
        }
    }

    private void updateGlobalBest() {
        for (Particle p : particles) {
            if (p.getFitness() < globalBest.getFitness()) {
                globalBest = new Particle(p);
            }
        }
    }

    private Map<Cloudlet, Vm> convertToScheduleMap(double[] position) {
        Map<Cloudlet, Vm> schedule = new HashMap<>();
        int[] vmUsage = new int[vms.size()];
        
        // Initial assignment based on position
        performInitialAssignment(position, schedule, vmUsage);
        
        // Load balancing: redistribute to unused VMs
        performLoadBalancing(schedule, vmUsage);
        
        return schedule;
    }
    
    private void performInitialAssignment(double[] position, Map<Cloudlet, Vm> schedule, int[] vmUsage) {
        for (int i = 0; i < position.length && i < cloudlets.size(); i++) {
            int vmIndex = mapPositionToVmIndex(position[i]);
            schedule.put(cloudlets.get(i), vms.get(vmIndex));
            vmUsage[vmIndex]++;
        }
    }
    
    private int mapPositionToVmIndex(double positionValue) {
        int idx = (int) Math.round(positionValue) % vms.size();
        return idx < 0 ? 0 : idx;
    }
    
    private void performLoadBalancing(Map<Cloudlet, Vm> schedule, int[] vmUsage) {
        for (int vmIdx = 0; vmIdx < vms.size(); vmIdx++) {
            if (shouldRedistribute(vmUsage[vmIdx])) {
                redistributeFromOverloadedVm(schedule, vmUsage, vmIdx);
            }
        }
    }
    
    private boolean shouldRedistribute(int currentVmUsage) {
        return currentVmUsage == 0 && cloudlets.size() > vms.size();
    }
    
    private void redistributeFromOverloadedVm(Map<Cloudlet, Vm> schedule, int[] vmUsage, int targetVmIdx) {
        int maxLoadedVmIdx = findMostLoadedVm(vmUsage);
        
        if (vmUsage[maxLoadedVmIdx] > 2) {
            reassignCloudlet(schedule, vmUsage, maxLoadedVmIdx, targetVmIdx);
        }
    }
    
    private int findMostLoadedVm(int[] vmUsage) {
        int maxLoadedVm = 0;
        int maxLoad = vmUsage[0];
        
        for (int j = 1; j < vms.size(); j++) {
            if (vmUsage[j] > maxLoad) {
                maxLoad = vmUsage[j];
                maxLoadedVm = j;
            }
        }
        
        return maxLoadedVm;
    }
    
    private void reassignCloudlet(Map<Cloudlet, Vm> schedule, int[] vmUsage, int fromVmIdx, int toVmIdx) {
        for (Map.Entry<Cloudlet, Vm> entry : schedule.entrySet()) {
            if (entry.getValue().equals(vms.get(fromVmIdx))) {
                schedule.put(entry.getKey(), vms.get(toVmIdx));
                vmUsage[fromVmIdx]--;
                vmUsage[toVmIdx]++;
                break;
            }
        }
    }

    private double calculateFitness(double[] position) {
        Map<Cloudlet, Vm> schedule = convertToScheduleMap(position);
        
        // Get raw metrics
        double m = AlgorithmMetricUtils.makespan(schedule);
        double c = AlgorithmMetricUtils.enhancedCost(schedule, cloudlets, vms);
        double e = AlgorithmMetricUtils.energy(schedule);
        double lb = AlgorithmMetricUtils.loadBalance(schedule);
        
        // Normalize to [0,1] range
        m = AlgorithmMetricUtils.normalise("makespan", m, cloudlets, vms);
        c = AlgorithmMetricUtils.normalise("enhancedCost", c, cloudlets, vms);
        e = AlgorithmMetricUtils.normalise("energy", e, cloudlets, vms);
        lb = AlgorithmMetricUtils.normalise("loadBalance", lb, cloudlets, vms);
        
        // Weighted sum of objectives
        return parameters.getDouble(AlgorithmParameters.MAKESPAN_WEIGHT) * m
             + parameters.getDouble(AlgorithmParameters.COST_WEIGHT)     * c
             + parameters.getDouble(AlgorithmParameters.ENERGY_WEIGHT)   * e
             + parameters.getDouble(AlgorithmParameters.LOAD_BALANCE_WEIGHT) * lb;
    }

    // These methods were removed as they duplicate functionality in AlgorithmMetricUtils
    // Use AlgorithmMetricUtils.makespan(), cost(), energy(), loadBalance() instead

    private void calculateMetrics(Map<Cloudlet, Vm> sched) {
        metrics.clear();
        metrics.put("makespan", AlgorithmMetricUtils.makespan(sched));
        metrics.put("cost", AlgorithmMetricUtils.cost(sched));
        metrics.put("energy", AlgorithmMetricUtils.energy(sched));
        metrics.put("loadBalance", AlgorithmMetricUtils.loadBalance(sched));
        metrics.put("responseTime", AlgorithmMetricUtils.responseTime(sched));
        metrics.put("iterations", (double) currentIteration);
        metrics.put("converged", currentIteration < parameters.getInt(AlgorithmParameters.MAX_ITERATIONS) ? 1.0 : 0.0);
        
        // Calculate and store the fitness value
        double fitness = calculateFitness(convertPositionToArray(sched));
        metrics.put("fitness", fitness);
        
        // Log the final metrics for debugging
        if (isDebugEnabled()) {
            System.out.println("[EPSO] Final Metrics:");
            System.out.println("  Makespan: " + metrics.get("makespan"));
            System.out.println("  Cost: " + metrics.get("cost"));
            System.out.println("  Energy: " + metrics.get("energy"));
            System.out.println("  Load Balance: " + metrics.get("loadBalance"));
            System.out.println("  Response Time: " + metrics.get("responseTime"));
            System.out.println("  Fitness: " + fitness);
        }
    }
    
    private double[] convertPositionToArray(Map<Cloudlet, Vm> schedule) {
        double[] position = new double[cloudlets.size()];
        for (int i = 0; i < cloudlets.size(); i++) {
            Vm assignedVm = schedule.get(cloudlets.get(i));
            if (assignedVm != null) {
                position[i] = vms.indexOf(assignedVm);
            }
        }
        return position;
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
        particles.clear();
        globalBest = null;
        currentIteration = 0;
        metrics.clear();
    }
    
    private boolean isDebugEnabled() {
        return debugConfig != null && debugConfig.isEnabled();
    }
    
    private int getDebugIterationInterval() {
        return debugConfig != null ? debugConfig.getIterationInterval() : 10;
    }

    private static class Particle {
        private double[] position;
        private double[] velocity;
        private double[] personalBest;
        private double fitness;
        private double personalBestFitness;

        public Particle(int dims, int vmCount) {
            position = new double[dims];
            velocity = new double[dims];
            personalBest = new double[dims];
            fitness = Double.MAX_VALUE;
            personalBestFitness = Double.MAX_VALUE;
            Random rnd = ThreadLocalRandom.current();
            for (int i = 0; i < dims; i++) {
                position[i] = rnd.nextDouble() * vmCount;
                velocity[i] = (rnd.nextDouble() - 0.5) * 2.0;
                personalBest[i] = position[i];
            }
        }

        public Particle(Particle other) {
            position = other.position.clone();
            velocity = other.velocity.clone();
            personalBest = other.personalBest.clone();
            fitness = other.fitness;
            personalBestFitness = other.personalBestFitness;
        }

        public double[] getPosition() { return position; }
        public double[] getVelocity() { return velocity; }
        public double[] getPersonalBest() { return personalBest; }
        public double getFitness() { return fitness; }
        public double getPersonalBestFitness() { return personalBestFitness; }
        public void setFitness(double f) { this.fitness = f; }
        public void setPersonalBest(double[] pb) { this.personalBest = pb; }
        public void setPersonalBestFitness(double f) { this.personalBestFitness = f; }
    }
}
