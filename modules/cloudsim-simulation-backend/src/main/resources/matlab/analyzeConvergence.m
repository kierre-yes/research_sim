function convergenceResults = analyzeConvergence(iterationData, algorithmName, runId)
    % analyzeConvergence - Analyze algorithm convergence behavior
    % Essential for demonstrating algorithm efficiency in thesis
    
    fprintf('\n=== Convergence Analysis for %s ===\n', algorithmName);
    
    % Create output directory
    outputDir = fullfile('plots', runId, 'convergence');
    if ~exist(outputDir, 'dir')
        mkdir(outputDir);
    end
    
    % Initialize results
    convergenceResults = struct();
    
    % Extract iteration numbers and fitness values
    if isstruct(iterationData)
        iterations = iterationData.iterations;
        bestFitness = iterationData.bestFitness;
        avgFitness = iterationData.avgFitness;
        worstFitness = iterationData.worstFitness;
    else
        % If only best fitness is provided
        iterations = 1:length(iterationData);
        bestFitness = iterationData;
        avgFitness = [];
        worstFitness = [];
    end
    
    %% Calculate Convergence Metrics
    
    % 1. Convergence Rate
    convergenceRate = abs(bestFitness(end) - bestFitness(1)) / length(iterations);
    convergenceResults.rate = convergenceRate;
    
    % 2. Time to Convergence (iterations to reach 95% of final value)
    threshold95 = bestFitness(1) + 0.95 * (bestFitness(end) - bestFitness(1));
    idx95 = find(bestFitness >= threshold95, 1);
    if isempty(idx95)
        idx95 = length(iterations);
    end
    convergenceResults.timeToConverge95 = idx95;
    
    % 3. Stability (standard deviation of last 20% iterations)
    last20pct = round(0.8 * length(iterations)):length(iterations);
    convergenceResults.stability = std(bestFitness(last20pct));
    
    % 4. Improvement per iteration
    improvements = diff(bestFitness);
    convergenceResults.avgImprovement = mean(abs(improvements));
    convergenceResults.maxImprovement = max(abs(improvements));
    
    % 5. Stagnation detection
    stagnationThreshold = 1e-6;
    stagnantIterations = sum(abs(improvements) < stagnationThreshold);
    convergenceResults.stagnationRatio = stagnantIterations / length(improvements);
    
    %% Generate Convergence Plots
    
    % Plot 1: Main Convergence Curve
    figure('Visible', 'off', 'Position', [100, 100, 1200, 800]);
    
    subplot(2, 2, 1);
    plot(iterations, bestFitness, 'b-', 'LineWidth', 2);
    hold on;
    if ~isempty(avgFitness)
        plot(iterations, avgFitness, 'g--', 'LineWidth', 1.5);
    end
    if ~isempty(worstFitness)
        plot(iterations, worstFitness, 'r:', 'LineWidth', 1.5);
    end
    
    % Mark 95% convergence point
    plot(idx95, bestFitness(idx95), 'ko', 'MarkerSize', 8, 'MarkerFaceColor', 'yellow');
    
    xlabel('Iteration');
    ylabel('Fitness Value');
    title(sprintf('%s - Convergence Curve', algorithmName));
    if ~isempty(avgFitness) && ~isempty(worstFitness)
        legend('Best', 'Average', 'Worst', '95% Convergence', 'Location', 'best');
    else
        legend('Best Fitness', '95% Convergence', 'Location', 'best');
    end
    grid on;
    
    % Add convergence info text
    text(0.6, 0.9, sprintf('Conv. Rate: %.4f\n95%% at iter: %d\nStability: %.4f', ...
        convergenceRate, idx95, convergenceResults.stability), ...
        'Units', 'normalized', 'BackgroundColor', 'white', 'EdgeColor', 'black');
    
    % Plot 2: Improvement Rate
    subplot(2, 2, 2);
    bar(iterations(2:end), improvements, 'FaceColor', [0.3 0.6 0.9]);
    xlabel('Iteration');
    ylabel('Improvement');
    title('Per-Iteration Improvement');
    grid on;
    
    % Add moving average line
    if length(improvements) > 5
        movAvg = movmean(improvements, 5);
        hold on;
        plot(iterations(2:end), movAvg, 'r-', 'LineWidth', 2);
        legend('Improvement', 'Moving Avg (5)', 'Location', 'best');
    end
    
    % Plot 3: Log-scale Convergence
    subplot(2, 2, 3);
    semilogy(iterations, bestFitness - min(bestFitness) + eps, 'b-', 'LineWidth', 2);
    xlabel('Iteration');
    ylabel('Distance to Optimum (log scale)');
    title('Logarithmic Convergence Analysis');
    grid on;
    
    % Fit exponential decay
    if length(iterations) > 10
        % Fit exponential model
        x = iterations(:);
        y = bestFitness(:) - min(bestFitness) + eps;
        f = fit(x, y, 'exp1');
        hold on;
        plot(f, 'r--');
        legend('Actual', 'Exponential Fit', 'Location', 'best');
        
        % Store fit parameters
        convergenceResults.expFit.a = f.a;
        convergenceResults.expFit.b = f.b;
    end
    
    % Plot 4: Convergence Speed Heatmap
    subplot(2, 2, 4);
    
    % Calculate convergence speed in windows
    windowSize = max(1, floor(length(iterations) / 20));
    speeds = zeros(1, floor(length(iterations) / windowSize));
    
    for i = 1:length(speeds)
        startIdx = (i-1) * windowSize + 1;
        endIdx = min(i * windowSize, length(iterations));
        if endIdx > startIdx
            speeds(i) = abs(bestFitness(endIdx) - bestFitness(startIdx)) / (endIdx - startIdx);
        end
    end
    
    % Create heatmap
    imagesc(speeds);
    colormap(hot);
    colorbar;
    xlabel('Time Window');
    ylabel('');
    title('Convergence Speed Over Time');
    set(gca, 'YTick', []);
    
    % Save main plot
    saveas(gcf, fullfile(outputDir, sprintf('%s_convergence_analysis.png', lower(algorithmName))));
    close(gcf);
    
    %% Generate Detailed Convergence Report
    
    % Plot 2: Phase Analysis
    figure('Visible', 'off', 'Position', [100, 100, 1000, 600]);
    
    % Divide convergence into phases
    nPhases = 3;
    phaseSize = floor(length(iterations) / nPhases);
    phaseColors = {'r', 'y', 'g'};
    phaseNames = {'Exploration', 'Transition', 'Exploitation'};
    
    hold on;
    for p = 1:nPhases
        startIdx = (p-1) * phaseSize + 1;
        if p == nPhases
            endIdx = length(iterations);
        else
            endIdx = p * phaseSize;
        end
        
        plot(iterations(startIdx:endIdx), bestFitness(startIdx:endIdx), ...
            'Color', phaseColors{p}, 'LineWidth', 2.5);
        
        % Calculate phase statistics
        phaseImprovement = bestFitness(endIdx) - bestFitness(startIdx);
        phaseRate = phaseImprovement / (endIdx - startIdx);
        
        convergenceResults.phases(p).name = phaseNames{p};
        convergenceResults.phases(p).improvement = phaseImprovement;
        convergenceResults.phases(p).rate = phaseRate;
        convergenceResults.phases(p).iterations = [startIdx, endIdx];
    end
    
    xlabel('Iteration');
    ylabel('Fitness Value');
    title(sprintf('%s - Convergence Phase Analysis', algorithmName));
    legend(phaseNames, 'Location', 'best');
    grid on;
    
    % Add phase statistics
    statsText = '';
    for p = 1:nPhases
        statsText = sprintf('%s%s: Rate=%.4f\n', statsText, ...
            phaseNames{p}, convergenceResults.phases(p).rate);
    end
    text(0.7, 0.3, statsText, 'Units', 'normalized', ...
        'BackgroundColor', 'white', 'EdgeColor', 'black');
    
    saveas(gcf, fullfile(outputDir, sprintf('%s_phase_analysis.png', lower(algorithmName))));
    close(gcf);
    
    %% Generate Convergence Quality Score
    
    % Calculate overall convergence quality (0-100)
    qualityFactors = [
        (1 - convergenceResults.stagnationRatio) * 30;  % Low stagnation (30%)
        min(1, convergenceRate / 0.1) * 25;              % Good rate (25%)
        (1 - idx95/length(iterations)) * 25;             % Quick convergence (25%)
        (1 - min(1, convergenceResults.stability/0.01)) * 20; % Stability (20%)
    ];
    
    convergenceResults.qualityScore = sum(qualityFactors);
    
    %% Save Convergence Report
    
    reportFile = fullfile(outputDir, sprintf('%s_convergence_report.txt', lower(algorithmName)));
    fid = fopen(reportFile, 'w');
    
    fprintf(fid, '========================================\n');
    fprintf(fid, '     CONVERGENCE ANALYSIS REPORT\n');
    fprintf(fid, '     Algorithm: %s\n', algorithmName);
    fprintf(fid, '========================================\n\n');
    
    fprintf(fid, 'CONVERGENCE METRICS\n');
    fprintf(fid, '-------------------\n');
    fprintf(fid, 'Convergence Rate: %.6f\n', convergenceRate);
    fprintf(fid, 'Iterations to 95%%: %d (%.1f%%)\n', idx95, (idx95/length(iterations))*100);
    fprintf(fid, 'Final Stability: %.6f\n', convergenceResults.stability);
    fprintf(fid, 'Avg Improvement: %.6f\n', convergenceResults.avgImprovement);
    fprintf(fid, 'Max Improvement: %.6f\n', convergenceResults.maxImprovement);
    fprintf(fid, 'Stagnation Ratio: %.2f%%\n\n', convergenceResults.stagnationRatio * 100);
    
    fprintf(fid, 'PHASE ANALYSIS\n');
    fprintf(fid, '--------------\n');
    for p = 1:length(convergenceResults.phases)
        phase = convergenceResults.phases(p);
        fprintf(fid, '%s Phase (Iter %d-%d):\n', phase.name, phase.iterations(1), phase.iterations(2));
        fprintf(fid, '  Improvement: %.6f\n', phase.improvement);
        fprintf(fid, '  Rate: %.6f\n', phase.rate);
    end
    
    fprintf(fid, '\nCONVERGENCE QUALITY\n');
    fprintf(fid, '-------------------\n');
    fprintf(fid, 'Overall Score: %.1f/100\n', convergenceResults.qualityScore);
    
    if convergenceResults.qualityScore >= 80
        fprintf(fid, 'Assessment: EXCELLENT\n');
    elseif convergenceResults.qualityScore >= 60
        fprintf(fid, 'Assessment: GOOD\n');
    elseif convergenceResults.qualityScore >= 40
        fprintf(fid, 'Assessment: MODERATE\n');
    else
        fprintf(fid, 'Assessment: NEEDS IMPROVEMENT\n');
    end
    
    fprintf(fid, '\n========================================\n');
    fprintf(fid, 'Report Generated: %s\n', datestr(now));
    fprintf(fid, '========================================\n');
    
    fclose(fid);
    
    % Output summary
    fprintf('Convergence Quality Score: %.1f/100\n', convergenceResults.qualityScore);
    fprintf('Time to 95%% convergence: %d iterations\n', idx95);
    fprintf('Convergence analysis saved to: %s\n', outputDir);
    
    % Convert to JSON
    convergenceJson = jsonencode(convergenceResults);
    assignin('base', 'convergenceAnalysisJson', convergenceJson);
end
