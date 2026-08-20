package com.hwalro.regulation.report.dto;

import java.util.List;

public record AiReportDraftCreateRequest(Long sourceSimulationResultId, List<Long> comparisonSimulationResultIds) {}
