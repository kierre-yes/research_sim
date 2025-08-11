function plotPaths = generateComparisonPlots_fast(avgResponseTime, makespan, runId, ...
    resourceUtilization, imbalanceDegree, algorithmName, throughput, energyData, vmUtilData)
    % Fast version with reduced quality for speed
    % Optimizations:
    % 1. Lower resolution (72 DPI instead of 150)
    % 2. Smaller figure sizes
    % 3. Skip complex visual effects
    % 4. Use print instead of exportgraphics (faster)
    % 5. Generate only essential plots (skip radar and detailed views)
    
    % Validate inputs
    if nargin < 8
        error('Missing required parameters');
    end
    if nargin < 9
        vmUtilData = [];
    end
    
    % Create output directory
    outputDir = fullfile('plots', runId);
    if ~exist(outputDir, 'dir')
        mkdir(outputDir);
    end

    % Initialize plot paths
    plotPaths = {};
    
    % Use smaller figure size for faster rendering
    figSize = [100, 100, 480, 320];
    
    %% Plot 1: Key Metrics Bar Chart (SIMPLIFIED)
    figure('Visible', 'off', 'Position', figSize, 'Color', 'white');
    
    metrics = categorical({'Makespan', 'Response', 'Resource', 'Energy', 'Balance'});
    values = [makespan; avgResponseTime; resourceUtilization; energyData/1000; (1-imbalanceDegree)*100];
    
    % Simple bar plot without fancy colors
    bar(metrics, values);
    
    % Minimal formatting
    ylabel('Values');
    title(sprintf('%s - Metrics', algorithmName));
    
    % Fast save with lower DPI
    plotPath1 = fullfile(outputDir, sprintf('%s_metrics.png', lower(algorithmName)));
    print(gcf, '-dpng', '-r72', plotPath1);  % 72 DPI instead of 150
    plotPaths{end+1} = plotPath1;
    close(gcf);
    
    %% Plot 2: Combined Summary (REPLACES 4 SEPARATE PLOTS)
    figure('Visible', 'off', 'Position', figSize, 'Color', 'white');
    
    % Create a simple 2x2 text summary instead of complex subplots
    subplot(2, 2, 1);
    bar([makespan, avgResponseTime]);
    set(gca, 'XTickLabel', {'Makespan', 'Response'});
    title('Time');
    
    subplot(2, 2, 2);
    bar([resourceUtilization, throughput]);
    set(gca, 'XTickLabel', {'Util%', 'Throughput'});
    title('Efficiency');
    
    subplot(2, 2, 3);
    bar(energyData);
    set(gca, 'XTickLabel', {'Energy'});
    title('Energy (Wh)');
    
    subplot(2, 2, 4);
    pie([(1-imbalanceDegree)*100, imbalanceDegree*100], {'Balanced', 'Imbalanced'});
    title('Load');
    
    plotPath2 = fullfile(outputDir, sprintf('%s_summary.png', lower(algorithmName)));
    print(gcf, '-dpng', '-r72', plotPath2);
    plotPaths{end+1} = plotPath2;
    close(gcf);
    
    %% Skip VM utilization and radar plots for speed
    
    %% Create minimal data structure for frontend
    plotData = struct();
    plotData.metrics = struct(...
        'makespan', makespan, ...
        'avgResponseTime', avgResponseTime, ...
        'resourceUtilization', resourceUtilization, ...
        'energyConsumption', energyData, ...
        'loadBalance', (1 - imbalanceDegree) * 100, ...
        'throughput', throughput ...
    );
    
    plotData.plotPaths = plotPaths;
    plotData.algorithm = algorithmName;
    plotData.simulationId = runId;
    
    % Convert to JSON
    plotJson = jsonencode(plotData);
    assignin('base', 'plotJson', plotJson);
    
    % Display summary
    fprintf('\n=== %s Results (Fast Mode) ===\n', algorithmName);
    fprintf('Generated %d plots\n', length(plotPaths));
    fprintf('==============================\n');
end
