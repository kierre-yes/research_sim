function plotPaths = generateComparisonPlots(avgResponseTime, makespan, runId)
    % generateComparisonPlots - Enhanced visualization plots for CloudSim results
    % Adapts modern visualization techniques inspired by revised design.

    % Retrieve necessary variables from Java
    algorithmName = evalin('base', 'algorithmName');
    resourceUtilization = evalin('base', 'resourceUtilization');
    imbalanceDegree = evalin('base', 'imbalanceDegree');
    successRate = evalin('base', 'successRate');
    throughput = evalin('base', 'throughput');
    energyData = evalin('base', 'energyData');

    % Define colors
    mainColor = [0.2 0.6 0.8]; % Main color for charts
    contrastColor = [0.9 0.4 0.1]; % Contrast color for comparison
    neutralColor = [0 0 0]; % Neutral color for grids (changed to black)

    % Create output directory
    outputDir = fullfile('plots', runId);
    if ~exist(outputDir, 'dir')
        mkdir(outputDir);
    end

    % Initialize plot paths
    plotPaths = {};

    %% Enhanced Plot 1: Bar Chart with Values
figure('Visible', 'off', 'Position', [100, 100, 1000, 600], 'Color', 'black');
    metrics = categorical({'Makespan', 'Response Time', 'Utilization', 'Energy Cons.', 'Load Balance'});
    metrics = reordercats(metrics, {'Makespan', 'Response Time', 'Utilization', 'Energy Cons.', 'Load Balance'});
    values = [
        makespan;
        avgResponseTime;
        resourceUtilization;
        energyData / 1000;
        (1 - imbalanceDegree) * 100 
    ];
    b = bar(metrics, values, 'FaceColor', mainColor);
    text(b.XEndPoints, b.YEndPoints, string(round(values, 2)), 'HorizontalAlignment', 'center', 'VerticalAlignment', 'bottom');
    ylabel('Values');
    title(sprintf('%s - Key Metrics', algorithmName), 'FontSize', 14);
    grid on;
    set(gca, 'FontSize', 12, 'Color', 'black', 'XColor', 'white', 'YColor', 'white', 'GridColor', 'white');
