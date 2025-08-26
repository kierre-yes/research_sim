function plotPaths = generateComparisonPlots(avgResponseTime, makespan, runId, ...
    resourceUtilization, imbalanceDegree, algorithmName, throughput, energyData, vmUtilData)
    % Optimized plot generation with improved visuals and performance
    % Version 5.0: Fixed visual issues and improved rendering speed
    % This version only changes text colors to black across all plots.

    % Validate inputs
    if nargin < 8
        error('Missing required parameters');
    end
    if nargin < 9
        vmUtilData = [];
    end

    % Set default font for better readability
    set(0, 'DefaultAxesFontSize', 11);
    set(0, 'DefaultTextFontSize', 11);
    set(0, 'DefaultAxesFontName', 'Helvetica');
    set(0, 'DefaultTextFontName', 'Helvetica');

    % Force text to black globally (minimal change for the problem)
    set(0,'DefaultAxesXColor','k');
    set(0,'DefaultAxesYColor','k');
    set(0,'DefaultAxesZColor','k');
    set(0,'DefaultTextColor','k');

    % Define professional color scheme
    colors = struct();
    colors.primary   = [46, 134, 171]/255;
    colors.secondary = [162, 59, 114]/255;
    colors.success   = [115, 194, 190]/255;
    colors.warning   = [241, 143, 1]/255;
    colors.danger    = [199, 62, 29]/255;
    colors.neutral   = [107, 114, 128]/255;
    colors.light     = [240, 240, 240]/255;
    colors.dark      = [31, 41, 55]/255;

    % Create output directory
    outputDir = fullfile('plots', runId);
    if ~exist(outputDir, 'dir')
        mkdir(outputDir);
    end

    plotPaths = {};
    plotMetadata = {};

    %% Plot 1: Main Performance Metrics
    fig1 = figure('Visible', 'off', 'Position', [100, 100, 800, 500]);
    set(fig1, 'Color', 'white', 'PaperPositionMode', 'auto');

    metricNames = {'Makespan', 'Response Time', 'Resource Util.', 'Energy Cons.', 'Load Balance'};
    metricValues = [makespan; avgResponseTime; resourceUtilization; energyData / 1000; (1 - imbalanceDegree) * 100];

    ax1 = axes('Position', [0.1, 0.15, 0.85, 0.75]);
    b = bar(metricValues, 'FaceColor', 'flat');
    b.CData = [colors.primary; colors.primary; colors.success; colors.warning; colors.success];

    set(ax1, 'XTickLabel', metricNames, 'XTickLabelRotation', 0);
    ylabel('Metric Values', 'FontWeight', 'bold', 'FontSize', 12, 'Color','k');
    title(sprintf('%s Algorithm - Performance Metrics', algorithmName), 'FontSize', 14, 'FontWeight', 'bold', 'Interpreter', 'none', 'Color','k');
    ax1.XColor = 'k'; ax1.YColor = 'k';

    for i = 1:length(metricValues)
        text(i, metricValues(i), sprintf('%.2f', metricValues(i)), 'HorizontalAlignment','center', 'VerticalAlignment','bottom', 'FontSize',10,'FontWeight','bold','Color','w');
    end

    grid(ax1, 'on');
    set(ax1, 'GridAlpha', 0.2, 'GridLineStyle', '--');
    box off;

    plotPath1 = fullfile(outputDir, sprintf('%s_metrics.png', lower(algorithmName)));
    print(fig1, '-dpng', '-r120', plotPath1);
    plotPaths{end+1} = plotPath1;
    close(fig1);

    meta1 = struct('plotId', char(java.util.UUID.randomUUID.toString()), 'type', 'PERFORMANCE_METRICS', 'title', 'Main Performance Metrics', 'filename', plotPath1, 'dataPoints', struct('makespan', makespan, 'responseTime', avgResponseTime, 'resourceUtilization', resourceUtilization, 'energyConsumption', energyData, 'loadBalance', (1 - imbalanceDegree) * 100));
    plotMetadata{end+1} = meta1;

    %% Plot 2: Detailed Performance Analysis
    fig2 = figure('Visible', 'off', 'Position', [100, 100, 900, 600]);
    set(fig2, 'Color', 'white');

    try
        t = tiledlayout(2, 2, 'TileSpacing', 'compact', 'Padding', 'compact');
    catch
    end

    if exist('t', 'var'), axTime = nexttile; else, axTime = subplot(2, 2, 1); end
    timeData = [avgResponseTime, makespan];
    bar(timeData, 'FaceColor', colors.primary);
    set(gca, 'XTickLabel', {'Avg Response Time', 'Makespan'});
    ylabel('Time (seconds)', 'FontWeight', 'bold', 'Color','k');
    title('Time-based Metrics', 'FontWeight', 'bold', 'Color','k');
    axTime.XColor='k'; axTime.YColor='k';
    for i = 1:length(timeData), text(i, timeData(i), sprintf('%.2f', timeData(i)), 'HorizontalAlignment','center','VerticalAlignment','bottom', 'FontSize',9,'FontWeight','bold','Color','w'); end
    grid(axTime, 'on'); set(axTime, 'GridAlpha', 0.2);

    if exist('t', 'var'), axEff = nexttile; else, axEff = subplot(2, 2, 2); end
    effData = [resourceUtilization, throughput];
    bar(effData, 'FaceColor', colors.success);
    set(gca, 'XTickLabel', {'Resource Util. (%)', 'Throughput'});
    ylabel('Value', 'FontWeight', 'bold', 'Color','k');
    title('Efficiency Metrics', 'FontWeight', 'bold', 'Color','k');
    axEff.XColor='k'; axEff.YColor='k';
    for i = 1:length(effData), text(i, effData(i), sprintf('%.2f', effData(i)), 'HorizontalAlignment','center','VerticalAlignment','bottom', 'FontSize',9,'FontWeight','bold','Color','w'); end
    grid(axEff, 'on'); set(axEff, 'GridAlpha', 0.2);

    if exist('t', 'var'), axEnergy = nexttile; else, axEnergy = subplot(2, 2, 3); end
    energyDisplay = energyData * 1000;
    bar([energyDisplay, energyDisplay/makespan], 'FaceColor', colors.warning);
    set(gca, 'XTickLabel', {'Total Energy', 'Energy/Time'});
    ylabel('Energy (mWh)', 'FontWeight', 'bold', 'Color','k');
    title('Energy Consumption Analysis', 'FontWeight', 'bold', 'Color','k');
    axEnergy.XColor='k'; axEnergy.YColor='k';
    grid(axEnergy, 'on'); set(axEnergy, 'GridAlpha', 0.2);

    if exist('t', 'var'), axPie = nexttile; else, axPie = subplot(2, 2, 4); end
    balancePercentage = max(0, min(100, (1 - imbalanceDegree) * 100));
    p = pie([balancePercentage, 100 - balancePercentage]);
    colormap([colors.success; colors.light]);
    labels = {sprintf('Balanced\n%.1f%%', balancePercentage), sprintf('Imbalance\n%.1f%%', 100-balancePercentage)};
    textObjs = findobj(p, 'Type', 'text');
    for i = 1:length(textObjs), if i <= length(labels), set(textObjs(i), 'String', labels{i}, 'FontSize', 10, 'FontWeight', 'bold', 'Color','k'); end, end
    title('Load Distribution', 'FontWeight', 'bold', 'Color','k');

    if exist('t', 'var'), title(t, 'Detailed Performance Analysis', 'FontSize', 14, 'FontWeight', 'bold', 'Color','k'); else, sgtitle('Detailed Performance Analysis', 'FontSize', 14, 'FontWeight', 'bold', 'Color','k'); end

    plotPath2 = fullfile(outputDir, sprintf('%s_detailed.png', lower(algorithmName)));
    print(fig2, '-dpng', '-r120', plotPath2);
    plotPaths{end+1} = plotPath2;
    close(fig2);

    meta2 = struct('plotId', char(java.util.UUID.randomUUID.toString()), 'type', 'DETAILED_ANALYSIS', 'title', 'Detailed Performance Analysis', 'filename', plotPath2, 'dataPoints', struct('responseTime', avgResponseTime, 'makespan', makespan, 'resourceUtilization', resourceUtilization, 'throughput', throughput, 'energyConsumption', energyData, 'loadBalance', (1 - imbalanceDegree) * 100));
    plotMetadata{end+1} = meta2;

    %% Plot 3: VM Utilization
    if ~isempty(vmUtilData) && size(vmUtilData, 1) > 0
        fig3 = figure('Visible', 'off', 'Position', [100, 100, 900, 500]);
        set(fig3, 'Color', 'white');
        cpuUtil = vmUtilData(:, 1); ramUtil = vmUtilData(:, 2); numVMs = min(length(cpuUtil), 50); vmIds = 1:numVMs;
        ax3 = axes('Position', [0.08, 0.35, 0.88, 0.55]);
        b = bar(vmIds, [cpuUtil(1:numVMs), ramUtil(1:numVMs)], 'grouped');
        b(1).FaceColor = colors.primary; b(2).FaceColor = colors.warning;
        xlabel('VM ID', 'FontWeight', 'bold', 'Color','k'); ylabel('Utilization (%)', 'FontWeight', 'bold', 'Color','k');
        title(sprintf('%s - VM Resource Utilization', algorithmName), 'FontSize', 14, 'FontWeight', 'bold', 'Color','k');
        legend({'CPU', 'RAM'}, 'Location', 'northeast', 'FontSize', 10, 'TextColor','k');
        ylim([0 100]); grid(ax3, 'on'); set(ax3, 'GridAlpha', 0.2); ax3.XColor='k'; ax3.YColor='k';
        ax4 = axes('Position', [0.08, 0.08, 0.88, 0.20]);
        avgCpu = mean(cpuUtil); avgRam = mean(ramUtil);
        axis off;
        summaryText = sprintf(['Average Resource Utilization:\n', ...
            'CPU: %.1f%% | RAM: %.1f%%\n', ...
            'CPU Range: %.1f%% - %.1f%%\n', ...
            'RAM Range: %.1f%% - %.1f%%'], avgCpu, avgRam, min(cpuUtil), max(cpuUtil), min(ramUtil), max(ramUtil));
        text(0.5, 0.5, summaryText, 'HorizontalAlignment', 'center', 'VerticalAlignment', 'middle', 'FontSize', 11, 'FontWeight', 'bold', 'BackgroundColor', colors.light, 'EdgeColor', colors.neutral, 'Margin', 10, 'Color','k');
        plotPath3 = fullfile(outputDir, sprintf('%s_vm_utilization.png', lower(algorithmName)));
        print(fig3, '-dpng', '-r120', plotPath3);
        plotPaths{end+1} = plotPath3;
        close(fig3);
        meta3 = struct('plotId', char(java.util.UUID.randomUUID.toString()), 'type', 'VM_UTILIZATION', 'title', 'VM Resource Utilization', 'filename', plotPath3, 'dataPoints', struct('avgCpuUtilization', avgCpu, 'avgRamUtilization', avgRam, 'cpuVariance', var(cpuUtil)));
        plotMetadata{end+1} = meta3;
    end

    %% Plot 4: Energy Analysis
    fig4 = figure('Visible', 'off', 'Position', [100, 100, 800, 400]);
    set(fig4, 'Color', 'white');
    ax5 = axes('Position', [0.08, 0.15, 0.40, 0.70]);
    energyData1000 = energyData * 1000;
    energyBars = [energyData1000, energyData1000/makespan];
    bar(energyBars, 'FaceColor', colors.warning);
    set(gca, 'XTickLabel', {'Total', 'Per Second'});
    ylabel('Energy (mWh)', 'FontWeight', 'bold', 'Color','k');
    title('Energy Consumption', 'FontWeight', 'bold', 'Color','k');
    ax5.XColor='k'; ax5.YColor='k';
    for i = 1:length(energyBars), text(i, energyBars(i), sprintf('%.2f mWh', energyBars(i)), 'HorizontalAlignment', 'center', 'VerticalAlignment', 'bottom', 'FontSize', 9, 'FontWeight', 'bold', 'Color',[0.3 0.3 0.3]); end
    grid(ax5, 'on'); set(ax5, 'GridAlpha', 0.2);
    ax6 = axes('Position', [0.55, 0.15, 0.40, 0.70]);
    tasksCompleted = throughput * makespan;
    efficiencyMetrics = [tasksCompleted/energyData, energyData*1000/tasksCompleted];
    bar(efficiencyMetrics, 'FaceColor', colors.success);
    set(gca, 'XTickLabel', {'Tasks/Wh', 'mWh/Task'});
    ylabel('Efficiency', 'FontWeight', 'bold', 'Color','k');
    title('Energy Efficiency', 'FontWeight', 'bold', 'Color','k');
    ax6.XColor='k'; ax6.YColor='k';
    text(1, efficiencyMetrics(1), sprintf('%.1f tasks/Wh', efficiencyMetrics(1)), 'HorizontalAlignment', 'center', 'VerticalAlignment', 'bottom', 'FontSize', 9, 'FontWeight', 'bold', 'Color','k');
    text(2, efficiencyMetrics(2), sprintf('%.2f mWh/task', efficiencyMetrics(2)), 'HorizontalAlignment', 'center', 'VerticalAlignment', 'bottom', 'FontSize', 9, 'FontWeight', 'bold', 'Color',[0.3 0.3 0.3]);
    grid(ax6, 'on'); set(ax6, 'GridAlpha', 0.2);
    annotation('textbox', [0 0.88 1 0.1], 'String', 'Energy Consumption Analysis', 'FontSize', 14, 'FontWeight', 'bold', 'HorizontalAlignment', 'center', 'EdgeColor', 'none', 'Color','k');
    plotPath4 = fullfile(outputDir, sprintf('%s_energy.png', lower(algorithmName)));
    print(fig4, '-dpng', '-r120', plotPath4);
    plotPaths{end+1} = plotPath4;
    close(fig4);
    meta4 = struct('plotId', char(java.util.UUID.randomUUID.toString()), 'type', 'ENERGY_ANALYSIS', 'title', 'Energy Consumption Analysis', 'filename', plotPath4, 'dataPoints', struct('energyConsumption', energyData, 'makespan', makespan, 'throughput', throughput));
    plotMetadata{end+1} = meta4;

    %% Plot 5: Performance Radar Chart
    fig5 = figure('Visible', 'off', 'Position', [100, 100, 600, 600]);
    set(fig5, 'Color', 'white');
    dimensions = {'Makespan', 'Response Time', 'Resource Util.', 'Energy Eff.', 'Load Balance'};
    numDims = length(dimensions);
    normalizedValues = zeros(numDims, 1);
    normalizedValues(1) = max(0, min(1, 1 - (makespan - 10) / 90));
    normalizedValues(2) = max(0, min(1, 1 - (avgResponseTime - 5) / 45));
    normalizedValues(3) = resourceUtilization / 100;
    normalizedValues(4) = max(0, min(1, 1 - (energyData - 0.01) / 0.09));
    normalizedValues(5) = (1 - imbalanceDegree);
    normalizedValues = max(0, min(1, normalizedValues));
    angles = linspace(0, 2*pi, numDims+1);
    data = [normalizedValues; normalizedValues(1)]';
    polarplot(angles, data, '-o', 'LineWidth', 2.5, 'MarkerSize', 10, 'Color', colors.primary, 'MarkerFaceColor', colors.primary, 'MarkerEdgeColor', colors.primary);
    hold on;
    referenceValues = [0.25, 0.5, 0.75, 1.0];
    for r = referenceValues, polarplot(angles, r*ones(size(angles)), ':', 'Color', colors.neutral, 'LineWidth', 0.8); end
    ax = gca;
    ax.ThetaGrid = 'on'; ax.RGrid = 'on'; ax.ThetaZeroLocation = 'top'; ax.ThetaDir = 'clockwise';
    ax.GridColor = colors.neutral; ax.GridAlpha = 0.3;
    ax.ThetaTick = (0:numDims-1) * 360/numDims;
    ax.ThetaTickLabel = dimensions;
    ax.RLim = [0 1]; ax.RTick = referenceValues; ax.RTickLabel = {'25%', '50%', '75%', '100%'};
    ax.ThetaColor = 'k'; ax.RColor = [0.3 0.3 0.3];
    ax.FontSize = 10; ax.FontWeight = 'bold';
    title(sprintf('%s - Performance Radar (Higher is Better)', algorithmName), 'FontSize', 14, 'FontWeight', 'bold', 'Units', 'normalized', 'Position', [0.5, 1.05, 0], 'Color','k');
    hold off;
    plotPath5 = fullfile(outputDir, sprintf('%s_radar.png', lower(algorithmName)));
    print(fig5, '-dpng', '-r120', plotPath5);
    plotPaths{end+1} = plotPath5;
    close(fig5);
    meta5 = struct('plotId', char(java.util.UUID.randomUUID.toString()), 'type', 'RADAR_CHART', 'title', 'Performance Radar Chart', 'filename', plotPath5, 'dataPoints', struct('makespanNormalized', normalizedValues(1), 'responseTimeNormalized', normalizedValues(2), 'utilizationNormalized', normalizedValues(3), 'energyEfficiencyNormalized', normalizedValues(4), 'loadBalanceNormalized', normalizedValues(5)));
    plotMetadata{end+1} = meta5;

    %% Create JSON data structure
    plotData = struct();
    plotData.metrics = struct('makespan', makespan, 'avgResponseTime', avgResponseTime, 'resourceUtilization', resourceUtilization, 'energyConsumption', energyData, 'loadBalance', (1 - imbalanceDegree) * 100, 'throughput', throughput);
    plotData.plotPaths = plotPaths;
    plotData.algorithm = algorithmName;
    plotData.simulationId = runId;
    plotData.plotMetadata = plotMetadata;

    plotJson = jsonencode(plotData);
    assignin('base', 'plotJson', plotJson);

    fprintf('=== %s Results ===\n', algorithmName);
    fprintf('Plots generated: %d\n', length(plotPaths));
    fprintf('====================\n');

    set(0, 'DefaultAxesFontSize', 'remove');
    set(0, 'DefaultTextFontSize', 'remove');
    set(0, 'DefaultAxesFontName', 'remove');
    set(0, 'DefaultTextFontName', 'remove');
end
