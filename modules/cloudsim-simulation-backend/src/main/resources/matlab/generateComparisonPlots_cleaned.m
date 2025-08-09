function plotPaths = generateComparisonPlots(avgResponseTime, makespan, runId)
    % Generate plots for comparing EACO and EPSO algorithms
    % This version uses ONLY actual data from the simulation
    
    % Get data from base workspace (passed from Java)
    resourceUtilization = evalin('base', 'resourceUtilization');
    loadBalancePercentage = evalin('base', 'loadBalancePercentage');
    imbalanceDegree = evalin('base', 'imbalanceDegree');
    algorithmName = evalin('base', 'algorithmName');
    throughput = evalin('base', 'throughput');
    energyData = evalin('base', 'energyData');

    % Define improved color palette (better for printing and readability)
    mainColor = [0.2 0.6 0.8]; % Blue for primary metrics
    contrastColor = [0.9 0.4 0.1]; % Orange for contrasts
    successColor = [0.4 0.8 0.6]; % Green for positive metrics
    warningColor = [0.9 0.6 0.4]; % Coral for energy/warning metrics
    neutralColor = [0.5 0.5 0.5]; % Gray for grid lines
    bgColor = 'white'; % White background for better readability

    % Create output directory
    outputDir = fullfile('plots', runId);
    if ~exist(outputDir, 'dir')
        mkdir(outputDir);
    end

    % Initialize plot paths
    plotPaths = {};
    
    % Check if VM utilization data exists
    hasVmData = evalin('base', 'exist(''vmUtilData'', ''var'')');
    if hasVmData
        vmUtilData = evalin('base', 'vmUtilData');
    end
    
    %% Plot 1: Five Key Metrics Bar Chart with Improved Styling
    figure('Visible', 'off', 'Position', [100, 100, 1000, 600], 'Color', bgColor);
    
    % The 5 key metrics from your research
    metrics = categorical({'Makespan', 'Response Time', 'Resource Util.', 'Energy Cons.', 'Load Balance'});
    metrics = reordercats(metrics, {'Makespan', 'Response Time', 'Resource Util.', 'Energy Cons.', 'Load Balance'});
    
    % Current algorithm values with proper units
    values = [
        makespan;
        avgResponseTime;
        resourceUtilization;
        energyData / 1000; % Convert to kWh for better scale
        (1 - imbalanceDegree) * 100 % Convert imbalance to balance percentage
    ];
    
    % Create bar plot with different colors for each metric
    b = bar(metrics, values);
    b.FaceColor = 'flat';
    % Assign different colors to each bar for better distinction
    b.CData = [mainColor; mainColor; successColor; warningColor; successColor];
    
    % Add value labels on bars with units
    units = {'s', 's', '%', 'kWh', '%'};
    for i = 1:length(values)
        text(b.XEndPoints(i), b.YEndPoints(i), ...
            sprintf('%.2f %s', values(i), units{i}), ...
            'HorizontalAlignment', 'center', 'VerticalAlignment', 'bottom', ...
            'FontWeight', 'bold');
    end
    
    % Improved formatting
    ylabel('Metric Values', 'FontWeight', 'bold');
    title(sprintf('%s Algorithm - Performance Metrics', algorithmName), ...
        'FontSize', 14, 'FontWeight', 'bold');
    grid on;
    set(gca, 'FontSize', 11, 'GridAlpha', 0.3, 'GridLineStyle', ':');
    
    % Save plot
    plotPath1 = fullfile(outputDir, sprintf('%s_metrics.png', lower(algorithmName)));
    saveas(gcf, plotPath1);
    plotPaths{end+1} = plotPath1;
    close(gcf);
    
    %% Plot 2: Detailed Performance Analysis
    figure('Visible', 'off', 'Position', [100, 100, 1200, 800], 'Color', bgColor);
    
    % Subplot 1: Time Metrics
    subplot(2, 2, 1);
    timeMetrics = [makespan; avgResponseTime];
    timeLabels = categorical({'Makespan', 'Avg Response Time'});
    b1 = bar(timeLabels, timeMetrics);
    b1.FaceColor = mainColor;
    ylabel('Time (seconds)', 'FontWeight', 'bold');
    title('Time-based Metrics');
    grid on;
    set(gca, 'GridAlpha', 0.3);
    
    % Add values on bars
    text(b1.XEndPoints, b1.YEndPoints, string(round(timeMetrics, 2)), ...
        'HorizontalAlignment', 'center', 'VerticalAlignment', 'bottom');
    
    % Subplot 2: Efficiency Metrics (using only actual data)
    subplot(2, 2, 2);
    effMetrics = [resourceUtilization; throughput];
    effLabels = categorical({'Resource Util. (%)', 'Throughput (tasks/s)'});
    b2 = bar(effLabels, effMetrics);
    b2.FaceColor = successColor;
    ylabel('Value', 'FontWeight', 'bold');
    title('Efficiency Metrics');
    grid on;
    set(gca, 'GridAlpha', 0.3);
    
    % Add values on bars
    text(b2.XEndPoints, b2.YEndPoints, string(round(effMetrics, 2)), ...
        'HorizontalAlignment', 'center', 'VerticalAlignment', 'bottom');
    
    % Subplot 3: Energy Analysis
    subplot(2, 2, 3);
    % Convert to mWh for better scale
    energyMetrics = [energyData*1000; (energyData/makespan)*1000; (energyData/(throughput*makespan))*1000];
    energyLabels = categorical({'Total Energy', 'Energy/Time', 'Energy/Task'});
    b3 = bar(energyLabels, energyMetrics);
    b3.FaceColor = warningColor;
    ylabel('Energy (mWh)', 'FontWeight', 'bold');
    title('Energy Consumption Analysis');
    grid on;
    set(gca, 'GridAlpha', 0.3);
    
    % Subplot 4: Load Distribution
    subplot(2, 2, 4);
    balancePercentage = (1 - imbalanceDegree) * 100;
    loadMetrics = [balancePercentage; 100 - balancePercentage];
    p = pie(loadMetrics, {'Balanced Load', 'Imbalance'});
    title('Load Distribution');
    colormap([successColor; warningColor]);
    
    % Save plot
    plotPath2 = fullfile(outputDir, sprintf('%s_detailed.png', lower(algorithmName)));
    saveas(gcf, plotPath2);
    plotPaths{end+1} = plotPath2;
    close(gcf);
    
    %% Plot 3: VM Utilization (if data available)
    if hasVmData && ~isempty(vmUtilData)
        figure('Visible', 'off', 'Position', [100, 100, 1000, 600], 'Color', bgColor);
        
        % Extract CPU and RAM utilization
        cpuUtil = vmUtilData(:, 1);
        ramUtil = vmUtilData(:, 2);
        vmIds = 1:length(cpuUtil);
        
        % Create grouped bar chart
        subplot(2, 1, 1);
        bar(vmIds, [cpuUtil, ramUtil]);
        xlabel('VM ID', 'FontWeight', 'bold');
        ylabel('Utilization (%)', 'FontWeight', 'bold');
        title(sprintf('%s - VM Resource Utilization', algorithmName));
        legend({'CPU', 'RAM'}, 'Location', 'northwest');
        grid on;
        ylim([0 100]);
        set(gca, 'GridAlpha', 0.3);
        
        % Average utilization pie chart
        subplot(2, 1, 2);
        avgCpu = mean(cpuUtil);
        avgRam = mean(ramUtil);
        pie([avgCpu, avgRam, 100-avgCpu, 100-avgRam], ...
            {sprintf('CPU Used (%.1f%%)', avgCpu), ...
             sprintf('RAM Used (%.1f%%)', avgRam), ...
             'CPU Free', 'RAM Free'});
        title('Average Resource Utilization');
        colormap([mainColor; contrastColor; 0.9 0.9 0.9; 0.9 0.9 0.9]);
        
        plotPath3 = fullfile(outputDir, sprintf('%s_vm_utilization.png', lower(algorithmName)));
        saveas(gcf, plotPath3);
        plotPaths{end+1} = plotPath3;
        close(gcf);
    end
    
    %% Plot 4: Energy Consumption Summary (ONLY ACTUAL DATA)
    figure('Visible', 'off', 'Position', [100, 100, 1200, 600], 'Color', bgColor);
    
    % Show only actual energy data we have
    subplot(1, 2, 1);
    % Total energy consumption
    energyMetrics = categorical({'Total Energy', 'Energy per Second'});
    energyValues = [energyData; energyData/makespan];
    b = bar(energyMetrics, energyValues);
    b.FaceColor = warningColor;
    ylabel('Energy (Wh)', 'FontWeight', 'bold');
    title('Energy Consumption', 'FontWeight', 'bold');
    
    % Add value labels
    for i = 1:length(energyValues)
        text(b.XEndPoints(i), b.YEndPoints(i), ...
            sprintf('%.2f Wh', energyValues(i)), ...
            'HorizontalAlignment', 'center', 'VerticalAlignment', 'bottom', ...
            'FontWeight', 'bold');
    end
    grid on;
    set(gca, 'GridAlpha', 0.3);
    
    subplot(1, 2, 2);
    % Energy efficiency metrics
    efficiencyMetrics = categorical({'Energy per Task', 'Energy Efficiency'});
    tasksCompleted = throughput * makespan; % Approximate number of tasks
    efficiencyValues = [
        energyData / tasksCompleted * 1000; % mWh per task
        tasksCompleted / energyData % Tasks per Wh
    ];
    b2 = bar(efficiencyMetrics, efficiencyValues);
    b2.FaceColor = successColor;
    ylabel('Value', 'FontWeight', 'bold');
    title('Energy Efficiency', 'FontWeight', 'bold');
    
    % Add value labels
    units = {'mWh/task', 'tasks/Wh'};
    for i = 1:length(efficiencyValues)
        text(b2.XEndPoints(i), b2.YEndPoints(i), ...
            sprintf('%.2f %s', efficiencyValues(i), units{i}), ...
            'HorizontalAlignment', 'center', 'VerticalAlignment', 'bottom', ...
            'FontWeight', 'bold');
    end
    grid on;
    set(gca, 'GridAlpha', 0.3);
    
    plotPath4 = fullfile(outputDir, sprintf('%s_energy.png', lower(algorithmName)));
    saveas(gcf, plotPath4);
    plotPaths{end+1} = plotPath4;
    close(gcf);
    
    %% Plot 5: Performance Radar Chart (using actual data)
    figure('Visible', 'off', 'Position', [100, 100, 800, 800], 'Color', bgColor);
    
    % Five core dimensions
    dimensions = {'Makespan', 'Response Time', 'Resource Util.', 'Energy Eff.', 'Load Balance'};
    numDims = length(dimensions);
    
    % Current values
    currentValues = [
        makespan;
        avgResponseTime;
        resourceUtilization;
        energyData;
        (1 - imbalanceDegree) * 100
    ];
    
    % Dynamic normalization based on actual data ranges
    % Use percentile-based normalization for better comparison
    normalizedValues = zeros(numDims, 1);
    
    % For makespan and response time (lower is better)
    normalizedValues(1) = max(0, min(1, 1 - (makespan - 10) / 190)); % Assuming 10-200s range
    normalizedValues(2) = max(0, min(1, 1 - (avgResponseTime - 5) / 145)); % Assuming 5-150s range
    
    % For resource utilization (higher is better)
    normalizedValues(3) = resourceUtilization / 100;
    
    % For energy (lower is better)
    normalizedValues(4) = max(0, min(1, 1 - (energyData - 50) / 450)); % Assuming 50-500Wh range
    
    % For load balance (higher is better)
    normalizedValues(5) = currentValues(5) / 100;
    
    % Create radar chart
    angles = linspace(0, 2*pi, numDims+1);
    data = [normalizedValues; normalizedValues(1)]';
    
    % Create the radar plot
    polarplot(angles, data, '-o', 'LineWidth', 2.5, 'MarkerSize', 8, ...
              'Color', mainColor, 'MarkerFaceColor', mainColor);
    hold on;
    
    % Add reference circles
    for r = 0.25:0.25:1
        polarplot(angles, r*ones(size(angles)), ':', 'Color', neutralColor, 'LineWidth', 0.5);
    end
    
    % Configure radar chart
    ax = gca;
    ax.ThetaGrid = 'on';
    ax.RGrid = 'on';
    ax.ThetaZeroLocation = 'top';
    ax.ThetaDir = 'clockwise';
    
    % Set labels
    ax.ThetaTick = (0:numDims-1) * 360/numDims;
    ax.ThetaTickLabel = dimensions;
    ax.RLim = [0 1];
    ax.RTick = [0.25 0.5 0.75 1];
    ax.RTickLabel = {'25%', '50%', '75%', '100%'};
    
    % Add title
    title(sprintf('%s - Performance Radar (Higher is Better)', algorithmName), ...
        'FontSize', 14, 'FontWeight', 'bold');
    
    % Add value annotations
    for i = 1:numDims
        angle = angles(i);
        value = normalizedValues(i);
        actualValue = currentValues(i);
        
        % Position text slightly outside the data point
        textRadius = value + 0.15;
        if textRadius > 1.2
            textRadius = 1.2;
        end
        
        % Format the actual value with appropriate units
        if i == 1 || i == 2
            valueStr = sprintf('%.1fs', actualValue);
        elseif i == 3 || i == 5
            valueStr = sprintf('%.1f%%', actualValue);
        else
            valueStr = sprintf('%.1fWh', actualValue);
        end
        
        % Add the text
        text(angle, textRadius, valueStr, ...
            'HorizontalAlignment', 'center', ...
            'FontSize', 10, 'FontWeight', 'bold');
    end
    
    hold off;
    
    % Save plot
    plotPath5 = fullfile(outputDir, sprintf('%s_radar.png', lower(algorithmName)));
    saveas(gcf, plotPath5);
    plotPaths{end+1} = plotPath5;
    close(gcf);
    
    %% Create data structure for frontend
    plotData = struct();
    
    % Store metric values (using actual data only)
    plotData.metrics = struct(...
        'makespan', makespan, ...
        'avgResponseTime', avgResponseTime, ...
        'resourceUtilization', resourceUtilization, ...
        'energyConsumption', energyData, ...
        'loadBalance', (1 - imbalanceDegree) * 100, ...
        'throughput', throughput ...
    );
    
    % Store plot paths (crucial for frontend)
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
    fprintf('Load Balance: %.2f%%\n', (1 - imbalanceDegree) * 100);
    fprintf('Throughput: %.2f tasks/sec\n', throughput);
    fprintf('Plots generated: %d\n', length(plotPaths));
    fprintf('============================\n');
end
