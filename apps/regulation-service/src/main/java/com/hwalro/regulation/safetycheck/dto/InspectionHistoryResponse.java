package com.hwalro.regulation.safetycheck.dto;

import java.time.LocalDateTime;

public record InspectionHistoryResponse(
        Long id,
        Long inspectorId,
        String status,
        int completedItemCount,
        int totalItemCount,
        int failCount,
        int reviewRequiredCount,
        LocalDateTime createdAt) {}
