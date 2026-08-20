package com.hwalro.simulation.improvement.dto;

import java.util.List;

/** 선택한 개선안의 검증 시뮬레이션 생성을 요청합니다. */
public record ImprovementProposalExecutionRequest(List<Long> proposalIds) {}
