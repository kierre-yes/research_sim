package com.thesis.cloudsim.controller;

import com.thesis.cloudsim.dto.SimulationRequest;
import com.thesis.cloudsim.metrics.SimulationResults;
import com.thesis.cloudsim.simulation.SimulationManager;
import com.thesis.cloudsim.algorithm.ISchedulingAlgorithm;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;

@RestController
@RequestMapping("/api/simulate")
public class SimulationController {

    private final ISchedulingAlgorithm epso;
    private final ISchedulingAlgorithm eaco;

    public SimulationController(@Qualifier("epso") ISchedulingAlgorithm epso,
                                @Qualifier("eaco") ISchedulingAlgorithm eaco) {
        this.epso = epso;
        this.eaco = eaco;
    }

    @PostMapping
    public SimulationResults runSimulation(@RequestBody SimulationRequest request) throws IOException {
        ISchedulingAlgorithm algorithm = "EPSO".equalsIgnoreCase(request.getAlgorithm()) ? epso : eaco;
        SimulationManager manager = new SimulationManager(algorithm, request.getWorkloadPath());
        return manager.run();
    }
}
