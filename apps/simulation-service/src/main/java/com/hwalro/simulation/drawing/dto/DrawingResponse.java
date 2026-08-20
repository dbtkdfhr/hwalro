package com.hwalro.simulation.drawing.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record DrawingResponse(
        Long id,
        String title,
        String description,
        Long createdBy,
        LocalDateTime createdAt,
        BigDecimal width,
        BigDecimal height,
        List<WallDto> walls,
        List<OutsideWallDto> outsideWalls,
        List<PillarDto> pillars,
        List<FabricDto> fabrics,
        List<LayoutTextDto> layoutTexts,
        List<ExitDto> exits,
        Integer version,
        Long layoutVersionId,
        Integer layoutVersionNumber,
        String layoutVersionStatus) {}
