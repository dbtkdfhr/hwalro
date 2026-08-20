package com.hwalro.simulation.result.dto;

import java.util.List;

public record ComparableSimulationPageResponse(
        int totalCount, int page, int size, boolean hasNext, List<ComparableSimulation> items) {

    public record ComparableSimulation(Long id, Long simulationResultId, String name, double totalEvacuationTime) {}
}
