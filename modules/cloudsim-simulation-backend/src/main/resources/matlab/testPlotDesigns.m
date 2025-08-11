% Test file for generateComparisonPlots visualization design
% This file contains hardcoded test data to verify plot styling and layout
% Run this independently in MATLAB to test and refine visualizations

% Main test script - run directly
fprintf('=== Testing Plot Designs with Sample Data ===\n');

% Create test data for EACO algorithm
test_EACO();

% Create test data for EPSO algorithm  
test_EPSO();

% Test comparison plots
test_comparison();

fprintf('\n=== All test plots generated successfully! ===\n');
fprintf('Check the ./test_plots/ directory for output files.\n');

function test_EACO()
    fprintf('\nGenerating EACO test plots...\n');
    
    % Hardcoded test data for EACO
    avgResponseTime = 45.67;
    makespan = 123.45;
    runId = 'test_eaco_' + string(datestr(now, 'yyyymmdd_HHMMSS'));
    resourceUtilization = 78.9;
    imbalanceDegree = 0.15; % 15% imbalance = 85% balance
    algorithmName = 'EACO';
    throughput = 8.12;
    energyData = 234.56; % Wh
    
    % Sample VM utilization data (10 VMs)
    vmUtilData = [
        65.4, 72.1;  % VM1: CPU, RAM
        78.2, 65.5;  % VM2
        82.1, 88.3;  % VM3
        55.6, 61.2;  % VM4
        91.3, 85.4;  % VM5
        73.2, 69.8;  % VM6
        68.9, 77.5;  % VM7
        85.4, 92.1;  % VM8
        62.3, 58.9;  % VM9
        77.8, 81.2   % VM10
    ];
    
    % Call the main function
    plotPaths = generateComparisonPlots(avgResponseTime, makespan, runId, ...
        resourceUtilization, imbalanceDegree, algorithmName, throughput, ...
        energyData, vmUtilData);
    
    fprintf('Generated %d plots for EACO\n', length(plotPaths));
end

function test_EPSO()
    fprintf('\nGenerating EPSO test plots...\n');
    
    % Hardcoded test data for EPSO (slightly different metrics)
    avgResponseTime = 52.34;
    makespan = 145.67;
    runId = 'test_epso_' + string(datestr(now, 'yyyymmdd_HHMMSS'));
    resourceUtilization = 72.3;
    imbalanceDegree = 0.22; % 22% imbalance = 78% balance
    algorithmName = 'EPSO';
    throughput = 6.87;
    energyData = 287.91; % Wh
    
    % Sample VM utilization data (10 VMs) - different pattern
    vmUtilData = [
        58.2, 68.4;  % VM1: CPU, RAM
        71.5, 59.8;  % VM2
        76.3, 82.1;  % VM3
        49.8, 55.6;  % VM4
        87.6, 79.2;  % VM5
        66.4, 63.1;  % VM6
        62.1, 71.8;  % VM7
        79.7, 86.4;  % VM8
        55.6, 52.3;  % VM9
        71.1, 75.5   % VM10
    ];
    
    % Call the main function
    plotPaths = generateComparisonPlots(avgResponseTime, makespan, runId, ...
        resourceUtilization, imbalanceDegree, algorithmName, throughput, ...
        energyData, vmUtilData);
    
    fprintf('Generated %d plots for EPSO\n', length(plotPaths));
end

