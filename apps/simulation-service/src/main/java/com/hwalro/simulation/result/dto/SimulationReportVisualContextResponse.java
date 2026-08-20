package com.hwalro.simulation.result.dto;

import java.util.List;

public record SimulationReportVisualContextResponse(
        Long simulationResultId,
        Long simulationId,
        String layoutTitle,
        SimulationResultDetailResponse.Drawing drawing,
        List<SimulationResultDetailResponse.Bottleneck> bottlenecks) {}
