package com.hwalro.simulation.search.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.hwalro.simulation.search.domain.Metric;
import com.hwalro.simulation.search.domain.MetricDelta;
import java.util.List;
import org.junit.jupiter.api.Test;

class CandidateSelectorTest {
    private static final double MARGIN = 0.02;
    /** 시스템 공통 밀집도 안전 기준(density_threshold_settings)의 실제 설정값이다. */
    private static final double SAFETY_THRESHOLD = 3.0;

    private static List<Metric> metrics(double totalSeconds, double averageSeconds, double maxDensity) {
        return List.of(
                new Metric("TOTAL_EVACUATION_TIME_SECONDS", "seconds", totalSeconds),
                new Metric("AVERAGE_EVACUATION_TIME_SECONDS", "seconds", averageSeconds),
                new Metric("MAX_DENSITY", "PERSON_PER_M2", maxDensity));
    }

    @Test
    void a_candidate_that_only_shortens_the_average_still_counts_as_improved() {
        // 총 대피시간은 마지막 한 명이 나가는 시각이라, 출구를 모두 열고 인원을 고루 배치하면 가장 먼
        // 사람의 도보 시간이 값을 고정한다. 그 사람의 경로를 건드리지 않는 변경은 총 대피시간을 전혀
        // 바꾸지 못하면서 나머지 모두를 빠르게 만들 수 있다.
        List<Metric> baseline = metrics(59.6, 24.0, 4.0);
        List<Metric> trial = metrics(59.6, 22.0, 4.0);

        assertThat(CandidateSelector.judge(trial, baseline, MARGIN, SAFETY_THRESHOLD)
                        .improved())
                .isTrue();
    }

    @Test
    void a_candidate_that_only_shortens_the_total_still_counts_as_improved() {
        List<Metric> baseline = metrics(59.6, 24.0, 4.0);
        List<Metric> trial = metrics(55.0, 24.0, 4.0);

        assertThat(CandidateSelector.judge(trial, baseline, MARGIN, SAFETY_THRESHOLD)
                        .improved())
                .isTrue();
    }

    @Test
    void density_improvement_alone_does_not_count_as_evacuation_improvement() {
        // 탐색 4번 후보 19의 실측값이다. 총 대피시간은 소수점까지 그대로였고 평균은 0.79%만 줄었지만
        // 최대 밀집도가 4.0에서 3.0으로 내려갔다 - 그 후보가 겨냥한 BOTTLENECK 진단이 근거로 든 값이
        // 바로 그 밀집도(PEAK_DENSITY 4.0)였다.
        List<Metric> baseline = metrics(59.6, 24.0, 4.0);
        List<Metric> trial = metrics(59.6, 23.81, 3.0);

        assertThat(CandidateSelector.judge(trial, baseline, MARGIN, SAFETY_THRESHOLD)
                        .improved())
                .isFalse();
    }

    @Test
    void thinner_crowding_does_not_excuse_a_materially_slower_evacuation() {
        // 밀집도만 승인 사유로 두면 "덜 붐비지만 훨씬 느린" 배치가 통과한다. 사각지대 스윕에서
        // 실제로 그런 후보가 나왔다 - 밀집도 4.0→3.0인데 평균 대피시간은 늘었다.
        List<Metric> baseline = metrics(59.29, 22.92, 4.0);
        List<Metric> trial = metrics(70.0, 27.0, 3.0);

        assertThat(CandidateSelector.judge(trial, baseline, MARGIN, SAFETY_THRESHOLD)
                        .improved())
                .isFalse();
    }

    @Test
    void density_increase_does_not_block_average_evacuation_improvement() {
        List<Metric> baseline = metrics(59.6, 24.0, 4.0);
        List<Metric> trial = metrics(59.6, 22.0, 5.0);

        assertThat(CandidateSelector.judge(trial, baseline, MARGIN, SAFETY_THRESHOLD)
                        .improved())
                .isTrue();
    }

    @Test
    void no_metric_clearing_the_margin_is_still_not_an_improvement() {
        // 탐색 3번의 실측값이다. 총 대피시간 -1.48%, 평균 -0.97%로 둘 다 2%에 못 미쳤고 밀집도는
        // 그대로였다. 평균과 밀집도를 판정에 넣어도 이 탐색의 결과는 바뀌지 않는다 - 이 변경이 그
        // 사례를 통과시킨다고 오해하지 않도록 실제 숫자로 못박는다.
        List<Metric> baseline = metrics(59.6, 23.90747, 4.0);
        List<Metric> trial = metrics(58.72, 23.67508, 4.0);

        assertThat(CandidateSelector.judge(trial, baseline, MARGIN, SAFETY_THRESHOLD)
                        .improved())
                .isFalse();
    }

