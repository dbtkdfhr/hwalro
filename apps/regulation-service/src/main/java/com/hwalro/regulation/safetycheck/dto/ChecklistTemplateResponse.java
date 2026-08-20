package com.hwalro.regulation.safetycheck.dto;

import java.util.List;

public record ChecklistTemplateResponse(Long id, int version, List<ChecklistTemplateItemResponse> items) {}
