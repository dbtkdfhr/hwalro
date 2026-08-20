package com.hwalro.regulation.safetycheck.dto;

import java.util.List;

public record ChecklistTemplateUpdateRequest(List<ItemInput> items) {
    public record ItemInput(String title, String criterion, String category) {}
}
