package com.thesis.cloudsim.matlab;

import com.mathworks.engine.MatlabEngine;
import com.thesis.cloudsim.dto.PlotMetadata;
import com.thesis.cloudsim.metrics.SimulationResults;
import com.thesis.cloudsim.metrics.SimulationResults.VmUtilization;
import com.thesis.cloudsim.dto.ProcessedResults;
import com.thesis.cloudsim.dto.TTestResults;
import com.thesis.cloudsim.service.PlotInterpretationService;
import jakarta.annotation.PreDestroy;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.TimeUnit;

@Service
@SuppressWarnings("unchecked")
public class MatlabIntegrationService implements PlotGenerationEngine {

    private static final Logger logger = LoggerFactory.getLogger(MatlabIntegrationService.class);
    private static final ObjectMapper objectMapper = new ObjectMapper();
    private static final int MATLAB_TIMEOUT_SECONDS = 300; // 5 minutes timeout
    private final PlotInterpretationService plotInterpretationService;
    
    @Value("${matlab.scripts.path:classpath:matlab}")
    private String matlabScriptsPath;
    
    private volatile MatlabEngine engine;

    public MatlabIntegrationService(PlotInterpretationService plotInterpretationService) {
        this.plotInterpretationService = plotInterpretationService;
    }

    // Try to connect lazily; no heavy work on context startup
    @org.springframework.context.event.EventListener(org.springframework.boot.context.event.ApplicationReadyEvent.class)
    public void warmUp() {
        // Fire-and-forget preload so first user request is fast
        java.util.concurrent.CompletableFuture.runAsync(() -> {
            try {
                ensureEngine();
            } catch (Exception e) {
                logger.error("MATLAB Engine initialization failed (non-critical)", e);
                logger.warn("MATLAB visualization features will be disabled");
            }
        });
    }

    private synchronized void ensureEngine() {
        if (engine != null) {
            logger.debug("MATLAB engine already initialized");
            return;
        }
        
        logger.info("Attempting to connect to MATLAB engine...");
        try {
            // Preferred: connect to pre-started shared engine
            logger.debug("Trying to connect to shared MATLAB engine 'thesisEngine'...");
            engine = MatlabEngine.connectMatlab("thesisEngine");
            logger.info("Successfully connected to shared MATLAB engine");
        } catch (Exception connectEx) {
            logger.warn("Failed to connect to shared MATLAB engine: {}", connectEx.getMessage());
            logger.debug("Connection error details:", connectEx);
            
            try {
                // Fallback: launch a new MATLAB process (takes ~50 s)
                logger.info("Starting new MATLAB engine instance...");
                engine = MatlabEngine.startMatlab();
                logger.info("Successfully started new MATLAB engine");
            } catch (Exception startEx) {
                logger.error("Failed to start MATLAB engine: {}", startEx.getMessage());
                logger.error("Stack trace:", startEx);
                throw new RuntimeException("Unable to start or connect to MATLAB engine", startEx);
            }
        }
    }

    public boolean isReady() {
        return engine != null;
    }

    public ProcessedResults processResults(SimulationResults results) {
        return processResults(results, "CloudSim");
    }
    
