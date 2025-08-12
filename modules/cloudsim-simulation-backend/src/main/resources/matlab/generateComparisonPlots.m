function plotPaths = generateComparisonPlots(avgResponseTime, makespan, runId, ...
    resourceUtilization, imbalanceDegree, algorithmName, throughput, energyData, vmUtilData)
    % Optimized plot generation with improved visuals and performance
    % Version 5.0: Fixed visual issues and improved rendering speed
    %
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
    colors.primary   = [46, 134, 171]/255;   % Deep blue
    colors.secondary = [162, 59, 114]/255;   % Purple
    colors.success   = [115, 194, 190]/255;  % Teal
    colors.warning   = [241, 143, 1]/255;    % Orange
    colors.danger    = [199, 62, 29]/255;    % Red
    colors.neutral   = [107, 114, 128]/255;  % Gray
    colors.light     = [240, 240, 240]/255;  % Light gray
    colors.dark      = [31, 41, 55]/255;     % Dark gray

    % Create output directory
    outputDir = fullfile('plots', runId);
    if ~exist(outputDir, 'dir')
        mkdir(outputDir);
    end

    plotPaths = {};

    %% Plot 1: Main Performance Metrics (Fixed layout)
    fig1 = figure('Visible', 'off', 'Position', [100, 100, 800, 500]);
    set(fig1, 'Color', 'white', 'PaperPositionMode', 'auto');

    % Prepare data
    metricNames = {'Makespan', 'Response Time', 'Resource Util.', 'Energy Cons.', 'Load Balance'};
    metricValues = [
        makespan;
        avgResponseTime;
        resourceUtilization;
        energyData / 1000; % Convert to kWh for better scale
        (1 - imbalanceDegree) * 100 % Balance percentage
    ];

    % Create bar chart with proper spacing
    ax1 = axes('Position', [0.1, 0.15, 0.85, 0.75]); % Adjusted margins
    b = bar(metricValues, 'FaceColor', 'flat');

    % Apply gradient colors
    b.CData = [colors.primary; colors.primary; colors.success; colors.warning; colors.success];

    % Customize axes
    set(ax1, 'XTickLabel', metricNames, 'XTickLabelRotation', 0);
    ylabel('Metric Values', 'FontWeight', 'bold', 'FontSize', 12, 'Color','k');
    title(sprintf('%s Algorithm - Performance Metrics', algorithmName), ...
        'FontSize', 14, 'FontWeight', 'bold', 'Interpreter', 'none', 'Color','k');
    ax1.XColor = 'k'; ax1.YColor = 'k';

    % Add value labels on bars with proper positioning
    for i = 1:length(metricValues)
        text(i, metricValues(i), sprintf('%.2f', metricValues(i)), ...
            'HorizontalAlignment','center', ...
            'VerticalAlignment','bottom', ...
            'FontSize',10,'FontWeight','bold','Color','w');
    end

    % Add grid for better readability
    grid(ax1, 'on');
    set(ax1, 'GridAlpha', 0.2, 'GridLineStyle', '--');
    box off;

    % Save with high quality
    plotPath1 = fullfile(outputDir, sprintf('%s_metrics.png', lower(algorithmName)));
    print(fig1, '-dpng', '-r120', plotPath1);
    plotPaths{end+1} = plotPath1;
    close(fig1);

    %% Plot 2: Detailed Performance Analysis (Fixed subplot spacing)
    fig2 = figure('Visible', 'off', 'Position', [100, 100, 900, 600]);
    set(fig2, 'Color', 'white');

    % Use tiledlayout for better subplot management (if available)
    try
        t = tiledlayout(2, 2, 'TileSpacing', 'compact', 'Padding', 'compact');
    catch
        % Fallback for older MATLAB versions
    end

    % Subplot 1: Time Metrics
    if exist('t', 'var')
        axTime = nexttile;
    else
        axTime = subplot(2, 2, 1);
    end

    timeData = [avgResponseTime, makespan];
    b1 = bar(timeData, 'FaceColor', colors.primary);
    set(gca, 'XTickLabel', {'Avg Response Time', 'Makespan'});
    ylabel('Time (seconds)', 'FontWeight', 'bold', 'Color','k');
    title('Time-based Metrics', 'FontWeight', 'bold', 'Color','k');
    axTime.XColor='k'; axTime.YColor='k';

    % Add values with better formatting
    for i = 1:length(timeData)
        text(i, timeData(i), sprintf('%.2f', timeData(i)), ...
            'HorizontalAlignment','center','VerticalAlignment','bottom', ...
            'FontSize',9,'FontWeight','bold','Color','w');
    end
    grid(axTime, 'on');
    set(axTime, 'GridAlpha', 0.2);

    % Subplot 2: Efficiency Metrics
    if exist('t', 'var')
        axEff = nexttile;
    else
        axEff = subplot(2, 2, 2);
    end

    effData = [resourceUtilization, throughput];
    b2 = bar(effData, 'FaceColor', colors.success);
    set(gca, 'XTickLabel', {'Resource Util. (%)', 'Throughput'});
    ylabel('Value', 'FontWeight', 'bold', 'Color','k');
    title('Efficiency Metrics', 'FontWeight', 'bold', 'Color','k');
    axEff.XColor='k'; axEff.YColor='k';

    for i = 1:length(effData)
        text(i, effData(i), sprintf('%.2f', effData(i)), ...
            'HorizontalAlignment','center','VerticalAlignment','bottom', ...
            'FontSize',9,'FontWeight','bold','Color','w');
    end
    grid(axEff, 'on');
    set(axEff, 'GridAlpha', 0.2);

    % Subplot 3: Energy Analysis
    if exist('t', 'var')
        axEnergy = nexttile;
    else
        axEnergy = subplot(2, 2, 3);
    end

    % Display energy in appropriate scale
    energyDisplay = energyData * 1000; % Convert to mWh
    b3 = bar([energyDisplay, energyDisplay/makespan], 'FaceColor', colors.warning);
    set(gca, 'XTickLabel', {'Total Energy', 'Energy/Time'});
    ylabel('Energy (mWh)', 'FontWeight', 'bold', 'Color','k');
    title('Energy Consumption Analysis', 'FontWeight', 'bold', 'Color','k');
    axEnergy.XColor='k'; axEnergy.YColor='k';
    grid(axEnergy, 'on');
    set(axEnergy, 'GridAlpha', 0.2);

    % Subplot 4: Load Distribution (Fixed pie chart)
    if exist('t', 'var')
        axPie = nexttile;
    else
        axPie = subplot(2, 2, 4);
    end

    balancePercentage = (1 - imbalanceDegree) * 100;
    if balancePercentage < 0
        balancePercentage = 0;
    elseif balancePercentage > 100
        balancePercentage = 100;
    end

    pieData = [balancePercentage, 100 - balancePercentage];
    p = pie(pieData);

    % Customize pie chart appearance
    colormap([colors.success; colors.light]);

    % Add labels with percentages
    labels = {sprintf('Balanced\n%.1f%%', balancePercentage), ...
              sprintf('Imbalance\n%.1f%%', 100-balancePercentage)};

    % Update text labels
    textObjs = findobj(p, 'Type', 'text');
    for i = 1:length(textObjs)
        if i <= length(labels)
            set(textObjs(i), 'String', labels{i}, 'FontSize', 10, 'FontWeight', 'bold', 'Color','k');
        end
    end

    title('Load Distribution', 'FontWeight', 'bold', 'Color','k');

    % Add main title for the figure
    if exist('t', 'var')
        title(t, 'Detailed Performance Analysis', 'FontSize', 14, 'FontWeight', 'bold', 'Color','k');
    else
        sgtitle('Detailed Performance Analysis', 'FontSize', 14, 'FontWeight', 'bold', 'Color','k');
    end

    plotPath2 = fullfile(outputDir, sprintf('%s_detailed.png', lower(algorithmName)));
    print(fig2, '-dpng', '-r120', plotPath2);
    plotPaths{end+1} = plotPath2;
    close(fig2);

    %% Plot 3: VM Utilization (if data available)
    if ~isempty(vmUtilData) && size(vmUtilData, 1) > 0
        fig3 = figure('Visible', 'off', 'Position', [100, 100, 900, 500]);
        set(fig3, 'Color', 'white');

        cpuUtil = vmUtilData(:, 1);
        ramUtil = vmUtilData(:, 2);
        numVMs = min(length(cpuUtil), 50); % Limit to 50 VMs for clarity
        vmIds = 1:numVMs;

        % Main bar chart
        ax3 = axes('Position', [0.08, 0.35, 0.88, 0.55]);
        b = bar(vmIds, [cpuUtil(1:numVMs), ramUtil(1:numVMs)], 'grouped');
        b(1).FaceColor = colors.primary;
        b(2).FaceColor = colors.warning;

        xlabel('VM ID', 'FontWeight', 'bold', 'Color','k');
        ylabel('Utilization (%)', 'FontWeight', 'bold', 'Color','k');
        title(sprintf('%s - VM Resource Utilization', algorithmName), ...
            'FontSize', 14, 'FontWeight', 'bold', 'Color','k');
        legend({'CPU', 'RAM'}, 'Location', 'northeast', 'FontSize', 10, 'TextColor','k');
        ylim([0 100]);
        grid(ax3, 'on');
        set(ax3, 'GridAlpha', 0.2);
        ax3.XColor='k'; ax3.YColor='k';

        % Summary statistics in bottom panel
        ax4 = axes('Position', [0.08, 0.08, 0.88, 0.20]);
        avgCpu = mean(cpuUtil);
        avgRam = mean(ramUtil);

        % Create summary text box instead of pie chart
        axis off;
        summaryText = sprintf(['Average Resource Utilization:\n' ...
                              'CPU: %.1f%% | RAM: %.1f%%\n' ...
                              'CPU Range: %.1f%% - %.1f%%\n' ...
                              'RAM Range: %.1f%% - %.1f%%'], ...
                              avgCpu, avgRam, ...
                              min(cpuUtil), max(cpuUtil), ...
                              min(ramUtil), max(ramUtil));

        text(0.5, 0.5, summaryText, ...
            'HorizontalAlignment', 'center', ...
            'VerticalAlignment', 'middle', ...
            'FontSize', 11, ...
            'FontWeight', 'bold', ...
            'BackgroundColor', colors.light, ...
            'EdgeColor', colors.neutral, ...
            'Margin', 10, ...
            'Color','k');

        plotPath3 = fullfile(outputDir, sprintf('%s_vm_utilization.png', lower(algorithmName)));
        print(fig3, '-dpng', '-r120', plotPath3);
        plotPaths{end+1} = plotPath3;
        close(fig3);
    end

    %% Plot 4: Energy Analysis (Improved layout)
    fig4 = figure('Visible', 'off', 'Position', [100, 100, 800, 400]);
    set(fig4, 'Color', 'white');

    % Energy consumption bar chart
    ax5 = axes('Position', [0.08, 0.15, 0.40, 0.70]);
    energyData1000 = energyData * 1000;
    energyBars = [energyData1000, energyData1000/makespan]; % mWh
    b4 = bar(energyBars, 'FaceColor', colors.warning);
    set(gca, 'XTickLabel', {'Total', 'Per Second'});
    ylabel('Energy (mWh)', 'FontWeight', 'bold', 'Color','k');
    title('Energy Consumption', 'FontWeight', 'bold', 'Color','k');
    ax5.XColor='k'; ax5.YColor='k';

    % Add values on bars with dark grey color
    for i = 1:length(energyBars)
        text(i, energyBars(i), sprintf('%.2f mWh', energyBars(i)), ...
            'HorizontalAlignment', 'center', ...
            'VerticalAlignment', 'bottom', ...
            'FontSize', 9, 'FontWeight', 'bold', 'Color',[0.3 0.3 0.3]);
    end
    grid(ax5, 'on');
    set(ax5, 'GridAlpha', 0.2);

    % Energy efficiency metrics
    ax6 = axes('Position', [0.55, 0.15, 0.40, 0.70]);
    tasksCompleted = throughput * makespan;
    efficiencyMetrics = [tasksCompleted/energyData, energyData*1000/tasksCompleted];
    b5 = bar(efficiencyMetrics, 'FaceColor', colors.success);
    set(gca, 'XTickLabel', {'Tasks/Wh', 'mWh/Task'});
    ylabel('Efficiency', 'FontWeight', 'bold', 'Color','k');
    title('Energy Efficiency', 'FontWeight', 'bold', 'Color','k');
    ax6.XColor='w'; ax6.YColor='w';

    % Add formatted values
    text(1, efficiencyMetrics(1), sprintf('%.1f tasks/Wh', efficiencyMetrics(1)), ...
        'HorizontalAlignment', 'center', 'VerticalAlignment', 'bottom', ...
        'FontSize', 9, 'FontWeight', 'bold', 'Color','k');
    text(2, efficiencyMetrics(2), sprintf('%.2f mWh/task', efficiencyMetrics(2)), ...
        'HorizontalAlignment', 'center', 'VerticalAlignment', 'bottom', ...
        'FontSize', 9, 'FontWeight', 'bold', 'Color',[0.3 0.3 0.3]);

    grid(ax6, 'on');
    set(ax6, 'GridAlpha', 0.2);

    % Add main title
    annotation('textbox', [0 0.88 1 0.1], ...
        'String', 'Energy Consumption Analysis', ...
        'FontSize', 14, 'FontWeight', 'bold', ...
        'HorizontalAlignment', 'center', ...
        'EdgeColor', 'none', ...
        'Color','k');

    plotPath4 = fullfile(outputDir, sprintf('%s_energy.png', lower(algorithmName)));
    print(fig4, '-dpng', '-r120', plotPath4);
    plotPaths{end+1} = plotPath4;
    close(fig4);

    %% Plot 5: Performance Radar Chart (Improved)
    fig5 = figure('Visible', 'off', 'Position', [100, 100, 600, 600]);
    set(fig5, 'Color', 'white');

    % Radar chart dimensions
    dimensions = {'Makespan', 'Response Time', 'Resource Util.', 'Energy Eff.', 'Load Balance'};
    numDims = length(dimensions);

    % Normalize values for radar (0-1 scale)
    normalizedValues = zeros(numDims, 1);

    % Better normalization logic
    normalizedValues(1) = max(0, min(1, 1 - (makespan - 10) / 90)); % 10-100s range
    normalizedValues(2) = max(0, min(1, 1 - (avgResponseTime - 5) / 45)); % 5-50s range
    normalizedValues(3) = resourceUtilization / 100;
    normalizedValues(4) = max(0, min(1, 1 - (energyData - 0.01) / 0.09)); % 0.01-0.1 Wh range
    normalizedValues(5) = (1 - imbalanceDegree);

    % Ensure all values are in valid range
    normalizedValues = max(0, min(1, normalizedValues));

    % Create radar plot
    angles = linspace(0, 2*pi, numDims+1);
    data = [normalizedValues; normalizedValues(1)]';

    % Plot with better styling
    polarplot(angles, data, '-o', ...
        'LineWidth', 2.5, ...
        'MarkerSize', 10, ...
        'Color', 'w', ...            % white line
        'MarkerFaceColor', 'w', ...  % white markers
        'MarkerEdgeColor', 'w');
    hold on;

    % Add reference circles with labels
    referenceValues = [0.25, 0.5, 0.75, 1.0];
    for r = referenceValues
        polarplot(angles, r*ones(size(angles)), ':', ...
            'Color', colors.neutral, ...
            'LineWidth', 0.8);
    end

    % Configure polar axes
    ax = gca;
    ax.ThetaGrid = 'on';
    ax.RGrid = 'on';
    ax.ThetaZeroLocation = 'top';
    ax.ThetaDir = 'clockwise';
    ax.GridColor = colors.neutral;
    ax.GridAlpha = 0.3;

    % Set dimension labels with better positioning
    ax.ThetaTick = (0:numDims-1) * 360/numDims;
    ax.ThetaTickLabel = dimensions;
    ax.RLim = [0 1];
    ax.RTick = referenceValues;
    ax.RTickLabel = {'25%', '50%', '75%', '100%'};

    % Force polar text colors to black
    ax.ThetaColor = 'k';
    ax.RColor     = [0.3 0.3 0.3];

    % Improve font properties
    ax.FontSize = 10;
    ax.FontWeight = 'bold';

    % Add title with proper spacing
    title(sprintf('%s - Performance Radar (Higher is Better)', algorithmName), ...
        'FontSize', 14, 'FontWeight', 'bold', 'Units', 'normalized', ...
        'Position', [0.5, 1.05, 0], 'Color','k');

    hold off;

    plotPath5 = fullfile(outputDir, sprintf('%s_radar.png', lower(algorithmName)));
    print(fig5, '-dpng', '-r120', plotPath5);
    plotPaths{end+1} = plotPath5;
    close(fig5);

    %% Create JSON data structure for frontend
    plotData = struct();

    % Store metrics
    plotData.metrics = struct(...
        'makespan', makespan, ...
        'avgResponseTime', avgResponseTime, ...
        'resourceUtilization', resourceUtilization, ...
        'energyConsumption', energyData, ...
        'loadBalance', (1 - imbalanceDegree) * 100, ...
        'throughput', throughput ...
    );

    % Store plot paths
    plotData.plotPaths = plotPaths;
    plotData.algorithm = algorithmName;
    plotData.simulationId = runId;

    % Convert to JSON
    plotJson = jsonencode(plotData);

    % Place in base workspace for Java retrieval
    assignin('base', 'plotJson', plotJson);

    % Display summary (reduced output for speed)
    fprintf('\n=== %s Results ===\n', algorithmName);
    fprintf('Plots generated: %d\n', length(plotPaths));
    fprintf('==\n');

    % Reset default font settings
    set(0, 'DefaultAxesFontSize', 'remove');
    set(0, 'DefaultTextFontSize', 'remove');
    set(0, 'DefaultAxesFontName', 'remove');
    set(0, 'DefaultTextFontName', 'remove');
end
