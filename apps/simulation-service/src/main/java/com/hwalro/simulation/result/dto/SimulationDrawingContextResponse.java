package com.hwalro.simulation.result.dto;

public record SimulationDrawingContextResponse(
        Long simulationResultId,
        Long simulationId,
        String layoutTitle,
        String title,
        SimulationResultDetailResponse.Drawing drawing) {}
