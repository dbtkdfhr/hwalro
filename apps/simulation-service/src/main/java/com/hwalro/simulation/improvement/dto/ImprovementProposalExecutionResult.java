package com.hwalro.simulation.improvement.dto;

/** 단일 개선안의 검증 시뮬레이션 생성 결과입니다. */
public record ImprovementProposalExecutionResult(
        Long proposalId, Long simulationId, String status, String errorMessage) {}
