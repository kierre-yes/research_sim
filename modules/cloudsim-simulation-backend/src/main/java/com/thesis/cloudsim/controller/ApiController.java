package com.thesis.cloudsim.controller;

import com.thesis.cloudsim.dto.SimulationRequest;
import com.thesis.cloudsim.metrics.SimulationResults;
// Import the new, simplified simulation manager located in the "simulation" package
import com.thesis.cloudsim.simulation.EnhancedSimulationManager;
// Removed old SimulationManager import – we now use EnhancedSimulationManager (see above).
import com.thesis.cloudsim.algorithm.ISchedulingAlgorithm;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.HashMap;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = {"http://localhost:3000", "http://localhost:5173"})
public class ApiController {

    private final ISchedulingAlgorithm epso;
    private final ISchedulingAlgorithm eaco;
    private final ObjectMapper objectMapper;

    public ApiController(@Qualifier("epso") ISchedulingAlgorithm epso,
                        @Qualifier("eaco") ISchedulingAlgorithm eaco,
                        ObjectMapper objectMapper) {
        this.epso = epso;
        this.eaco = eaco;
        this.objectMapper = objectMapper;
    }

    @PostMapping("/run")
    public ResponseEntity<?> runSimulation(@RequestBody SimulationRequest request) {
        try {
            // Pick algorithm instance based on simple string comparison (no reflection)
            ISchedulingAlgorithm algorithm;
            if ("EPSO".equalsIgnoreCase(request.getOptimizationAlgorithm())) {
                algorithm = epso;
                System.out.println("[DEBUG] Using EPSO algorithm for simulation");
            } else {
                algorithm = eaco;
                System.out.println("[DEBUG] Using EACO algorithm for simulation");
            }

            // Create the simulation manager that builds the datacenter and runs CloudSim
            EnhancedSimulationManager manager = new EnhancedSimulationManager(algorithm, request);
            SimulationResults results = manager.run();

            // Return HTTP 200 with JSON body
            return ResponseEntity.ok(results);
        } catch (Exception e) {
            e.printStackTrace();
            // Return detailed error message in response body
            Map<String, String> errorResponse = new HashMap<>();
            errorResponse.put("error", e.getClass().getSimpleName());
            errorResponse.put("message", e.getMessage());
            errorResponse.put("algorithm", request.getOptimizationAlgorithm());
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }

    @PostMapping("/run-with-file")
    public ResponseEntity<SimulationResults> runSimulationWithFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam Map<String, String> params) {
        try {
            // Convert params to SimulationRequest
            SimulationRequest request = mapParamsToRequest(params);
            
            // Save uploaded file temporarily
            Path tempFile = Files.createTempFile("workload", ".csv");
            Files.copy(file.getInputStream(), tempFile, StandardCopyOption.REPLACE_EXISTING);
            request.setWorkloadPath(tempFile.toString());
            
            // Choose algorithm (simple ternary operator – beginner-friendly)
            ISchedulingAlgorithm algorithm = "EPSO".equalsIgnoreCase(request.getOptimizationAlgorithm()) ? epso : eaco;

            // Run the simulation with the uploaded workload file
            EnhancedSimulationManager manager = new EnhancedSimulationManager(algorithm, request);
            SimulationResults results = manager.run();

            // Clean up the temporary CSV once done
            Files.deleteIfExists(tempFile);

            return ResponseEntity.ok(results);
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }

    private SimulationRequest mapParamsToRequest(Map<String, String> params) {
        SimulationRequest request = new SimulationRequest();
        
        // Map all the parameters from the form data
        request.setOptimizationAlgorithm(params.get("optimizationAlgorithm"));
        request.setNumHosts(Integer.parseInt(params.getOrDefault("numHosts", "10")));
        request.setNumVMs(Integer.parseInt(params.getOrDefault("numVMs", "50")));
        request.setNumPesPerHost(Integer.parseInt(params.getOrDefault("numPesPerHost", "2")));
        request.setPeMips(Integer.parseInt(params.getOrDefault("peMips", "2000")));
        request.setRamPerHost(Integer.parseInt(params.getOrDefault("ramPerHost", "2048")));
        request.setBwPerHost(Integer.parseInt(params.getOrDefault("bwPerHost", "10000")));
        request.setStoragePerHost(Integer.parseInt(params.getOrDefault("storagePerHost", "100000")));
        request.setVmMips(Integer.parseInt(params.getOrDefault("vmMips", "1000")));
        request.setVmPes(Integer.parseInt(params.getOrDefault("vmPes", "2")));
        request.setVmRam(Integer.parseInt(params.getOrDefault("vmRam", "1024")));
        request.setVmBw(Integer.parseInt(params.getOrDefault("vmBw", "1000")));
        request.setVmSize(Integer.parseInt(params.getOrDefault("vmSize", "10000")));
        request.setVmScheduler(params.getOrDefault("vmScheduler", "TimeShared"));
        request.setNumCloudlets(Integer.parseInt(params.getOrDefault("numCloudlets", "100")));
        request.setWorkloadType(params.getOrDefault("workloadType", "CSV"));
        request.setUseDefaultWorkload(Boolean.parseBoolean(params.getOrDefault("useDefaultWorkload", "false")));
        
        return request;
    }
}
