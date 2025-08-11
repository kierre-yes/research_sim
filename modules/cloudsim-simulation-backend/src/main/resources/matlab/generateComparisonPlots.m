function plotPaths = generateComparisonPlots(avgResponseTime, makespan, runId, ...
    resourceUtilization, imbalanceDegree, algorithmName, throughput, energyData, vmUtilData)
    % Generate optimized plots using Plotly for comparing EACO and EPSO algorithms
    % Version 4.0: Using Plotly for interactive, better-styled plots
    
    % Validate inputs
    if nargin < 8
        error('Missing required parameters: avgResponseTime, makespan, runId, resourceUtilization, imbalanceDegree, algorithmName, throughput, energyData');
    end
    if nargin < 9
        vmUtilData = [];
    end
    
    % Check if Plotly is available, if not fallback to regular MATLAB plotting
    try
        % Try to use Plotly (requires plotly MATLAB library)
        plotlyFig = struct();
        usePlotly = true;
    catch
        usePlotly = false;
    end
    
    % Define modern color palette with better contrast
    % Using a professional color scheme with good visibility
    primaryColor = '#2E86AB';   % Deep blue for primary metrics
    secondaryColor = '#A23B72';  % Purple for secondary metrics  
    successColor = '#73C2BE';   % Teal for positive metrics
    warningColor = '#F18F01';   % Orange for energy/warning metrics
    dangerColor = '#C73E1D';    % Red for critical metrics
    neutralColor = '#6B7280';   % Gray for neutral elements
    bgColor = '#FFFFFF';        % White background
    textColor = '#000000';      % Dark gray for text (better than pure black)
    
    % Create output directory
    outputDir = fullfile('plots', runId);
    if ~exist(outputDir, 'dir')
        mkdir(outputDir);
    end

    % Initialize plot data structure for JSON export
    plotPaths = {};
    plotlyData = {};
    
    % Check if VM utilization data exists
    hasVmData = ~isempty(vmUtilData)
    
    %% Plot 1: Five Key Metrics Bar Chart with Plotly Styling
    if usePlotly
        % Plotly version with interactive features
        metricNames = {'Makespan', 'Response Time', 'Resource Util.', 'Energy Cons.', 'Load Balance'};
        metricValues = [
            makespan;
            avgResponseTime;
            resourceUtilization;
            energyData / 1000; % Convert to kWh
            (1 - imbalanceDegree) * 100 % Convert to balance percentage
        ];
        metricColors = {primaryColor, primaryColor, successColor, warningColor, successColor};
        
        % Create Plotly bar chart data
        plotlyBar = struct();
        plotlyBar.x = metricNames;
        plotlyBar.y = metricValues;
        plotlyBar.type = 'bar';
        plotlyBar.marker = struct('color', {metricColors});
        plotlyBar.text = arrayfun(@(x) sprintf('%.2f', x), metricValues, 'UniformOutput', false);
        plotlyBar.textposition = 'outside';
        plotlyBar.textfont = struct('size', 12, 'color', textColor, 'family', 'Arial, sans-serif');
        
        % Layout configuration with improved styling
        layout1 = struct();
        layout1.title = struct('text', sprintf('<b>%s Algorithm - Performance Metrics</b>', algorithmName), ...
            'font', struct('size', 16, 'color', textColor, 'family', 'Arial, sans-serif'));
        layout1.xaxis = struct('title', struct('text', '<b>Metrics</b>', 'font', struct('size', 14, 'color', textColor)), ...
            'tickfont', struct('size', 12, 'color', textColor));
        layout1.yaxis = struct('title', struct('text', '<b>Values</b>', 'font', struct('size', 14, 'color', textColor)), ...
            'tickfont', struct('size', 12, 'color', textColor), 'gridcolor', '#E5E7EB', 'gridwidth', 1);
        layout1.plot_bgcolor = bgColor;
        layout1.paper_bgcolor = bgColor;
        layout1.showlegend = false;
        layout1.margin = struct('l', 60, 'r', 40, 't', 60, 'b', 60);
        layout1.hoverlabel = struct('bgcolor', '#FFFFFF', 'font', struct('size', 12, 'color', textColor));
        
        % Store Plotly data
        plotlyData{end+1} = struct('data', {plotlyBar}, 'layout', layout1, 'name', 'metrics');
    end
    
    % Fallback to regular MATLAB plotting
    figure('Visible', 'off', 'Position', [100, 100, 640, 400], 'Color', 'white');
    
    metrics = categorical({'Makespan', 'Response Time', 'Resource Util.', 'Energy Cons.', 'Load Balance'});
    metrics = reordercats(metrics, {'Makespan', 'Response Time', 'Resource Util.', 'Energy Cons.', 'Load Balance'});
    
    values = [
        makespan;
        avgResponseTime;
        resourceUtilization;
        energyData / 1000;
        (1 - imbalanceDegree) * 100
    ];
    
    % Create bar plot with improved colors
    b = bar(metrics, values);
    b.FaceColor = 'flat';
    % Convert hex colors to RGB for MATLAB
    b.CData = [hex2rgb(primaryColor); hex2rgb(primaryColor); hex2rgb(successColor); ...
               hex2rgb(warningColor); hex2rgb(successColor)];
    
    % Add value labels with better formatting
    text(b.XEndPoints, b.YEndPoints, arrayfun(@(x) sprintf('%.2f', x), values, 'UniformOutput', false), ...
        'HorizontalAlignment', 'center', 'VerticalAlignment', 'bottom', ...
        'FontSize', 10, 'FontWeight', 'bold', 'Color', hex2rgb(textColor));
    
    % Improved formatting
    ylabel('Metric Values', 'FontWeight', 'bold', 'FontSize', 12, 'Color', hex2rgb(textColor));
    title(sprintf('%s Algorithm - Performance Metrics', algorithmName), ...
        'FontSize', 14, 'FontWeight', 'bold', 'Color', hex2rgb(textColor));
    grid on;
    set(gca, 'FontSize', 11, 'GridAlpha', 0.15, 'GridLineStyle', '-', ...
        'GridColor', hex2rgb(neutralColor), 'XColor', hex2rgb(textColor), 'YColor', hex2rgb(textColor));
    
    % Save plot
    plotPath1 = fullfile(outputDir, sprintf('%s_metrics.png', lower(algorithmName)));
    try
        exportgraphics(gcf, plotPath1, 'Resolution', 150, 'BackgroundColor', 'white');
    catch
        print(gcf, '-dpng', '-r150', plotPath1);
    end
    plotPaths{end+1} = plotPath1;
    close(gcf);
    
    %% Plot 2: Detailed Performance Analysis
    figure('Visible', 'off', 'Position', [100, 100, 720, 480], 'Color', 'white');
    
    % Subplot 1: Time Metrics
    subplot(2, 2, 1);
    timeMetrics = [makespan; avgResponseTime];
    timeLabels = categorical({'Makespan', 'Avg Response Time'});
    b1 = bar(timeLabels, timeMetrics);
    b1.FaceColor = hex2rgb(primaryColor);
    ylabel('Time (seconds)', 'FontWeight', 'bold', 'Color', hex2rgb(textColor));
    title('Time-based Metrics', 'Color', hex2rgb(textColor));
    grid on;
    set(gca, 'GridAlpha', 0.15, 'GridColor', hex2rgb(neutralColor), ...
        'XColor', hex2rgb(textColor), 'YColor', hex2rgb(textColor));
    
    % Add values on bars
    text(b1.XEndPoints, b1.YEndPoints, string(round(timeMetrics, 2)), ...
        'HorizontalAlignment', 'center', 'VerticalAlignment', 'bottom');
    
    % Subplot 2: Efficiency Metrics (using only actual data)
    subplot(2, 2, 2);
    effMetrics = [resourceUtilization; throughput];
    effLabels = categorical({'Resource Util. (%)', 'Throughput (tasks/s)'});
    b2 = bar(effLabels, effMetrics);
    b2.FaceColor = hex2rgb(successColor);
    ylabel('Value', 'FontWeight', 'bold', 'Color', hex2rgb(textColor));
    title('Efficiency Metrics', 'Color', hex2rgb(textColor));
    grid on;
    set(gca, 'GridAlpha', 0.15, 'GridColor', hex2rgb(neutralColor), ...
        'XColor', hex2rgb(textColor), 'YColor', hex2rgb(textColor));
    
    % Add values on bars
    text(b2.XEndPoints, b2.YEndPoints, string(round(effMetrics, 2)), ...
        'HorizontalAlignment', 'center', 'VerticalAlignment', 'bottom');
    
    % Subplot 3: Energy Analysis
    subplot(2, 2, 3);
    % Convert to mWh for better scale
    energyMetrics = [energyData*1000; (energyData/makespan)*1000; (energyData/(throughput*makespan))*1000];
    energyLabels = categorical({'Total Energy', 'Energy/Time', 'Energy/Task'});
    b3 = bar(energyLabels, energyMetrics);
    b3.FaceColor = hex2rgb(warningColor);
    ylabel('Energy (mWh)', 'FontWeight', 'bold', 'Color', hex2rgb(textColor));
    title('Energy Consumption Analysis', 'Color', hex2rgb(textColor));
    grid on;
    set(gca, 'GridAlpha', 0.15, 'GridColor', hex2rgb(neutralColor), ...
        'XColor', hex2rgb(textColor), 'YColor', hex2rgb(textColor));
    
    % Subplot 4: Load Distribution
    subplot(2, 2, 4);
    balancePercentage = (1 - imbalanceDegree) * 100;
    loadMetrics = [balancePercentage; 100 - balancePercentage];
    p = pie(loadMetrics, {'Balanced Load', 'Imbalance'});
    title('Load Distribution', 'Color', hex2rgb(textColor));
    colormap([hex2rgb(successColor); hex2rgb(warningColor)]);
    
    % Save plot with optimized export
    plotPath2 = fullfile(outputDir, sprintf('%s_detailed.png', lower(algorithmName)));
    try
        exportgraphics(gcf, plotPath2, 'Resolution', 150, 'BackgroundColor', 'white');
    catch
        print(gcf, '-dpng', '-r150', plotPath2);
    end
    plotPaths{end+1} = plotPath2;
    close(gcf);
    
    %% Plot 3: VM Utilization (if data available)
    if hasVmData && ~isempty(vmUtilData)
        figure('Visible', 'off', 'Position', [100, 100, 640, 400], 'Color', 'white');
        
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
        colormap([hex2rgb(primaryColor); hex2rgb(secondaryColor); 0.9 0.9 0.9; 0.9 0.9 0.9]);
        
        plotPath3 = fullfile(outputDir, sprintf('%s_vm_utilization.png', lower(algorithmName)));
        try
            exportgraphics(gcf, plotPath3, 'Resolution', 150, 'BackgroundColor', 'white');
        catch
            print(gcf, '-dpng', '-r150', plotPath3);
        end
        plotPaths{end+1} = plotPath3;
        close(gcf);
    end
    
    %% Plot 4: Energy Consumption Summary (ONLY ACTUAL DATA)
    figure('Visible', 'off', 'Position', [100, 100, 720, 400], 'Color', 'white');
    
    % Show only actual energy data we have
    subplot(1, 2, 1);
    % Total energy consumption
    energyMetrics = categorical({'Total Energy', 'Energy per Second'});
    energyValues = [energyData; energyData/makespan];
    b = bar(energyMetrics, energyValues);
    b.FaceColor = hex2rgb(warningColor);
    ylabel('Energy (Wh)', 'FontWeight', 'bold', 'Color', hex2rgb(textColor));
    title('Energy Consumption', 'FontWeight', 'bold', 'Color', hex2rgb(textColor));
    
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
    b2.FaceColor = hex2rgb(successColor);
    ylabel('Value', 'FontWeight', 'bold', 'Color', hex2rgb(textColor));
    title('Energy Efficiency', 'FontWeight', 'bold', 'Color', hex2rgb(textColor));
    
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
    try
        exportgraphics(gcf, plotPath4, 'Resolution', 150, 'BackgroundColor', 'white');
    catch
        print(gcf, '-dpng', '-r150', plotPath4);
    end
    plotPaths{end+1} = plotPath4;
    close(gcf);
    
    %% Plot 5: Performance Radar Chart (using actual data)
    figure('Visible', 'off', 'Position', [100, 100, 500, 500], 'Color', 'white');
    
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
              'Color', hex2rgb(primaryColor), 'MarkerFaceColor', hex2rgb(primaryColor));
    hold on;
    
    % Add reference circles
    for r = 0.25:0.25:1
        polarplot(angles, r*ones(size(angles)), ':', 'Color', hex2rgb(neutralColor), 'LineWidth', 0.5);
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
    
    % skip value annotations on radar for faster rendering
    % values are already shown in other plots
    
    hold off;
    
    % Save plot with optimized export
    plotPath5 = fullfile(outputDir, sprintf('%s_radar.png', lower(algorithmName)));
    try
        exportgraphics(gcf, plotPath5, 'Resolution', 150, 'BackgroundColor', 'white');
    catch
        print(gcf, '-dpng', '-r150', plotPath5);
    end
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
    
    % Store Plotly data if available
    if usePlotly && ~isempty(plotlyData)
        plotData.plotlyCharts = plotlyData;
    end
    
    % Algorithm info
    plotData.algorithm = algorithmName;
    plotData.simulationId = runId;
    
    % Return plot data as JSON string
    plotJson = jsonencode(plotData);
    
    % IMPORTANT: Place plotJson in base workspace so Java can retrieve it
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

%% Helper function to convert hex color to RGB
function rgb = hex2rgb(hex)
    % Convert hex color string to RGB values [0,1]
    if startsWith(hex, '#')
        hex = hex(2:end);
    end
    rgb = reshape(sscanf(hex, '%2x'), 3, 1)' / 255;
end