function test_comparison()
    fprintf('\nGenerating comparison visualization test...\n');
    
    % Create a custom comparison plot with both algorithms
    figure('Position', [100, 100, 1200, 600], 'Color', 'white');
    
    % Define modern color palette
    primaryColor = [46, 134, 171]/255;   % Deep blue
    secondaryColor = [162, 59, 114]/255;  % Purple
    successColor = [115, 194, 190]/255;   % Teal
    warningColor = [241, 143, 1]/255;     % Orange
    textColor = [31, 41, 55]/255;         % Dark gray
    
    % Comparison data
    algorithms = categorical({'EACO', 'EPSO'});
    
    % Subplot 1: Key metrics comparison
    subplot(2, 3, 1);
    makespanData = [123.45, 145.67];
    b1 = bar(algorithms, makespanData);
    b1.FaceColor = primaryColor;
    ylabel('Makespan (s)', 'FontWeight', 'bold', 'Color', textColor);
    title('Makespan Comparison', 'FontWeight', 'bold', 'Color', textColor);
    grid on;
    set(gca, 'GridAlpha', 0.15);
    % Add value labels
    text(b1.XEndPoints, b1.YEndPoints, string(round(makespanData, 2)), ...
        'HorizontalAlignment', 'center', 'VerticalAlignment', 'bottom', ...
        'FontWeight', 'bold');
    
    % Subplot 2: Response Time
    subplot(2, 3, 2);
    responseData = [45.67, 52.34];
    b2 = bar(algorithms, responseData);
    b2.FaceColor = secondaryColor;
    ylabel('Response Time (s)', 'FontWeight', 'bold', 'Color', textColor);
    title('Response Time Comparison', 'FontWeight', 'bold', 'Color', textColor);
    grid on;
    set(gca, 'GridAlpha', 0.15);
    text(b2.XEndPoints, b2.YEndPoints, string(round(responseData, 2)), ...
        'HorizontalAlignment', 'center', 'VerticalAlignment', 'bottom', ...
        'FontWeight', 'bold');
    
    % Subplot 3: Resource Utilization
    subplot(2, 3, 3);
    utilizationData = [78.9, 72.3];
    b3 = bar(algorithms, utilizationData);
    b3.FaceColor = successColor;
    ylabel('Utilization (%)', 'FontWeight', 'bold', 'Color', textColor);
    title('Resource Utilization', 'FontWeight', 'bold', 'Color', textColor);
    grid on;
    set(gca, 'GridAlpha', 0.15);
    ylim([0 100]);
    text(b3.XEndPoints, b3.YEndPoints, string(round(utilizationData, 1)) + "%", ...
        'HorizontalAlignment', 'center', 'VerticalAlignment', 'bottom', ...
        'FontWeight', 'bold');
    
    % Subplot 4: Energy Consumption
    subplot(2, 3, 4);
    energyData = [234.56, 287.91];
    b4 = bar(algorithms, energyData);
    b4.FaceColor = warningColor;
    ylabel('Energy (Wh)', 'FontWeight', 'bold', 'Color', textColor);
    title('Energy Consumption', 'FontWeight', 'bold', 'Color', textColor);
    grid on;
    set(gca, 'GridAlpha', 0.15);
    text(b4.XEndPoints, b4.YEndPoints, string(round(energyData, 1)) + " Wh", ...
        'HorizontalAlignment', 'center', 'VerticalAlignment', 'bottom', ...
        'FontWeight', 'bold');
    
    % Subplot 5: Load Balance
    subplot(2, 3, 5);
    balanceData = [85, 78]; % Converted from imbalance
    b5 = bar(algorithms, balanceData);
    b5.FaceColor = successColor;
    ylabel('Load Balance (%)', 'FontWeight', 'bold', 'Color', textColor);
    title('Load Balance', 'FontWeight', 'bold', 'Color', textColor);
    grid on;
    set(gca, 'GridAlpha', 0.15);
    ylim([0 100]);
    text(b5.XEndPoints, b5.YEndPoints, string(round(balanceData, 1)) + "%", ...
        'HorizontalAlignment', 'center', 'VerticalAlignment', 'bottom', ...
        'FontWeight', 'bold');
    
    % Subplot 6: Throughput
    subplot(2, 3, 6);
    throughputData = [8.12, 6.87];
    b6 = bar(algorithms, throughputData);
    b6.FaceColor = primaryColor;
    ylabel('Throughput (tasks/s)', 'FontWeight', 'bold', 'Color', textColor);
    title('Throughput Comparison', 'FontWeight', 'bold', 'Color', textColor);
    grid on;
    set(gca, 'GridAlpha', 0.15);
    text(b6.XEndPoints, b6.YEndPoints, string(round(throughputData, 2)), ...
        'HorizontalAlignment', 'center', 'VerticalAlignment', 'bottom', ...
        'FontWeight', 'bold');
    
    % Add main title
    sgtitle('EACO vs EPSO Performance Comparison', ...
        'FontSize', 16, 'FontWeight', 'bold', 'Color', textColor);
    
    % Save the comparison plot
    if ~exist('test_plots', 'dir')
        mkdir('test_plots');
    end
    saveas(gcf, 'test_plots/algorithm_comparison.png');
    
    fprintf('Comparison plot saved to test_plots/algorithm_comparison.png\n');
    
    % Test Plotly-style interactive features (if available)
    test_plotly_features();
