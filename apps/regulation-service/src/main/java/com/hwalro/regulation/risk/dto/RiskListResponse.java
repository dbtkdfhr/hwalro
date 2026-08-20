package com.hwalro.regulation.risk.dto;

import java.util.List;

public record RiskListResponse(int totalCount, int page, int size, boolean hasNext, List<RiskResponse> items) {}
