package com.thesis.cloudsim.dto;

import com.thesis.cloudsim.metrics.SimulationResults;
import lombok.Builder;
import lombok.Data;

import java.util.Map;

@Data
@Builder
public class ProcessedResults {
/** Unique ID to correlate simulation + MATLAB plots */
    private String simulationId;
    /** Raw numerical results coming from CloudSim run */
    private SimulationResults rawResults;
    /** Chart-ready structure – keys map to chart names, values are any JSON-serialisable payload */
    private Map<String, Object> plotData;
}
