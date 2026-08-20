package com.hwalro.simulation.drawing.dto;

import java.util.List;

public record DrawingListResponse(int totalCount, int page, int size, boolean hasNext, List<DrawingSummary> items) {}
