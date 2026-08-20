package com.hwalro.simulation.analysis.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hwalro.simulation.analysis.domain.DetectedBottleneck;
import com.hwalro.simulation.analysis.domain.DetectedBottleneck.RectangleGeometry;
import com.hwalro.simulation.analysis.service.DensityThresholdProvider.DensityThreshold;
import com.hwalro.simulation.simulation.dto.SimulationDtos.HeatmapChunkResponse;
import com.hwalro.simulation.simulation.dto.SimulationDtos.HeatmapFrameResponse;
import com.hwalro.simulation.simulation.dto.SimulationDtos.HeatmapGridResponse;
import com.hwalro.simulation.simulation.engine.SimulationEngineRunner.HeatmapChunk;
import java.math.BigDecimal;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class BottleneckDetector {
    public static final String ANALYSIS_VERSION = "GRID_CONNECTED_COMPONENT_HYSTERESIS_OVERLAP_V7";
    private static final BigDecimal MINIMUM_DURATION_SECONDS = BigDecimal.valueOf(5);
    private static final BigDecimal MINIMUM_TEMPORAL_COVERAGE_RATIO = BigDecimal.valueOf(0.5);
    private static final BigDecimal MINIMUM_CORE_DURATION_SECONDS = BigDecimal.valueOf(2);
    private static final BigDecimal MINIMUM_CORE_TEMPORAL_COVERAGE_RATIO = BigDecimal.valueOf(0.1);
    private static final BigDecimal NEAR_THRESHOLD_DENSITY_MARGIN = BigDecimal.valueOf(0.5);
    private static final BigDecimal MAXIMUM_NEAR_THRESHOLD_DISTANCE_SQUARED_METERS =
            BigDecimal.valueOf(2).pow(2);
    private static final BigDecimal MAXIMUM_CORE_ANCHOR_DISTANCE_SQUARED_METERS =
            BigDecimal.valueOf(5).pow(2);
    private static final BigDecimal MAXIMUM_MERGE_TIME_GAP_SECONDS = BigDecimal.valueOf(5);
    private static final double MINIMUM_MERGE_OVERLAP_RATIO = 0.5;
    private static final double REGION_PADDING_METERS = 1.0;
    private static final int[][] NEIGHBOR_OFFSETS = {
        {-1, -1}, {-1, 0}, {-1, 1},
        {0, -1}, {0, 0}, {0, 1},
        {1, -1}, {1, 0}, {1, 1}
    };

    private final ObjectMapper objectMapper;

    public BottleneckDetector(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public List<DetectedBottleneck> detect(List<HeatmapChunk> chunks, DensityThreshold threshold) {
        validateThreshold(threshold);
        BigDecimal nearThreshold = nearThreshold(threshold.value());
        List<HeatmapChunk> ordered = orderedChunks(chunks);
        DetectionState state = new DetectionState();
        HeatmapGridResponse referenceGrid = null;
        BigDecimal referenceFrameRate = null;
        int expectedSequence = 0;

        for (HeatmapChunk storedChunk : ordered) {
            if (storedChunk.sequence() != expectedSequence++) {
                throw invalidContract();
            }
            HeatmapChunkResponse chunk = read(storedChunk.densityData());
            validateChunk(chunk, storedChunk.sequence(), threshold.unit());
            if (referenceGrid == null) {
                referenceGrid = chunk.grid();
                referenceFrameRate = chunk.frameRate();
            } else if (!sameGrid(referenceGrid, chunk.grid()) || referenceFrameRate.compareTo(chunk.frameRate()) != 0) {
                throw invalidContract();
            }
            validateFrameRange(chunk);
            for (HeatmapFrameResponse frame : chunk.frames()) {
                List<ParsedCell> cells = validateFrame(frame, chunk.grid());
                state.process(
                        frame,
                        cells,
                        threshold.value(),
                        nearThreshold,
                        chunk.grid().cellSize());
            }
        }

        if (referenceGrid == null || !state.hasFrames()) {
            throw invalidContract();
        }
        state.finish();
        List<EventAccumulator> persistentEvents =
                persistentEvents(mergeNearbyEvents(state.completedEvents(), referenceGrid), referenceFrameRate);
        List<EventAccumulator> consolidatedEvents = consolidateUntilStable(persistentEvents, referenceGrid);
        return toBottlenecks(consolidatedEvents, referenceGrid, threshold.value());
    }

    private static List<HeatmapChunk> orderedChunks(List<HeatmapChunk> chunks) {
        if (chunks == null || chunks.isEmpty() || chunks.stream().anyMatch(java.util.Objects::isNull)) {
            throw invalidContract();
        }
        return chunks.stream()
                .sorted(Comparator.comparingInt(HeatmapChunk::sequence))
                .toList();
    }

    private HeatmapChunkResponse read(String json) {
        if (json == null || json.isBlank()) {
            throw invalidContract();
        }
        try {
            return objectMapper.readValue(json, HeatmapChunkResponse.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("지원하지 않는 히트맵 JSON 계약입니다.", exception);
        }
    }

    private static void validateChunk(HeatmapChunkResponse chunk, int sequence, String thresholdUnit) {
        if (chunk == null
                || !Integer.valueOf(1).equals(chunk.schemaVersion())
                || !"GRID_COUNT_V1".equals(chunk.analysisVersion())
                || !"FLOOR_PLAN".equals(chunk.coordinateSystem())
                || !"METER".equals(chunk.coordinateUnit())
                || !"GRID_COUNT".equals(chunk.densityMethod())
                || !thresholdUnit.equals(chunk.densityUnit())
                || !finitePositive(chunk.frameRate())
                || !Integer.valueOf(sequence).equals(chunk.chunkSequence())
                || chunk.grid() == null
                || chunk.frames() == null
                || chunk.frames().isEmpty()) {
            throw invalidContract();
        }
        HeatmapGridResponse grid = chunk.grid();
        if (!finite(grid.originX())
                || !finite(grid.originY())
                || !finitePositive(grid.cellSize())
                || grid.rows() == null
                || grid.rows() <= 0
                || grid.columns() == null
                || grid.columns() <= 0
                || !"ROW_COLUMN_VALUE".equals(grid.cellOrder())) {
            throw invalidContract();
        }
    }

    private static void validateFrameRange(HeatmapChunkResponse chunk) {
        HeatmapFrameResponse first = chunk.frames().get(0);
        HeatmapFrameResponse last = chunk.frames().get(chunk.frames().size() - 1);
        if (first == null
                || last == null
                || chunk.startFrame() == null
                || chunk.endFrame() == null
                || !chunk.startFrame().equals(first.frameIndex())
                || !chunk.endFrame().equals(last.frameIndex())) {
            throw invalidContract();
        }
    }

    private static List<ParsedCell> validateFrame(HeatmapFrameResponse frame, HeatmapGridResponse grid) {
        if (frame == null
                || frame.frameIndex() == null
                || frame.frameIndex() < 0
                || !finiteNonNegative(frame.timeSeconds())
                || frame.cells() == null) {
            throw invalidContract();
        }
        List<ParsedCell> parsed = new ArrayList<>(frame.cells().size());
        Set<Cell> unique = new HashSet<>();
        for (List<BigDecimal> rawCell : frame.cells()) {
            ParsedCell cell = parseCell(rawCell, grid);
            if (!unique.add(cell.position())) {
                throw invalidContract();
            }
            parsed.add(cell);
        }
        return List.copyOf(parsed);
    }

    private static ParsedCell parseCell(List<BigDecimal> rawCell, HeatmapGridResponse grid) {
        if (rawCell == null || rawCell.size() != 3) {
            throw invalidContract();
        }
        try {
            int row = rawCell.get(0).intValueExact();
            int column = rawCell.get(1).intValueExact();
            BigDecimal density = rawCell.get(2);
            if (row < 0
                    || row >= grid.rows()
                    || column < 0
                    || column >= grid.columns()
                    || !finiteNonNegative(density)) {
                throw invalidContract();
            }
            return new ParsedCell(new Cell(row, column), density);
        } catch (ArithmeticException | NullPointerException exception) {
            throw invalidContract();
        }
    }

    private static List<SpatialComponent> spatialComponents(
            List<ParsedCell> parsedCells, BigDecimal coreThreshold, BigDecimal nearThreshold, BigDecimal cellSize) {
        Map<Cell, BigDecimal> nearCells = new LinkedHashMap<>();
        List<ParsedCell> orderedCells = parsedCells.stream()
                .filter(cell -> cell.density().compareTo(nearThreshold) >= 0)
                .sorted(Comparator.comparingInt(
                                (ParsedCell cell) -> cell.position().row())
                        .thenComparingInt(cell -> cell.position().column()))
                .toList();
        for (ParsedCell cell : orderedCells) {
            nearCells.put(cell.position(), cell.density());
        }

        Map<Cell, BigDecimal> coreCells = new LinkedHashMap<>();
        nearCells.forEach((cell, density) -> {
            if (density.compareTo(coreThreshold) >= 0) {
                coreCells.put(cell, density);
            }
        });
        List<CoreComponent> cores = coreComponents(coreCells);
        if (cores.isEmpty()) {
            return List.of();
        }

        List<Set<Cell>> assignedCells = new ArrayList<>();
        List<BigDecimal> peaks = new ArrayList<>();
        for (CoreComponent core : cores) {
            assignedCells.add(new HashSet<>(core.cells()));
            peaks.add(core.peakDensity());
        }
        nearCells.forEach((cell, density) -> {
            if (coreCells.containsKey(cell)) {
                return;
            }
            int nearestCoreIndex = -1;
            BigDecimal nearestSquaredDistance = null;
            for (int index = 0; index < cores.size(); index++) {
                BigDecimal squaredDistance =
                        squaredDistance(cell, cores.get(index).cells(), cellSize);
                if (squaredDistance.compareTo(MAXIMUM_NEAR_THRESHOLD_DISTANCE_SQUARED_METERS) <= 0
                        && (nearestSquaredDistance == null || squaredDistance.compareTo(nearestSquaredDistance) < 0)) {
                    nearestCoreIndex = index;
                    nearestSquaredDistance = squaredDistance;
                }
            }
            if (nearestCoreIndex >= 0) {
                assignedCells.get(nearestCoreIndex).add(cell);
                peaks.set(nearestCoreIndex, peaks.get(nearestCoreIndex).max(density));
            }
        });

        List<SpatialComponent> result = new ArrayList<>();
        for (int index = 0; index < cores.size(); index++) {
            CoreComponent core = cores.get(index);
            result.add(new SpatialComponent(
                    Set.copyOf(assignedCells.get(index)),
                    core.bounds(),
                    peaks.get(index).doubleValue()));
        }
        return result;
    }

    private static List<CoreComponent> coreComponents(Map<Cell, BigDecimal> coreCells) {
        List<CoreComponent> result = new ArrayList<>();
        Set<Cell> visited = new HashSet<>();
        for (Cell start : coreCells.keySet()) {
            if (!visited.add(start)) {
                continue;
            }
            Set<Cell> cells = new HashSet<>();
            BigDecimal peak = coreCells.get(start);
            ArrayDeque<Cell> queue = new ArrayDeque<>();
            queue.add(start);
            while (!queue.isEmpty()) {
                Cell current = queue.removeFirst();
                cells.add(current);
                peak = peak.max(coreCells.get(current));
                for (int[] offset : NEIGHBOR_OFFSETS) {
                    Cell neighbor = current.offset(offset[0], offset[1]);
                    if (coreCells.containsKey(neighbor) && visited.add(neighbor)) {
                        queue.addLast(neighbor);
                    }
                }
            }
            Set<Cell> immutableCells = Set.copyOf(cells);
            result.add(new CoreComponent(immutableCells, bounds(immutableCells), peak));
        }
        return result;
    }

    private static BigDecimal squaredDistance(Cell cell, Set<Cell> targets, BigDecimal cellSize) {
        BigDecimal nearest = null;
        for (Cell target : targets) {
            int rowGap = axisGap(cell.row(), cell.row(), target.row(), target.row());
            int columnGap = axisGap(cell.column(), cell.column(), target.column(), target.column());
            BigDecimal xGap = cellSize.multiply(BigDecimal.valueOf(columnGap));
            BigDecimal yGap = cellSize.multiply(BigDecimal.valueOf(rowGap));
            BigDecimal distance = xGap.multiply(xGap).add(yGap.multiply(yGap));
            if (nearest == null || distance.compareTo(nearest) < 0) {
                nearest = distance;
            }
        }
        return nearest == null ? BigDecimal.ZERO : nearest;
    }

    private static TrackMatch trackFor(
            SpatialComponent component,
            boolean consecutive,
            Map<Cell, Track> previousCells,
            long newTrackOrdinal,
            BigDecimal cellSize) {
        Set<Track> connected = new HashSet<>();
        if (consecutive) {
            for (Cell cell : component.cells()) {
                for (int[] offset : NEIGHBOR_OFFSETS) {
                    Track previous = previousCells.get(cell.offset(offset[0], offset[1]));
                    if (previous != null) {
                        Track root = previous.root();
                        if (root.event.acceptsCore(component, cellSize)) {
                            connected.add(root);
                        }
                    }
                }
            }
        }
        if (connected.isEmpty()) {
            return new TrackMatch(new Track(newTrackOrdinal), true);
        }
        var iterator = connected.stream()
                .sorted(Comparator.comparingLong(track -> track.event.ordinal))
                .iterator();
        Track result = iterator.next();
        while (iterator.hasNext()) {
            result = Track.union(result, iterator.next());
        }
        return new TrackMatch(result.root(), false);
    }

    private static Comparator<EventAccumulator> mergeEventOrder() {
        return Comparator.comparing((EventAccumulator event) -> event.startTime)
                .thenComparingInt(event -> event.anchorBounds.minRow())
                .thenComparingInt(event -> event.anchorBounds.minColumn())
                .thenComparingLong(event -> event.ordinal);
    }

    private static Comparator<EventAccumulator> resultEventOrder() {
        return Comparator.comparing((EventAccumulator event) -> event.startTime)
                .thenComparingInt(event -> event.minRow)
                .thenComparingInt(event -> event.minColumn)
                .thenComparingInt(event -> event.maxRow)
                .thenComparingInt(event -> event.maxColumn)
                .thenComparing(event -> event.endTime)
                .thenComparingLong(event -> event.ordinal);
    }

    private static List<EventAccumulator> mergeNearbyEvents(List<EventAccumulator> events, HeatmapGridResponse grid) {
        List<EventAccumulator> ordered =
                events.stream().sorted(mergeEventOrder()).toList();
        List<MergeGroup> merged = new ArrayList<>();
        for (EventAccumulator event : ordered) {
            boolean included = false;
            for (MergeGroup group : merged) {
                EventAccumulator existing = group.event();
                boolean spatiallyRelated = withinAnchorDistance(existing, event, grid.cellSize())
                        || renderedOverlapRatio(group.referenceBounds(), event.observedBounds(), grid.cellSize())
                                >= MINIMUM_MERGE_OVERLAP_RATIO;
                if (timeGap(existing, event).compareTo(MAXIMUM_MERGE_TIME_GAP_SECONDS) <= 0 && spatiallyRelated) {
                    existing.include(event);
                    included = true;
                    break;
                }
            }
            if (!included) {
                EventAccumulator copy = new EventAccumulator(event.ordinal);
                copy.include(event);
                merged.add(new MergeGroup(copy, event.observedBounds()));
            }
        }
        return merged.stream().map(MergeGroup::event).toList();
    }

    private static List<EventAccumulator> consolidateUntilStable(
            List<EventAccumulator> events, HeatmapGridResponse grid) {
        List<EventAccumulator> current = events;
        while (true) {
            List<EventAccumulator> merged = mergeNearbyEvents(current, grid);
            if (merged.size() == current.size()) {
                return merged;
            }
            current = merged;
        }
    }

    private static double renderedOverlapRatio(CellBounds first, CellBounds second, BigDecimal cellSize) {
        double size = cellSize.doubleValue();
        double firstMinX = first.minColumn() * size - REGION_PADDING_METERS;
        double firstMinY = first.minRow() * size - REGION_PADDING_METERS;
        double firstMaxX = (first.maxColumn() + 1) * size + REGION_PADDING_METERS;
        double firstMaxY = (first.maxRow() + 1) * size + REGION_PADDING_METERS;
        double secondMinX = second.minColumn() * size - REGION_PADDING_METERS;
        double secondMinY = second.minRow() * size - REGION_PADDING_METERS;
        double secondMaxX = (second.maxColumn() + 1) * size + REGION_PADDING_METERS;
        double secondMaxY = (second.maxRow() + 1) * size + REGION_PADDING_METERS;
        double overlapWidth = Math.min(firstMaxX, secondMaxX) - Math.max(firstMinX, secondMinX);
        double overlapHeight = Math.min(firstMaxY, secondMaxY) - Math.max(firstMinY, secondMinY);
        if (overlapWidth <= 0 || overlapHeight <= 0) {
            return 0;
        }
        double firstArea = (firstMaxX - firstMinX) * (firstMaxY - firstMinY);
        double secondArea = (secondMaxX - secondMinX) * (secondMaxY - secondMinY);
        return (overlapWidth * overlapHeight) / Math.min(firstArea, secondArea);
    }

    private static BigDecimal timeGap(EventAccumulator first, EventAccumulator second) {
        if (first.endTime.compareTo(second.startTime) < 0) {
            return second.startTime.subtract(first.endTime);
        }
        if (second.endTime.compareTo(first.startTime) < 0) {
            return first.startTime.subtract(second.endTime);
        }
        return BigDecimal.ZERO;
    }

    private static boolean isPersistent(EventAccumulator event, BigDecimal frameRate) {
        if (event.coreFrameIndexes.isEmpty()) {
            return false;
        }
        BigDecimal duration = event.durationSeconds();
        if (duration.compareTo(MINIMUM_DURATION_SECONDS) < 0) {
            return false;
        }
        BigDecimal expectedObservations = duration.multiply(frameRate).add(BigDecimal.ONE);
        BigDecimal minimumObservations = expectedObservations.multiply(MINIMUM_TEMPORAL_COVERAGE_RATIO);
        if (BigDecimal.valueOf(event.observedFrameIndexes.size()).compareTo(minimumObservations) < 0) {
            return false;
        }
        BigDecimal minimumCoreObservationsByDuration =
                MINIMUM_CORE_DURATION_SECONDS.multiply(frameRate).add(BigDecimal.ONE);
        BigDecimal minimumCoreObservationsByRatio = expectedObservations.multiply(MINIMUM_CORE_TEMPORAL_COVERAGE_RATIO);
        BigDecimal minimumCoreObservations = minimumCoreObservationsByDuration.max(minimumCoreObservationsByRatio);
        return BigDecimal.valueOf(event.coreFrameIndexes.size()).compareTo(minimumCoreObservations) >= 0;
    }

    private static List<EventAccumulator> persistentEvents(List<EventAccumulator> events, BigDecimal frameRate) {
        return events.stream().filter(event -> isPersistent(event, frameRate)).toList();
    }

    private static boolean withinAnchorDistance(EventAccumulator first, EventAccumulator second, BigDecimal cellSize) {
        BigDecimal squaredDistance = squaredDistance(first.anchorBounds, second.anchorBounds, cellSize);
        return squaredDistance.compareTo(MAXIMUM_CORE_ANCHOR_DISTANCE_SQUARED_METERS) <= 0;
    }

    private static int axisGap(int firstMin, int firstMax, int secondMin, int secondMax) {
        if (firstMax < secondMin) {
            return secondMin - firstMax - 1;
        }
        if (secondMax < firstMin) {
            return firstMin - secondMax - 1;
        }
        return 0;
    }

    private static BigDecimal squaredDistance(CellBounds first, CellBounds second, BigDecimal cellSize) {
        int columnGap = axisGap(first.minColumn(), first.maxColumn(), second.minColumn(), second.maxColumn());
        int rowGap = axisGap(first.minRow(), first.maxRow(), second.minRow(), second.maxRow());
        BigDecimal xGap = cellSize.multiply(BigDecimal.valueOf(columnGap));
        BigDecimal yGap = cellSize.multiply(BigDecimal.valueOf(rowGap));
        return xGap.multiply(xGap).add(yGap.multiply(yGap));
    }

    private static CellBounds bounds(Set<Cell> cells) {
        int minRow = Integer.MAX_VALUE;
        int minColumn = Integer.MAX_VALUE;
        int maxRow = Integer.MIN_VALUE;
        int maxColumn = Integer.MIN_VALUE;
        for (Cell cell : cells) {
            minRow = Math.min(minRow, cell.row());
            minColumn = Math.min(minColumn, cell.column());
            maxRow = Math.max(maxRow, cell.row());
            maxColumn = Math.max(maxColumn, cell.column());
        }
        return new CellBounds(minRow, minColumn, maxRow, maxColumn);
    }

    private static List<DetectedBottleneck> toBottlenecks(
            List<EventAccumulator> events, HeatmapGridResponse grid, BigDecimal threshold) {
        List<EventAccumulator> ordered =
                events.stream().sorted(resultEventOrder()).toList();
        List<DetectedBottleneck> result = new ArrayList<>(ordered.size());
        for (int index = 0; index < ordered.size(); index++) {
            int order = index + 1;
            EventAccumulator event = ordered.get(index);
            double cellSize = grid.cellSize().doubleValue();
            double gridMinX = grid.originX().doubleValue();
            double gridMinY = grid.originY().doubleValue();
            double gridMaxX = gridMinX + grid.columns() * cellSize;
            double gridMaxY = gridMinY + grid.rows() * cellSize;
            double rawX = gridMinX + event.minColumn * cellSize;
            double rawY = gridMinY + event.minRow * cellSize;
            double rawEndX = gridMinX + (event.maxColumn + 1) * cellSize;
            double rawEndY = gridMinY + (event.maxRow + 1) * cellSize;
            double x = Math.max(gridMinX, rawX - REGION_PADDING_METERS);
            double y = Math.max(gridMinY, rawY - REGION_PADDING_METERS);
            double endX = Math.min(gridMaxX, rawEndX + REGION_PADDING_METERS);
            double endY = Math.min(gridMaxY, rawEndY + REGION_PADDING_METERS);
            double width = endX - x;
            double height = endY - y;
            result.add(new DetectedBottleneck(
                    order,
                    event.startTime.doubleValue(),
                    event.endTime.doubleValue(),
                    event.peakDensity,
                    threshold.doubleValue(),
                    new RectangleGeometry("RECTANGLE", "병목 구역 " + order, x, y, width, height),
                    ANALYSIS_VERSION));
        }
        return List.copyOf(result);
    }

    private static boolean sameGrid(HeatmapGridResponse first, HeatmapGridResponse second) {
        return second != null
                && first.originX().compareTo(second.originX()) == 0
                && first.originY().compareTo(second.originY()) == 0
                && first.cellSize().compareTo(second.cellSize()) == 0
                && first.rows().equals(second.rows())
                && first.columns().equals(second.columns())
                && first.cellOrder().equals(second.cellOrder());
    }

    private static void validateThreshold(DensityThreshold threshold) {
        if (threshold == null
                || !finitePositive(threshold.value())
                || !DensityThresholdProvider.DENSITY_UNIT.equals(threshold.unit())) {
            throw new IllegalArgumentException("지원하지 않는 밀집도 기준입니다.");
        }
    }

    private static BigDecimal nearThreshold(BigDecimal coreThreshold) {
        BigDecimal candidate = coreThreshold.subtract(NEAR_THRESHOLD_DENSITY_MARGIN);
        return candidate.signum() > 0 ? candidate : coreThreshold;
    }

    private static SpatialComponent supportComponent(
            EventAccumulator event,
            List<ParsedCell> parsedCells,
            BigDecimal nearThreshold,
            BigDecimal cellSize,
            Map<Cell, Track> occupiedCells) {
        Set<Cell> cells = new HashSet<>();
        BigDecimal peak = BigDecimal.ZERO;
        for (ParsedCell parsedCell : parsedCells) {
            if (parsedCell.density().compareTo(nearThreshold) >= 0
                    && !occupiedCells.containsKey(parsedCell.position())
                    && event.acceptsSupport(parsedCell.position(), cellSize)) {
                cells.add(parsedCell.position());
                peak = peak.max(parsedCell.density());
            }
        }
        if (cells.isEmpty()) {
            return null;
        }
        return new SpatialComponent(Set.copyOf(cells), null, peak.doubleValue());
    }

    private static boolean finite(BigDecimal value) {
        return value != null && Double.isFinite(value.doubleValue());
    }

    private static boolean finitePositive(BigDecimal value) {
        return finite(value) && value.signum() > 0;
    }

    private static boolean finiteNonNegative(BigDecimal value) {
        return finite(value) && value.signum() >= 0;
    }

    private static IllegalArgumentException invalidContract() {
        return new IllegalArgumentException("지원하지 않는 히트맵 JSON 계약입니다.");
    }

    private record ParsedCell(Cell position, BigDecimal density) {}

    private record Cell(int row, int column) {
        private Cell offset(int rowOffset, int columnOffset) {
            return new Cell(row + rowOffset, column + columnOffset);
        }

        private CellBounds bounds() {
            return new CellBounds(row, column, row, column);
        }
    }

    private record CellBounds(int minRow, int minColumn, int maxRow, int maxColumn) {}

    private record CoreComponent(Set<Cell> cells, CellBounds bounds, BigDecimal peakDensity) {}

    private record SpatialComponent(Set<Cell> cells, CellBounds coreBounds, double peakDensity) {
        private boolean containsCore() {
            return coreBounds != null;
        }
    }

    private record TrackMatch(Track track, boolean created) {}

    private record MergeGroup(EventAccumulator event, CellBounds referenceBounds) {}

    private static final class DetectionState {
        private Map<Cell, Track> previousCells = Map.of();
        private final List<EventAccumulator> completedEvents = new ArrayList<>();
        private Integer previousFrameIndex;
        private BigDecimal previousTimeSeconds;
        private long nextTrackOrdinal;

        private void process(
                HeatmapFrameResponse frame,
                List<ParsedCell> cells,
                BigDecimal coreThreshold,
                BigDecimal nearThreshold,
                BigDecimal cellSize) {
            if (previousFrameIndex != null
                    && (frame.frameIndex() <= previousFrameIndex
                            || frame.timeSeconds().compareTo(previousTimeSeconds) <= 0)) {
                throw invalidContract();
            }
            boolean consecutive = previousFrameIndex != null && frame.frameIndex() == previousFrameIndex + 1;
            Map<Cell, Track> currentCells = new HashMap<>();
            for (SpatialComponent component : spatialComponents(cells, coreThreshold, nearThreshold, cellSize)) {
                TrackMatch match = trackFor(component, consecutive, previousCells, nextTrackOrdinal, cellSize);
                if (match.created()) {
                    nextTrackOrdinal++;
                }
                Track track = match.track();
                track.root().event.include(frame.frameIndex(), frame.timeSeconds(), component);
                for (Cell cell : component.cells()) {
                    currentCells.put(cell, track);
                }
            }
            currentCells.replaceAll((cell, track) -> track.root());
            Set<Track> unmatchedTracks = roots(previousCells);
            unmatchedTracks.removeAll(roots(currentCells));
            List<Track> orderedUnmatchedTracks = unmatchedTracks.stream()
                    .sorted(Comparator.comparingLong(track -> track.event.ordinal))
                    .toList();
            for (Track track : orderedUnmatchedTracks) {
                SpatialComponent support = supportComponent(track.event, cells, nearThreshold, cellSize, currentCells);
                if (support == null) {
                    continue;
                }
                track.event.include(frame.frameIndex(), frame.timeSeconds(), support);
                for (Cell cell : support.cells()) {
                    currentCells.put(cell, track);
                }
            }
            currentCells.replaceAll((cell, track) -> track.root());
            completeInactive(previousCells, currentCells);
            previousCells = Map.copyOf(currentCells);
            previousFrameIndex = frame.frameIndex();
            previousTimeSeconds = frame.timeSeconds();
        }

        private void finish() {
            completeInactive(previousCells, Map.of());
            previousCells = Map.of();
        }

        private void completeInactive(Map<Cell, Track> before, Map<Cell, Track> after) {
            Set<Track> inactive = roots(before);
            inactive.removeAll(roots(after));
            for (Track track : inactive) {
                Track root = track.root();
                if (!root.completed) {
                    root.completed = true;
                    completedEvents.add(root.event);
                }
            }
        }

        private static Set<Track> roots(Map<Cell, Track> cells) {
            Set<Track> roots = new HashSet<>();
            cells.values().forEach(track -> roots.add(track.root()));
            return roots;
        }

        private boolean hasFrames() {
            return previousFrameIndex != null;
        }

        private List<EventAccumulator> completedEvents() {
            return List.copyOf(completedEvents);
        }
    }

    private static final class Track {
        private Track parent = this;
        private int rank;
        private boolean completed;
        private final EventAccumulator event;

        private Track(long ordinal) {
            event = new EventAccumulator(ordinal);
        }

        private Track root() {
            if (parent != this) {
                parent = parent.root();
            }
            return parent;
        }

        private static Track union(Track first, Track second) {
            Track firstRoot = first.root();
            Track secondRoot = second.root();
            if (firstRoot == secondRoot) {
                return firstRoot;
            }
            if (firstRoot.rank < secondRoot.rank) {
                return merge(secondRoot, firstRoot);
            }
            if (firstRoot.rank > secondRoot.rank) {
                return merge(firstRoot, secondRoot);
            }
            firstRoot.rank++;
            return merge(firstRoot, secondRoot);
        }

        private static Track merge(Track target, Track source) {
            source.parent = target;
            target.event.include(source.event);
            return target;
        }
    }

    private static final class EventAccumulator {
        private long ordinal;
        private BigDecimal startTime;
        private BigDecimal endTime;
        private double peakDensity;
        private int minRow = Integer.MAX_VALUE;
        private int minColumn = Integer.MAX_VALUE;
        private int maxRow = Integer.MIN_VALUE;
        private int maxColumn = Integer.MIN_VALUE;
        private CellBounds anchorBounds;
        private final Set<Integer> observedFrameIndexes = new HashSet<>();
        private final Set<Integer> coreFrameIndexes = new HashSet<>();

        private EventAccumulator(long ordinal) {
            this.ordinal = ordinal;
        }

        private void include(int frameIndex, BigDecimal timeSeconds, SpatialComponent component) {
            startTime = startTime == null ? timeSeconds : startTime.min(timeSeconds);
            endTime = endTime == null ? timeSeconds : endTime.max(timeSeconds);
            peakDensity = Math.max(peakDensity, component.peakDensity());
            observedFrameIndexes.add(frameIndex);
            if (component.containsCore()) {
                coreFrameIndexes.add(frameIndex);
                if (anchorBounds == null) {
                    anchorBounds = component.coreBounds();
                }
            }
            for (Cell cell : component.cells()) {
                minRow = Math.min(minRow, cell.row());
                minColumn = Math.min(minColumn, cell.column());
                maxRow = Math.max(maxRow, cell.row());
                maxColumn = Math.max(maxColumn, cell.column());
            }
        }

        private void include(EventAccumulator other) {
            if (anchorBounds == null || (other.anchorBounds != null && other.ordinal < ordinal)) {
                anchorBounds = other.anchorBounds;
            }
            ordinal = Math.min(ordinal, other.ordinal);
            startTime = startTime == null ? other.startTime : startTime.min(other.startTime);
            endTime = endTime == null ? other.endTime : endTime.max(other.endTime);
            peakDensity = Math.max(peakDensity, other.peakDensity);
            minRow = Math.min(minRow, other.minRow);
            minColumn = Math.min(minColumn, other.minColumn);
            maxRow = Math.max(maxRow, other.maxRow);
            maxColumn = Math.max(maxColumn, other.maxColumn);
            observedFrameIndexes.addAll(other.observedFrameIndexes);
            coreFrameIndexes.addAll(other.coreFrameIndexes);
        }

        private boolean acceptsCore(SpatialComponent component, BigDecimal cellSize) {
            if (anchorBounds == null || component.coreBounds() == null) {
                return true;
            }
            BigDecimal squaredDistance = squaredDistance(anchorBounds, component.coreBounds(), cellSize);
            return squaredDistance.compareTo(MAXIMUM_CORE_ANCHOR_DISTANCE_SQUARED_METERS) <= 0;
        }

        private boolean acceptsSupport(Cell cell, BigDecimal cellSize) {
            if (anchorBounds == null) {
                return false;
            }
            BigDecimal squaredDistance = squaredDistance(anchorBounds, cell.bounds(), cellSize);
            return squaredDistance.compareTo(MAXIMUM_NEAR_THRESHOLD_DISTANCE_SQUARED_METERS) <= 0;
        }

        private BigDecimal durationSeconds() {
            return endTime.subtract(startTime);
        }

        private CellBounds observedBounds() {
            return new CellBounds(minRow, minColumn, maxRow, maxColumn);
        }
    }
}
