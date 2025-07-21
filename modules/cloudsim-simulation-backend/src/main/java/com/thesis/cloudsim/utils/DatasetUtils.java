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
        try (BufferedReader br = new BufferedReader(new FileReader(path))) {
            String line = br.readLine(); // Skip header if present
            while ((line = br.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                String[] tokens = line.split(",");
                long length = Long.parseLong(tokens[0].trim());
                int pes = tokens.length > 1 ? Integer.parseInt(tokens[1].trim()) : 1;
                
                // Parse network-related columns for enhanced cost model
                // Values in CSV are normalized (0-1), convert to bytes assuming 1GB max
                double normalizedFileSize = tokens.length > 2 ? Double.parseDouble(tokens[2].trim()) : 0.0;
                double normalizedOutputSize = tokens.length > 3 ? Double.parseDouble(tokens[3].trim()) : 0.0;
                
                // Convert normalized values to bytes (assuming max 1GB = 1024*1024*1024 bytes)
                long fileSize = (long)(normalizedFileSize * 1024 * 1024 * 1024);
                long outputSize = (long)(normalizedOutputSize * 1024 * 1024 * 1024);

                Cloudlet cl = new Cloudlet(cloudletId++, length, pes, fileSize, outputSize, 
                    new UtilizationModelFull(), new UtilizationModelFull(), new UtilizationModelFull());
                cl.setUserId(0); // Set user ID
                cloudlets.add(cl);
            }
        }

        if (cloudlets.isEmpty()) {
            throw new IOException("Empty dataset: " + path);
        }
        return cloudlets;
    }
} 