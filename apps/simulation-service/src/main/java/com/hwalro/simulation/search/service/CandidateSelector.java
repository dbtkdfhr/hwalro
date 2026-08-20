package com.hwalro.simulation.search.service;

import com.hwalro.simulation.search.domain.Metric;
import com.hwalro.simulation.search.domain.MetricDelta;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class CandidateSelector {
    public static final String TOTAL_EVACUATION_TIME_SECONDS = "TOTAL_EVACUATION_TIME_SECONDS";
    public static final String AVERAGE_EVACUATION_TIME_SECONDS = "AVERAGE_EVACUATION_TIME_SECONDS";
    public static final String EVACUATED_PEOPLE = "EVACUATED_PEOPLE";
    public static final String REMAINING_PEOPLE = "REMAINING_PEOPLE";
    public static final String MAX_DENSITY = "MAX_DENSITY";

    private CandidateSelector() {}

    public record Judgement(boolean improved, MetricDelta primaryDelta) {}

    public record RankableCandidate(
            long candidateId, List<Metric> trialMetrics, List<MetricDelta> deltas, int changeOpCount) {}

    public static Judgement judge(List<Metric> trialMetrics, List<Metric> baselineMetrics, double improvementMargin) {
        Double baselineTotal = metricValue(baselineMetrics, TOTAL_EVACUATION_TIME_SECONDS);
        if (baselineTotal != null) {
            Double measuredTotal = metricValue(trialMetrics, TOTAL_EVACUATION_TIME_SECONDS);
            MetricDelta primaryDelta =
                    measuredTotal == null ? null : delta(TOTAL_EVACUATION_TIME_SECONDS, baselineTotal, measuredTotal);
            boolean improved = measuredTotal != null && measuredTotal <= baselineTotal * (1.0 - improvementMargin);
            if (improved && densityWorsened(trialMetrics, baselineMetrics)) {
                improved = false;
            }
            return new Judgement(improved, primaryDelta);
        }
        Double baselineRemaining = metricValue(baselineMetrics, REMAINING_PEOPLE);
        Double measuredRemaining = metricValue(trialMetrics, REMAINING_PEOPLE);
        MetricDelta primaryDelta = baselineRemaining != null && measuredRemaining != null
                ? delta(REMAINING_PEOPLE, baselineRemaining, measuredRemaining)
                : null;
        boolean improved =
                measuredRemaining != null && baselineRemaining != null && measuredRemaining < baselineRemaining;
        if (improved && densityWorsened(trialMetrics, baselineMetrics)) {
            improved = false;
        }
        return new Judgement(improved, primaryDelta);
    }

    private static boolean densityWorsened(List<Metric> trialMetrics, List<Metric> baselineMetrics) {
        Double baselineDensity = metricValue(baselineMetrics, MAX_DENSITY);
        Double measuredDensity = metricValue(trialMetrics, MAX_DENSITY);
        if (baselineDensity == null || measuredDensity == null) {
            return false;
        }
        return measuredDensity > baselineDensity;
    }

    public static List<MetricDelta> deltas(List<Metric> trialMetrics, List<Metric> baselineMetrics) {
        List<MetricDelta> result = new ArrayList<>();
        for (Metric baseline : baselineMetrics) {
            Double measured = metricValue(trialMetrics, baseline.metricType());
            if (measured != null) {
                result.add(delta(baseline.metricType(), baseline.metricValue(), measured));
            }
        }
        return List.copyOf(result);
    }

    public static Comparator<RankableCandidate> rankingComparator(List<Metric> baselineMetrics) {
        List<String> priority =
                baselineMetrics.stream().anyMatch(metric -> TOTAL_EVACUATION_TIME_SECONDS.equals(metric.metricType()))
                        ? List.of(TOTAL_EVACUATION_TIME_SECONDS, MAX_DENSITY, AVERAGE_EVACUATION_TIME_SECONDS)
                        : List.of(REMAINING_PEOPLE, EVACUATED_PEOPLE, MAX_DENSITY);
        return (left, right) -> {
            for (String metricType : priority) {
                int comparison = Double.compare(deltaRatio(left, metricType), deltaRatio(right, metricType));
                if (comparison != 0) {
                    return comparison;
                }
            }
            return Integer.compare(left.changeOpCount(), right.changeOpCount());
        };
    }

    private static double deltaRatio(RankableCandidate candidate, String metricType) {
        return candidate.deltas().stream()
                .filter(delta -> metricType.equals(delta.metricType()))
                .findFirst()
                .map(MetricDelta::ratio)
                .orElse(0.0);
    }

    private static MetricDelta delta(String metricType, double baseline, double measured) {
        double difference = measured - baseline;
        double ratio = baseline == 0 ? 0.0 : difference / baseline;
        return new MetricDelta(metricType, baseline, measured, difference, ratio);
    }

    private static Double metricValue(List<Metric> metrics, String metricType) {
        return metrics.stream()
                .filter(metric -> metricType.equals(metric.metricType()))
                .map(Metric::metricValue)
                .findFirst()
                .orElse(null);
    }
}
