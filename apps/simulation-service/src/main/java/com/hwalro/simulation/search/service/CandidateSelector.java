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
    public static final String EXIT_IMBALANCE = "EXIT_IMBALANCE";

    private CandidateSelector() {}

    public record Judgement(boolean improved, MetricDelta primaryDelta) {}

    public record RankableCandidate(
            long candidateId, List<Metric> trialMetrics, List<MetricDelta> deltas, int changeOpCount) {}

    /**
     * 후보가 개선인지 판정한다.
     *
     * <p>총 대피시간 하나만 보면 놓치는 개선이 있다. 총 대피시간은 마지막 한 명이 나가는 시각이라 가장
     * 먼 사람의 도보 시간이 값을 고정하고, 그 경로를 건드리지 않는 변경은 나머지 전원을 빠르게 만들어도
     * 총합을 1초도 못 줄인다. 최대 밀집도만 낮추는 변경도 마찬가지다 - BOTTLENECK 진단이 근거로 드는
     * 값이 바로 그 밀집도다.
     *
     * <p>그래서 총 대피시간·평균 대피시간·최대 밀집도 중 <b>하나라도</b> 여유치를 넘어 좋아지면 개선으로
     * 본다. 다만 다른 지표가 여유치를 넘어 나빠지면 개선이 아니다 - "덜 붐비지만 훨씬 느린" 배치가
     * 통과하면 안 된다.
     *
     * <p>최대 밀집도는 비율이 아니라 시스템 공통 안전 기준으로 판단한다. 이 값은 1m 격자 한 칸이 가장
     * 붐빈 순간의 값이라 정수로 튀고, 상대 비교만 하면 안전 범위 안의 사소한 상승(1.0→1.2)이 대피시간
     * 40% 개선을 통째로 버린다. 반대로 기준을 넘어선 상승은 아무리 빨라져도 받아들이면 안 된다.
     */
    public static Judgement judge(
            List<Metric> trialMetrics,
            List<Metric> baselineMetrics,
            double improvementMargin,
            double densitySafetyThreshold) {
        return judge(trialMetrics, baselineMetrics, improvementMargin, densitySafetyThreshold, false);
    }

    /**
     * requireExitBalanceImprovement가 true면 출구 편중 진단(비상구 편중)을 근거로 만들어진 후보다.
     * 그런 후보는 편중도 자체가 여유치만큼 좋아졌을 때만 개선이다 - 대피 시간이 줄어도 편중이 그대면
     * 이 후보가 노린 문제는 해결되지 않았다. false면 기존 판정과 같다.
     */
    public static Judgement judge(
            List<Metric> trialMetrics,
            List<Metric> baselineMetrics,
            double improvementMargin,
            double densitySafetyThreshold,
            boolean requireExitBalanceImprovement) {
        Double baselineTotal = metricValue(baselineMetrics, TOTAL_EVACUATION_TIME_SECONDS);
        if (baselineTotal == null) {
            return judgeByRemainingPeople(trialMetrics, baselineMetrics, densitySafetyThreshold);
        }
        Double measuredTotal = metricValue(trialMetrics, TOTAL_EVACUATION_TIME_SECONDS);
        MetricDelta primaryDelta =
                measuredTotal == null ? null : delta(TOTAL_EVACUATION_TIME_SECONDS, baselineTotal, measuredTotal);

        boolean improved = requireExitBalanceImprovement
                ? clearsMargin(trialMetrics, baselineMetrics, EXIT_IMBALANCE, improvementMargin)
                : clearsMargin(trialMetrics, baselineMetrics, TOTAL_EVACUATION_TIME_SECONDS, improvementMargin)
                        || clearsMargin(
                                trialMetrics, baselineMetrics, AVERAGE_EVACUATION_TIME_SECONDS, improvementMargin)
                        || clearsMargin(trialMetrics, baselineMetrics, MAX_DENSITY, improvementMargin);
        if (improved && worsenedBeyondMargin(trialMetrics, baselineMetrics, improvementMargin)) {
            improved = false;
        }
        if (improved && densityBecameUnsafe(trialMetrics, baselineMetrics, densitySafetyThreshold)) {
            improved = false;
        }
        return new Judgement(improved, primaryDelta);
    }

    private static Judgement judgeByRemainingPeople(
            List<Metric> trialMetrics, List<Metric> baselineMetrics, double densitySafetyThreshold) {
        Double baselineRemaining = metricValue(baselineMetrics, REMAINING_PEOPLE);
        Double measuredRemaining = metricValue(trialMetrics, REMAINING_PEOPLE);
        MetricDelta primaryDelta = baselineRemaining != null && measuredRemaining != null
                ? delta(REMAINING_PEOPLE, baselineRemaining, measuredRemaining)
                : null;
        boolean improved =
                measuredRemaining != null && baselineRemaining != null && measuredRemaining < baselineRemaining;
        if (improved && densityBecameUnsafe(trialMetrics, baselineMetrics, densitySafetyThreshold)) {
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

    /**
     * 최대 밀집도가 안전 기준을 넘긴 채 나빠졌는지. 더 빨라졌더라도 기준 위에서 더 빽빽해진 배치는
     * 개선이 아니다. 기준 아래에 머무는 상승은 안전 여유 안이므로 다른 지표의 개선을 막지 않는다.
     */
    private static boolean densityBecameUnsafe(
            List<Metric> trialMetrics, List<Metric> baselineMetrics, double safetyThreshold) {
        Double baselineDensity = metricValue(baselineMetrics, MAX_DENSITY);
        Double measuredDensity = metricValue(trialMetrics, MAX_DENSITY);
        if (baselineDensity == null || measuredDensity == null) {
            return false;
        }
        return measuredDensity > baselineDensity && measuredDensity > safetyThreshold;
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
