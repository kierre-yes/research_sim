package com.thesis.cloudsim.controller;

import com.thesis.cloudsim.algorithm.AlgorithmParameters;
import com.thesis.cloudsim.config.ParameterConfig;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import com.thesis.cloudsim.algorithm.AlgorithmFactory;  
import java.io.IOException;
import java.util.Map;

/**
 * REST Controller for managing algorithm parameters and configurations.
 */
@RestController
@RequestMapping("/api/config")
public class ConfigController {

    @PostMapping("/load")
    public ResponseEntity<AlgorithmParameters> loadConfig(@RequestParam String filePath) {
        try {
            AlgorithmParameters params = ParameterConfig.loadFromFile(filePath);
            return ResponseEntity.ok(params);
        } catch (IOException e) {
            return ResponseEntity.badRequest().body(null);
        }
    }

    @GetMapping("/default")
    public ResponseEntity<AlgorithmParameters> getDefaultConfig(@RequestParam String algorithmName) {
        AlgorithmParameters params = AlgorithmFactory.createDefaultParameters(algorithmName);
        return ResponseEntity.ok(params);
    }

    @PostMapping("/multi-objective")
    public ResponseEntity<AlgorithmParameters> createMultiObjectiveConfig(@RequestBody Map<String, Double> weights) {
        double makespan = weights.getOrDefault("makespanWeight", 0.25);
        double cost = weights.getOrDefault("costWeight", 0.25);
        double energy = weights.getOrDefault("energyWeight", 0.25);
        double loadBalance = weights.getOrDefault("loadBalanceWeight", 0.25);
        AlgorithmParameters params = AlgorithmFactory.createMultiObjectiveParameters(makespan, cost, energy, loadBalance);
        return ResponseEntity.ok(params);
    }
}
