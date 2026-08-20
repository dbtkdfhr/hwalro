package com.hwalro.regulation.report.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/** 보고서 목록의 페이지 정보와 항목을 함께 반환한다. */
@Schema(description = "보고서 목록 페이지 응답")
public record ReportListResponse(
        @Schema(description = "필터링된 전체 보고서 수", example = "12") int totalCount,
        @Schema(description = "현재 페이지 번호", example = "1") int page,
        @Schema(description = "페이지당 조회 건수", example = "5") int size,
        @Schema(description = "다음 페이지 존재 여부", example = "true") boolean hasNext,
        @Schema(description = "보고서 목록") List<ReportListItem> items) {}
