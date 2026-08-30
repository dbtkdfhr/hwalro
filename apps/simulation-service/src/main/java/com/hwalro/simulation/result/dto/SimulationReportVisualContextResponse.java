package com.hwalro.simulation.result.dto;

import java.util.List;

public record SimulationReportVisualContextResponse(
        Long simulationResultId,
        Long simulationId,
        Long layoutId,
        Long layoutVersionId,
        String layoutTitle,
        SimulationResultDetailResponse.Drawing drawing,
        List<SimulationResultDetailResponse.Bottleneck> bottlenecks) {}
