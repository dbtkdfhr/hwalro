package com.hwalro.simulation.improvement.service;

import com.hwalro.simulation.improvement.domain.ProposalEvaluation;

/** JuPedSim 실행 전 후보 순위를 정하는 간이 점수 계산기입니다. */
public final class ProposalScorer {
    private static final double BOTTLENECK_WIDTH_WEIGHT = 40;
    private static final double EXIT_PATH_WIDTH_WEIGHT = 25;
    private static final double HEATMAP_OVERLAP_WEIGHT = 20;
    private static final double MOVE_DISTANCE_PENALTY = 10;
    private static final double CHANGE_COUNT_PENALTY = 15;
    private static final double NEARBY_CLEARANCE_LOSS_PENALTY = 40;

    /**
     * 공식 안전 지표를 추정하지 않고, 배치 변경의 상대적 우선순위만 계산합니다.
     */
    public double score(ProposalEvaluation evaluation) {
        return evaluation.bottleneckWidthIncrease() * BOTTLENECK_WIDTH_WEIGHT
                + evaluation.exitPathWidthIncrease() * EXIT_PATH_WIDTH_WEIGHT
                + evaluation.heatmapOverlapDecrease() * HEATMAP_OVERLAP_WEIGHT
                - evaluation.candidate().totalMoveDistance() * MOVE_DISTANCE_PENALTY
                - evaluation.candidate().changes().size() * CHANGE_COUNT_PENALTY
                // 병목을 넓혀도 주변 통로를 막는 후보는 상위 검증 대상으로 올리지 않습니다.
                - evaluation.nearbyClearanceLoss() * NEARBY_CLEARANCE_LOSS_PENALTY;
    }
}
