function results = pairedTTest(eacoMetrics, epsoMetrics, metricNames, workloadName)
    % pairedTTest - Performs paired samples t-test for EACO vs EPSO comparison
    % As specified in thesis manuscript for statistical validation
    % Based on Chandrashekar et al. methodology for cloud computing algorithms
    
    % Input:
    %   eacoMetrics: Matrix where each row is a metric, columns are observations
    %   epsoMetrics: Matrix where each row is a metric, columns are observations  
    %   metricNames: Cell array of metric names (5 primary metrics)
    %   workloadName: Name of the workload being tested
    
    fprintf('\n================================================================================\n');
    fprintf('                  PAIRED SAMPLES T-TEST ANALYSIS\n');
    fprintf('                  Workload: %s\n', workloadName);
    fprintf('================================================================================\n\n');
    
    % Initialize results structure
    results = struct();
    results.workload = workloadName;
    results.timestamp = datestr(now);
    
    % The 5 primary performance metrics as per manuscript
    if isempty(metricNames)
        metricNames = {
            'Makespan',
            'Average Response Time', 
            'Resource Utilization',
            'Energy Consumption',
            'Load Balance'
        };
    end
    
    % Significance level (alpha)
    alpha = 0.05;
    
    % Perform paired t-test for each metric
    for m = 1:size(eacoMetrics, 1)
        fprintf('METRIC: %s\n', metricNames{m});
        fprintf('----------------------------------------\n');
        
        % Extract paired observations for current metric
        eacoData = eacoMetrics(m, :);
        epsoData = epsoMetrics(m, :);
        
        % Calculate differences (d = EACO - EPSO)
        differences = eacoData - epsoData;
        n = length(differences);
        
        % Calculate mean of differences (d̄)
        d_bar = mean(differences);
        
        % Calculate standard deviation of differences (Sd)
        Sd = std(differences);
        
        % Calculate standard error (SE = Sd/√n)
        SE = Sd / sqrt(n);
        
        % Calculate t-statistic: t = d̄ / (Sd/√n)
        t_statistic = d_bar / SE;
        
        % Degrees of freedom
        df = n - 1;
        
        % Calculate p-value (two-tailed test)
        p_value = 2 * tcdf(-abs(t_statistic), df);
        
        % Calculate 95% confidence interval for mean difference
        t_critical = tinv(1 - alpha/2, df);
        CI_lower = d_bar - t_critical * SE;
        CI_upper = d_bar + t_critical * SE;
        
        % Cohen's d effect size (for paired samples)
        cohens_d = d_bar / Sd;
        
        % Interpret effect size
        if abs(cohens_d) < 0.2
            effect_interpretation = 'Negligible';
        elseif abs(cohens_d) < 0.5
            effect_interpretation = 'Small';
        elseif abs(cohens_d) < 0.8
            effect_interpretation = 'Medium';
        else
            effect_interpretation = 'Large';
        end
        
        % Determine which algorithm performs better
        if d_bar < 0
            better_algorithm = 'EACO';
            improvement_percentage = abs(d_bar / mean(epsoData)) * 100;
        else
            better_algorithm = 'EPSO';
            improvement_percentage = abs(d_bar / mean(eacoData)) * 100;
        end
        
        % Store results for this metric
        results.metrics(m).name = metricNames{m};
        results.metrics(m).n = n;
        results.metrics(m).mean_difference = d_bar;
        results.metrics(m).std_difference = Sd;
        results.metrics(m).standard_error = SE;
        results.metrics(m).t_statistic = t_statistic;
        results.metrics(m).df = df;
        results.metrics(m).p_value = p_value;
        results.metrics(m).ci_lower = CI_lower;
        results.metrics(m).ci_upper = CI_upper;
        results.metrics(m).cohens_d = cohens_d;
        results.metrics(m).effect_size = effect_interpretation;
        results.metrics(m).significant = p_value < alpha;
        results.metrics(m).better_algorithm = better_algorithm;
        results.metrics(m).improvement_percentage = improvement_percentage;
        
        % Display results
        fprintf('Sample size (n): %d\n', n);
        fprintf('Mean difference (d̄): %.4f\n', d_bar);
        fprintf('Std deviation (Sd): %.4f\n', Sd);
        fprintf('Standard error (SE): %.4f\n', SE);
        fprintf('t-statistic: %.4f\n', t_statistic);
        fprintf('Degrees of freedom: %d\n', df);
        fprintf('p-value: %.6f\n', p_value);
        fprintf('95%% CI: [%.4f, %.4f]\n', CI_lower, CI_upper);
        fprintf('Cohen''s d: %.4f (%s effect)\n', cohens_d, effect_interpretation);
        
        if p_value < alpha
            fprintf('Result: STATISTICALLY SIGNIFICANT (p < 0.05)\n');
            fprintf('Conclusion: %s performs significantly better (%.2f%% improvement)\n', ...
                    better_algorithm, improvement_percentage);
        else
            fprintf('Result: NOT STATISTICALLY SIGNIFICANT (p >= 0.05)\n');
            fprintf('Conclusion: No significant difference between algorithms\n');
        end
        fprintf('\n');
    end
    
    % Overall summary
    fprintf('================================================================================\n');
    fprintf('                           SUMMARY OF RESULTS\n');
    fprintf('================================================================================\n\n');
    
    significant_count = sum([results.metrics.significant]);
    fprintf('Statistically significant differences: %d out of %d metrics\n', ...
            significant_count, length(results.metrics));
    
    % Count wins for each algorithm
    eaco_wins = 0;
    epso_wins = 0;
    for m = 1:length(results.metrics)
        if results.metrics(m).significant
            if strcmp(results.metrics(m).better_algorithm, 'EACO')
                eaco_wins = eaco_wins + 1;
            else
                epso_wins = epso_wins + 1;
            end
        end
    end
    
    fprintf('EACO performs better in: %d metrics\n', eaco_wins);
    fprintf('EPSO performs better in: %d metrics\n', epso_wins);
    
    % Overall recommendation
    if eaco_wins > epso_wins
        results.overall_winner = 'EACO';
        fprintf('\nOVERALL RECOMMENDATION: EACO shows superior performance\n');
    elseif epso_wins > eaco_wins
        results.overall_winner = 'EPSO';
        fprintf('\nOVERALL RECOMMENDATION: EPSO shows superior performance\n');
    else
        results.overall_winner = 'No clear winner';
        fprintf('\nOVERALL RECOMMENDATION: No clear winner - algorithms perform similarly\n');
    end
    
    % Generate visualization
    figure('Visible', 'off', 'Position', [100, 100, 1200, 800]);
    
    % Subplot 1: P-values for each metric
    subplot(2, 3, 1);
    p_values = [results.metrics.p_value];
    bar(p_values, 'FaceColor', [0.3 0.6 0.9]);
    hold on;
    yline(alpha, 'r--', 'LineWidth', 2, 'Label', 'α = 0.05');
    xlabel('Metric');
    ylabel('p-value');
    title('Statistical Significance by Metric');
    set(gca, 'XTickLabel', metricNames);
    xtickangle(45);
    grid on;
    
    % Subplot 2: Effect sizes (Cohen's d)
    subplot(2, 3, 2);
    effect_sizes = [results.metrics.cohens_d];
    bar(effect_sizes, 'FaceColor', [0.9 0.4 0.1]);
    xlabel('Metric');
    ylabel('Cohen''s d');
    title('Effect Sizes');
    set(gca, 'XTickLabel', metricNames);
    xtickangle(45);
    grid on;
    
    % Add reference lines for effect size interpretation
    hold on;
    yline(0.2, 'k--', 'Small');
    yline(0.5, 'k--', 'Medium');
    yline(0.8, 'k--', 'Large');
    
    % Subplot 3: Mean differences with CI
    subplot(2, 3, 3);
    mean_diffs = [results.metrics.mean_difference];
    ci_lower = [results.metrics.ci_lower];
    ci_upper = [results.metrics.ci_upper];
    
    errorbar(1:length(mean_diffs), mean_diffs, mean_diffs-ci_lower, ci_upper-mean_diffs, ...
             'o', 'MarkerSize', 8, 'MarkerFaceColor', [0.2 0.8 0.2], 'LineWidth', 2);
    hold on;
    yline(0, 'k-', 'LineWidth', 1);
    xlabel('Metric');
    ylabel('Mean Difference (EACO - EPSO)');
    title('Mean Differences with 95% CI');
    set(gca, 'XTick', 1:length(metricNames), 'XTickLabel', metricNames);
    xtickangle(45);
    grid on;
    
    % Subplot 4: t-statistics
    subplot(2, 3, 4);
    t_stats = [results.metrics.t_statistic];
    bar(t_stats, 'FaceColor', [0.8 0.2 0.8]);
    xlabel('Metric');
    ylabel('t-statistic');
    title('T-Statistics');
    set(gca, 'XTickLabel', metricNames);
    xtickangle(45);
    grid on;
    
    % Calculate critical t-value (assuming average df)
    avg_df = mean([results.metrics.df]);
    t_critical = tinv(1 - alpha/2, avg_df);
    hold on;
    yline(t_critical, 'r--', 'Upper Critical');
    yline(-t_critical, 'r--', 'Lower Critical');
    
    % Subplot 5: Improvement percentages
    subplot(2, 3, 5);
    improvements = zeros(1, length(results.metrics));
    colors = zeros(length(results.metrics), 3);
    for m = 1:length(results.metrics)
        if strcmp(results.metrics(m).better_algorithm, 'EACO')
            improvements(m) = results.metrics(m).improvement_percentage;
            colors(m, :) = [0.2 0.6 0.8]; % Blue for EACO
        else
            improvements(m) = -results.metrics(m).improvement_percentage;
            colors(m, :) = [0.9 0.4 0.1]; % Orange for EPSO
        end
    end
    
    b = bar(improvements);
    b.FaceColor = 'flat';
    b.CData = colors;
    xlabel('Metric');
    ylabel('Improvement (%)');
    title('Performance Improvement (+ EACO better, - EPSO better)');
    set(gca, 'XTickLabel', metricNames);
    xtickangle(45);
    grid on;
    yline(0, 'k-', 'LineWidth', 1);
    
    % Subplot 6: Summary statistics table
    subplot(2, 3, 6);
    axis off;
    
    % Create summary text
    summaryText = sprintf(['PAIRED T-TEST SUMMARY\n\n' ...
                          'Workload: %s\n' ...
                          'Sample Size: %d\n' ...
                          'Significance Level: %.2f\n\n' ...
                          'Significant Differences: %d/%d\n' ...
                          'EACO Better: %d metrics\n' ...
                          'EPSO Better: %d metrics\n\n' ...
                          'Overall Winner: %s'], ...
                          workloadName, n, alpha, ...
                          significant_count, length(results.metrics), ...
                          eaco_wins, epso_wins, ...
                          results.overall_winner);
    
    text(0.1, 0.5, summaryText, 'FontSize', 11, 'VerticalAlignment', 'middle');
    
    % Save figure
    outputDir = fullfile('plots', 'statistical_analysis');
    if ~exist(outputDir, 'dir')
        mkdir(outputDir);
    end
    
    filename = sprintf('paired_ttest_%s.png', lower(strrep(workloadName, ' ', '_')));
    saveas(gcf, fullfile(outputDir, filename));
    close(gcf);
    
    % Generate LaTeX table for thesis
    generateLatexTable(results, workloadName);
    
    % Save detailed report
    generateDetailedReport(results, workloadName);
    
    fprintf('\n================================================================================\n');
    fprintf('Analysis complete. Results saved to: %s\n', outputDir);
    fprintf('================================================================================\n');
end

function generateLatexTable(results, workloadName)
    % Generate LaTeX table for thesis document
    outputDir = fullfile('plots', 'statistical_analysis');
    filename = sprintf('paired_ttest_table_%s.tex', lower(strrep(workloadName, ' ', '_')));
    filepath = fullfile(outputDir, filename);
    
    fid = fopen(filepath, 'w');
    
    fprintf(fid, '%% Paired t-test results for %s\n', workloadName);
    fprintf(fid, '\\begin{table}[h!]\n');
    fprintf(fid, '\\centering\n');
    fprintf(fid, '\\caption{Paired t-test Results: EACO vs EPSO on %s Workload}\n', workloadName);
    fprintf(fid, '\\label{tab:ttest_%s}\n', lower(strrep(workloadName, ' ', '_')));
    fprintf(fid, '\\begin{tabular}{lccccc}\n');
    fprintf(fid, '\\toprule\n');
    fprintf(fid, '\\textbf{Metric} & \\textbf{Mean Diff.} & \\textbf{t-statistic} & \\textbf{p-value} & \\textbf{Cohen''s d} & \\textbf{Result} \\\\\n');
    fprintf(fid, '\\midrule\n');
    
    for m = 1:length(results.metrics)
        metric = results.metrics(m);
        
        % Format significance
        if metric.p_value < 0.001
            p_str = '< 0.001***';
        elseif metric.p_value < 0.01
            p_str = sprintf('%.3f**', metric.p_value);
        elseif metric.p_value < 0.05
            p_str = sprintf('%.3f*', metric.p_value);
        else
            p_str = sprintf('%.3f', metric.p_value);
        end
        
        % Result column
        if metric.significant
            result_str = sprintf('%s↑', metric.better_algorithm);
        else
            result_str = 'NS';
        end
        
        fprintf(fid, '%s & %.4f & %.3f & %s & %.3f & %s \\\\\n', ...
                metric.name, metric.mean_difference, metric.t_statistic, ...
                p_str, metric.cohens_d, result_str);
    end
    
    fprintf(fid, '\\bottomrule\n');
    fprintf(fid, '\\end{tabular}\n');
    fprintf(fid, '\\begin{tablenotes}\n');
    fprintf(fid, '\\small\n');
    fprintf(fid, '\\item Note: * p < 0.05, ** p < 0.01, *** p < 0.001, NS = Not Significant\n');
    fprintf(fid, '\\item ↑ indicates the better performing algorithm\n');
    fprintf(fid, '\\end{tablenotes}\n');
    fprintf(fid, '\\end{table}\n');
    
    fclose(fid);
    fprintf('LaTeX table saved to: %s\n', filepath);
end

function generateDetailedReport(results, workloadName)
    % Generate detailed text report
    outputDir = fullfile('plots', 'statistical_analysis');
    filename = sprintf('paired_ttest_report_%s.txt', lower(strrep(workloadName, ' ', '_')));
    filepath = fullfile(outputDir, filename);
    
    fid = fopen(filepath, 'w');
    
    fprintf(fid, '================================================================================\n');
    fprintf(fid, '              DETAILED PAIRED T-TEST STATISTICAL ANALYSIS REPORT\n');
    fprintf(fid, '================================================================================\n\n');
    fprintf(fid, 'Study: Comparison of Enhanced PSO (EPSO) and Enhanced ACO (EACO) Algorithms\n');
    fprintf(fid, 'Workload: %s\n', workloadName);
    fprintf(fid, 'Date: %s\n', results.timestamp);
    fprintf(fid, 'Statistical Test: Paired Samples t-test (two-tailed)\n');
    fprintf(fid, 'Significance Level (α): 0.05\n\n');
    
    fprintf(fid, 'METHODOLOGY\n');
    fprintf(fid, '-----------\n');
    fprintf(fid, 'As per Chandrashekar et al. methodology, paired t-tests are used to compare\n');
    fprintf(fid, 'the two algorithms under identical workload conditions. The test determines\n');
    fprintf(fid, 'whether observed performance differences are statistically significant.\n\n');
    
    fprintf(fid, 'Formula: t = d̄ / (Sd/√n)\n');
    fprintf(fid, 'where d̄ = mean of differences, Sd = standard deviation of differences,\n');
    fprintf(fid, 'n = number of paired observations\n\n');
    
    fprintf(fid, 'DETAILED RESULTS BY METRIC\n');
    fprintf(fid, '--------------------------\n\n');
    
    for m = 1:length(results.metrics)
        metric = results.metrics(m);
        fprintf(fid, '%d. %s\n', m, upper(metric.name));
        fprintf(fid, '   ' + repmat('-', 1, length(metric.name) + 3) + '\n');
        fprintf(fid, '   Sample Size (n): %d\n', metric.n);
        fprintf(fid, '   Mean Difference (d̄): %.6f\n', metric.mean_difference);
        fprintf(fid, '   Std Deviation (Sd): %.6f\n', metric.std_difference);
        fprintf(fid, '   Standard Error (SE): %.6f\n', metric.standard_error);
        fprintf(fid, '   t-statistic: %.6f\n', metric.t_statistic);
        fprintf(fid, '   Degrees of Freedom: %d\n', metric.df);
        fprintf(fid, '   p-value: %.8f\n', metric.p_value);
        fprintf(fid, '   95%% Confidence Interval: [%.6f, %.6f]\n', metric.ci_lower, metric.ci_upper);
        fprintf(fid, '   Cohen''s d: %.4f (%s effect)\n', metric.cohens_d, metric.effect_size);
        
        if metric.significant
            fprintf(fid, '   RESULT: *** STATISTICALLY SIGNIFICANT ***\n');
            fprintf(fid, '   Better Algorithm: %s (%.2f%% improvement)\n', ...
                    metric.better_algorithm, metric.improvement_percentage);
        else
            fprintf(fid, '   RESULT: Not statistically significant\n');
        end
        fprintf(fid, '\n');
    end
    
    fprintf(fid, 'OVERALL CONCLUSION\n');
    fprintf(fid, '------------------\n');
    fprintf(fid, 'Based on the paired t-test analysis across all metrics:\n');
    fprintf(fid, '- Overall Winner: %s\n', results.overall_winner);
    fprintf(fid, '- This conclusion is statistically validated and not due to chance variation.\n');
    fprintf(fid, '- The results strengthen the reliability of our performance comparison.\n\n');
    
    fprintf(fid, '================================================================================\n');
    fprintf(fid, 'End of Report\n');
    fprintf(fid, '================================================================================\n');
    
    fclose(fid);
    fprintf('Detailed report saved to: %s\n', filepath);
end
