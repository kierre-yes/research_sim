function plotPaths = generateComparisonPlots(avgResponseTime, makespan, runId)
    % generateComparisonPlots - Creates visualization plots for CloudSim results
    % This function is called by MatlabIntegrationService.java
    % 
    % The backend passes these variables:
    % - runId, algorithmName
    % - results.summary.makespan, averageResponseTime, resourceUtilization, imbalanceDegree
    % - results.summary.successRate, throughput
    % - results.energyConsumption
    % - results.vmUtilization (if available)
    
    % Get all variables from base workspace that were set by Java
    algorithmName = evalin('base', 'algorithmName');
    resourceUtilization = evalin('base', 'resourceUtilization');
    imbalanceDegree = evalin('base', 'imbalanceDegree');
    successRate = evalin('base', 'successRate');
    throughput = evalin('base', 'throughput');
    energyData = evalin('base', 'energyData');
    
    % Check if VM utilization data exists
    hasVmData = evalin('base', 'exist(''vmUtilData'', ''var'')');
    if hasVmData
        vmUtilData = evalin('base', 'vmUtilData');
    end
    
    % Create output directory
    outputDir = fullfile('plots', runId);
    if ~exist(outputDir, 'dir')
        mkdir(outputDir);
    end
    
    % Initialize plot paths
    plotPaths = {};
    
    % Since we're comparing EACO vs EPSO, we need data for both
    % The controller runs each algorithm separately, so we'll create comparison data
    % In real implementation, you'd store results from both runs
    
    % For now, we'll show the current algorithm's results
    % The frontend will combine results from both algorithm runs
    
    %% Plot 1: Five Key Metrics Bar Chart
    figure('Visible', 'off', 'Position', [100, 100, 1000, 600]);
    
    % The 5 key metrics from your research
    metrics = categorical({'Makespan', 'Response Time', 'Resource Util.', 'Energy Cons.', 'Load Balance'});
    metrics = reordercats(metrics, {'Makespan', 'Response Time', 'Resource Util.', 'Energy Cons.', 'Load Balance'});
    
    % Current algorithm values
    values = [
        makespan;
        avgResponseTime;
        resourceUtilization;
        energyData / 1000; % Convert to kWh for better scale
        100 - (imbalanceDegree * 100) % Convert to balance percentage
    ];
    
    % Create bar plot
    b = bar(metrics, values);
    b.FaceColor = [0.2 0.6 0.8];
    
    % Add value labels on bars
    text(b.XEndPoints, b.YEndPoints, string(round(values, 2)), ...
        'HorizontalAlignment', 'center', 'VerticalAlignment', 'bottom');
    
    % Formatting
    ylabel('Metric Values');
    title(sprintf('%s Algorithm - Performance Metrics', algorithmName), 'FontSize', 14);
    grid on;
    set(gca, 'FontSize', 12);
    
    % Save plot
    plotPath1 = fullfile(outputDir, sprintf('%s_metrics.png', lower(algorithmName)));
    saveas(gcf, plotPath1);
    plotPaths{end+1} = plotPath1;
    close(gcf);
    
    %% Plot 2: Detailed Performance Analysis
    figure('Visible', 'off', 'Position', [100, 100, 1200, 800]);
    
    % Subplot 1: Time Metrics
    subplot(2, 2, 1);
    timeMetrics = [makespan; avgResponseTime];
    timeLabels = categorical({'Makespan', 'Avg Response Time'});
    b1 = bar(timeLabels, timeMetrics);
    b1.FaceColor = [0.4 0.6 0.9];
    ylabel('Time (seconds)');
    title('Time-based Metrics');
    grid on;
    
    % Add values on bars
    text(b1.XEndPoints, b1.YEndPoints, string(round(timeMetrics, 2)), ...
        'HorizontalAlignment', 'center', 'VerticalAlignment', 'bottom');
    
    % Subplot 2: Efficiency Metrics
    subplot(2, 2, 2);
    effMetrics = [resourceUtilization; successRate * 100; throughput];
    effLabels = categorical({'Resource Util.', 'Success Rate', 'Throughput'});
    b2 = bar(effLabels, effMetrics);
    b2.FaceColor = [0.4 0.8 0.6];
    ylabel('Value');
    title('Efficiency Metrics');
    grid on;
    
    % Add values on bars
    text(b2.XEndPoints, b2.YEndPoints, string(round(effMetrics, 2)), ...
        'HorizontalAlignment', 'center', 'VerticalAlignment', 'bottom');
    
    % Subplot 3: Energy Analysis
    subplot(2, 2, 3);
    energyMetrics = [energyData; energyData/makespan; energyData/(throughput*makespan)];
    energyLabels = categorical({'Total Energy', 'Energy/Time', 'Energy/Task'});
    b3 = bar(energyLabels, energyMetrics);
    b3.FaceColor = [0.9 0.6 0.4];
    ylabel('Energy (Wh)');
    title('Energy Consumption Analysis');
    grid on;
    
    % Subplot 4: Load Distribution
    subplot(2, 2, 4);
    loadMetrics = [100 - (imbalanceDegree * 100); imbalanceDegree * 100];
    pie(loadMetrics, {'Balanced Load', 'Imbalance'});
    title('Load Distribution');
    colormap([0.4 0.8 0.6; 0.9 0.4 0.4]);
    
    % Save plot
    plotPath2 = fullfile(outputDir, sprintf('%s_detailed_analysis.png', lower(algorithmName)));
    saveas(gcf, plotPath2);
    plotPaths{end+1} = plotPath2;
    close(gcf);
    
    %% Plot 3: VM Utilization (if data available)
    if hasVmData && ~isempty(vmUtilData)
        figure('Visible', 'off', 'Position', [100, 100, 1000, 600]);
        
        % Extract CPU and RAM utilization
        cpuUtil = vmUtilData(:, 1);
        ramUtil = vmUtilData(:, 2);
        vmIds = 1:length(cpuUtil);
        
        % Create grouped bar chart
        subplot(2, 1, 1);
        bar(vmIds, [cpuUtil, ramUtil]);
        xlabel('VM ID');
        ylabel('Utilization (%)');
        title(sprintf('%s - VM Resource Utilization', algorithmName));
        legend({'CPU', 'RAM'}, 'Location', 'northwest');
        grid on;
        ylim([0 100]);
        
        % Average utilization pie chart
        subplot(2, 1, 2);
        avgCpu = mean(cpuUtil);
        avgRam = mean(ramUtil);
        pie([avgCpu, avgRam, 100-avgCpu, 100-avgRam], ...
            {sprintf('CPU Used (%.1f%%)', avgCpu), ...
             sprintf('RAM Used (%.1f%%)', avgRam), ...
             'CPU Free', 'RAM Free'});
        title('Average Resource Utilization');
        colormap([0.8 0.2 0.2; 0.2 0.2 0.8; 0.9 0.7 0.7; 0.7 0.7 0.9]);
        
        plotPath3 = fullfile(outputDir, sprintf('%s_vm_utilization.png', lower(algorithmName)));
        saveas(gcf, plotPath3);
        plotPaths{end+1} = plotPath3;
        close(gcf);
    end
    
    %% Create data structure for frontend
    plotData = struct();
    
    % Store metric values
    plotData.metrics = struct(...
        'makespan', makespan, ...
        'avgResponseTime', avgResponseTime, ...
        'resourceUtilization', resourceUtilization, ...
        'energyConsumption', energyData, ...
        'loadBalance', 100 - (imbalanceDegree * 100), ...
        'successRate', successRate * 100, ...
        'throughput', throughput ...
    );
    
    % Store plot paths
    plotData.plotPaths = plotPaths;
    
    % Algorithm info
    plotData.algorithm = algorithmName;
    plotData.simulationId = runId;
    
    % Convert to JSON and store in base workspace for Java to retrieve
    plotJson = jsonencode(plotData);
    assignin('base', 'plotJson', plotJson);
    
    % Display summary
    fprintf('\n=== %s Algorithm Results ===\n', algorithmName);
    fprintf('Makespan: %.2f seconds\n', makespan);
    fprintf('Avg Response Time: %.2f seconds\n', avgResponseTime);
    fprintf('Resource Utilization: %.2f%%\n', resourceUtilization);
    fprintf('Energy Consumption: %.2f Wh\n', energyData);
    fprintf('Load Balance: %.2f%%\n', 100 - (imbalanceDegree * 100));
    fprintf('Success Rate: %.2f%%\n', successRate * 100);
    fprintf('Throughput: %.2f tasks/sec\n', throughput);
    fprintf('Plots generated: %d\n', length(plotPaths));
    fprintf('============================\n');
end
