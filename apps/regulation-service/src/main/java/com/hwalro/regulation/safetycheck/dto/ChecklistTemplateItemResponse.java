package com.hwalro.regulation.safetycheck.dto;

public record ChecklistTemplateItemResponse(
        Long id, String title, String criterion, String category, int displayOrder) {}
