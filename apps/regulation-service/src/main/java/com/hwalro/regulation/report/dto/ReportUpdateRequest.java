package com.hwalro.regulation.report.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record ReportUpdateRequest(
        @Schema(description = "보고서 제목") String title,
        @Schema(description = "보고서 본문 구역") ReportContent content,
        @Schema(description = "변경 상태. 작성 중 또는 완료만 허용", example = "완료") String status) {}
