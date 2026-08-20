package com.hwalro.simulation.improvement.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hwalro.simulation.drawing.domain.Fabric;
import com.hwalro.simulation.drawing.domain.FloorPlan;
import com.hwalro.simulation.drawing.domain.Layout;
import com.hwalro.simulation.drawing.domain.LayoutExit;
import com.hwalro.simulation.drawing.domain.LayoutVersion;
import com.hwalro.simulation.drawing.domain.OutsideWall;
import com.hwalro.simulation.drawing.domain.Pillar;
import com.hwalro.simulation.drawing.domain.Wall;
import com.hwalro.simulation.drawing.mapper.DrawingMapper;
import com.hwalro.simulation.improvement.domain.BottleneckArea;
import com.hwalro.simulation.improvement.domain.FabricState;
import com.hwalro.simulation.improvement.domain.ImprovementSource;
import com.hwalro.simulation.improvement.domain.StoredBottleneck;
import com.hwalro.simulation.improvement.geometry.RotatedRectangle;
import com.hwalro.simulation.improvement.mapper.ImprovementSourceMapper;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * 원본 simulation ID를 후보 탐색 엔진의 입력으로 변환합니다.
 *
 * <p>도면 데이터는 기존 DrawingMapper를 재사용하고, 결과 데이터만 개선안 전용 Mapper로 읽습니다.
 */
@Service
public class ImprovementSourceLoader {
    private final DrawingMapper drawingMapper;
    private final ImprovementSourceMapper improvementSourceMapper;
    private final ObjectMapper objectMapper;

    public ImprovementSourceLoader(
            DrawingMapper drawingMapper, ImprovementSourceMapper improvementSourceMapper, ObjectMapper objectMapper) {
        this.drawingMapper = drawingMapper;
        this.improvementSourceMapper = improvementSourceMapper;
        this.objectMapper = objectMapper;
    }

    /** 원본 simulation의 도면·병목·히트맵을 하나의 불변 입력으로 읽습니다. */
    public ImprovementSource load(long simulationId) {
        Long layoutVersionId = improvementSourceMapper.findLayoutVersionIdBySimulationId(simulationId);
        LayoutVersion layoutVersion =
                require(layoutVersionId == null ? null : drawingMapper.findLayoutVersionById(layoutVersionId));
        Layout layout = require(drawingMapper.findLayoutById(layoutVersion.getLayoutId()));
        FloorPlan floorPlan = require(drawingMapper.findFloorPlanById(layout.getFloorPlanId()));

        List<FabricState> fabrics = drawingMapper.findFabricsByVersionId(layoutVersionId).stream()
                .map(fabric -> new FabricState(fabric.getId(), bounds(fabric)))
                .toList();
        List<RotatedRectangle> exits = drawingMapper.findLayoutExitsByVersionId(layoutVersionId).stream()
                .map(this::bounds)
                .toList();
        List<RotatedRectangle> fixedObstacles = List.of(
                        drawingMapper.findWallsByVersionId(layoutVersionId).stream()
                                .map(this::bounds),
                        drawingMapper.findPillarsByVersionId(layoutVersionId).stream()
                                .map(this::bounds),
                        drawingMapper.findOutsideWallsByVersionId(layoutVersionId).stream()
                                .map(this::bounds),
                        exits.stream())
                .stream()
                .flatMap(stream -> stream)
                .toList();

        return new ImprovementSource(
                simulationId,
                floorPlan.getWidth().doubleValue(),
                floorPlan.getHeight().doubleValue(),
                fabrics,
                fixedObstacles,
                exits,
                improvementSourceMapper.findBottlenecksBySimulationId(simulationId).stream()
                        .map(this::toBottleneckArea)
                        .toList(),
                improvementSourceMapper.findHeatmapChunksBySimulationId(simulationId));
    }

    private BottleneckArea toBottleneckArea(StoredBottleneck bottleneck) {
        try {
            BottleneckGeometry geometry = objectMapper.readValue(bottleneck.getGeometry(), BottleneckGeometry.class);
            if (geometry.schemaVersion() != 1
                    || !"FLOOR_PLAN".equals(geometry.coordinateSystem())
                    || !"METER".equals(geometry.coordinateUnit())
                    || !"RECTANGLE".equals(geometry.geometryType())
                    || geometry.startX() >= geometry.endX()
                    || geometry.startY() >= geometry.endY()) {
                throw new IllegalArgumentException("Unsupported bottleneck geometry");
            }
            return new BottleneckArea(
                    RotatedRectangle.of(geometry.startX(), geometry.startY(), geometry.endX(), geometry.endY(), 0),
                    bottleneck.getStartTimeSeconds(),
                    bottleneck.getEndTimeSeconds());
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Invalid bottleneck geometry", exception);
        }
    }

    private RotatedRectangle bounds(Fabric fabric) {
        return bounds(fabric.getStartX(), fabric.getStartY(), fabric.getEndX(), fabric.getEndY(), fabric.getRotation());
    }

    private RotatedRectangle bounds(Pillar pillar) {
        return bounds(pillar.getStartX(), pillar.getStartY(), pillar.getEndX(), pillar.getEndY(), pillar.getRotation());
    }

    private RotatedRectangle bounds(Wall wall) {
        return bounds(wall.getStartX(), wall.getStartY(), wall.getEndX(), wall.getEndY(), BigDecimal.ZERO);
    }

    private RotatedRectangle bounds(OutsideWall wall) {
        return bounds(wall.getStartX(), wall.getStartY(), wall.getEndX(), wall.getEndY(), BigDecimal.ZERO);
    }

    private RotatedRectangle bounds(LayoutExit layoutExit) {
        return bounds(
                layoutExit.getStartX(),
                layoutExit.getStartY(),
                layoutExit.getEndX(),
                layoutExit.getEndY(),
                BigDecimal.ZERO);
    }

    private RotatedRectangle bounds(
            BigDecimal startX, BigDecimal startY, BigDecimal endX, BigDecimal endY, BigDecimal rotation) {
        return RotatedRectangle.of(
                startX.doubleValue(),
                startY.doubleValue(),
                endX.doubleValue(),
                endY.doubleValue(),
                rotation.doubleValue());
    }

    private <T> T require(T value) {
        if (value == null) {
            throw new IllegalArgumentException("Source simulation drawing was not found");
        }
        return value;
    }

    /** 병목 JSON 계약의 필요한 필드만 읽는 내부 DTO입니다. */
    private record BottleneckGeometry(
            int schemaVersion,
            String coordinateSystem,
            String coordinateUnit,
            String geometryType,
            double startX,
            double startY,
            double endX,
            double endY) {}
}
