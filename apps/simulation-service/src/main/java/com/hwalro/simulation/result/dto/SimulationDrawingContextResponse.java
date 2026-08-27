package com.hwalro.simulation.result.dto;

public record SimulationDrawingContextResponse(
        Long simulationResultId,
        Long simulationId,
        Long layoutId,
        Long layoutVersionId,
        String layoutTitle,
        String title,
        SimulationResultDetailResponse.Drawing drawing) {}
