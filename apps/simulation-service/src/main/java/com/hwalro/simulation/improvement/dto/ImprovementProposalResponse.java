package com.hwalro.simulation.improvement.dto;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDateTime;

/** 개선안 목록 API가 반환하는 저장된 제안과 구조화된 변경 데이터입니다. */
public record ImprovementProposalResponse(
        Long id,
        Long savedLayoutVersionId,
        Integer proposalOrder,
        String proposalType,
        String title,
        String description,
        JsonNode changeData,
        JsonNode changeSummary,
        LocalDateTime createdAt,
        LocalDateTime savedAt) {}
