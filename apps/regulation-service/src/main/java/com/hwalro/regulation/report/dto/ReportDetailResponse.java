package com.hwalro.regulation.report.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;

@Schema(description = "보고서 상세 응답")
public record ReportDetailResponse(
        Long id,
        Long authorId,
        String title,
        ReportContent content,
        String status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        List<Long> simulationResultIds) {}
