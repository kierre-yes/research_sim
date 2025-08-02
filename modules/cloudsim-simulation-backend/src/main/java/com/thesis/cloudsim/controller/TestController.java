package com.thesis.cloudsim.controller;

import com.thesis.cloudsim.matlab.MatlabIntegrationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/test")
@CrossOrigin(origins = {"http://localhost:3000", "http://localhost:5173"})
public class TestController {

    @Autowired(required = false)
    private MatlabIntegrationService matlabService;

    @GetMapping("/matlab-status")
    public ResponseEntity<Map<String, Object>> checkMatlabStatus() {
        Map<String, Object> status = new HashMap<>();
        
        if (matlabService == null) {
            status.put("matlabAvailable", false);
            status.put("message", "MATLAB integration service not configured");
        } else {
            status.put("matlabAvailable", true);
            status.put("engineReady", matlabService.isReady());
            status.put("message", matlabService.isReady() ? 
                "MATLAB engine is ready" : "MATLAB engine is initializing");
        }
        
        status.put("backendPort", 8081);
        status.put("plotsEndpoint", "/api/plots/{simulationId}/{filename}");
        
        return ResponseEntity.ok(status);
    }
}
