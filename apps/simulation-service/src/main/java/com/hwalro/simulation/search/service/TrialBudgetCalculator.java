package com.hwalro.simulation.search.service;

import com.hwalro.simulation.search.domain.Metric;
import java.util.List;

public final class TrialBudgetCalculator {
    public static final double GLOBAL_MAX_SIMULATION_TIME_SECONDS = 600.0;
    public static final String TOTAL_EVACUATION_TIME_SECONDS = "TOTAL_EVACUATION_TIME_SECONDS";
    public static final String SIMULATION_DURATION_SECONDS = "SIMULATION_DURATION_SECONDS";

    private TrialBudgetCalculator() {}

    public static double trialCapSeconds(List<Metric> baselineMetrics, double abortMargin) {
        Double totalEvacuation = metricValue(baselineMetrics, TOTAL_EVACUATION_TIME_SECONDS);
        double baselineDuration = metricValueOrZero(baselineMetrics, SIMULATION_DURATION_SECONDS);
        if (totalEvacuation != null) {
            return clamp(totalEvacuation * (1.0 + abortMargin), 0.0001, GLOBAL_MAX_SIMULATION_TIME_SECONDS);
        }
        if (baselineDuration > 0) {
            return clamp(baselineDuration, 0.0001, GLOBAL_MAX_SIMULATION_TIME_SECONDS);
        }
        return GLOBAL_MAX_SIMULATION_TIME_SECONDS;
    }

    public static long estimatedStudySeconds(
            int trials, int trialConcurrency, double baselineWallClockSeconds, double abortMargin) {
        if (trials < 1 || trialConcurrency < 1 || baselineWallClockSeconds <= 0) {
            return 0;
        }
        double batches = Math.ceil((double) trials / trialConcurrency);
        return Math.round(batches * baselineWallClockSeconds * (1.0 + abortMargin));
    }

    private static Double metricValue(List<Metric> metrics, String metricType) {
        return metrics.stream()
                .filter(metric -> metricType.equals(metric.metricType()))
                .map(Metric::metricValue)
                .findFirst()
                .orElse(null);
    }

    private static double metricValueOrZero(List<Metric> metrics, String metricType) {
        Double value = metricValue(metrics, metricType);
        return value == null ? 0.0 : value;
    }

    private static double clamp(double value, double minimum, double maximum) {
        return Math.max(minimum, Math.min(maximum, value));
    }
}
