package com.hwalro.regulation.safetycheck.dto;

import java.util.List;

public record InspectionUpdateRequest(String status, String comment, List<ItemUpdate> items) {
    public record ItemUpdate(Long id, String result, String comment, Double markerX, Double markerY) {}
}
