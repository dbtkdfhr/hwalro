package com.hwalro.regulation.risk.dto;

import java.time.LocalDateTime;
import java.util.List;

public record RiskResponse(
        Long id,
        Long layoutId,
        Long layoutVersionId,
        Long assigneeId,
        String assigneeName,
        String title,
        String description,
        Double startX,
        Double startY,
        Double endX,
        Double endY,
        String severity,
        String status,
        LocalDateTime createdAt,
        List<AttachedLawRef> attachedLaws,
        String layoutTitle) {}
