package com.hwalro.simulation.search.service;

import com.hwalro.simulation.search.domain.Metric;
import com.hwalro.simulation.search.domain.MetricDelta;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class CandidateSelector {
    public static final String TOTAL_EVACUATION_TIME_SECONDS = "TOTAL_EVACUATION_TIME_SECONDS";
    public static final String AVERAGE_EVACUATION_TIME_SECONDS = "AVERAGE_EVACUATION_TIME_SECONDS";
    public static final String EVACUATED_PEOPLE = "EVACUATED_PEOPLE";
    public static final String REMAINING_PEOPLE = "REMAINING_PEOPLE";
    public static final String MAX_DENSITY = "MAX_DENSITY";
    public static final String EXIT_IMBALANCE = "EXIT_IMBALANCE";

    private CandidateSelector() {}

    public record Judgement(boolean improved, MetricDelta primaryDelta) {}

    public record RankableCandidate(
            long candidateId, List<Metric> trialMetrics, List<MetricDelta> deltas, int changeOpCount) {}

    public static Judgement judge(
            List<Metric> trialMetrics,
            List<Metric> baselineMetrics,
            double improvementMargin,
            double densitySafetyThreshold) {
        return judge(trialMetrics, baselineMetrics, improvementMargin, densitySafetyThreshold, false);
    }

    public static Judgement judge(
            List<Metric> trialMetrics,
            List<Metric> baselineMetrics,
            double improvementMargin,
            double densitySafetyThreshold,
            boolean requireExitBalanceImprovement) {
        Double baselineTotal = metricValue(baselineMetrics, TOTAL_EVACUATION_TIME_SECONDS);
        Double baselineAverage = metricValue(baselineMetrics, AVERAGE_EVACUATION_TIME_SECONDS);
        if (baselineTotal == null || baselineAverage == null) {
            return new Judgement(false, null);
        }
        Double measuredTotal = metricValue(trialMetrics, TOTAL_EVACUATION_TIME_SECONDS);
        MetricDelta primaryDelta =
                measuredTotal == null ? null : delta(TOTAL_EVACUATION_TIME_SECONDS, baselineTotal, measuredTotal);

        boolean improved = clearsMargin(trialMetrics, baselineMetrics, TOTAL_EVACUATION_TIME_SECONDS, improvementMargin)
                || clearsMargin(trialMetrics, baselineMetrics, AVERAGE_EVACUATION_TIME_SECONDS, improvementMargin);
        if (improved && worsenedBeyondMargin(trialMetrics, baselineMetrics, improvementMargin)) {
            improved = false;
        }
        return new Judgement(improved, primaryDelta);
    }

    /** 값이 작을수록 좋은 지표들이다. 기준값이 0이면 줄일 여지가 없으므로 개선으로 세지 않는다. */
    private static boolean clearsMargin(
            List<Metric> trialMetrics, List<Metric> baselineMetrics, String metricType, double margin) {
        Double baseline = metricValue(baselineMetrics, metricType);
        Double measured = metricValue(trialMetrics, metricType);
        if (baseline == null || measured == null || baseline <= 0) {
            return false;
        }
        return measured <= baseline * (1.0 - margin);
    }

    /** 하나가 좋아져도 다른 하나가 여유치를 넘어 나빠졌다면 맞바꾼 것이지 개선이 아니다. */
    private static boolean worsenedBeyondMargin(
            List<Metric> trialMetrics, List<Metric> baselineMetrics, double margin) {
        for (String metricType : List.of(TOTAL_EVACUATION_TIME_SECONDS, AVERAGE_EVACUATION_TIME_SECONDS)) {
            Double baseline = metricValue(baselineMetrics, metricType);
            Double measured = metricValue(trialMetrics, metricType);
            if (baseline == null || measured == null || baseline <= 0) {
                continue;
            }
            if (measured >= baseline * (1.0 + margin)) {
                return true;
            }
        }
        return false;
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
                        ? List.of(TOTAL_EVACUATION_TIME_SECONDS, AVERAGE_EVACUATION_TIME_SECONDS)
                        : List.of(REMAINING_PEOPLE, EVACUATED_PEOPLE);
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

    public static Map<Long, List<String>> selectRecommendations(
            List<RankableCandidate> candidates, double improvementMargin) {
        Map<Long, List<String>> selected = new LinkedHashMap<>();
        candidates.stream()
                .filter(candidate -> improvement(candidate, TOTAL_EVACUATION_TIME_SECONDS) >= improvementMargin)
                .filter(candidate -> improvement(candidate, AVERAGE_EVACUATION_TIME_SECONDS) >= -improvementMargin)
                .max(Comparator.comparingDouble(candidate -> improvement(candidate, TOTAL_EVACUATION_TIME_SECONDS)))
                .ifPresent(candidate -> addRecommendation(selected, candidate.candidateId(), "TOTAL_TIME"));
        candidates.stream()
                .filter(candidate -> improvement(candidate, AVERAGE_EVACUATION_TIME_SECONDS) >= improvementMargin)
                .filter(candidate -> improvement(candidate, TOTAL_EVACUATION_TIME_SECONDS) >= -improvementMargin)
                .max(Comparator.comparingDouble(candidate -> improvement(candidate, AVERAGE_EVACUATION_TIME_SECONDS)))
                .ifPresent(candidate -> addRecommendation(selected, candidate.candidateId(), "AVERAGE_TIME"));
        candidates.stream()
                .filter(candidate -> improvement(candidate, TOTAL_EVACUATION_TIME_SECONDS) >= improvementMargin)
                .filter(candidate -> improvement(candidate, AVERAGE_EVACUATION_TIME_SECONDS) >= improvementMargin)
                .max(Comparator.comparingDouble(candidate -> Math.min(
                        improvement(candidate, TOTAL_EVACUATION_TIME_SECONDS),
                        improvement(candidate, AVERAGE_EVACUATION_TIME_SECONDS))))
                .ifPresent(candidate -> addRecommendation(selected, candidate.candidateId(), "BALANCED"));
        Map<Long, List<String>> immutable = new LinkedHashMap<>();
        selected.forEach((candidateId, types) -> immutable.put(candidateId, List.copyOf(types)));
        return java.util.Collections.unmodifiableMap(immutable);
    }

    private static void addRecommendation(Map<Long, List<String>> selected, long candidateId, String type) {
        selected.computeIfAbsent(candidateId, ignored -> new ArrayList<>()).add(type);
    }

    private static double improvement(RankableCandidate candidate, String metricType) {
        return candidate.deltas().stream()
                .filter(delta -> metricType.equals(delta.metricType()))
                .findFirst()
                .map(delta -> -delta.ratio())
                .orElse(Double.NEGATIVE_INFINITY);
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
