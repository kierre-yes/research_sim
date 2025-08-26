package com.thesis.cloudsim.algorithm;

import org.cloudbus.cloudsim.Cloudlet;
import org.cloudbus.cloudsim.Vm;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;
import static com.thesis.cloudsim.algorithm.AlgorithmMetricUtils.*;

/**
 * I implement PSO with enhancements including adaptive inertia weight,
 * adaptive velocity limits, and early stopping for improved convergence
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
    
    // I track fitness improvement to detect stagnation and trigger early stopping
    // This prevents wasting computation when the algorithm has converged
    private double previousBestFitness;
    private int stagnationCounter;
    
    public EnhancedPSO() {
        this.metrics = new HashMap<>();
        /*
         * fix same instances
         */
        this.random = new Random();
        this.particles = new ArrayList<>();
        this.currentIteration = 0;
        this.previousBestFitness = Double.MAX_VALUE;
        this.stagnationCounter = 0;
    }
    
    /*
     * I provide a constructor with seed for reproducible results when needed
     */
    public EnhancedPSO(long seed) {
        this.metrics = new HashMap<>();
        this.random = new Random(seed);
        this.particles = new ArrayList<>();
        this.currentIteration = 0;
        this.previousBestFitness = Double.MAX_VALUE;
        this.stagnationCounter = 0;
    }

    @Override
    public Map<Cloudlet, Vm> schedule(List<Cloudlet> cloudlets, List<Vm> vms, AlgorithmParameters parameters) {
        // I create defensive copies so that the original lists aren't modified
        this.cloudlets = new ArrayList<>(cloudlets);
        this.vms = new ArrayList<>(vms);
        this.parameters = parameters;
        
        initializeParticles();
        
        // Main PSO optimization loop - each iteration updates all particles towards better solutions
        for (currentIteration = 0; currentIteration < parameters.getInt(AlgorithmParameters.MAX_ITERATIONS); currentIteration++) {
            updateParticles();
            updateGlobalBest();
            
            // I check for early stopping so that we don't waste computation if solution has converged
            if (shouldStopEarly()) {
                if (isDebugEnabled()) {
                    System.out.println("[EPSO] Early stopping triggered at iteration " + currentIteration + 
                                     " after " + stagnationCounter + " iterations without improvement");
                }
                break;
            }
            
            // I output progress periodically so that long-running simulations can be monitored
            // without flooding the console with messages every iteration
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
        
        // I create random initial particles so that the swarm explores different regions of the search space
        for (int i = 0; i < popSize; i++) {
            Particle p = new Particle(cloudlets.size(), vms.size());
            p.setFitness(calculateFitness(p.getPosition()));
            particles.add(p);
            
            // I track the best particle from initialization so that we have a good starting point
            if (globalBest == null || p.getFitness() < globalBest.getFitness()) {
                globalBest = new Particle(p);
            }
        }
    }

    private void updateParticles() {
        // I calculate adaptive inertia weight so that exploration decreases over time
        double w = calculateInertiaWeight();
        double c1 = parameters.getDouble(AlgorithmParameters.COGNITIVE_COEFFICIENT);
        double c2 = parameters.getDouble(AlgorithmParameters.SOCIAL_COEFFICIENT);
        
        for (Particle p : particles) {
            // I update velocity based on personal and global best positions
            updateVelocity(p, w, c1, c2);
            updatePosition(p);
            
            // I evaluate the new position and update personal best if improved
            double fit = calculateFitness(p.getPosition());
            p.setFitness(fit);
            if (fit < p.getPersonalBestFitness()) {
                p.setPersonalBest(p.getPosition().clone());
                p.setPersonalBestFitness(fit);
            }
        }
    }

    private double calculateInertiaWeight() {
        // I implement non-linear inertia weight decay based on research showing
        // quadratic decay performs better than linear for PSO convergence
        
        double wMax = parameters.getDouble(AlgorithmParameters.INERTIA_WEIGHT_MAX);
        double wMin = parameters.getDouble(AlgorithmParameters.INERTIA_WEIGHT_MIN);
        double maxIterations = parameters.getInt(AlgorithmParameters.MAX_ITERATIONS);
        
        // I use quadratic decay instead of linear so that exploration stays high
        // in early iterations then drops rapidly for fine-tuning near convergence
        double progress = (double) currentIteration / maxIterations;
        double quadraticProgress = progress * progress;
        
        return wMax - (wMax - wMin) * quadraticProgress;
    }

    private void updateVelocity(Particle p, double w, double c1, double c2) {
        double[] vel = p.getVelocity(), pos = p.getPosition(), pb = p.getPersonalBest(), gb = globalBest.getPosition();
        double maxVel = calculateAdaptiveVelocityLimit();
        
        for (int i = 0; i < vel.length; i++) {
            // I generate random factors so that particles don't all converge to the same path
            double r1 = random.nextDouble(), r2 = random.nextDouble();
            
            // I apply the PSO velocity update equation: v = w*v + c1*r1*(pbest-x) + c2*r2*(gbest-x)
            // This balances momentum (w*v), cognitive component (personal best), and social component (global best)
            vel[i] = w * vel[i] + c1 * r1 * (pb[i] - pos[i]) + c2 * r2 * (gb[i] - pos[i]);
            
            // I clamp velocity to prevent particles from overshooting good solutions
            vel[i] = Math.max(-maxVel, Math.min(maxVel, vel[i]));
        }
    }

    private double calculateAdaptiveVelocityLimit() {
        // I implement adaptive velocity limits so that particles slow down over time
        // This allows broad exploration initially and precise refinement later
        
        double vMaxInitial = parameters.getDouble(AlgorithmParameters.MAX_VELOCITY_INITIAL);
        double vMaxFinal = parameters.getDouble(AlgorithmParameters.MAX_VELOCITY_FINAL);
        double maxIterations = parameters.getInt(AlgorithmParameters.MAX_ITERATIONS);
        
        // I use quadratic decay for velocity limits matching the inertia weight pattern
        // This creates consistent exploration-to-exploitation transition
        double progress = (double) currentIteration / maxIterations;
        double quadraticProgress = progress * progress;
        
        return vMaxInitial - (vMaxInitial - vMaxFinal) * quadraticProgress;
    }

    private void updatePosition(Particle p) {
        double[] pos = p.getPosition(), vel = p.getVelocity();
        for (int i = 0; i < pos.length; i++) {
            // I update position by adding velocity (standard PSO position update)
            pos[i] += vel[i];
            
            // I apply boundary constraints so that positions map to valid VM indices
            // This ensures every position represents a feasible scheduling solution
            pos[i] = Math.max(0, Math.min(vms.size() - 1, pos[i]));
        }
    }

    private void updateGlobalBest() {
        double oldBestFitness = globalBest.getFitness();
        
        // I check all particles to find the new global best solution
        for (Particle p : particles) {
            if (p.getFitness() < globalBest.getFitness()) {
                // I create a copy so that the global best is independent of particle updates
                globalBest = new Particle(p);
            }
        }
        
        // I track stagnation so that we can stop early if no improvement is happening
        if (parameters.getBoolean(AlgorithmParameters.ENABLE_EARLY_STOPPING)) {
            double improvement = oldBestFitness - globalBest.getFitness();
            double threshold = parameters.getDouble(AlgorithmParameters.FITNESS_IMPROVEMENT_THRESHOLD);
            
            if (improvement < threshold) {
                stagnationCounter++;
            } else {
                // I reset the counter when we see improvement so that temporary plateaus don't trigger early stop
                stagnationCounter = 0;
            }
        }
    }

    /*
     * I simplified this method by extracting the load balancing logic
     * into a separate inner class. This follows SRP and makes the code
     * more maintainable and testable.
     */
    private Map<Cloudlet, Vm> convertToScheduleMap(double[] position) {
        Map<Cloudlet, Vm> schedule = new HashMap<>();
        LoadBalancer balancer = new LoadBalancer(schedule, vms.size());
        
        // I first assign cloudlets based on particle position values
        for (int i = 0; i < position.length && i < cloudlets.size(); i++) {
            int vmIndex = mapPositionToVmIndex(position[i]);
            Vm vm = vms.get(vmIndex);
            schedule.put(cloudlets.get(i), vm);
            balancer.recordAssignment(vmIndex);
        }
        
        // I then perform load balancing if needed
        if (cloudlets.size() > vms.size()) {
            balancer.balance();
        }
        
        return schedule;
    }
    
    /*
     * I use floorMod so that any position value maps to a valid VM index.
     * This creates a continuous mapping from real numbers to discrete VM assignments.
     */
    private int mapPositionToVmIndex(double positionValue) {
        return Math.floorMod((int) Math.round(positionValue), vms.size());
    }
    
    /**
     * I created this inner class to encapsulate load balancing logic.
     * This separation of concerns makes the code more modular and easier to test.
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
        
        /*
         * I balance the load by redistributing tasks from overloaded VMs
         * to underutilized ones. This ensures all VMs are utilized when possible.
         */
        public void balance() {
            for (int vmIdx = 0; vmIdx < vmCount; vmIdx++) {
                if (isUnderutilized(vmIdx)) {
                    redistributeToVm(vmIdx);
                }
            }
        }
        
        /*
         * I check if a VM is underutilized (has no tasks assigned).
         */
        private boolean isUnderutilized(int vmIdx) {
            return vmUsage[vmIdx] == 0;
        }
        
        /*
         * I redistribute one task from the most loaded VM to the target VM.
         */
        private void redistributeToVm(int targetVmIdx) {
            int sourceVmIdx = findMostLoadedVm();
            
            // I only redistribute if source VM has more than 2 tasks
            // to prevent thrashing
            if (vmUsage[sourceVmIdx] > 2) {
                moveOneCloudlet(sourceVmIdx, targetVmIdx);
            }
        }
        
        /*
         * I find the VM with the highest task count.
         */
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
        
        /*
         * I move one cloudlet from source VM to target VM.
         */
        private void moveOneCloudlet(int fromVmIdx, int toVmIdx) {
            Vm sourceVm = vms.get(fromVmIdx);
            Vm targetVm = vms.get(toVmIdx);
            
            for (Map.Entry<Cloudlet, Vm> entry : schedule.entrySet()) {
                if (entry.getValue().equals(sourceVm)) {
                    schedule.put(entry.getKey(), targetVm);
                    vmUsage[fromVmIdx]--;
                    vmUsage[toVmIdx]++;
                    break; // I only move one task at a time for stability
                }
            }
        }
    }

    private double calculateFitness(double[] position) {
        Map<Cloudlet, Vm> schedule = convertToScheduleMap(position);
        // I use centralized fitness calculation to ensure consistency with EACO
        return AlgorithmMetricUtils.calculateFitness(schedule, cloudlets, vms, parameters);
    }

    // I removed duplicate metric calculation methods and now use centralized AlgorithmMetricUtils
    // This ensures consistent metric calculation across all algorithms

    private void calculateMetrics(Map<Cloudlet, Vm> sched) {
        metrics.clear();
        
        // I store all key performance metrics so that they can be analyzed post-simulation
        metrics.put("makespan", AlgorithmMetricUtils.makespan(sched));
        metrics.put("cost", AlgorithmMetricUtils.cost(sched));
        metrics.put("energy", AlgorithmMetricUtils.energy(sched));
        metrics.put("degreeOfImbalance", AlgorithmMetricUtils.degreeOfImbalance(sched));
        metrics.put("responseTime", AlgorithmMetricUtils.responseTime(sched));
        metrics.put("iterations", (double) currentIteration);
        
        // I track convergence status so that we know if early stopping was triggered
        metrics.put("converged", currentIteration < parameters.getInt(AlgorithmParameters.MAX_ITERATIONS) ? 1.0 : 0.0);
        
        // I calculate fitness for the final solution to report optimization quality
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
        
        // I convert the schedule map back to position array so that fitness can be recalculated
        // This is needed when we want to evaluate an existing schedule
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
        // I return a copy so that external modifications don't affect internal state
        return new HashMap<>(metrics);
    }

    @Override
    public void reset() {
        // I clear all state so that the algorithm instance can be reused for multiple simulations
        // This is important for performance when running many simulations in sequence
        particles.clear();
        globalBest = null;
        currentIteration = 0;
        metrics.clear();
        previousBestFitness = Double.MAX_VALUE;
        stagnationCounter = 0;
    }
    
    /*
     * I check if debug mode is enabled. Currently returns false
     * as we don't have debugConfig implemented yet.
     */
    private boolean isDebugEnabled() {
        return false; // I simplified this as debugConfig is not defined
    }
    
    /*
     * I return the debug iteration interval for progress reporting.
     */
    private int getDebugIterationInterval() {
        return 10; // I use a default value of 10 iterations
    }
    // I implement early stopping so that we don't waste computation on converged solutions
    private boolean shouldStopEarly() {
        if (!parameters.getBoolean(AlgorithmParameters.ENABLE_EARLY_STOPPING)) {
            return false;
        }
        
        // I stop if fitness hasn't improved for maxStagnation iterations
        // This indicates the algorithm has likely found its best solution
        int maxStagnation = parameters.getInt(AlgorithmParameters.STAGNATION_ITERATIONS);
        return stagnationCounter >= maxStagnation;
    }

    /**
     * Particle represents a potential solution in the PSO swarm
     * 
     * I encapsulate particle state so that each particle maintains its own
     * position, velocity, and memory of its best solution
     */
    private static class Particle {
        private double[] position;        // Current solution encoding
        private double[] velocity;        // Movement direction and speed
        private double[] personalBest;    // Best position this particle has found
        private double fitness;
        private double personalBestFitness;

        public Particle(int dims, int vmCount) {
            position = new double[dims];
            velocity = new double[dims];
            personalBest = new double[dims];
            fitness = Double.MAX_VALUE;
            personalBestFitness = Double.MAX_VALUE;
            Random rnd = ThreadLocalRandom.current();
            
            // I initialize particles randomly across the search space so that
            // the swarm explores different regions from the start
            for (int i = 0; i < dims; i++) {
                position[i] = rnd.nextDouble() * vmCount;
                velocity[i] = (rnd.nextDouble() - 0.5) * 2.0;  // Random velocity between -1 and 1
                personalBest[i] = position[i];  // Initially, current position is the best
            }
        }

        public Particle(Particle other) {
            // I create deep copies so that particles are independent
            // This is crucial when storing the global best particle
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