end

function test_plotly_features()
    fprintf('\nTesting Plotly-style features...\n');
    
    % Create an interactive-style plot with hover effects simulation
    figure('Position', [100, 100, 800, 500], 'Color', 'white');
    
    % Sample data
    metrics = {'Makespan', 'Response Time', 'Resource Util.', 'Energy Cons.', 'Load Balance'};
    eaco_values = [123.45, 45.67, 78.9, 234.56, 85];
    epso_values = [145.67, 52.34, 72.3, 287.91, 78];
    
    % Normalize for comparison (0-100 scale)
    eaco_norm = normalize_metrics(eaco_values);
    epso_norm = normalize_metrics(epso_values);
    
    % Create grouped bar chart
    x = 1:length(metrics);
    width = 0.35;
    
    b1 = bar(x - width/2, eaco_norm, width);
    hold on;
    b2 = bar(x + width/2, epso_norm, width);
    
    % Style the bars with gradient-like appearance
    b1.FaceColor = [46, 134, 171]/255;
    b2.FaceColor = [162, 59, 114]/255;
    b1.EdgeColor = 'none';
    b2.EdgeColor = 'none';
    
    % Add data labels
    for i = 1:length(eaco_norm)
        text(i - width/2, eaco_norm(i), sprintf('%.1f', eaco_values(i)), ...
            'HorizontalAlignment', 'center', 'VerticalAlignment', 'bottom', ...
            'FontSize', 9, 'FontWeight', 'bold');
        text(i + width/2, epso_norm(i), sprintf('%.1f', epso_values(i)), ...
            'HorizontalAlignment', 'center', 'VerticalAlignment', 'bottom', ...
            'FontSize', 9, 'FontWeight', 'bold');
    end
    
    % Customize axes
    set(gca, 'XTick', x, 'XTickLabel', metrics);
    ylabel('Normalized Score (0-100)', 'FontWeight', 'bold');
    title('Algorithm Performance Metrics (Normalized)', ...
        'FontSize', 14, 'FontWeight', 'bold');
    legend({'EACO', 'EPSO'}, 'Location', 'northwest', ...
        'FontSize', 11, 'FontWeight', 'bold');
    
    % Add grid with subtle styling
    grid on;
    set(gca, 'GridAlpha', 0.15, 'GridLineStyle', '-');
    set(gca, 'FontSize', 10);
    
    % Add box for cleaner look
    box off;
    
    % Save
    saveas(gcf, 'test_plots/plotly_style_comparison.png');
    fprintf('Plotly-style plot saved to test_plots/plotly_style_comparison.png\n');
end

function norm_values = normalize_metrics(values)
    % Normalize metrics to 0-100 scale
    % For makespan and response time, lower is better (invert)
    % For others, higher is better
    
    norm_values = zeros(size(values));
    
    % Makespan (lower is better) - assume range 50-200
    norm_values(1) = max(0, min(100, (200 - values(1)) / 150 * 100));
    
    % Response Time (lower is better) - assume range 20-100
    norm_values(2) = max(0, min(100, (100 - values(2)) / 80 * 100));
    
    % Resource Utilization (higher is better) - already in percentage
    norm_values(3) = values(3);
    
    % Energy Consumption (lower is better) - assume range 100-400
    norm_values(4) = max(0, min(100, (400 - values(4)) / 300 * 100));
    
    % Load Balance (higher is better) - already in percentage
    norm_values(5) = values(5);
end

% Script runs automatically when called