    @Test
    void a_zero_baseline_never_counts_as_improved() {
        List<Metric> baseline = metrics(0.0, 0.0, 0.0);
        List<Metric> trial = metrics(0.0, 0.0, 0.0);

        assertThat(CandidateSelector.judge(trial, baseline, MARGIN, SAFETY_THRESHOLD)
                        .improved())
                .isFalse();
    }

    @Test
    void a_much_faster_layout_is_accepted_regardless_of_peak_density() {
        // 탐색 2번 후보 1의 실측값이다. 총 대피시간 -29.4%, 평균 -39.6%로 크게 좋아졌지만 최대 밀집도가
        // 3.0에서 4.0으로 올랐다. 화면에는 이 +1이 "비상구 편중도 / 밀집도" 칸에 떠서 편중도가 나빠진
        // 것처럼 보였지만, 실제로는 밀집도이며 거부 사유도 밀집도다.
        List<Metric> baseline = metrics(53.35, 38.032947, 3.0);
        List<Metric> trial = metrics(37.68, 22.957474, 4.0);

        assertThat(CandidateSelector.judge(trial, baseline, MARGIN, SAFETY_THRESHOLD)
                        .improved())
                .isTrue();
    }

    @Test
    void a_faster_layout_that_leaves_crowding_untouched_is_an_improvement() {
        // 같은 탐색의 후보 2다. 밀집도가 그대로라 속도 개선이 그대로 인정된다.
        List<Metric> baseline = metrics(53.35, 38.032947, 3.0);
        List<Metric> trial = metrics(44.3, 25.037158, 3.0);

        assertThat(CandidateSelector.judge(trial, baseline, MARGIN, SAFETY_THRESHOLD)
                        .improved())
                .isTrue();
    }

    @Test
    void a_density_rise_that_stays_below_the_safety_threshold_does_not_block_a_faster_layout() {
        // 최대 밀집도는 1m 격자 한 칸이 가장 붐빈 순간의 값이라 정수로 튄다. 안전 기준(3.0) 아래에서의
        // 상승까지 거부하면 대피시간을 크게 줄인 배치가 노이즈 수준의 차이로 버려진다.
        List<Metric> baseline = metrics(59.6, 24.0, 1.0);
        List<Metric> trial = metrics(40.0, 16.0, 2.0);

        assertThat(CandidateSelector.judge(trial, baseline, MARGIN, SAFETY_THRESHOLD)
                        .improved())
                .isTrue();
    }

    @Test
    void density_threshold_crossing_does_not_block_evacuation_time_improvement() {
        List<Metric> baseline = metrics(59.6, 24.0, 3.0);
        List<Metric> trial = metrics(40.0, 16.0, 3.5);

        assertThat(CandidateSelector.judge(trial, baseline, MARGIN, SAFETY_THRESHOLD)
                        .improved())
                .isTrue();
    }

    @Test
    void density_reduction_without_evacuation_time_improvement_is_not_accepted() {
        // 기준을 넘은 상태에서 조금이라도 내려오는 것은 안전상 분명한 개선이다.
        List<Metric> baseline = metrics(59.6, 24.0, 5.0);
        List<Metric> trial = metrics(59.6, 24.0, 4.0);

        assertThat(CandidateSelector.judge(trial, baseline, MARGIN, SAFETY_THRESHOLD)
                        .improved())
                .isFalse();
    }

    @Test
    void selects_total_average_and_balanced_recommendations_without_duplicates() {
        List<CandidateSelector.RankableCandidate> candidates =
                List.of(rankable(1, -0.30, -0.03), rankable(2, -0.04, -0.35), rankable(3, -0.20, -0.18));

        assertThat(CandidateSelector.selectRecommendations(candidates, MARGIN))
                .containsEntry(1L, List.of("TOTAL_TIME"))
                .containsEntry(2L, List.of("AVERAGE_TIME"))
                .containsEntry(3L, List.of("BALANCED"));
    }

    @Test
    void one_candidate_can_hold_multiple_recommendation_types() {
        List<CandidateSelector.RankableCandidate> candidates = List.of(rankable(1, -0.30, -0.35));

        assertThat(CandidateSelector.selectRecommendations(candidates, MARGIN))
                .containsEntry(1L, List.of("TOTAL_TIME", "AVERAGE_TIME", "BALANCED"));
    }

    private static CandidateSelector.RankableCandidate rankable(long id, double totalRatio, double averageRatio) {
        return new CandidateSelector.RankableCandidate(
                id,
                List.of(),
                List.of(
                        new MetricDelta("TOTAL_EVACUATION_TIME_SECONDS", 100, 100 * (1 + totalRatio), 0, totalRatio),
                        new MetricDelta(
                                "AVERAGE_EVACUATION_TIME_SECONDS", 100, 100 * (1 + averageRatio), 0, averageRatio)),
                1);
    }
}
