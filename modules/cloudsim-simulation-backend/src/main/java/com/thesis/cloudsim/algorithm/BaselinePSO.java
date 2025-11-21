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

public class BaselinePSO implements ISchedulingAlgorithm {

    private static final String ALGORITHM_NAME = "BPSO";
    private static final double W_MAX = 0.9;
    private static final double W_MIN = 0.4;
    private static final double C1_DEFAULT = 2.0;
    private static final double C2_DEFAULT = 2.0;
    private static final double V_CLAMP_RATIO = 0.2; // 20% of VM index range

    private final Map<String, Double> metrics;
    private final Random random;

    private List<Particle> particles;
    private Particle globalBest;
    private int currentIteration;
    private List<Cloudlet> cloudlets;
    private List<Vm> vms;
    private AlgorithmParameters parameters;

    private double previousBestFitness;
    private int stagnationCounter;

    public BaselinePSO() {
        this.metrics = new HashMap<>();
        this.random = new Random();
        this.particles = new ArrayList<>();
        this.currentIteration = 0;
        this.previousBestFitness = Double.MAX_VALUE;
        this.stagnationCounter = 0;
    }

    @Override
    public Map<Cloudlet, Vm> schedule(List<Cloudlet> cloudlets, List<Vm> vms, AlgorithmParameters parameters) {
        this.cloudlets = new ArrayList<>(cloudlets);
        this.vms = new ArrayList<>(vms);
        this.parameters = parameters;

        initializeParticles();

        for (currentIteration = 0; currentIteration < parameters.getInt(AlgorithmParameters.MAX_ITERATIONS); currentIteration++) {
            if (shouldStopEarly()) {
                break;
            }

            updateParticles();
            updateGlobalBest();
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
        double wMax = parameters.hasParameter(AlgorithmParameters.INERTIA_WEIGHT_MAX)
                ? parameters.getDouble(AlgorithmParameters.INERTIA_WEIGHT_MAX)
                : W_MAX;
        double wMin = parameters.hasParameter(AlgorithmParameters.INERTIA_WEIGHT_MIN)
                ? parameters.getDouble(AlgorithmParameters.INERTIA_WEIGHT_MIN)
                : W_MIN;
        double maxIterations = parameters.getInt(AlgorithmParameters.MAX_ITERATIONS);
        double progress = (double) currentIteration / maxIterations;
        double w = wMax - (wMax - wMin) * progress;

        double c1 = parameters.hasParameter(AlgorithmParameters.COGNITIVE_COEFFICIENT)
                ? parameters.getDouble(AlgorithmParameters.COGNITIVE_COEFFICIENT)
                : C1_DEFAULT;
        double c2 = parameters.hasParameter(AlgorithmParameters.SOCIAL_COEFFICIENT)
                ? parameters.getDouble(AlgorithmParameters.SOCIAL_COEFFICIENT)
                : C2_DEFAULT;

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

    private void updateVelocity(Particle p, double w, double c1, double c2) {
        double[] vel = p.getVelocity(), pos = p.getPosition(), pb = p.getPersonalBest(), gb = globalBest.getPosition();
        double maxVel = V_CLAMP_RATIO * vms.size();

        for (int i = 0; i < vel.length; i++) {
            double r1 = random.nextDouble(), r2 = random.nextDouble();

            vel[i] = w * vel[i] + c1 * r1 * (pb[i] - pos[i]) + c2 * r2 * (gb[i] - pos[i]);
            vel[i] = Math.max(-maxVel, Math.min(maxVel, vel[i]));
        }
    }

    private void updatePosition(Particle p) {
        double[] pos = p.getPosition(), vel = p.getVelocity();
        for (int i = 0; i < pos.length; i++) {
            pos[i] += vel[i];
            pos[i] = Math.max(0, Math.min(vms.size() - 1, pos[i]));
        }
    }

    private void updateGlobalBest() {
        double oldBestFitness = globalBest.getFitness();

        for (Particle p : particles) {
            if (p.getFitness() < globalBest.getFitness()) {
                globalBest = new Particle(p);
            }
        }

        if (parameters.getBoolean(AlgorithmParameters.ENABLE_EARLY_STOPPING)) {
            double improvement = oldBestFitness - globalBest.getFitness();
            double threshold = parameters.getDouble(AlgorithmParameters.FITNESS_IMPROVEMENT_THRESHOLD);

            if (improvement < threshold) {
                stagnationCounter++;
            } else {
                stagnationCounter = 0;
            }
        }
    }

    private Map<Cloudlet, Vm> convertToScheduleMap(double[] position) {
        Map<Cloudlet, Vm> schedule = new HashMap<>();
        
        for (int i = 0; i < position.length && i < cloudlets.size(); i++) {
            int vmIndex = mapPositionToVmIndex(position[i]);
            Vm vm = vms.get(vmIndex);
            schedule.put(cloudlets.get(i), vm);
        }
        
        return schedule;
    }
        
        private int mapPositionToVmIndex(double positionValue) {
            return Math.floorMod((int) Math.round(positionValue), vms.size());
        }

        private double calculateFitness(double[] position) {
            Map<Cloudlet, Vm> schedule = convertToScheduleMap(position);
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
            
            double fitness = calculateFitness(convertPositionToArray(schedule));
            metrics.put("fitness", fitness);
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
            previousBestFitness = Double.MAX_VALUE;
            stagnationCounter = 0;
        }
        
        private boolean shouldStopEarly() {
            if (!parameters.getBoolean(AlgorithmParameters.ENABLE_EARLY_STOPPING)) {
                return false;
            }
            
            int maxStagnation = parameters.getInt(AlgorithmParameters.STAGNATION_ITERATIONS);
            return stagnationCounter >= maxStagnation;
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
