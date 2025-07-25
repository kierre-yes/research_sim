package com.thesis.cloudsim.utils;

import org.cloudbus.cloudsim.Cloudlet;
import org.cloudbus.cloudsim.UtilizationModelFull;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class DatasetUtils {

    /**
     * Load workload CSV → List<Cloudlet> using only core Java classes so a
     * beginner can read every statement. Expected CSV columns:
     *   length,pes,file_size,output_size
     * First row (header) is skipped.
     */
    public List<Cloudlet> loadWorkload(String path) throws IOException {
        List<Cloudlet> cloudlets = new ArrayList<>();
        int cloudletId = 0; // Manual ID counter
        int lineNumber = 0;
        int maxCloudlets = 100; // Limit to first 100 cloudlets for now
        
        try (BufferedReader br = new BufferedReader(new FileReader(path))) {
            String line = br.readLine(); // Skip header if present
            lineNumber++;
            
            while ((line = br.readLine()) != null && cloudlets.size() < maxCloudlets) {
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
} 