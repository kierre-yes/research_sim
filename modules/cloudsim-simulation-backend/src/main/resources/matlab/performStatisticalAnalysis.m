function analysisResults = performStatisticalAnalysis(eacoData, epsoData, runId)
    % performStatisticalAnalysis - Statistical comparison of EACO vs EPSO algorithms
    % This function performs comprehensive statistical tests for thesis validation
    
    fprintf('\n=== Starting Statistical Analysis ===\n');
    
    % Initialize results structure
    analysisResults = struct();
    
    % Create output directory for statistical reports
    outputDir = fullfile('plots', runId, 'statistics');
    if ~exist(outputDir, 'dir')
        mkdir(outputDir);
    end
    
    %% 1. Descriptive Statistics
    fprintf('Computing descriptive statistics...\n');
    
    % EACO Statistics
    analysisResults.eaco.mean = mean(eacoData);
    analysisResults.eaco.median = median(eacoData);
    analysisResults.eaco.std = std(eacoData);
    analysisResults.eaco.variance = var(eacoData);
    analysisResults.eaco.min = min(eacoData);
    analysisResults.eaco.max = max(eacoData);
    analysisResults.eaco.range = range(eacoData);
    analysisResults.eaco.iqr = iqr(eacoData);
    analysisResults.eaco.cv = (std(eacoData) / mean(eacoData)) * 100; % Coefficient of variation
    
    % EPSO Statistics
    analysisResults.epso.mean = mean(epsoData);
    analysisResults.epso.median = median(epsoData);
    analysisResults.epso.std = std(epsoData);
    analysisResults.epso.variance = var(epsoData);
    analysisResults.epso.min = min(epsoData);
    analysisResults.epso.max = max(epsoData);
    analysisResults.epso.range = range(epsoData);
    analysisResults.epso.iqr = iqr(epsoData);
    analysisResults.epso.cv = (std(epsoData) / mean(epsoData)) * 100;
    
    %% 2. Hypothesis Testing
    fprintf('Performing hypothesis tests...\n');
    
    % Shapiro-Wilk test for normality (if sample size <= 5000)
    if length(eacoData) <= 5000
        [h_eaco, p_eaco] = swtest(eacoData);
        [h_epso, p_epso] = swtest(epsoData);
        analysisResults.normality.eaco_normal = ~h_eaco;
        analysisResults.normality.epso_normal = ~h_epso;
        analysisResults.normality.eaco_p = p_eaco;
        analysisResults.normality.epso_p = p_epso;
    else
        % Use Kolmogorov-Smirnov test for larger samples
        [h_eaco, p_eaco] = kstest(eacoData);
        [h_epso, p_epso] = kstest(epsoData);
        analysisResults.normality.eaco_normal = ~h_eaco;
        analysisResults.normality.epso_normal = ~h_epso;
        analysisResults.normality.eaco_p = p_eaco;
        analysisResults.normality.epso_p = p_epso;
    end
    
    % Two-sample t-test (parametric)
    [h_ttest, p_ttest, ci_ttest, stats_ttest] = ttest2(eacoData, epsoData);
    analysisResults.ttest.significant = logical(h_ttest);
    analysisResults.ttest.pvalue = p_ttest;
    analysisResults.ttest.ci = ci_ttest;
    analysisResults.ttest.tstat = stats_ttest.tstat;
    analysisResults.ttest.df = stats_ttest.df;
    
    % Mann-Whitney U test (non-parametric alternative)
    [p_mw, h_mw, stats_mw] = ranksum(eacoData, epsoData);
    analysisResults.mannwhitney.significant = logical(h_mw);
    analysisResults.mannwhitney.pvalue = p_mw;
    analysisResults.mannwhitney.ranksum = stats_mw.ranksum;
    
    % F-test for equality of variances
    [h_var, p_var] = vartest2(eacoData, epsoData);
    analysisResults.vartest.equal_variance = ~h_var;
    analysisResults.vartest.pvalue = p_var;
    
    %% 3. Effect Size Calculation
    fprintf('Calculating effect sizes...\n');
    
    % Cohen's d
    pooled_std = sqrt(((length(eacoData)-1)*var(eacoData) + (length(epsoData)-1)*var(epsoData)) / ...
                      (length(eacoData) + length(epsoData) - 2));
    analysisResults.effect_size.cohens_d = (mean(eacoData) - mean(epsoData)) / pooled_std;
    
    % Interpret Cohen's d
    d = abs(analysisResults.effect_size.cohens_d);
    if d < 0.2
        analysisResults.effect_size.interpretation = 'Negligible';
    elseif d < 0.5
        analysisResults.effect_size.interpretation = 'Small';
    elseif d < 0.8
        analysisResults.effect_size.interpretation = 'Medium';
    else
        analysisResults.effect_size.interpretation = 'Large';
    end
    
    %% 4. Confidence Intervals
    fprintf('Computing confidence intervals...\n');
    
    % 95% Confidence intervals
    alpha = 0.05;
    
    % EACO CI
    n_eaco = length(eacoData);
    se_eaco = std(eacoData) / sqrt(n_eaco);
    t_critical = tinv(1 - alpha/2, n_eaco - 1);
    analysisResults.ci.eaco_lower = mean(eacoData) - t_critical * se_eaco;
    analysisResults.ci.eaco_upper = mean(eacoData) + t_critical * se_eaco;
    
    % EPSO CI
    n_epso = length(epsoData);
    se_epso = std(epsoData) / sqrt(n_epso);
    t_critical = tinv(1 - alpha/2, n_epso - 1);
    analysisResults.ci.epso_lower = mean(epsoData) - t_critical * se_epso;
    analysisResults.ci.epso_upper = mean(epsoData) + t_critical * se_epso;
    
    %% 5. Performance Improvement Calculation
    fprintf('Calculating performance improvements...\n');
    
    % Calculate percentage improvement (assuming lower is better for metrics like makespan)
    analysisResults.improvement.percentage = ((mean(epsoData) - mean(eacoData)) / mean(epsoData)) * 100;
    analysisResults.improvement.absolute = mean(epsoData) - mean(eacoData);
    
    % Determine which algorithm performs better
    if analysisResults.improvement.percentage > 0
        analysisResults.improvement.better_algorithm = 'EACO';
    else
        analysisResults.improvement.better_algorithm = 'EPSO';
    end
    
    %% 6. Generate Statistical Plots
    fprintf('Generating statistical visualization plots...\n');
    
    % Plot 1: Box plot comparison
    figure('Visible', 'off', 'Position', [100, 100, 800, 600]);
    boxplot([eacoData(:); epsoData(:)], ...
            [ones(length(eacoData), 1); 2*ones(length(epsoData), 1)], ...
            'Labels', {'EACO', 'EPSO'});
    ylabel('Performance Metric Value');
    title('Algorithm Performance Comparison - Box Plot');
    grid on;
    
    % Add mean markers
    hold on;
    plot(1, mean(eacoData), 'r*', 'MarkerSize', 10, 'LineWidth', 2);
    plot(2, mean(epsoData), 'r*', 'MarkerSize', 10, 'LineWidth', 2);
    legend('', 'Mean', 'Location', 'best');
    hold off;
    
    saveas(gcf, fullfile(outputDir, 'boxplot_comparison.png'));
    close(gcf);
    
    % Plot 2: Distribution comparison
    figure('Visible', 'off', 'Position', [100, 100, 1000, 600]);
    
    subplot(1, 2, 1);
    histogram(eacoData, 'Normalization', 'pdf', 'FaceColor', [0.2 0.6 0.8]);
    hold on;
    x_range = linspace(min(eacoData), max(eacoData), 100);
    plot(x_range, normpdf(x_range, mean(eacoData), std(eacoData)), 'r-', 'LineWidth', 2);
    xlabel('Value');
    ylabel('Probability Density');
    title('EACO Distribution');
    legend('Empirical', 'Normal Fit');
    grid on;
    
    subplot(1, 2, 2);
    histogram(epsoData, 'Normalization', 'pdf', 'FaceColor', [0.9 0.4 0.1]);
    hold on;
    x_range = linspace(min(epsoData), max(epsoData), 100);
    plot(x_range, normpdf(x_range, mean(epsoData), std(epsoData)), 'r-', 'LineWidth', 2);
    xlabel('Value');
    ylabel('Probability Density');
    title('EPSO Distribution');
    legend('Empirical', 'Normal Fit');
    grid on;
    
    saveas(gcf, fullfile(outputDir, 'distribution_comparison.png'));
    close(gcf);
    
    % Plot 3: Q-Q plots for normality assessment
    figure('Visible', 'off', 'Position', [100, 100, 1000, 500]);
    
    subplot(1, 2, 1);
    qqplot(eacoData);
    title('EACO Q-Q Plot');
    grid on;
    
    subplot(1, 2, 2);
    qqplot(epsoData);
    title('EPSO Q-Q Plot');
    grid on;
    
    saveas(gcf, fullfile(outputDir, 'qq_plots.png'));
    close(gcf);
    
    %% 7. Generate Statistical Report
    fprintf('Generating statistical report...\n');
    
    reportFile = fullfile(outputDir, 'statistical_report.txt');
    fid = fopen(reportFile, 'w');
    
    fprintf(fid, '================================================================================\n');
    fprintf(fid, '                    STATISTICAL ANALYSIS REPORT\n');
    fprintf(fid, '                    Algorithm Comparison: EACO vs EPSO\n');
    fprintf(fid, '================================================================================\n\n');
    
    fprintf(fid, '1. DESCRIPTIVE STATISTICS\n');
    fprintf(fid, '-------------------------\n');
    fprintf(fid, '                    EACO            EPSO\n');
    fprintf(fid, 'Mean:               %.4f          %.4f\n', analysisResults.eaco.mean, analysisResults.epso.mean);
    fprintf(fid, 'Median:             %.4f          %.4f\n', analysisResults.eaco.median, analysisResults.epso.median);
    fprintf(fid, 'Std Dev:            %.4f          %.4f\n', analysisResults.eaco.std, analysisResults.epso.std);
    fprintf(fid, 'Variance:           %.4f          %.4f\n', analysisResults.eaco.variance, analysisResults.epso.variance);
    fprintf(fid, 'Min:                %.4f          %.4f\n', analysisResults.eaco.min, analysisResults.epso.min);
    fprintf(fid, 'Max:                %.4f          %.4f\n', analysisResults.eaco.max, analysisResults.epso.max);
    fprintf(fid, 'Range:              %.4f          %.4f\n', analysisResults.eaco.range, analysisResults.epso.range);
    fprintf(fid, 'IQR:                %.4f          %.4f\n', analysisResults.eaco.iqr, analysisResults.epso.iqr);
    fprintf(fid, 'CV (%%):             %.2f%%         %.2f%%\n\n', analysisResults.eaco.cv, analysisResults.epso.cv);
    
    fprintf(fid, '2. HYPOTHESIS TESTING\n');
    fprintf(fid, '---------------------\n');
    fprintf(fid, 'Normality Test (p-values):\n');
    fprintf(fid, '  EACO: p = %.4f (%s)\n', analysisResults.normality.eaco_p, ...
            iff(analysisResults.normality.eaco_normal, 'Normal', 'Not Normal'));
    fprintf(fid, '  EPSO: p = %.4f (%s)\n\n', analysisResults.normality.epso_p, ...
            iff(analysisResults.normality.epso_normal, 'Normal', 'Not Normal'));
    
    fprintf(fid, 'Two-Sample T-Test:\n');
    fprintf(fid, '  p-value: %.6f\n', analysisResults.ttest.pvalue);
    fprintf(fid, '  Result: %s at α = 0.05\n', ...
            iff(analysisResults.ttest.significant, 'Significant difference', 'No significant difference'));
    fprintf(fid, '  t-statistic: %.4f\n', analysisResults.ttest.tstat);
    fprintf(fid, '  Degrees of freedom: %d\n\n', analysisResults.ttest.df);
    
    fprintf(fid, 'Mann-Whitney U Test (Non-parametric):\n');
    fprintf(fid, '  p-value: %.6f\n', analysisResults.mannwhitney.pvalue);
    fprintf(fid, '  Result: %s at α = 0.05\n\n', ...
            iff(analysisResults.mannwhitney.significant, 'Significant difference', 'No significant difference'));
    
    fprintf(fid, 'Variance Test:\n');
    fprintf(fid, '  p-value: %.6f\n', analysisResults.vartest.pvalue);
    fprintf(fid, '  Result: %s\n\n', ...
            iff(analysisResults.vartest.equal_variance, 'Equal variances', 'Unequal variances'));
    
    fprintf(fid, '3. EFFECT SIZE\n');
    fprintf(fid, '--------------\n');
    fprintf(fid, "Cohen's d: %.4f (%s effect)\n\n", ...
            analysisResults.effect_size.cohens_d, analysisResults.effect_size.interpretation);
    
    fprintf(fid, '4. CONFIDENCE INTERVALS (95%%)\n');
    fprintf(fid, '-----------------------------\n');
    fprintf(fid, 'EACO: [%.4f, %.4f]\n', analysisResults.ci.eaco_lower, analysisResults.ci.eaco_upper);
    fprintf(fid, 'EPSO: [%.4f, %.4f]\n\n', analysisResults.ci.epso_lower, analysisResults.ci.epso_upper);
    
    fprintf(fid, '5. PERFORMANCE COMPARISON\n');
    fprintf(fid, '-------------------------\n');
    fprintf(fid, 'Better performing algorithm: %s\n', analysisResults.improvement.better_algorithm);
    fprintf(fid, 'Improvement: %.2f%% (%.4f absolute)\n\n', ...
            abs(analysisResults.improvement.percentage), abs(analysisResults.improvement.absolute));
    
    fprintf(fid, '================================================================================\n');
    fprintf(fid, 'Report generated: %s\n', datestr(now, 'yyyy-mm-dd HH:MM:SS'));
    fprintf(fid, '================================================================================\n');
    
    fclose(fid);
    
    % Also create LaTeX table for thesis
    latexFile = fullfile(outputDir, 'results_table.tex');
    fid = fopen(latexFile, 'w');
    
    fprintf(fid, '\\begin{table}[h]\n');
    fprintf(fid, '\\centering\n');
    fprintf(fid, '\\caption{Statistical Comparison of EACO and EPSO Algorithms}\n');
    fprintf(fid, '\\label{tab:statistical_comparison}\n');
    fprintf(fid, '\\begin{tabular}{lcc}\n');
    fprintf(fid, '\\toprule\n');
    fprintf(fid, '\\textbf{Metric} & \\textbf{EACO} & \\textbf{EPSO} \\\\\n');
    fprintf(fid, '\\midrule\n');
    fprintf(fid, 'Mean $\\pm$ SD & $%.2f \\pm %.2f$ & $%.2f \\pm %.2f$ \\\\\n', ...
            analysisResults.eaco.mean, analysisResults.eaco.std, ...
            analysisResults.epso.mean, analysisResults.epso.std);
    fprintf(fid, 'Median [IQR] & $%.2f$ [$%.2f$] & $%.2f$ [$%.2f$] \\\\\n', ...
            analysisResults.eaco.median, analysisResults.eaco.iqr, ...
            analysisResults.epso.median, analysisResults.epso.iqr);
    fprintf(fid, 'Min--Max & $%.2f$--$%.2f$ & $%.2f$--$%.2f$ \\\\\n', ...
            analysisResults.eaco.min, analysisResults.eaco.max, ...
            analysisResults.epso.min, analysisResults.epso.max);
    fprintf(fid, 'CV (\\%%) & $%.1f$ & $%.1f$ \\\\\n', ...
            analysisResults.eaco.cv, analysisResults.epso.cv);
    fprintf(fid, '\\midrule\n');
    fprintf(fid, 'p-value (t-test) & \\multicolumn{2}{c}{$%.4f$} \\\\\n', analysisResults.ttest.pvalue);
    fprintf(fid, "Cohen's d & \\multicolumn{2}{c}{$%.3f$} \\\\\n", analysisResults.effect_size.cohens_d);
    fprintf(fid, '\\bottomrule\n');
    fprintf(fid, '\\end{tabular}\n');
    fprintf(fid, '\\end{table}\n');
    
    fclose(fid);
    
    fprintf('Statistical analysis complete. Results saved to: %s\n', outputDir);
    
    % Convert to JSON for Java
    analysisJson = jsonencode(analysisResults);
    assignin('base', 'statisticalAnalysisJson', analysisJson);
end

% Helper function for conditional strings
function result = iff(condition, trueStr, falseStr)
    if condition
        result = trueStr;
    else
        result = falseStr;
    end
end

% Shapiro-Wilk test implementation (if not available)
function [H, pValue] = swtest(x)
    % Simple implementation - you may need to add the full implementation
    % or use an existing MATLAB package
    [H, pValue] = kstest((x - mean(x))/std(x)); % Fallback to KS test
end
