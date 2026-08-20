package com.hwalro.regulation.safetycheck.dto;

import java.time.LocalDateTime;

public record InspectionDetailHeader(
        Long id,
        Long inspectionAreaId,
        String areaName,
        Long simulationResultId,
        Long inspectorId,
        String status,
        String comment,
        LocalDateTime updatedAt,
        LocalDateTime completedAt) {}
