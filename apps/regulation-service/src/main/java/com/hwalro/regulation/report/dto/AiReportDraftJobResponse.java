package com.hwalro.regulation.report.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "AI 보고서 초안 생성 작업 응답")
public record AiReportDraftJobResponse(
        @Schema(description = "보고서 ID") Long id, @Schema(description = "생성 상태") String status) {}