    public ProcessedResults processResults(SimulationResults results, String algorithmName) {
        logger.info("Processing results with MATLAB for algorithm: {}", algorithmName);
        
        try {
            // Check if MATLAB engine is available
            if (engine == null) {
                logger.warn("MATLAB engine not available, attempting to initialize...");
                try {
                    ensureEngine();
                } catch (Exception engineEx) {
                    logger.error("Failed to initialize MATLAB engine: {}", engineEx.getMessage());
                    // Return results without plots when MATLAB is unavailable
                    return ProcessedResults.builder()
                            .simulationId(results.getRunId() != null ? results.getRunId() : UUID.randomUUID().toString())
                            .rawResults(results)
                            .plotData(new HashMap<>())
                            .plotMetadata(new ArrayList<>())
                            .build();
                }
            }
            
            String runId = results.getRunId();
            if (runId == null || runId.isEmpty()) {
                runId = UUID.randomUUID().toString();
            }
            
            engine.putVariable("runId", runId);
            engine.putVariable("algorithmName", algorithmName);
            
            engine.eval("results = struct();");
            engine.eval("results.summary = struct();");
            
            double makespan = results.getSummary().getMakespan();
            double responseTime = results.getSummary().getResponseTime();
            double utilization = results.getSummary().getResourceUtilization();
            double loadBalance = results.getSummary().getLoadBalance();
            
            double balancePercentage = (1.0 - loadBalance) * 100.0;
            double clampedLoadBalance = Math.max(0.0, Math.min(100.0, balancePercentage));
            
            engine.putVariable("makespan", makespan);
            engine.putVariable("avgResponseTime", responseTime);
            engine.putVariable("resourceUtilization", utilization);
            engine.putVariable("loadBalancePercentage", clampedLoadBalance);
            engine.putVariable("imbalanceDegree", loadBalance);
            engine.eval("results.summary.makespan = makespan;");
            engine.eval("results.summary.averageResponseTime = avgResponseTime;");
            engine.eval("results.summary.resourceUtilization = resourceUtilization;");
            engine.eval("results.summary.loadBalancePercentage = loadBalancePercentage;");
            engine.eval("results.summary.imbalanceDegree = imbalanceDegree;");
            
            double successRate = 100.0;
            double throughput = results.getSummary().getMakespan() > 0 ? 
                1000.0 / results.getSummary().getMakespan() : 0.0;
                
            engine.putVariable("successRate", successRate);
            engine.putVariable("throughput", throughput);
            engine.eval("results.summary.successRate = successRate;");
            engine.eval("results.summary.throughput = throughput;");
            
            engine.putVariable("energyData", results.getEnergyConsumption());
            engine.eval("results.energyConsumption = energyData;");
            
            if (results.getVmUtilization() != null && !results.getVmUtilization().isEmpty()) {
                double[][] vmUtilMatrix = new double[results.getVmUtilization().size()][2];
                for (int i = 0; i < results.getVmUtilization().size(); i++) {
                    VmUtilization vm = results.getVmUtilization().get(i);
                    vmUtilMatrix[i][0] = vm.getCpuUtilization();
                    vmUtilMatrix[i][1] = vm.getRamUtilization();
                }
                engine.putVariable("vmUtilData", vmUtilMatrix);
                engine.eval("results.vmUtilization = vmUtilData;");
            } else {
                // If no VM utilization data, create empty matrix
                engine.eval("vmUtilData = [];");
            }
            
            try {
                // Resolve and add MATLAB scripts path
                String matlabPath = resolveMatlabScriptsPath();
                logger.debug("Adding MATLAB path: {}", matlabPath);
                // I escape backslashes and quotes for Windows paths so MATLAB doesn't get confused
                String escapedPath = matlabPath.replace("\\", "/");
                engine.putVariable("scriptPath", escapedPath);
                engine.eval("addpath(scriptPath);");
                
                // Check if script exists after adding path
                engine.eval("exist('generateComparisonPlots', 'file')");
                Object scriptExistsAfter = engine.getVariable("ans");
                if (scriptExistsAfter == null || ((Double) scriptExistsAfter) == 0) {
                    logger.error("MATLAB script generateComparisonPlots.m not found at: {}", matlabPath);
                    // Return results without plots when script is missing
                    return ProcessedResults.builder()
                            .simulationId(runId)
                            .rawResults(results)
                            .plotData(new HashMap<>())
                            .plotMetadata(new ArrayList<>())
                            .build();
                }
                
                // Clear any previous error state
                engine.eval("clear lasterror; lastwarn('');");
                
                // Pass string parameters as MATLAB variables to avoid escaping issues
                engine.putVariable("runIdParam", runId);
                engine.putVariable("algorithmNameParam", algorithmName);
                
                // Put all numeric inputs as MATLAB variables to avoid String.format issues
                engine.putVariable("avgResponseTime", responseTime);
                engine.putVariable("mkspan", makespan);
                engine.putVariable("resUtil", utilization);
                engine.putVariable("imbalance", loadBalance);
                engine.putVariable("thru", throughput);
                // energyData and vmUtilData were already set earlier
                
                // Initialize outputs
                engine.eval("plotJsonGenerated = false; plotPaths = {};");
                
                // Safer try/catch inside MATLAB without fprintf or quoted strings
                logger.debug("Executing MATLAB generateComparisonPlots with workspace variables");
                engine.eval(
                    "try;" +
                    "  plotPaths = generateComparisonPlots(avgResponseTime, mkspan, runIdParam, resUtil, imbalance, algorithmNameParam, thru, energyData, vmUtilData);" +
                    "  plotJsonGenerated = true;" +
                    "catch ME;" +
                    "  disp(ME.message);" +
                    "  for i = 1:length(ME.stack)" +
                    "    disp([ME.stack(i).name ' ' ME.stack(i).file ' line ' num2str(ME.stack(i).line)]);" +
                    "  end;" +
                    "  plotPaths = {}; plotJsonGenerated = false; plotJson = '{}';" +
                    "end"
                );
                
                // Check if the MATLAB script executed successfully
                Object plotJsonGenerated = engine.getVariable("plotJsonGenerated");
                boolean scriptSuccess = false;
                if (plotJsonGenerated != null) {
                    // MATLAB returns logical as double (0 or 1)
                    if (plotJsonGenerated instanceof Double) {
                        scriptSuccess = ((Double) plotJsonGenerated) > 0;
                    } else if (plotJsonGenerated instanceof Boolean) {
                        scriptSuccess = (Boolean) plotJsonGenerated;
                    }
                }
                
                if (!scriptSuccess) {
                    // Check for warnings or errors
                    engine.eval("[lastMsg, lastId] = lastwarn;");
                    String lastWarning = (String) engine.getVariable("lastMsg");
                    if (lastWarning != null && !lastWarning.isEmpty()) {
                        logger.warn("MATLAB warning: {}", lastWarning);
                    }
                    logger.error("MATLAB script did not generate plot JSON successfully");
                }

            } catch (Exception matlabEx) {
                logger.error("Error executing MATLAB script: {}", matlabEx.getMessage(), matlabEx);
                // Return results without plots on MATLAB execution error
                return ProcessedResults.builder()
                        .simulationId(runId)
                        .rawResults(results)
                        .plotData(new HashMap<>())
                        .plotMetadata(new ArrayList<>())
                        .build();
            }
            
            String json = null;
            try {
                // First check if plotJson variable exists
                engine.eval("exist('plotJson', 'var')");
                Object plotJsonExists = engine.getVariable("ans");
                if (plotJsonExists != null && ((Double) plotJsonExists) > 0) {
                    json = (String) engine.getVariable("plotJson");
                    logger.debug("Successfully retrieved plotJson from MATLAB");
                } else {
                    logger.warn("plotJson variable not found in MATLAB workspace, creating empty JSON");
                    json = "{}";
                }
            } catch (Exception varEx) {
                logger.error("Error retrieving plotJson variable: {}", varEx.getMessage());
                json = "{}";
            }
            
            java.util.Map<String,Object> plotMap = null;
            if (json != null) {
                try {
                    plotMap = objectMapper
                            .readValue(json, new com.fasterxml.jackson.core.type.TypeReference<java.util.Map<String,Object>>() {});
                } catch (Exception parseEx) {
                    logger.error("Error parsing JSON from MATLAB: {}", parseEx.getMessage());
                    throw parseEx;
                }
            } else {
                plotMap = new java.util.HashMap<>();
            }

            // --- Plot Metadata ---
            List<PlotMetadata> plotMetadataList = new ArrayList<>();
            List<String> plotFiles = (List<String>) plotMap.get("plotPaths");

            if (plotFiles != null) {
                for (String plotFile : plotFiles) {
                    PlotMetadata.PlotType plotType = PlotMetadata.PlotType.fromFilename(plotFile);

                    Map<String, Object> dataPoints = new HashMap<>();
                    dataPoints.put("makespan", results.getSummary().getMakespan());
                    dataPoints.put("responseTime", results.getSummary().getResponseTime());
                    dataPoints.put("resourceUtilization", results.getSummary().getResourceUtilization());
                    dataPoints.put("energyConsumption", results.getSummary().getEnergyConsumption());
                    dataPoints.put("loadBalance", results.getSummary().getLoadBalance());

                    PlotMetadata.PlotInterpretation interpretation = plotInterpretationService.interpretPlot(plotType, dataPoints, algorithmName);

                    PlotMetadata metadata = PlotMetadata.builder()
                            .plotId(java.util.UUID.randomUUID().toString())
                            .type(plotType)
                            .title(plotType.getDescription())
                            .filename(plotFile)
                            .dataPoints(dataPoints)
                            .interpretation(interpretation)
                            .build();

                    plotMetadataList.add(metadata);
                }
            }

            ProcessedResults processedResults = ProcessedResults.builder()
                    .simulationId(runId)
                    .plotData(plotMap)
                    .rawResults(results)
                    .plotMetadata(plotMetadataList)
                    .build();
            
            return processedResults;
            
        } catch (Exception e) {
            logger.error("MATLAB processing failed: ", e);
            // Return basic results without plots when any error occurs
            String fallbackRunId = results.getRunId() != null ? results.getRunId() : UUID.randomUUID().toString();
            return ProcessedResults.builder()
                    .simulationId(fallbackRunId)
                    .rawResults(results)
                    .plotData(new HashMap<>())
                    .plotMetadata(new ArrayList<>())
                    .build();
        }
    }

