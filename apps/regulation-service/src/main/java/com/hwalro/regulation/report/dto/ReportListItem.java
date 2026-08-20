package com.hwalro.regulation.report.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

/** 보고서 목록 화면에 표시하는 최소 정보다. */
@Schema(description = "보고서 목록 항목")
public record ReportListItem(
        @Schema(description = "보고서 ID", example = "1") Long id,
        @Schema(description = "작성자 사용자 ID", example = "1") Long authorId,
        @Schema(description = "작성자 이름", example = "김안전") String authorName,
        @Schema(description = "보고서 제목", example = "2026 여름 팝업스토어 안전 검토 보고서") String title,
        @Schema(description = "보고서 상태", example = "완료") String status,
        @Schema(description = "최근 수정 일시", example = "2026-08-03T14:20:00") LocalDateTime updatedAt) {
    public ReportListItem withAuthorName(String name) {
        return new ReportListItem(id, authorId, name, title, status, updatedAt);
    }
}
