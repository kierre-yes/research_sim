package com.thesis.cloudsim.utils;

import org.cloudbus.cloudsim.cloudlets.Cloudlet;
import org.cloudbus.cloudsim.cloudlets.CloudletSimple;
import org.cloudbus.cloudsim.utilizationmodels.UtilizationModelFull;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class DatasetUtils {

    /**
     * Loads a workload from a CSV file whose rows are formatted as: length,pes
     * (The header line is skipped.)
     */
    public List<Cloudlet> loadWorkload(String path) throws IOException {
        List<Cloudlet> cloudlets = new ArrayList<>();
        try (BufferedReader br = new BufferedReader(new FileReader(path))) {
            String line = br.readLine(); // Skip header if present
            while ((line = br.readLine()) != null) {
                if (line.isBlank()) {
                    continue;
                }
                String[] tokens = line.split(",");
                long length = Long.parseLong(tokens[0].trim());
                int pes = tokens.length > 1 ? Integer.parseInt(tokens[1].trim()) : 1;

                Cloudlet cl = new CloudletSimple(length, pes);
                cl.setUtilizationModel(new UtilizationModelFull());
                cloudlets.add(cl);
            }
        }

        if (cloudlets.isEmpty()) {
            throw new IOException("Empty dataset: " + path);
        }
        return cloudlets;
    }
} 