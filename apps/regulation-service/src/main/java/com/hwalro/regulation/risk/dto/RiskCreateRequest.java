package com.hwalro.regulation.risk.dto;

import java.util.List;

public record RiskCreateRequest(
        Long layoutId,
        Long layoutVersionId,
        Double startX,
        Double startY,
        Double endX,
        Double endY,
        String title,
        String description,
        String severity,
        String status,
        List<AttachedLawRef> attachedLaws) {}
