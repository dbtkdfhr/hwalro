package com.hwalro.simulation.improvement.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hwalro.simulation.improvement.domain.BottleneckArea;
import com.hwalro.simulation.improvement.domain.FabricChange;
import com.hwalro.simulation.improvement.domain.FabricState;
import com.hwalro.simulation.improvement.domain.HeatmapChunk;
import com.hwalro.simulation.improvement.domain.ImprovementSource;
import com.hwalro.simulation.improvement.domain.ProposalCandidate;
import com.hwalro.simulation.improvement.geometry.Geometry;
import com.hwalro.simulation.improvement.geometry.Point;
import com.hwalro.simulation.improvement.geometry.RotatedRectangle;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/** 병목 시간대에 시설물이 점유한 고밀도 히트맵 셀의 초과 밀집도를 합산합니다. */
@Component
public class HeatmapOverlapEvaluator {
    private final ObjectMapper objectMapper;

    public HeatmapOverlapEvaluator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** 후보 적용 전 점수에서 적용 후 점수를 빼, 양수일수록 고밀도 셀을 비운 후보로 만듭니다. */
    public double overlapDecrease(ProposalCandidate candidate, ImprovementSource source) {
        Map<Long, RotatedRectangle> changed =
                candidate.changes().stream().collect(Collectors.toMap(FabricChange::fabricId, FabricChange::after));
        List<RotatedRectangle> before =
                source.fabrics().stream().map(FabricState::bounds).toList();
        List<RotatedRectangle> after = source.fabrics().stream()
                .map(fabric -> changed.getOrDefault(fabric.id(), fabric.bounds()))
                .toList();
        return overlap(before, source.heatmapChunks(), source.bottlenecks())
                - overlap(after, source.heatmapChunks(), source.bottlenecks());
    }

    private double overlap(
            List<RotatedRectangle> fabrics, List<HeatmapChunk> chunks, List<BottleneckArea> bottlenecks) {
        return chunks.stream()
                .mapToDouble(chunk -> overlap(fabrics, chunk, bottlenecks))
                .sum();
    }

    private double overlap(List<RotatedRectangle> fabrics, HeatmapChunk chunk, List<BottleneckArea> bottlenecks) {
        HeatmapData heatmap = read(chunk);
        validate(heatmap);
        return heatmap.frames().stream()
                .filter(frame -> isBottleneckTime(frame.timeSeconds(), bottlenecks))
                .mapToDouble(frame -> frameOverlap(fabrics, heatmap, frame))
                .sum();
    }

    private double frameOverlap(List<RotatedRectangle> fabrics, HeatmapData heatmap, HeatmapFrame frame) {
        if (frame.values().size() != heatmap.grid().rows() * heatmap.grid().columns()) {
            throw new IllegalArgumentException("히트맵 셀 수가 grid 크기와 다릅니다.");
        }
        double overlap = 0;
        for (int index = 0; index < frame.values().size(); index++) {
            Double value = frame.values().get(index);
            if (value == null || value <= heatmap.threshold().value()) {
                continue;
            }
            Point center = cellCenter(index, heatmap.grid());
            if (fabrics.stream().anyMatch(fabric -> Geometry.contains(fabric, center))) {
                overlap += value - heatmap.threshold().value();
            }
        }
        return overlap;
    }

    private Point cellCenter(int index, Grid grid) {
        int row = index / grid.columns();
        int column = index % grid.columns();
        // 1m 격자의 경계 겹침을 과대 계산하지 않도록, 확정한 셀 중심점만 판정합니다.
        return new Point(
                grid.originX() + (column + 0.5) * grid.cellSize(), grid.originY() + (row + 0.5) * grid.cellSize());
    }

    private boolean isBottleneckTime(double timeSeconds, List<BottleneckArea> bottlenecks) {
        return bottlenecks.stream()
                .anyMatch(bottleneck ->
                        timeSeconds >= bottleneck.startTimeSeconds() && timeSeconds <= bottleneck.endTimeSeconds());
    }

    private HeatmapData read(HeatmapChunk chunk) {
        try {
            return objectMapper.readValue(chunk.densityData(), HeatmapData.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("히트맵 JSON을 읽을 수 없습니다.", exception);
        }
    }

    private void validate(HeatmapData heatmap) {
        if (!"FLOOR_PLAN".equals(heatmap.coordinateSystem())
                || !"METER".equals(heatmap.coordinateUnit())
                || !"PERSON_PER_M2".equals(heatmap.threshold().unit())
                || !"ROW_MAJOR".equals(heatmap.grid().valueOrder())
                || heatmap.grid().cellSize() <= 0
                || heatmap.grid().rows() <= 0
                || heatmap.grid().columns() <= 0) {
            throw new IllegalArgumentException("지원하지 않는 히트맵 JSON 계약입니다.");
        }
    }

    private record HeatmapData(
            String coordinateSystem,
            String coordinateUnit,
            Threshold threshold,
            Grid grid,
            List<HeatmapFrame> frames) {}

    private record Threshold(double value, String unit) {}

    private record Grid(double originX, double originY, double cellSize, int rows, int columns, String valueOrder) {}

    private record HeatmapFrame(double timeSeconds, List<Double> values) {}
}
