package com.hwalro.simulation.improvement.dto;

import java.util.List;

/** 개선안별 검증 시뮬레이션 생성 요청 결과입니다. */
public record ImprovementProposalExecutionResponse(List<ImprovementProposalExecutionResult> results) {}
