package com.thesis.cloudsim.dto;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;

import java.time.LocalDateTime;
import java.util.Map;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SimulationHistoryEntry {
    private String id;
    private LocalDateTime timestamp;
    private String algorithm;
    private Map<String, Object> config;
    private Map<String, Object> summary;
    private Map<String, Object> energyConsumption;
    private Map<String, Object> vmUtilization;
    private Map<String, Object> plotData; // For MATLAB plot data
}
