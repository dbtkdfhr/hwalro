package com.hwalro.simulation.improvement.domain;

/** 후보 배치에서 측정한 비공식 개선 근거입니다. */
public record ProposalEvaluation(
        ProposalCandidate candidate,
        double bottleneckWidthIncrease,
        double exitPathWidthIncrease,
        double heatmapOverlapDecrease,
        // 기존에 비어 있던 주변 통로가 좁아지는 후보는 우선순위를 낮춥니다.
        double nearbyClearanceLoss) {}
