package com.hwalro.simulation.result.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hwalro.simulation.analysis.service.DensityThresholdProvider;
import com.hwalro.simulation.common.jwt.ForbiddenException;
import com.hwalro.simulation.common.jwt.JwtUser;
import com.hwalro.simulation.drawing.mapper.DrawingMapper;
import com.hwalro.simulation.result.dto.ComparableSimulationPageResponse;
import com.hwalro.simulation.result.dto.ComparableSimulationPageResponse.ComparableSimulation;
import com.hwalro.simulation.result.dto.SimulationResultDetailResponse;
import com.hwalro.simulation.result.dto.SimulationResultDetailResponse.Bottleneck;
import com.hwalro.simulation.result.dto.SimulationResultDetailResponse.Bounds;
import com.hwalro.simulation.result.dto.SimulationResultDetailResponse.Drawing;
import com.hwalro.simulation.result.dto.SimulationResultDetailResponse.HazardZone;
import com.hwalro.simulation.result.dto.SimulationResultDetailResponse.LayoutText;
import com.hwalro.simulation.result.dto.SimulationResultDetailResponse.Point;
import com.hwalro.simulation.result.dto.SimulationResultDetailResponse.Rectangle;
import com.hwalro.simulation.result.dto.SimulationResultDetailResponse.Segment;
import com.hwalro.simulation.result.mapper.SimulationResultDetailMapper;
import com.hwalro.simulation.result.mapper.SimulationResultDetailMapper.SegmentRow;
import com.hwalro.simulation.result.mapper.SimulationResultDetailMapper.SummaryRow;
import com.hwalro.simulation.simulation.exception.SimulationNotFoundException;
import com.hwalro.simulation.simulation.service.SimulationGeometry;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class SimulationResultDetailService {
    private static final String SUBTITLE = "시뮬레이션 결과 분석";
    private static final int MAX_PAGE = 10_000;
    private static final int MAX_PAGE_SIZE = 100;

    private final SimulationResultDetailMapper mapper;
    private final DrawingMapper drawingMapper;
    private final ObjectMapper objectMapper;
    private final DensityThresholdProvider densityThresholdProvider;

    public SimulationResultDetailService(
            SimulationResultDetailMapper mapper,
            DrawingMapper drawingMapper,
            ObjectMapper objectMapper,
            DensityThresholdProvider densityThresholdProvider) {
        this.mapper = mapper;
        this.drawingMapper = drawingMapper;
        this.objectMapper = objectMapper;
        this.densityThresholdProvider = densityThresholdProvider;
    }

    public SimulationResultDetailResponse find(Long simulationId, JwtUser user) {
        if (simulationId == null || simulationId <= 0) {
            throw new IllegalArgumentException("시뮬레이션 ID는 양수여야 합니다.");
        }
        SummaryRow summary = mapper.findSummary(simulationId);
        if (summary == null) {
            throw new SimulationNotFoundException("시뮬레이션을 찾을 수 없습니다: " + simulationId);
        }
        requireAccessible(summary, user);

        Map<String, Double> metrics = new HashMap<>();
        mapper.findMetrics(summary.simulationResultId())
                .forEach(metric -> metrics.put(metric.metricType(), metric.metricValue()));
        double duration = requiredMetric(metrics, "SIMULATION_DURATION_SECONDS");
        double maxDensity = requiredMetric(metrics, "MAX_DENSITY");

        List<Bottleneck> bottlenecks = findBottlenecks(summary.simulationResultId());
        double threshold = densityThresholdProvider.getCurrent().value().doubleValue();
        Drawing drawing = assembleDrawing(summary);
        return new SimulationResultDetailResponse(
                summary.simulationId(),
                summary.simulationResultId(),
                summary.title() != null && !summary.title().isBlank() ? summary.title() : summary.layoutTitle(),
                SUBTITLE,
                duration,
                summary.totalPeople(),
                maxDensity,
                threshold,
                drawing,
                mapper.findHazardZones(simulationId).stream()
                        .map(row -> new HazardZone(row.id(), row.centerX(), row.centerY(), row.radius()))
                        .toList(),
                bottlenecks);
    }

    public ComparableSimulationPageResponse findComparableSimulations(
            Long simulationId, int page, int size, JwtUser user) {
        if (simulationId == null || simulationId <= 0) {
            throw new IllegalArgumentException("시뮬레이션 ID는 양수여야 합니다.");
        }
        if (page < 1 || page > MAX_PAGE || size < 1 || size > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("page는 1 이상, size는 1~100이어야 합니다.");
        }

        SummaryRow summary = mapper.findSummary(simulationId);
        if (summary == null) {
            throw new SimulationNotFoundException("시뮬레이션을 찾을 수 없습니다: " + simulationId);
        }
        requireAccessible(summary, user);

        long totalCount = mapper.countComparableSimulations(simulationId, summary.createdBy());
        List<ComparableSimulation> items =
                mapper.findComparableSimulationPage(simulationId, summary.createdBy(), (page - 1) * size, size).stream()
                        .map(row -> new ComparableSimulation(
                                row.simulationId(), row.simulationResultId(), row.name(), row.totalEvacuationTime()))
                        .toList();

        return new ComparableSimulationPageResponse(
                Math.toIntExact(totalCount), page, size, page * size < totalCount, items);
    }

    public SimulationResultDetailResponse.Drawing findDrawing(Long simulationId) {
        if (simulationId == null || simulationId <= 0) {
            throw new IllegalArgumentException("시뮬레이션 ID는 양수여야 합니다.");
        }
        SummaryRow summary = mapper.findSummary(simulationId);
        if (summary == null) {
            throw new SimulationNotFoundException("시뮬레이션을 찾을 수 없습니다: " + simulationId);
        }
        return assembleDrawing(summary);
    }

    public List<Bottleneck> findBottlenecks(Long simulationResultId) {
        if (simulationResultId == null || simulationResultId <= 0) {
            throw new IllegalArgumentException("시뮬레이션 결과 ID는 양수여야 합니다.");
        }
        return mapper.findBottlenecks(simulationResultId).stream()
                .map(this::toBottleneck)
                .toList();
    }

    private SimulationResultDetailResponse.Drawing assembleDrawing(SummaryRow summary) {
        List<Point> outsideBoundary = SimulationGeometry.assembleBoundary(
                        drawingMapper.findOutsideWallsByVersionId(summary.layoutVersionId()),
                        BigDecimal.valueOf(summary.drawingWidth()),
                        BigDecimal.valueOf(summary.drawingHeight()))
                .stream()
                .map(point -> new Point(point.x().doubleValue(), point.y().doubleValue()))
                .toList();
        return new Drawing(
                summary.drawingName(),
                summary.drawingWidth(),
                summary.drawingHeight(),
                outsideBoundary,
                mapper.findWalls(summary.layoutVersionId()).stream()
                        .map(this::toSegment)
                        .toList(),
                mapper.findExits(summary.layoutVersionId()).stream()
                        .map(this::toSegment)
                        .toList(),
                mapper.findPillars(summary.layoutVersionId()).stream()
                        .map(this::toRectangle)
                        .toList(),
                mapper.findFabrics(summary.layoutVersionId()).stream()
                        .map(this::toRectangle)
                        .toList(),
                drawingMapper.findLayoutTextsByVersionId(summary.layoutVersionId()).stream()
                        .map(text -> new LayoutText(
                                text.getText(),
                                text.getX().doubleValue(),
                                text.getY().doubleValue()))
                        .toList());
    }

    private void requireAccessible(SummaryRow summary, JwtUser user) {
        if (user == null) {
            throw new ForbiddenException("시뮬레이션 결과 조회 권한이 없습니다.");
        }
        if (user.roles().contains("ADMIN") || user.roles().contains("SAFETY_REVIEWER")) {
            return;
        }
        if (user.roles().contains("OPERATOR") && summary.createdBy().equals(user.userId())) {
            return;
        }
        throw new ForbiddenException("시뮬레이션 결과 조회 권한이 없습니다.");
    }

    private double requiredMetric(Map<String, Double> metrics, String metricType) {
        Double value = metrics.get(metricType);
        if (value == null) {
            throw new IllegalArgumentException("필수 시뮬레이션 지표가 없습니다: " + metricType);
        }
        return value;
    }

    private Bottleneck toBottleneck(SimulationResultDetailMapper.BottleneckRow row) {
        JsonNode geometry = readJson(row.geometry(), "병목 구역");
        String name = geometry.path("name").asText("병목 구역 " + row.bottleneckOrder());
        return new Bottleneck(
                row.id(),
                row.bottleneckOrder(),
                name,
                row.startTimeSeconds(),
                row.endTimeSeconds(),
                row.peakDensity(),
                row.thresholdValue(),
                new Bounds(
                        requiredDouble(geometry, "x"),
                        requiredDouble(geometry, "y"),
                        requiredDouble(geometry, "width"),
                        requiredDouble(geometry, "height")));
    }

    private Segment toSegment(SegmentRow row) {
        return new Segment(row.name(), row.startX(), row.startY(), row.endX(), row.endY());
    }

    private Rectangle toRectangle(SegmentRow row) {
        return new Rectangle(row.name(), row.startX(), row.startY(), row.endX(), row.endY(), row.rotation());
    }

    private JsonNode readJson(String value, String label) {
        try {
            return objectMapper.readTree(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException(label + " JSON을 읽을 수 없습니다.", exception);
        }
    }

    private double requiredDouble(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || !value.isNumber()) {
            throw new IllegalArgumentException("JSON 숫자 값이 없습니다: " + field);
        }
        return value.asDouble();
    }
}