    public Map<String, Object> generateTTestPlots(com.thesis.cloudsim.dto.TTestResults tTestResults) {
        logger.info("Generating t-test visualization plots with MATLAB...");
        
        try {
            ensureEngine();
            
            Map<String, Object> plotPaths = new HashMap<>();
            
            int numMetrics = tTestResults.getMetricTests().size();
            double[] pValues = new double[numMetrics];
            double[] tStatistics = new double[numMetrics];
            double[] cohensD = new double[numMetrics];
            String[] metricNames = new String[numMetrics];
            
            int i = 0;
            for (Map.Entry<String, com.thesis.cloudsim.dto.TTestResults.MetricTest> entry : 
                 tTestResults.getMetricTests().entrySet()) {
                metricNames[i] = entry.getKey();
                pValues[i] = entry.getValue().getPValue();
                tStatistics[i] = entry.getValue().getTStatistic();
                cohensD[i] = entry.getValue().getCohensD();
                i++;
            }
            
            engine.putVariable("pValues", pValues);
            engine.putVariable("tStatistics", tStatistics);
            engine.putVariable("cohensD", cohensD);
            engine.putVariable("alpha", tTestResults.getAlpha());
            engine.putVariable("overallWinner", tTestResults.getOverallWinner());
            
            engine.eval("addpath('src/main/resources/matlab');");
            engine.eval("exist('pairedTTest', 'file')");
            Object scriptExists = engine.getVariable("ans");
            
            if (scriptExists != null && ((Double) scriptExists) > 0) {
                plotPaths.put("statisticalAnalysis", "plots/statistical_analysis/paired_ttest.png");
            } else {
                logger.warn("pairedTTest.m not found, skipping t-test visualization");
            }
            
            return plotPaths;
            
        } catch (Exception e) {
            logger.error("Failed to generate t-test plots: {}", e.getMessage());
            return new HashMap<>();
        }
    }
    
    private String resolveMatlabScriptsPath() {
        if (matlabScriptsPath.startsWith("classpath:")) {
            String resourcePath = matlabScriptsPath.substring("classpath:".length());
            String currentDir = System.getProperty("user.dir");
            return currentDir + "/src/main/resources/" + resourcePath;
        } else if (new java.io.File(matlabScriptsPath).isAbsolute()) {
            return matlabScriptsPath;
        } else {
            String currentDir = System.getProperty("user.dir");
            return currentDir + "/" + matlabScriptsPath;
        }
    }
    
    @Override
    public void shutdown() {
        close();
    }
    
    @PreDestroy
    public void close() {
        if (engine != null) {
            try {
                engine.disconnect();
            } catch (Exception e) {
                logger.warn("Error disconnecting MATLAB engine: {}", e.getMessage());
            }
        }
    }
}
