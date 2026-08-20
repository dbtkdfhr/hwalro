package com.hwalro.simulation.search.domain;

import com.hwalro.simulation.simulation.dto.SimulationDtos.HazardZoneDto;
import com.hwalro.simulation.simulation.dto.SimulationDtos.PointDto;
import java.math.BigDecimal;
import java.util.List;

public record SearchSource(
        long simulationId,
        DrawingInput drawing,
        List<PointDto> agents,
        List<HazardZoneDto> hazards,
        List<Long> selectedExitIds,
        BigDecimal densityThreshold) {

    public SearchSource {
        agents = List.copyOf(agents);
        hazards = List.copyOf(hazards);
        selectedExitIds = List.copyOf(selectedExitIds);
    }

    public record DrawingInput(
            Long layoutId,
            String title,
            BigDecimal width,
            BigDecimal height,
            List<PointDto> outsideBoundary,
            List<SegmentInput> walls,
            List<RectangleInput> pillars,
            List<RectangleInput> fabrics,
            List<TextInput> layoutTexts,
            List<ExitInput> exits) {}

    public record SegmentInput(
            Long id, String name, BigDecimal startX, BigDecimal startY, BigDecimal endX, BigDecimal endY) {}

    public record RectangleInput(
            Long id,
            String name,
            BigDecimal startX,
            BigDecimal startY,
            BigDecimal endX,
            BigDecimal endY,
            BigDecimal rotation) {}

    public record TextInput(Long id, String text, BigDecimal x, BigDecimal y) {}

    public record ExitInput(
            Long id, String name, BigDecimal startX, BigDecimal startY, BigDecimal endX, BigDecimal endY) {}
}
