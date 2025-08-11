package com.thesis.cloudsim.utils;

import org.cloudbus.cloudsim.Cloudlet;
import org.cloudbus.cloudsim.UtilizationModelFull;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;

public class DatasetUtils {
    
    /**
     * Add the rest of the columns, forgot on last july
     */
    public static class WorkloadData {
        public final List<Cloudlet> cloudlets;
        public final List<Double> arrivalTimes; 
        
        public WorkloadData(List<Cloudlet> cloudlets, List<Double> arrivalTimes) {
            this.cloudlets = cloudlets;
            this.arrivalTimes = arrivalTimes;
        }
        
        public boolean hasArrivalTimes() {
            return arrivalTimes != null && !arrivalTimes.isEmpty();
        }
    }

    /**
     * Load workload CSV → List<Cloudlet> using only core Java classes so a
     * beginner can read every statement. Expected CSV columns:
     *   length,pes,file_size,output_size
     * First row (header) is skipped.
     */
    public List<Cloudlet> loadWorkload(String path) throws IOException {
        return loadWorkload(path, -1); // No limit by default
    }
    
    /**
     * Load workload CSV with optional limit on number of cloudlets.
     * @param path CSV file path
     * @param maxCloudlets Maximum cloudlets to load (-1 for no limit)
     */
    public List<Cloudlet> loadWorkload(String path, int maxCloudlets) throws IOException {
        List<Cloudlet> cloudlets = new ArrayList<>();
        int cloudletId = 0; // Manual ID counter
        int lineNumber = 0;
        
        try (BufferedReader br = new BufferedReader(new FileReader(path))) {
            String line = br.readLine(); // Skip header if present
            lineNumber++;
            
            while ((line = br.readLine()) != null && (maxCloudlets < 0 || cloudlets.size() < maxCloudlets)) {
                lineNumber++;
                if (line.isBlank()) {
                    continue;
                }
                
                try {
                    String[] tokens = line.split(",");
                    if (tokens.length < 1 || tokens[0].trim().isEmpty()) {
                        continue; // Skip rows with empty first column
                    }
                    
                    // Handle floating-point values by parsing as double first, then converting to long
                    long length = (long) Double.parseDouble(tokens[0].trim());
                    
                    // Ensure length is reasonable (at least 1000 MI)
                    if (length < 1000) {
                        length = 1000;
                    }
                    
                    // Parse PES with default value
                    int pes = 1;
                    if (tokens.length > 1 && !tokens[1].trim().isEmpty()) {
                        try {
                            pes = Math.max(1, (int) Double.parseDouble(tokens[1].trim()));
                        } catch (NumberFormatException e) {
                            pes = 1;
                        }
                    }
                    
                    // Parse network-related columns for enhanced cost model
                    // Values in CSV are normalized (0-1), convert to bytes assuming 1GB max
                    double normalizedFileSize = 0.0;
                    double normalizedOutputSize = 0.0;
                    
                    if (tokens.length > 2 && !tokens[2].trim().isEmpty()) {
                        try {
                            normalizedFileSize = Double.parseDouble(tokens[2].trim());
                        } catch (NumberFormatException e) {
                            normalizedFileSize = 0.1; // Default small file size
                        }
                    }
                    
                    if (tokens.length > 3 && !tokens[3].trim().isEmpty()) {
                        try {
                            normalizedOutputSize = Double.parseDouble(tokens[3].trim());
                        } catch (NumberFormatException e) {
                            normalizedOutputSize = 0.1; // Default small output size
                        }
                    }
                    
                    // Convert normalized values to bytes (assuming max 1GB = 1024*1024*1024 bytes)
                    long fileSize = Math.max(300, (long)(normalizedFileSize * 1024 * 1024 * 1024));
                    long outputSize = Math.max(300, (long)(normalizedOutputSize * 1024 * 1024 * 1024));

                    Cloudlet cl = new Cloudlet(cloudletId++, length, pes, fileSize, outputSize, 
                        new UtilizationModelFull(), new UtilizationModelFull(), new UtilizationModelFull());
                    cl.setUserId(0); // Set user ID
                    cloudlets.add(cl);
                    
                } catch (Exception e) {
                    System.err.println("Error parsing line " + lineNumber + ": " + line);
                    System.err.println("Error: " + e.getMessage());
                    // Skip this line and continue
                }
            }
        }

        if (cloudlets.isEmpty()) {
            throw new IOException("Empty dataset: " + path);
        }
        
        System.out.println("[DEBUG] Successfully loaded " + cloudlets.size() + " cloudlets from CSV file");
        System.out.println("[DEBUG] Read " + lineNumber + " total lines from file");
        
        return cloudlets;
    }
    
