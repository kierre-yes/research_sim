package com.thesis.cloudsim.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

@RestController
@RequestMapping("/api/plots")
public class PlotController {
    
    private static final Logger logger = LoggerFactory.getLogger(PlotController.class);
    
    @Value("${plots.directory:plots}")
    private String plotsDirectory;
    
    @GetMapping("/{simulationId}/{filename}")
    public ResponseEntity<Resource> getPlot(
            @PathVariable String simulationId,
            @PathVariable String filename) {
        
        try {
            // Sanitize inputs to prevent directory traversal
            if (simulationId.contains("..") || filename.contains("..")) {
                return ResponseEntity.badRequest().build();
            }
            
            // Only allow PNG files
            if (!filename.endsWith(".png")) {
                return ResponseEntity.badRequest().build();
            }
            
            Path plotPath = Paths.get(plotsDirectory, simulationId, filename).normalize();
            
            // Check if file exists
            if (!Files.exists(plotPath)) {
                logger.warn("Plot file not found: {}", plotPath);
                return ResponseEntity.notFound().build();
            }
            
            Resource resource = new UrlResource(plotPath.toUri());
            
            if (!resource.exists() || !resource.isReadable()) {
                logger.error("Plot file not readable: {}", plotPath);
                return ResponseEntity.notFound().build();
            }
            
            // Set proper headers for image serving (CORS handled by Spring config)
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.IMAGE_PNG);
            headers.setCacheControl("public, max-age=3600");
            
            return ResponseEntity.ok()
                    .headers(headers)
                    .body(resource);
                    
        } catch (MalformedURLException e) {
            logger.error("Error serving plot file", e);
            return ResponseEntity.badRequest().build();
        }
    }
    
    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("Plot service is running");
    }
}
