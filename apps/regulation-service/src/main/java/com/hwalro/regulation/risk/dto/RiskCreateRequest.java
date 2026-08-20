package com.hwalro.regulation.risk.dto;

import java.util.List;

public record RiskCreateRequest(
        Long simulationResultId,
        Double startX,
        Double startY,
        Double endX,
        Double endY,
        String title,
        String description,
        String severity,
        String status,
        List<AttachedLawRef> attachedLaws) {}