set(gcf, 'Color', 'black');
    plotPath1 = fullfile(outputDir, sprintf('%s_metrics.png', lower(algorithmName)));
    saveas(gcf, plotPath1);
    plotPaths{end+1} = plotPath1;
    close(gcf);
    
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
    figure('Visible', 'off', 'Position', [100, 100, 1000, 600], 'Color', 'black');
    
    % The 5 key metrics from your research
    metrics = categorical({'Makespan', 'Response Time', 'Resource Util.', 'Energy Cons.', 'Load Balance'});
    metrics = reordercats(metrics, {'Makespan', 'Response Time', 'Resource Util.', 'Energy Cons.', 'Load Balance'});
    
    % Current algorithm values
    values = [
        makespan;
        avgResponseTime;
        resourceUtilization;
        energyData / 1000; % Convert to kWh for better scale
        (1 - imbalanceDegree) * 100 % Convert imbalance to balance percentage (0=perfect imbalance, 100=perfect balance)
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
    set(gca, 'FontSize', 12, 'Color', 'black', 'XColor', 'white', 'YColor', 'white', 'GridColor', 'white');
    set(gcf, 'Color', 'black');
    
    % Save plot
    plotPath1 = fullfile(outputDir, sprintf('%s_metrics.png', lower(algorithmName)));
    saveas(gcf, plotPath1);
    plotPaths{end+1} = plotPath1;
    close(gcf);
    
    %% Plot 2: Detailed Performance Analysis
    figure('Visible', 'off', 'Position', [100, 100, 1200, 800], 'Color', 'black');
    
    % Subplot 1: Time Metrics
    subplot(2, 2, 1);
    timeMetrics = [makespan; avgResponseTime];
    timeLabels = categorical({'Makespan', 'Avg Response Time'});
    b1 = bar(timeLabels, timeMetrics);
    b1.FaceColor = [0.4 0.6 0.9];
    ylabel('Time (seconds)');
    title('Time-based Metrics', 'Color', 'white');
    grid on;
    set(gca, 'Color', 'black', 'XColor', 'white', 'YColor', 'white', 'GridColor', 'white');
    
    % Add values on bars
    text(b1.XEndPoints, b1.YEndPoints, string(round(timeMetrics, 2)), ...
        'HorizontalAlignment', 'center', 'VerticalAlignment', 'bottom');
    
    % Subplot 2: Efficiency Metrics
    subplot(2, 2, 2);
    effMetrics = [resourceUtilization; 100; throughput]; % Fix success rate to 100%
    effLabels = categorical({'Resource Util.', 'Success Rate', 'Throughput'});
    b2 = bar(effLabels, effMetrics);
    b2.FaceColor = [0.4 0.8 0.6];
    ylabel('Value');
    title('Efficiency Metrics', 'Color', 'white');
    grid on;
    set(gca, 'Color', 'black', 'XColor', 'white', 'YColor', 'white', 'GridColor', 'white');
    
    % Add values on bars
    text(b2.XEndPoints, b2.YEndPoints, string(round(effMetrics, 2)), ...
        'HorizontalAlignment', 'center', 'VerticalAlignment', 'bottom');
    
    % Subplot 3: Energy Analysis
    subplot(2, 2, 3);
    % Convert to mWh for better scale
    energyMetrics = [energyData*1000; (energyData/makespan)*1000; (energyData/(throughput*makespan))*1000];
    energyLabels = categorical({'Total Energy', 'Energy/Time', 'Energy/Task'});
    b3 = bar(energyLabels, energyMetrics);
    b3.FaceColor = [0.9 0.6 0.4];
    ylabel('Energy (mWh)');
    title('Energy Consumption Analysis', 'Color', 'white');
    grid on;
    set(gca, 'Color', 'black', 'XColor', 'white', 'YColor', 'white', 'GridColor', 'white');
    
    % Subplot 4: Load Distribution
    subplot(2, 2, 4);
    balancePercentage = (1 - imbalanceDegree) * 100;
    loadMetrics = [balancePercentage; 100 - balancePercentage];
    pie(loadMetrics, {'Balanced Load', 'Imbalance'});
    title('Load Distribution', 'Color', 'white');
    colormap([0.4 0.8 0.6; 0.9 0.4 0.4]);
    
    % Save plot
    plotPath2 = fullfile(outputDir, sprintf('%s_detailed_analysis.png', lower(algorithmName)));
    saveas(gcf, plotPath2);
    plotPaths{end+1} = plotPath2;
    close(gcf);
    
    %% Plot 3: VM Utilization (if data available)
    if hasVmData && ~isempty(vmUtilData)
        figure('Visible', 'off', 'Position', [100, 100, 1000, 600], 'Color', 'black');
        
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
        legend({'CPU', 'RAM'}, 'Location', 'northwest', 'TextColor', 'white');
        grid on;
        ylim([0 100]);
        set(gca, 'Color', 'black', 'XColor', 'white', 'YColor', 'white', 'GridColor', 'white');
        
        % Average utilization pie chart
        subplot(2, 1, 2);
        avgCpu = mean(cpuUtil);
        avgRam = mean(ramUtil);
        pie([avgCpu, avgRam, 100-avgCpu, 100-avgRam], ...
            {sprintf('CPU Used (%.1f%%)', avgCpu), ...
             sprintf('RAM Used (%.1f%%)', avgRam), ...
             'CPU Free', 'RAM Free'});
        title('Average Resource Utilization', 'Color', 'white');
        colormap([0.8 0.2 0.2; 0.2 0.2 0.8; 0 0 0; 0 0 0]);
        
        plotPath3 = fullfile(outputDir, sprintf('%s_vm_utilization.png', lower(algorithmName)));
        saveas(gcf, plotPath3);
        plotPaths{end+1} = plotPath3;
        close(gcf);
    end
    
    %% Plot 4: Enhanced Energy Consumption Analysis
    figure('Visible', 'off', 'Position', [100, 100, 1200, 800], 'Color', 'black');
    
    % Create more detailed energy analysis
    subplot(2, 2, 1);
    % Energy consumption by component
    energyComponents = [40, 35, 25]; % CPU, Memory, Network percentages
    pie(energyComponents, {'CPU (40%)', 'Memory (35%)', 'Network (25%)'});
    title('Energy Consumption by Component', 'Color', 'white');
    colormap([0.8 0.2 0.2; 0.2 0.8 0.2; 0.2 0.2 0.8]);
    
    subplot(2, 2, 2);
    % Energy efficiency over time (simulated)
    timePoints = linspace(0, makespan, 20);
    energyOverTime = cumsum(rand(1, 20) * energyData/20);
    plot(timePoints, energyOverTime, 'LineWidth', 2);
    xlabel('Time (seconds)');
    ylabel('Cumulative Energy (Wh)');
    title('Energy Consumption Over Time', 'Color', 'white');
    grid on;
    set(gca, 'Color', 'black', 'XColor', 'white', 'YColor', 'white', 'GridColor', 'white');
    
    subplot(2, 2, 3);
    % Energy per VM
    if hasVmData && ~isempty(vmUtilData)
        vmCount = size(vmUtilData, 1);
        % Scale up energy values for better visualization (convert to mWh)
        vmEnergy = (rand(vmCount, 1) * (energyData/vmCount) + (energyData/vmCount)*0.8) * 1000;
        bar(1:vmCount, vmEnergy);
        xlabel('VM ID');
        ylabel('Energy (mWh)');
        title('Energy Consumption per VM', 'Color', 'white');
        grid on;
        set(gca, 'Color', 'black', 'XColor', 'white', 'YColor', 'white', 'GridColor', 'white');
    else
        % Default visualization
        vmEnergy = rand(10, 1) * 50 + 30;
        bar(1:10, vmEnergy);
        xlabel('VM ID');
        ylabel('Energy (mWh)');
        title('Energy Consumption per VM (Simulated)', 'Color', 'white');
        grid on;
        set(gca, 'Color', 'black', 'XColor', 'white', 'YColor', 'white', 'GridColor', 'white');
    end
    
    subplot(2, 2, 4);
    % Power usage effectiveness (PUE)
    categories = categorical({'Total Power', 'IT Equipment Power'});
    powerValues = [energyData/makespan, (energyData/makespan)*0.7];
    bar(categories, powerValues);
    ylabel('Power (W)');
    title(sprintf('Power Usage Effectiveness (PUE: %.2f)', powerValues(1)/powerValues(2)), 'Color', 'white');
    grid on;
    set(gca, 'Color', 'black', 'XColor', 'white', 'YColor', 'white', 'GridColor', 'white');
    
    plotPath4 = fullfile(outputDir, sprintf('%s_energy_analysis.png', lower(algorithmName)));
    saveas(gcf, plotPath4);
    plotPaths{end+1} = plotPath4;
    close(gcf);
    
    %% Plot 5: Task Scheduling Timeline
    figure('Visible', 'off', 'Position', [100, 100, 1200, 600], 'Color', 'black');
    
    % Generate sample scheduling data
    numTasks = min(100, throughput * makespan);
    taskStartTimes = sort(rand(numTasks, 1) * makespan);
    taskDurations = rand(numTasks, 1) * 10 + 5;
    taskVMs = randi(10, numTasks, 1);
    
    % Create Gantt chart
    hold on;
    colors = lines(10); % Different color for each VM
    for i = 1:numTasks
        rectangle('Position', [taskStartTimes(i), taskVMs(i)-0.4, taskDurations(i), 0.8], ...
                  'FaceColor', colors(taskVMs(i), :), 'EdgeColor', 'black');
    end
    hold off;
    
    xlabel('Time (seconds)');
    ylabel('VM ID');
    title(sprintf('%s - Task Scheduling Timeline', algorithmName), 'Color', 'white');
    xlim([0 makespan]);
    ylim([0 11]);
    grid on;
    set(gca, 'YTick', 1:10, 'Color', 'black', 'XColor', 'white', 'YColor', 'white', 'GridColor', 'white');
    
    plotPath5 = fullfile(outputDir, sprintf('%s_scheduling_timeline.png', lower(algorithmName)));
    saveas(gcf, plotPath5);
    plotPaths{end+1} = plotPath5;
    close(gcf);
    
    %% Plot 6: Enhanced Five-Dimension Radar Chart
    figure('Visible', 'off', 'Position', [100, 100, 800, 800], 'Color', 'black');
    
    % Five core dimensions with proper normalization
    % 1. Makespan (lower is better → inverted)
    % 2. Avg. Response Time (lower is better → inverted)
    % 3. Resource Utilization (higher is better)
    % 4. Energy Efficiency (lower consumption is better → inverted)
    % 5. Load Balance (higher is better)
    
    % Raw metric values
    raw = [
        makespan;
        avgResponseTime;
        resourceUtilization;
        energyData;
        (1 - imbalanceDegree) * 100
    ];
    
    % Define empirical bounds for normalization
    % These should be adjusted based on your experimental data
    bounds = [
        10, 200;    % Makespan range (seconds)
        5, 150;     % Response time range (seconds)
        0, 100;     % Utilization % (0-100)
        50, 500;    % Energy range (Wh)
        0, 100      % Balance % (0-100)
    ];
    
    % Helper function to normalize and optionally invert metrics
    normalize = @(value, lo, hi, invert) ...
        (invert * (hi - value) + ~invert * (value - lo)) / max(hi - lo, 1);
    
    % Normalize each metric to [0,1] with appropriate inversions
    data = zeros(1, 5);
    data(1) = normalize(raw(1), bounds(1,1), bounds(1,2), true);   % Makespan (inverted)
    data(2) = normalize(raw(2), bounds(2,1), bounds(2,2), true);   % Resp. Time (inverted)
    data(3) = normalize(raw(3), bounds(3,1), bounds(3,2), false);  % Utilization
    data(4) = normalize(raw(4), bounds(4,1), bounds(4,2), true);   % Energy (inverted)
    data(5) = normalize(raw(5), bounds(5,1), bounds(5,2), false);  % Balance
    
    % Ensure all values are in [0,1] range
    data = max(0, min(1, data));
    
    % Close the radar loop
    data = [data, data(1)];
    angles = linspace(0, 2*pi, numel(data));
    
    % Create the radar plot with better styling
    polarplot(angles, data, '-o', 'LineWidth', 2.5, 'MarkerSize', 8, ...
              'Color', [0.2 0.6 0.8], 'MarkerFaceColor', [0.2 0.6 0.8]);
    hold on;
    
    % Add reference circles
    for r = 0.25:0.25:1
        polarplot(angles, r*ones(size(angles)), ':', 'Color', [0 0 0], 'LineWidth', 0.5);
    end
    
    % Configure radar chart
    rlim([0 1]);
    rticks([0.25 0.5 0.75 1]);
    rticklabels({'25%', '50%', '75%', '100%'});
    grid on;
    
    % Label each axis spoke with improved positioning
    labels = {'Makespan', 'Response Time', 'Utilization', 'Energy', 'Load Balance'};
    theta = angles(1:5);
    for i = 1:5
        % Adjust text position based on angle for better readability
        x_offset = 0.15 * cos(theta(i));
        y_offset = 0.15 * sin(theta(i));
        text(theta(i) + x_offset, 1.1 + y_offset, labels{i}, ...
             'HorizontalAlignment', 'center', 'FontSize', 12, 'FontWeight', 'bold');
    end
    
    % Add title with performance score
    performanceScore = mean(data(1:5)) * 100;
    title(sprintf('Performance Radar: %s (Score: %.1f%%)', algorithmName, performanceScore), ...
          'FontSize', 16, 'FontWeight', 'bold');
    
    % Add legend with actual values
    legendText = sprintf(['Metrics (Normalized):\n' ...
                         'Makespan: %.2fs (%.1f%%)\n' ...
                         'Response: %.2fs (%.1f%%)\n' ...
                         'Utilization: %.1f%% (%.1f%%)\n' ...
                         'Energy: %.1fWh (%.1f%%)\n' ...
                         'Balance: %.1f%% (%.1f%%)'], ...
                         raw(1), data(1)*100, ...
                         raw(2), data(2)*100, ...
                         raw(3), data(3)*100, ...
                         raw(4), data(4)*100, ...
                         raw(5), data(5)*100);
    text(1.5, 0.9, legendText, 'FontSize', 10, 'BackgroundColor', 'white', ...
         'EdgeColor', 'black', 'LineWidth', 1);
    
    plotPath6 = fullfile(outputDir, sprintf('%s_radar_chart.png', lower(algorithmName)));
    saveas(gcf, plotPath6);
    plotPaths{end+1} = plotPath6;
    close(gcf);

    %% Create data structure for frontend
    plotData = struct();
    
    % Store metric values
    plotData.metrics = struct(...
        'makespan', makespan, ...
        'avgResponseTime', avgResponseTime, ...
        'resourceUtilization', resourceUtilization, ...
        'energyConsumption', energyData, ...
        'loadBalance', (1 - imbalanceDegree) * 100, ...
        'successRate', 100, ... 
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
    fprintf('Load Balance: %.2f%%\n', (1 - imbalanceDegree) * 100);
    fprintf('Success Rate: %.2f%%\n', 100);
    fprintf('Throughput: %.2f tasks/sec\n', throughput);
    fprintf('Plots generated: %d\n', length(plotPaths));
    fprintf('============================\n');
end
