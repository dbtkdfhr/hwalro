package com.hwalro.simulation.drawing.dto;

public record DrawingCreateRequest(String title, String description, Boolean withDefaultData) {

    public boolean hasDefaultData() {
        return withDefaultData == null || withDefaultData;
    }
}
