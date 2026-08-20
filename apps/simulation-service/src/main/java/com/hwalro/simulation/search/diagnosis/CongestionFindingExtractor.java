package com.hwalro.simulation.search.diagnosis;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hwalro.simulation.search.domain.Evidence;
import com.hwalro.simulation.search.domain.Finding;
import com.hwalro.simulation.search.domain.FindingType;
import com.hwalro.simulation.search.domain.HeatmapChunk;
import com.hwalro.simulation.search.domain.Rectangle;
import java.math.BigDecimal;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class CongestionFindingExtractor {
    private final ObjectMapper objectMapper;

    public CongestionFindingExtractor(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public List<Finding> extract(List<HeatmapChunk> chunks, BigDecimal densityThreshold) {
        double threshold = densityThreshold.doubleValue();
        Map<CellKey, Accumulator> accumulators = new HashMap<>();
        Map<CellKey, GridInfo> gridByCell = new HashMap<>();
        for (HeatmapChunk chunk : chunks) {
            accumulate(chunk, accumulators, gridByCell);
        }
        Map<CellKey, Double> avgByCell = new HashMap<>();
        for (Map.Entry<CellKey, Accumulator> entry : accumulators.entrySet()) {
            double sumValue = entry.getValue().sumValue;
            double sumWeight = entry.getValue().sumWeight;
            double avg = sumWeight > 0 ? sumValue / sumWeight : 0.0;
            if (avg > threshold) {
                avgByCell.put(entry.getKey(), avg);
            }
        }
        List<Finding> findings = new ArrayList<>();
        Set<CellKey> visited = new HashSet<>();
        for (CellKey start : avgByCell.keySet()) {
            if (!visited.add(start)) {
                continue;
            }
            double peakAvg = 0.0;
            int minRow = start.row();
            int maxRow = start.row();
            int minCol = start.col();
            int maxCol = start.col();
            Deque<CellKey> queue = new ArrayDeque<>();
            queue.add(start);
            while (!queue.isEmpty()) {
                CellKey cell = queue.poll();
                double avg = avgByCell.get(cell);
                peakAvg = Math.max(peakAvg, avg);
                minRow = Math.min(minRow, cell.row());
                maxRow = Math.max(maxRow, cell.row());
                minCol = Math.min(minCol, cell.col());
                maxCol = Math.max(maxCol, cell.col());
                for (CellKey neighbor : neighbors(cell)) {
                    if (avgByCell.containsKey(neighbor) && visited.add(neighbor)) {
                        queue.add(neighbor);
                    }
                }
            }
            GridInfo grid = gridByCell.get(start);
            double severity = clamp01((peakAvg - threshold) / threshold);
            Rectangle region = new Rectangle(
                    grid.originX() + minCol * grid.cellSize(),
                    grid.originY() + minRow * grid.cellSize(),
                    grid.originX() + (maxCol + 1) * grid.cellSize(),
                    grid.originY() + (maxRow + 1) * grid.cellSize());
            Evidence evidence = new Evidence("AVERAGE_DENSITY", peakAvg, "PERSON_PER_M2", "HEATMAP_GRID_COUNT");
            findings.add(new Finding(
                    FindingType.CONGESTION_HOTSPOT, severity, region, evidence, "혼잡 구역이 밀집 임계값을 초과하는 평균 밀도를 유지했습니다."));
        }
        findings.sort(Comparator.comparingDouble(Finding::severity).reversed());
        return findings;
    }

    private void accumulate(
            HeatmapChunk chunk, Map<CellKey, Accumulator> accumulators, Map<CellKey, GridInfo> gridByCell) {
        String densityData = chunk.getDensityData();
        if (densityData == null || densityData.isBlank()) {
            return;
        }
        JsonNode root;
        try {
            root = objectMapper.readTree(densityData);
        } catch (Exception e) {
            return;
        }
        JsonNode grid = root.get("grid");
        JsonNode frames = root.get("frames");
        if (grid == null || frames == null || !frames.isArray() || frames.isEmpty()) {
            return;
        }
        if (!isNumber(grid, "originX") || !isNumber(grid, "originY") || !isNumber(grid, "cellSize")) {
            return;
        }
        double originX = grid.get("originX").asDouble();
        double originY = grid.get("originY").asDouble();
        double cellSize = grid.get("cellSize").asDouble();
        if (cellSize <= 0) {
            return;
        }
        List<JsonNode> frameList = new ArrayList<>();
        for (JsonNode frame : frames) {
            if (frame.hasNonNull("timeSeconds")
                    && frame.hasNonNull("cells")
                    && frame.get("cells").isArray()) {
                frameList.add(frame);
            }
        }
        if (frameList.isEmpty()) {
            return;
        }
        frameList.sort(
                Comparator.comparingDouble(frame -> frame.get("timeSeconds").asDouble()));
        GridInfo gridInfo = new GridInfo(originX, originY, cellSize);
        for (int i = 0; i < frameList.size(); i++) {
            double weight;
            if (i < frameList.size() - 1) {
                weight = frameList.get(i + 1).get("timeSeconds").asDouble()
                        - frameList.get(i).get("timeSeconds").asDouble();
            } else {
                weight = 1.0;
            }
            if (weight <= 0) {
                continue;
            }
            for (JsonNode cellNode : frameList.get(i).get("cells")) {
                if (!cellNode.isArray() || cellNode.size() < 3) {
                    continue;
                }
                if (!cellNode.get(0).isNumber()
                        || !cellNode.get(1).isNumber()
                        || !cellNode.get(2).isNumber()) {
                    continue;
                }
                CellKey key =
                        new CellKey(cellNode.get(0).asInt(), cellNode.get(1).asInt());
                Accumulator accumulator = accumulators.computeIfAbsent(key, ignored -> new Accumulator());
                accumulator.sumValue += cellNode.get(2).asDouble() * weight;
                accumulator.sumWeight += weight;
                gridByCell.putIfAbsent(key, gridInfo);
            }
        }
    }

    private static List<CellKey> neighbors(CellKey cell) {
        return List.of(
                new CellKey(cell.row() - 1, cell.col()),
                new CellKey(cell.row() + 1, cell.col()),
                new CellKey(cell.row(), cell.col() - 1),
                new CellKey(cell.row(), cell.col() + 1));
    }

    private static boolean isNumber(JsonNode node, String field) {
        return node.hasNonNull(field) && node.get(field).isNumber();
    }

    private static double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    private record CellKey(int row, int col) {}

    private record GridInfo(double originX, double originY, double cellSize) {}

    private static class Accumulator {
        private double sumValue;
        private double sumWeight;
    }
}
