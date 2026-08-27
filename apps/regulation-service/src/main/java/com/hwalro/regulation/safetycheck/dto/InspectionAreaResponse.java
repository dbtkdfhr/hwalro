package com.hwalro.regulation.safetycheck.dto;

import java.time.LocalDateTime;

public record InspectionAreaResponse(
        Long id,
        String name,
        String description,
        Long layoutId,
        String layoutTitle,
        boolean active,
        int inspectionCount,
        LocalDateTime lastInspectedAt,
        boolean hasActiveTemplate) {}
