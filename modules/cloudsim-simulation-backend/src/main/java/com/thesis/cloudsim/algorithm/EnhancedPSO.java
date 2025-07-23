package com.thesis.cloudsim.algorithm;

import org.cloudbus.cloudsim.Cloudlet;
import org.cloudbus.cloudsim.Vm;
import org.cloudbus.cloudsim.Host;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import static com.thesis.cloudsim.algorithm.AlgorithmMetricUtils.*;

/**
 * Enhanced Particle Swarm Optimization (EPSO) implementation for task scheduling
 * 
 * @author Kier M.
 * @version 2.1.5
 * @since 2024-01-15
 * 
 * Changelog:
 * v2.1.5 - Fixed VM assignment bug when position rounds to negative
 * v2.1.4 - Added adaptive velocity clamping
 * v2.1.3 - Improved fitness calculation with network cost
 * v2.1.0 - Major refactor for multi-objective optimization
 * 
 * Known issues:
 * - Position clamping can cause particles to stick at boundaries
 * - Performance degrades with >1000 cloudlets (need parallel implementation)
 * 
 * TODO: Implement constriction factor variant
 * TODO: Add mutation operator for diversity maintenance
 * FIXME: Memory leak when running multiple simulations in sequence
 */
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
    
    // Not used currently but keeping for future time-based scheduling
    private double currentTime = 0.0;
    
    // Debug flag - set to true for detailed output
    private static final boolean DEBUG = true;

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
        
        // PSO main loop
        double prevBestFitness = Double.MAX_VALUE;
        int stagnationCount = 0;
        
        for (currentIteration = 0; currentIteration < parameters.getInt(AlgorithmParameters.MAX_ITERATIONS); currentIteration++) {
            updateParticles();
            updateGlobalBest();
            
            // Check for convergence (experimental - disabled for now)
            /*
            if (Math.abs(prevBestFitness - globalBest.getFitness()) < 0.0001) {
                stagnationCount++;
                if (stagnationCount > 10) {
                    if (DEBUG) System.out.println("[EPSO] Converged at iteration " + currentIteration);
                    break;
                }
            } else {
                stagnationCount = 0;
            }
            prevBestFitness = globalBest.getFitness();
            */
            
            // Debug output
            if (DEBUG && currentIteration % 10 == 0) {
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
        double progress = (double) currentIteration / parameters.getInt(AlgorithmParameters.MAX_ITERATIONS);
        double minW = 0.1;
        double maxW = parameters.getDouble(AlgorithmParameters.INERTIA_WEIGHT);
        
        // Exponential decay based on experiments from Feb 2024
        // Tested linear, exponential, and chaotic - exponential gave best results
        // Don't go below 0.1 or particles lose momentum
        return minW + (maxW - minW) * Math.exp(-2 * progress);
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
        double progress = (double) currentIteration / parameters.getInt(AlgorithmParameters.MAX_ITERATIONS);
        double maxV = parameters.getDouble(AlgorithmParameters.MAX_VELOCITY);
        double minV = parameters.getDouble(AlgorithmParameters.MIN_VELOCITY);
        return minV + (maxV - minV) * (1 - progress);
    }

    private void updatePosition(Particle p) {
        double[] pos = p.getPosition(), vel = p.getVelocity();
        for (int i = 0; i < pos.length; i++) {
            pos[i] += vel[i];
            
            // Boundary handling - using clamping approach
            // Tried reflection and wrapping but both performed worse in tests
            // See experiment logs from March 2024
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
        
        // Track VM usage to ensure all VMs get at least one cloudlet
        int[] vmUsage = new int[vms.size()];
        
        for (int i = 0; i < position.length && i < cloudlets.size(); i++) {
            // Round to nearest VM index
            int idx = (int) Math.round(position[i]) % vms.size();
            if (idx < 0) idx = 0;  // Shouldn't happen but just in case
            
            schedule.put(cloudlets.get(i), vms.get(idx));
            vmUsage[idx]++;
        }
        
        // Redistribute if some VMs are not utilized (optional optimization)
        // This helps avoid idle VMs which improves resource utilization
        for (int vmIdx = 0; vmIdx < vms.size(); vmIdx++) {
            if (vmUsage[vmIdx] == 0 && cloudlets.size() > vms.size()) {
                // Find the most loaded VM and reassign one cloudlet
                int maxLoadedVm = 0;
                int maxLoad = vmUsage[0];
                for (int j = 1; j < vms.size(); j++) {
                    if (vmUsage[j] > maxLoad) {
                        maxLoad = vmUsage[j];
                        maxLoadedVm = j;
                    }
                }
                
                // Only redistribute if the most loaded VM has more than 2 cloudlets
                if (maxLoad > 2) {
                    // Find a cloudlet assigned to the most loaded VM
                    for (Map.Entry<Cloudlet, Vm> entry : schedule.entrySet()) {
                        if (entry.getValue().equals(vms.get(maxLoadedVm))) {
                            schedule.put(entry.getKey(), vms.get(vmIdx));
                            vmUsage[maxLoadedVm]--;
                            vmUsage[vmIdx]++;
                            break;
                        }
                    }
                }
            }
        }
        
        return schedule;
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
        
        // Multi-objective weighted sum
        // Note: Weights don't need to sum to 1.0 - user can prioritize objectives
        // As discussed in thesis section 4.2.3
        return parameters.getDouble(AlgorithmParameters.MAKESPAN_WEIGHT) * m
             + parameters.getDouble(AlgorithmParameters.COST_WEIGHT)     * c
             + parameters.getDouble(AlgorithmParameters.ENERGY_WEIGHT)   * e
             + parameters.getDouble(AlgorithmParameters.LOAD_BALANCE_WEIGHT) * lb;
    }

    private double calculateMakespan(Map<Cloudlet, Vm> sched) {
        Map<Vm, Double> fin = new HashMap<>();
        for (Map.Entry<Cloudlet, Vm> e: sched.entrySet()) {
            double t = e.getKey().getCloudletLength() / e.getValue().getMips();
            fin.merge(e.getValue(), t, Double::sum);
        }
        return fin.values().stream().mapToDouble(Double::doubleValue).max().orElse(0.0);
    }

    private double calculateCost(Map<Cloudlet, Vm> sched) {
        double cost = 0.0;
        for (Map.Entry<Cloudlet, Vm> e: sched.entrySet()) {
            double t = e.getKey().getCloudletLength() / e.getValue().getMips();
            double hostRamCapacity = e.getValue().getHost().getGuestRamProvisioner().getRam();
            cost += t * (hostRamCapacity * 0.001);
        }
        return cost;
    }

    private double calculateEnergy(Map<Cloudlet, Vm> sched) {
        double energy = 0.0;
        for (Map.Entry<Cloudlet, Vm> e: sched.entrySet()) {
            double t = e.getKey().getCloudletLength() / e.getValue().getMips();
            // Simplified energy calculation
            energy += t * 100.0; // Basic power estimate
        }
        return energy;
    }

    private double calculateLoadBalance(Map<Cloudlet, Vm> sched) {
        Map<Vm, Double> loads = new HashMap<>();
        for (Map.Entry<Cloudlet, Vm> e: sched.entrySet()) {
            double l = e.getKey().getCloudletLength() / e.getValue().getMips();
            loads.merge(e.getValue(), l, Double::sum);
        }
        double[] arr = loads.values().stream().mapToDouble(Double::doubleValue).toArray();
        double mean = Arrays.stream(arr).average().orElse(0.0);
        double var = Arrays.stream(arr).map(x -> (x - mean)*(x - mean)).average().orElse(0.0);
        return Math.sqrt(var);
    }

    private double normalizeMetric(double val, String type) {
        double max = 1.0;
        switch (type) {
            case "makespan":
                max = cloudlets.stream().mapToDouble(Cloudlet::getCloudletLength).sum()
                    / vms.stream().mapToDouble(Vm::getMips).min().orElse(1.0);
                break;
            case "cost":
                max = cloudlets.stream().mapToDouble(Cloudlet::getCloudletLength).sum() * 0.1;
                break;
            case "energy":
                max = cloudlets.stream().mapToDouble(Cloudlet::getCloudletLength).sum() * 100.0;
                break;
            case "loadBalance":
                max = cloudlets.stream().mapToDouble(Cloudlet::getCloudletLength).sum();
                break;
        }
        return Math.max(0.0, Math.min(1.0, (val - 0.0) / (max - 0.0)));
    }

    private void calculateMetrics(Map<Cloudlet, Vm> sched) {
        metrics.clear();
        metrics.put("makespan", calculateMakespan(sched));
        metrics.put("cost", calculateCost(sched));
        metrics.put("energy", calculateEnergy(sched));
        metrics.put("loadBalance", calculateLoadBalance(sched));
        metrics.put("iterations", (double) currentIteration);
        metrics.put("converged", currentIteration < parameters.getInt(AlgorithmParameters.MAX_ITERATIONS) ? 1.0 : 0.0);
        
        // Calculate and store the fitness value
        double fitness = calculateFitness(convertPositionToArray(sched));
        metrics.put("fitness", fitness);
        
        // Log the final metrics for debugging
        if (DEBUG) {
            System.out.println("[EPSO] Final Metrics:");
            System.out.println("  Makespan: " + metrics.get("makespan"));
            System.out.println("  Cost: " + metrics.get("cost"));
            System.out.println("  Energy: " + metrics.get("energy"));
            System.out.println("  Load Balance: " + metrics.get("loadBalance"));
            System.out.println("  Fitness: " + fitness);
        }
    }
    
    // Helper method to convert schedule back to position array for fitness calculation
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
