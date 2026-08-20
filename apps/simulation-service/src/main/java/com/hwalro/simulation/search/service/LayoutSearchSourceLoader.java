package com.hwalro.simulation.search.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hwalro.simulation.analysis.service.DensityThresholdProvider;
import com.hwalro.simulation.drawing.domain.Fabric;
import com.hwalro.simulation.drawing.domain.FloorPlan;
import com.hwalro.simulation.drawing.domain.Layout;
import com.hwalro.simulation.drawing.domain.LayoutExit;
import com.hwalro.simulation.drawing.domain.LayoutText;
import com.hwalro.simulation.drawing.domain.LayoutVersion;
import com.hwalro.simulation.drawing.domain.Pillar;
import com.hwalro.simulation.drawing.domain.Wall;
import com.hwalro.simulation.drawing.mapper.DrawingMapper;
import com.hwalro.simulation.search.domain.SearchSource;
import com.hwalro.simulation.search.domain.SearchSource.DrawingInput;
import com.hwalro.simulation.search.domain.SearchSource.ExitInput;
import com.hwalro.simulation.search.domain.SearchSource.RectangleInput;
import com.hwalro.simulation.search.domain.SearchSource.SegmentInput;
import com.hwalro.simulation.search.domain.SearchSource.TextInput;
import com.hwalro.simulation.search.mapper.LayoutSearchSourceMapper;
import com.hwalro.simulation.simulation.dto.SimulationDtos.HazardZoneDto;
import com.hwalro.simulation.simulation.dto.SimulationDtos.PointDto;
import com.hwalro.simulation.simulation.mapper.SimulationMapper;
import com.hwalro.simulation.simulation.service.SimulationGeometry;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class LayoutSearchSourceLoader {
    private final DrawingMapper drawingMapper;
    private final LayoutSearchSourceMapper layoutSearchSourceMapper;
    private final SimulationMapper simulationMapper;
    private final ObjectMapper objectMapper;
    private final DensityThresholdProvider densityThresholdProvider;

    public LayoutSearchSourceLoader(
            DrawingMapper drawingMapper,
            LayoutSearchSourceMapper layoutSearchSourceMapper,
            SimulationMapper simulationMapper,
            ObjectMapper objectMapper,
            DensityThresholdProvider densityThresholdProvider) {
        this.drawingMapper = drawingMapper;
        this.layoutSearchSourceMapper = layoutSearchSourceMapper;
        this.simulationMapper = simulationMapper;
        this.objectMapper = objectMapper;
        this.densityThresholdProvider = densityThresholdProvider;
    }

    public SearchSource load(long simulationId) {
        Long layoutVersionId = layoutSearchSourceMapper.findLayoutVersionIdBySimulationId(simulationId);
        if (layoutVersionId == null) {
            throw new IllegalArgumentException("완료된 기준 시뮬레이션 결과가 없습니다.");
        }
        LayoutVersion layoutVersion = require(drawingMapper.findLayoutVersionById(layoutVersionId));
        Layout layout = require(drawingMapper.findLayoutById(layoutVersion.getLayoutId()));
        FloorPlan floorPlan = require(drawingMapper.findFloorPlanById(layout.getFloorPlanId()));

        DrawingInput drawing = new DrawingInput(
                layout.getId(),
                layout.getTitle(),
                floorPlan.getWidth(),
                floorPlan.getHeight(),
                SimulationGeometry.assembleBoundary(
                        drawingMapper.findOutsideWallsByVersionId(layoutVersionId),
                        floorPlan.getWidth(),
                        floorPlan.getHeight()),
                drawingMapper.findWallsByVersionId(layoutVersionId).stream()
                        .map(LayoutSearchSourceLoader::toSegment)
                        .toList(),
                drawingMapper.findPillarsByVersionId(layoutVersionId).stream()
                        .map(LayoutSearchSourceLoader::toRectangle)
                        .toList(),
                drawingMapper.findFabricsByVersionId(layoutVersionId).stream()
                        .map(LayoutSearchSourceLoader::toRectangle)
                        .toList(),
                drawingMapper.findLayoutTextsByVersionId(layoutVersionId).stream()
                        .map(LayoutSearchSourceLoader::toText)
                        .toList(),
                drawingMapper.findLayoutExitsByVersionId(layoutVersionId).stream()
                        .map(LayoutSearchSourceLoader::toExit)
                        .toList());

        return new SearchSource(
                simulationId,
                drawing,
                readAgents(simulationId),
                simulationMapper.findHazardZones(simulationId).stream()
                        .map(hazard -> new HazardZoneDto(
                                hazard.getId(), hazard.getCenterX(), hazard.getCenterY(), hazard.getRadius()))
                        .toList(),
                simulationMapper.findSelectedExitIds(simulationId),
                densityThresholdProvider.getCurrent().value());
    }

    private List<PointDto> readAgents(long simulationId) {
        String json = require(simulationMapper.findInitialStateJson(simulationId));
        try {
            List<List<BigDecimal>> compact = objectMapper.readValue(json, new TypeReference<>() {});
            return compact.stream()
                    .map(position -> {
                        if (position == null
                                || position.size() != 2
                                || position.get(0) == null
                                || position.get(1) == null) {
                            throw new IllegalStateException("저장된 에이전트 좌표 형식이 올바르지 않습니다.");
                        }
                        return new PointDto(position.get(0), position.get(1));
                    })
                    .toList();
        } catch (IOException exception) {
            throw new IllegalStateException("저장된 에이전트 좌표를 읽지 못했습니다.", exception);
        }
    }

    private static SegmentInput toSegment(Wall wall) {
        return new SegmentInput(
                wall.getId(), wall.getName(), wall.getStartX(), wall.getStartY(), wall.getEndX(), wall.getEndY());
    }

    private static RectangleInput toRectangle(Pillar pillar) {
        return new RectangleInput(
                pillar.getId(),
                pillar.getName(),
                pillar.getStartX(),
                pillar.getStartY(),
                pillar.getEndX(),
                pillar.getEndY(),
                pillar.getRotation());
    }

    private static RectangleInput toRectangle(Fabric fabric) {
        return new RectangleInput(
                fabric.getId(),
                fabric.getName(),
                fabric.getStartX(),
                fabric.getStartY(),
                fabric.getEndX(),
                fabric.getEndY(),
                fabric.getRotation());
    }

    private static TextInput toText(LayoutText text) {
        return new TextInput(text.getId(), text.getText(), text.getX(), text.getY());
    }

    private static ExitInput toExit(LayoutExit exit) {
        return new ExitInput(
                exit.getId(), exit.getName(), exit.getStartX(), exit.getStartY(), exit.getEndX(), exit.getEndY());
    }

    private <T> T require(T value) {
        if (value == null) {
            throw new IllegalArgumentException("기준 시뮬레이션의 도면 또는 설정을 찾을 수 없습니다.");
        }
        return value;
    }
}
