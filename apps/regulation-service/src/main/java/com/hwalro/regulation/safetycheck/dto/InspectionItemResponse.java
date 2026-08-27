package com.hwalro.regulation.safetycheck.dto;

public record InspectionItemResponse(
        Long id,
        String title,
        String criterion,
        String category,
        int displayOrder,
        String result,
        String comment,
        Double markerX,
        Double markerY) {}
