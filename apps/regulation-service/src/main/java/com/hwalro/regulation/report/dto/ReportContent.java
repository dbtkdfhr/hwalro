package com.hwalro.regulation.report.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "보고서 본문 구역")
public record ReportContent(
        @Schema(description = "검토 개요") String overview,
        @Schema(description = "핵심 분석 결과") String analysis,
        @Schema(description = "개선 조치") String improvements) {}
