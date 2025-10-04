package com.thesis.cloudsim.util;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class SimulationProgressHolder {
    
    private static final AtomicInteger currentIteration = new AtomicInteger(0);
    private static final AtomicInteger totalIterations = new AtomicInteger(0);
    private static final AtomicReference<String> currentStage = new AtomicReference<>("INITIALIZING");
    private static volatile boolean isComparisonRunning = false;
    
    public static void setCurrentIteration(int iteration, int total, String stage) {
        currentIteration.set(iteration);
        totalIterations.set(total);
        currentStage.set(stage);
    }
    
    public static void setStage(String stage) {
        if (!isComparisonRunning) {
            currentStage.set(stage);
        }
    }
    
    public static void reset() {
        // Don't allow resets during comparison to prevent progress flickering
        if (!isComparisonRunning) {
            currentIteration.set(0);
            totalIterations.set(0);
            currentStage.set("INITIALIZING");
        }
    }
    
    public static void setComparisonRunning(boolean running) {
        isComparisonRunning = running;
    }
    
    public static int getCurrentIteration() {
        return currentIteration.get();
    }
    
    public static int getTotalIterations() {
        return totalIterations.get();
    }
    
    public static String getCurrentStage() {
        return currentStage.get();
    }
    
    public static String getProgressInfo() {
        int current = currentIteration.get();
        int total = totalIterations.get();
        String stage = currentStage.get();
        
        if (total <= 1) {
            return stage;
        }
        
        return String.format("%s - Iteration %d of %d", stage, current, total);
    }
}