    /**
     * Enhanced workload loader that handles both normalized and raw Google Cluster schemas
     * Returns cloudlets with optional arrival times for staged submission
     */
    public WorkloadData loadWorkloadWithTiming(String path, int maxCloudlets) throws IOException {
        List<Cloudlet> cloudlets = new ArrayList<>();
        List<Double> arrivalTimes = new ArrayList<>();
        int cloudletId = 0;
        int lineNumber = 0;
        Double minArrivalTime = null;
        boolean hasArrivalData = false;
        
        try (BufferedReader br = new BufferedReader(new FileReader(path))) {
            // Read and parse header to detect schema
            String headerLine = br.readLine();
            lineNumber++;
            
            if (headerLine == null || headerLine.isBlank()) {
                throw new IOException("Empty or missing header in CSV file");
            }
            
            // Parse header to build column index map
            String[] headers = headerLine.toLowerCase().split(",");
            Map<String, Integer> columnIndex = new HashMap<>();
            for (int i = 0; i < headers.length; i++) {
                columnIndex.put(headers[i].trim(), i);
            }
            
            // Detect schema type
            boolean isNormalizedSchema = columnIndex.containsKey("length") && columnIndex.containsKey("pes");
            boolean isGoogleSchema = columnIndex.containsKey("cpu_request") || columnIndex.containsKey("arrival_ts");
            
            System.out.println("[DEBUG] Detected schema - Normalized: " + isNormalizedSchema + ", Google: " + isGoogleSchema);
            System.out.println("[DEBUG] Available columns: " + columnIndex.keySet());
            
            // Read data rows
            String line;
            while ((line = br.readLine()) != null && (maxCloudlets < 0 || cloudlets.size() < maxCloudlets)) {
                lineNumber++;
                if (line.isBlank()) {
                    continue;
                }
                
                try {
                    String[] tokens = line.split(",");
                    if (tokens.length == 0) {
                        continue;
                    }
                    
                    // Parse based on detected schema
                    long length;
                    int pes;
                    long fileSize;
                    long outputSize;
                    Double arrivalTime = null;
                    
                    if (isNormalizedSchema) {
                        // Handle normalized schema (length, pes, file_size, output_size)
                        length = parseColumnAsLong(tokens, columnIndex, "length", 1000);
                        pes = parseColumnAsInt(tokens, columnIndex, "pes", 1);
                        
                        // File sizes might be normalized [0,1] or actual values
                        double fsValue = parseColumnAsDouble(tokens, columnIndex, "file_size", 0.1);
                        double osValue = parseColumnAsDouble(tokens, columnIndex, "output_size", 0.1);
                        
                        // If values are small (< 1), treat as normalized
                        if (fsValue <= 1.0) {
                            fileSize = Math.max(300, (long)(fsValue * 1024 * 1024 * 1024));
                            outputSize = Math.max(300, (long)(osValue * 1024 * 1024 * 1024));
                        } else {
                            fileSize = Math.max(300, (long)fsValue);
                            outputSize = Math.max(300, (long)osValue);
                        }
                        
                        // Check for optional arrival time
                        if (columnIndex.containsKey("arrival_time")) {
                            arrivalTime = parseColumnAsDouble(tokens, columnIndex, "arrival_time", null);
                        }
                        
                    } else if (isGoogleSchema) {
                        // Handle Google Cluster schema
                        // PES from pes_number
                        pes = parseColumnAsInt(tokens, columnIndex, "pes_number", 1);
                        
                        // File sizes from file_size and output_size columns
                        double fsValue = parseColumnAsDouble(tokens, columnIndex, "file_size", 0.01);
                        double osValue = parseColumnAsDouble(tokens, columnIndex, "output_size", 0.01);
                        
                        // Google traces seem to have normalized values
                        fileSize = Math.max(300, (long)(fsValue * 1024 * 1024 * 1024));
                        outputSize = Math.max(300, (long)(osValue * 1024 * 1024 * 1024));
                        
                        // Calculate length from cpu_request
                        double cpuRequest = parseColumnAsDouble(tokens, columnIndex, "cpu_request", 0.01);
                        
                        // Check for time_window to calculate actual work
                        if (columnIndex.containsKey("time_window")) {
                            double timeWindow = parseColumnAsDouble(tokens, columnIndex, "time_window", 60.0);
                            // length = CPU fraction * time window * MIPS per core
                            length = Math.max(1000, (long)(cpuRequest * timeWindow * 2000)); // 2000 MIPS per core
                        } else {
                            // Heuristic: scale CPU request to MI
                            length = Math.max(1000, (long)(cpuRequest * 100000)); // Scale factor
                        }
                        
                        // Parse arrival time if available
                        if (columnIndex.containsKey("arrival_ts")) {
                            String arrivalStr = getColumnValue(tokens, columnIndex, "arrival_ts");
                            if (arrivalStr != null && !arrivalStr.isEmpty()) {
                                try {
                                    double arrivalTs = Double.parseDouble(arrivalStr);
                                    // Assume microseconds based on idle_timeout_us presence
                                    arrivalTime = arrivalTs / 1_000_000.0; // Convert to seconds
                                    hasArrivalData = true;
                                    
                                    // Track min for normalization
                                    if (minArrivalTime == null || arrivalTime < minArrivalTime) {
                                        minArrivalTime = arrivalTime;
                                    }
                                } catch (NumberFormatException e) {
                                    // No arrival time for this row
                                }
                            }
                        }
                        
                    } else {
                        // Unknown schema, try to be flexible
                        // Look for any combination of known columns
                        length = 10000; // Default
                        pes = 1;
                        fileSize = 1024;
                        outputSize = 1024;
                        
                        // Try to find useful columns
                        if (columnIndex.containsKey("length")) {
                            length = parseColumnAsLong(tokens, columnIndex, "length", 10000);
                        } else if (columnIndex.containsKey("cpu_request")) {
                            double cpu = parseColumnAsDouble(tokens, columnIndex, "cpu_request", 0.01);
                            length = Math.max(1000, (long)(cpu * 100000));
                        }
                        
                        if (columnIndex.containsKey("pes")) {
                            pes = parseColumnAsInt(tokens, columnIndex, "pes", 1);
                        } else if (columnIndex.containsKey("pes_number")) {
                            pes = parseColumnAsInt(tokens, columnIndex, "pes_number", 1);
                        }
                    }
                    
                    // Ensure reasonable bounds
                    length = Math.max(1000, Math.min(length, 10_000_000)); // 1K to 10M MI
                    pes = Math.max(1, Math.min(pes, 8)); // 1 to 8 PEs
                    
                    // Create cloudlet
                    Cloudlet cl = new Cloudlet(cloudletId++, length, pes, fileSize, outputSize,
                        new UtilizationModelFull(), new UtilizationModelFull(), new UtilizationModelFull());
                    cl.setUserId(0);
                    cloudlets.add(cl);
                    
                    // Store arrival time (will be normalized later)
                    arrivalTimes.add(arrivalTime);
                    
                } catch (Exception e) {
                    System.err.println("[WARN] Error parsing line " + lineNumber + ": " + e.getMessage());
                    // Continue processing other lines
                }
            }
        }
        
        if (cloudlets.isEmpty()) {
            throw new IOException("No valid cloudlets loaded from: " + path);
        }
        
        // Normalize arrival times if present
        if (hasArrivalData && minArrivalTime != null) {
            System.out.println("[DEBUG] Normalizing arrival times from min: " + minArrivalTime + " seconds");
            for (int i = 0; i < arrivalTimes.size(); i++) {
                Double time = arrivalTimes.get(i);
                if (time != null) {
                    arrivalTimes.set(i, time - minArrivalTime);
                } else {
                    // If some entries don't have arrival time, set to 0
                    arrivalTimes.set(i, 0.0);
                }
            }
            System.out.println("[DEBUG] Arrival times range: 0 to " + 
                arrivalTimes.stream().filter(t -> t != null).mapToDouble(Double::doubleValue).max().orElse(0) + " seconds");
        } else {
            // No arrival data, return null to indicate batch submission
            arrivalTimes = null;
        }
        
        System.out.println("[DEBUG] Loaded " + cloudlets.size() + " cloudlets" + 
            (arrivalTimes != null ? " with arrival times" : " for batch submission"));
        
        return new WorkloadData(cloudlets, arrivalTimes);
    }
    
    // Helper methods for safe column parsing
    private String getColumnValue(String[] tokens, Map<String, Integer> columnIndex, String columnName) {
        Integer index = columnIndex.get(columnName);
        if (index != null && index < tokens.length) {
            return tokens[index].trim();
        }
        return null;
    }
    
    private double parseColumnAsDouble(String[] tokens, Map<String, Integer> columnIndex, String columnName, Double defaultValue) {
        String value = getColumnValue(tokens, columnIndex, columnName);
        if (value != null && !value.isEmpty()) {
            try {
                return Double.parseDouble(value);
            } catch (NumberFormatException e) {
                // Fall through to default
            }
        }
        return defaultValue != null ? defaultValue : 0.0;
    }
    
    private long parseColumnAsLong(String[] tokens, Map<String, Integer> columnIndex, String columnName, long defaultValue) {
        double value = parseColumnAsDouble(tokens, columnIndex, columnName, (double)defaultValue);
        return (long)value;
    }
    
    private int parseColumnAsInt(String[] tokens, Map<String, Integer> columnIndex, String columnName, int defaultValue) {
        double value = parseColumnAsDouble(tokens, columnIndex, columnName, (double)defaultValue);
        return Math.max(1, (int)Math.round(value));
    }
}
