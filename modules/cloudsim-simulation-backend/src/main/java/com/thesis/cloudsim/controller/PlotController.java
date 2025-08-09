package com.thesis.cloudsim.controller;

import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.nio.file.Path;
import java.nio.file.Paths;

@RestController
@RequestMapping("/api/plots")
public class PlotController {

    @GetMapping("/{simulationId}/{filename}")
    public ResponseEntity<Resource> getPlot(
            @PathVariable String simulationId,
            @PathVariable String filename) {
        try {
            // Validate inputs to prevent path traversal
            if (simulationId.contains("..")
                    || simulationId.contains("/")
                    || simulationId.contains("\\")
                    || filename.contains("..")
                    || filename.contains("/")
                    || filename.contains("\\")) {
                return ResponseEntity.badRequest().build();
            }
            
            // Validate filename format
            if (!filename.matches("^[a-zA-Z0-9_-]+\\.(png|jpg|jpeg)$")) {
                return ResponseEntity.badRequest().build();
            }
            
            // Construct path to the plot file
            Path basePath = Paths.get("plots").toAbsolutePath().normalize();
            Path filePath = basePath.resolve(simulationId).resolve(filename).normalize();
            
            // Ensure the resolved path is within the expected directory
            if (!filePath.startsWith(basePath)) {
                return ResponseEntity.badRequest().build();
            }
            
            Resource resource = new UrlResource(filePath.toUri());
            
            if (resource.exists() && resource.isReadable()) {
                // Determine content type
                String contentType = "image/png"; // Default to PNG
                if (filename.toLowerCase().endsWith(".jpg") || filename.toLowerCase().endsWith(".jpeg")) {
                    contentType = "image/jpeg";
                }
                
                return ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType(contentType))
                        .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + resource.getFilename() + "\"")
                        .body(resource);
            } else {
                return ResponseEntity.notFound().build();
            }
        } catch (Exception e) {
            return ResponseEntity.badRequest().build();
        }
    }
}
