// Test component to understand how iteration results are displayed differently

// For Single Run:
const singleRunResult = {
  summary: {
    makespan: 18.5,
    energyConsumption: 0.0048,
    loadBalance: 0.85,
    resourceUtilization: 75.5,
    responseTime: 7.2,
    fitness: 0.29,
    totalCost: 0.16,
    costEfficiency: 6.48
  },
  vmUtilization: [...],
  schedulingLog: [...],
  energyConsumption: 0.0048
};

// For Iteration Run (3 iterations):
const iterationRunResult = {
  rawResults: {
    totalIterations: 3,
    algorithm: "EPSO",
    individualResults: [
      { summary: {...}, vmUtilization: [...], schedulingLog: [...] },
      { summary: {...}, vmUtilization: [...], schedulingLog: [...] },
      { summary: {...}, vmUtilization: [...], schedulingLog: [...] }
    ],
    averageMetrics: {
      makespan: 18.81575,
      energyConsumption: 0.004819883611111111,
      fitness: 0.2934400437488526,
      responseTime: 7.247479166666667,
      costEfficiency: 6.4830930846222445,
      totalCost: 0.16395558094444448,
      resourceUtilization: 28.949151644983942,
      loadBalance: 1.0
    },
    minMetrics: {
      makespan: 18.8155,
      energyConsumption: 0.0048198251111111114,
      // ... other min values
    },
    maxMetrics: {
      makespan: 18.816000000000003,
      energyConsumption: 0.0048199421111111114,
      // ... other max values
    },
    stdDevMetrics: {
      makespan: 0.00020412414523290616,
      energyConsumption: 4.7765049984271585E-08,
      // ... other std dev values
    },
    totalExecutionTime: 1702,
    successRate: 100.0,
    bestResult: { /* best individual result */ }
  },
  summary: { /* averageMetrics copied here */ },
  isIterationResult: true
};

// Key differences in display:

// 1. Data Source:
// - Single run: Uses result.summary directly
// - Iteration run: Uses result.rawResults.averageMetrics (via result.summary)

// 2. Additional Information (for iterations only):
// - Total iterations count
// - Success rate percentage
// - Total execution time
// - Statistical metrics (min, max, std deviation)
// - Individual results for each iteration
// - Best result selection

// 3. Visual Indicators:
// - The MetricCard component shows the same averaged values
// - No specific badge or indicator shows "This is from X iterations"
// - The statistical information (min/max/stddev) is available but not displayed in the current UI

// 4. What's displayed:
// - Both show the same metrics in MetricCard components
// - Both use the same visualization charts
// - The main difference is iteration results show AVERAGED values across all runs
