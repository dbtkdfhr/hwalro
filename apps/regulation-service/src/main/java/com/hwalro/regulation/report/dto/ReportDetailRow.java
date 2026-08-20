package com.hwalro.regulation.report.dto;

import java.time.LocalDateTime;

public record ReportDetailRow(
        Long id,
        Long authorId,
        String title,
        String content,
        String status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {}
