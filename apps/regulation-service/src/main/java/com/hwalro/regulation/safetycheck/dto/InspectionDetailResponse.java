package com.hwalro.regulation.safetycheck.dto;

import java.time.LocalDateTime;
import java.util.List;

public record InspectionDetailResponse(
        Long id,
        Long inspectionAreaId,
        String areaName,
        Long simulationResultId,
        Long inspectorId,
        String status,
        String comment,
        LocalDateTime updatedAt,
        LocalDateTime completedAt,
        List<InspectionItemResponse> items) {}
