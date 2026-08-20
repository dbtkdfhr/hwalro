package com.hwalro.simulation.analysis.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hwalro.simulation.analysis.service.DensityThresholdProvider.DensityThreshold;
import com.hwalro.simulation.simulation.engine.SimulationEngineRunner.HeatmapChunk;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class BottleneckDetectorTest {
    private BottleneckDetector detector;
    private DensityThreshold threshold;

    @BeforeEach
    void setUp() {
        detector = new BottleneckDetector(new ObjectMapper());
        threshold = new DensityThreshold(new BigDecimal("3.500"), "PERSON_PER_M2");
    }

    @Test
    void connectsDiagonalCellsAcrossFramesAndChunkBoundary() {
        List<HeatmapChunk> chunks = List.of(
                chunk(0, frame(0, 0, cell(1, 1, 3.5), cell(2, 2, 4.0)), frame(1, 1, cell(2, 2, 4.2))),
                chunk(
                        1,
                        frame(2, 2, cell(2, 3, 4.4)),
                        frame(3, 3, cell(2, 3, 4.1)),
                        frame(4, 4, cell(3, 3, 4.0)),
                        frame(5, 5, cell(3, 3, 3.8))));

        var bottlenecks = detector.detect(chunks, threshold);

        assertThat(bottlenecks).hasSize(1);
        var bottleneck = bottlenecks.get(0);
        assertThat(bottleneck.order()).isEqualTo(1);
        assertThat(bottleneck.startTimeSeconds()).isEqualTo(0.0);
        assertThat(bottleneck.endTimeSeconds()).isEqualTo(5.0);
        assertThat(bottleneck.peakDensity()).isEqualTo(4.4);
        assertThat(bottleneck.thresholdValue()).isEqualTo(3.5);
        assertThat(bottleneck.geometry().name()).isEqualTo("병목 구역 1");
        assertThat(bottleneck.geometry().x()).isEqualTo(11.0);
        assertThat(bottleneck.geometry().y()).isEqualTo(21.0);
        assertThat(bottleneck.geometry().width()).isEqualTo(8.0);
        assertThat(bottleneck.geometry().height()).isEqualTo(8.0);
        assertThat(bottleneck.analysisVersion()).isEqualTo("GRID_CONNECTED_COMPONENT_HYSTERESIS_OVERLAP_V7");
    }

    @Test
    void usesNearThresholdCellsToKeepAConfirmedBottleneckConnected() {
        var chunks = List.of(chunk(
                0,
                frame(0, 0, cell(1, 1, 4.0), cell(1, 2, 3.0)),
                frame(1, 1, cell(1, 2, 3.0)),
                frame(2, 2, cell(1, 2, 4.0)),
                frame(3, 3, cell(1, 2, 3.0)),
                frame(4, 4, cell(1, 2, 3.0)),
                frame(5, 5, cell(1, 2, 3.0), cell(1, 3, 4.0))));

        var bottlenecks = detector.detect(chunks, threshold);

        assertThat(bottlenecks).hasSize(1);
        assertThat(bottlenecks.get(0).startTimeSeconds()).isEqualTo(0.0);
        assertThat(bottlenecks.get(0).endTimeSeconds()).isEqualTo(5.0);
        assertThat(bottlenecks.get(0).peakDensity()).isEqualTo(4.0);
        assertThat(bottlenecks.get(0).thresholdValue()).isEqualTo(3.5);
        assertThat(bottlenecks.get(0).geometry().width()).isEqualTo(8.0);
    }

    @Test
    void doesNotPromoteNearThresholdCellsWithoutAnOfficialThresholdCore() {
        List<Frame> frames = new ArrayList<>();
        for (int second = 0; second <= 5; second++) {
            frames.add(frame(second, second, cell(1, 1, 3.0), cell(1, 2, 3.0)));
        }

        assertThat(detector.detect(List.of(chunk(0, frames.toArray(Frame[]::new))), threshold))
                .isEmpty();
    }

    @Test
    void doesNotUseCellsBelowNearThresholdDensityToBridgeCoreSpikes() {
        var chunks = List.of(chunk(
                0,
                frame(0, 0, cell(1, 1, 4.0)),
                frame(1, 1, cell(1, 1, 2.9)),
                frame(2, 2, cell(1, 1, 2.9)),
                frame(3, 3, cell(1, 1, 2.9)),
                frame(4, 4, cell(1, 1, 2.9)),
                frame(5, 5, cell(1, 1, 4.0))));

        assertThat(detector.detect(chunks, threshold)).isEmpty();
    }

    @Test
    void limitsNearThresholdExpansionToTwoMetersFromCoreCells() {
        List<Frame> frames = new ArrayList<>();
        for (int second = 0; second <= 5; second++) {
            frames.add(frame(second, second, cell(1, 1, 4.0), cell(1, 2, 3.0), cell(1, 3, 3.0), cell(1, 4, 3.0)));
        }

        var bottlenecks = detector.detect(List.of(chunk(0, frames.toArray(Frame[]::new))), threshold);

        assertThat(bottlenecks).hasSize(1);
        assertThat(bottlenecks.get(0).geometry().width()).isEqualTo(8.0);
    }

    @Test
    void requiresRecurringOfficialThresholdCoresWithinSupportedCongestion() {
        var chunks = List.of(chunk(
                0,
                frame(0, 0, cell(1, 1, 4.0)),
                frame(1, 1, cell(1, 1, 3.0)),
                frame(2, 2, cell(1, 1, 3.0)),
                frame(3, 3, cell(1, 1, 3.0)),
                frame(4, 4, cell(1, 1, 3.0)),
                frame(5, 5, cell(1, 1, 4.0))));

        assertThat(detector.detect(chunks, threshold)).isEmpty();
    }

    @Test
    void splitsMovingCongestionWhenCoreLeavesItsInitialAnchor() {
        List<Frame> frames = new ArrayList<>();
        for (int second = 0; second <= 5; second++) {
            frames.add(frame(second, second, cell(1, second, 4.0)));
        }

        assertThat(detector.detect(List.of(chunk(0, frames.toArray(Frame[]::new))), threshold))
                .isEmpty();
    }

    @Test
    void separatesDistantAreasAndFiltersShortLivedCandidate() {
        List<Frame> frames = new ArrayList<>();
        for (int second = 0; second <= 5; second++) {
            frames.add(frame(second, second, cell(0, 0, 4.0), cell(8, 8, second < 3 ? 4.5 : 0.0)));
        }

        var bottlenecks = detector.detect(List.of(chunk(0, frames.toArray(Frame[]::new))), threshold);

        assertThat(bottlenecks).hasSize(1);
        assertThat(bottlenecks.get(0).geometry().x()).isEqualTo(10.0);
        assertThat(bottlenecks.get(0).geometry().y()).isEqualTo(20.0);
    }

    @Test
    void returnsDistantPersistentAreasInDeterministicOrder() {
        List<Frame> frames = new ArrayList<>();
        for (int second = 0; second <= 5; second++) {
            frames.add(frame(second, second, cell(8, 8, 4.5), cell(1, 2, 4.0)));
        }

        var bottlenecks = detector.detect(List.of(chunk(0, frames.toArray(Frame[]::new))), threshold);

        assertThat(bottlenecks).hasSize(2);
        assertThat(bottlenecks).extracting(item -> item.geometry().name()).containsExactly("병목 구역 1", "병목 구역 2");
        assertThat(bottlenecks).extracting(item -> item.geometry().x()).containsExactly(13.0, 25.0);
    }

    @Test
    void keepsSplitAreasAsOneEventAfterTheyMerge() {
        var chunks = List.of(chunk(
                0,
                frame(0, 0, cell(2, 1, 4.0), cell(2, 3, 4.1)),
                frame(1, 1, cell(2, 1, 4.0), cell(2, 3, 4.1)),
                frame(2, 2, cell(2, 1, 4.0), cell(2, 2, 4.2), cell(2, 3, 4.1)),
                frame(3, 3, cell(2, 1, 4.0), cell(2, 3, 4.1)),
                frame(4, 4, cell(2, 1, 4.0), cell(2, 3, 4.1)),
                frame(5, 5, cell(2, 1, 4.0), cell(2, 3, 4.1))));

        var bottlenecks = detector.detect(chunks, threshold);

        assertThat(bottlenecks).hasSize(1);
        assertThat(bottlenecks.get(0).peakDensity()).isEqualTo(4.2);
        assertThat(bottlenecks.get(0).geometry().width()).isEqualTo(8.0);
    }

    @Test
    void includesExactlyFiveSecondsWithDecimalFrameTimes() {
        var chunks = List.of(chunk(
                0,
                frame(0, 3.04, cell(1, 1, 4.0)),
                frame(1, 4.04, cell(1, 1, 4.0)),
                frame(2, 5.04, cell(1, 1, 4.0)),
                frame(3, 6.04, cell(1, 1, 4.0)),
                frame(4, 7.04, cell(1, 1, 4.0)),
                frame(5, 8.04, cell(1, 1, 4.0))));

        var bottlenecks = detector.detect(chunks, threshold);

        assertThat(bottlenecks).hasSize(1);
        assertThat(bottlenecks.get(0).startTimeSeconds()).isEqualTo(3.04);
        assertThat(bottlenecks.get(0).endTimeSeconds()).isEqualTo(8.04);
    }

    @Test
    void keepsOrderStableWhenEventsHaveTheSameTimeAndBounds() {
        var first = detector.detect(List.of(chunk(0, perimeterFrames(false))), threshold);
        var reversed = detector.detect(List.of(chunk(0, perimeterFrames(true))), threshold);

        assertThat(first).hasSize(4);
        assertThat(first).allSatisfy(item -> assertThat(item.peakDensity()).isEqualTo(4.8));
        assertThat(reversed).containsExactlyElementsOf(first);
    }

    @Test
    void mergesNearbyPersistentEventsAcrossShortTimeGap() {
        List<Frame> frames = new ArrayList<>();
        for (int second = 0; second <= 7; second++) {
            if (second <= 2) {
                frames.add(frame(second, second, cell(1, 1, 4.0)));
            } else if (second >= 5) {
                frames.add(frame(second, second, cell(1, 3, 4.8)));
            } else {
                frames.add(frame(second, second));
            }
        }

        var bottlenecks = detector.detect(List.of(chunk(0, frames.toArray(Frame[]::new))), threshold);

        assertThat(bottlenecks).hasSize(1);
        assertThat(bottlenecks.get(0).startTimeSeconds()).isEqualTo(0.0);
        assertThat(bottlenecks.get(0).endTimeSeconds()).isEqualTo(7.0);
        assertThat(bottlenecks.get(0).peakDensity()).isEqualTo(4.8);
    }

    @Test
    void repeatsMergingWhenExpandedBoundsReachAnotherCandidate() {
        List<Frame> frames = new ArrayList<>();
        for (int second = 0; second <= 10; second++) {
            if (second <= 2) {
                frames.add(frame(second, second, cell(0, 0, 4.0)));
            } else if (second >= 4 && second <= 6) {
                frames.add(frame(second, second, cell(0, 2, 4.4)));
            } else if (second >= 8) {
                frames.add(frame(second, second, cell(2, 0, 4.8)));
            } else {
                frames.add(frame(second, second));
            }
        }

        var bottlenecks = detector.detect(List.of(chunk(0, frames.toArray(Frame[]::new))), threshold);

        assertThat(bottlenecks).hasSize(1);
        assertThat(bottlenecks.get(0).startTimeSeconds()).isEqualTo(0.0);
        assertThat(bottlenecks.get(0).endTimeSeconds()).isEqualTo(10.0);
        assertThat(bottlenecks.get(0).peakDensity()).isEqualTo(4.8);
    }

    @Test
    void filtersSparseSpikesEvenWhenTheirTimeSpanIsFiveSeconds() {
        var chunks = List.of(chunk(
                0,
                frame(0, 0, cell(1, 1, 4.0)),
                frame(1, 1),
                frame(2, 2),
                frame(3, 3),
                frame(4, 4),
                frame(5, 5, cell(1, 1, 4.5))));

        assertThat(detector.detect(chunks, threshold)).isEmpty();
    }

    @Test
    void doesNotMergeEventsTransitivelyBeyondTheInitialAnchor() {
        List<Frame> frames = new ArrayList<>();
        for (int second = 0; second <= 25; second++) {
            if (second <= 5) {
                frames.add(frame(second, second, cell(1, 0, 4.0)));
            } else if (second >= 10 && second <= 15) {
                frames.add(frame(second, second, cell(1, 2, 4.4)));
            } else if (second >= 20) {
                frames.add(frame(second, second, cell(1, 4, 4.8)));
            } else {
                frames.add(frame(second, second));
            }
        }

        var bottlenecks = detector.detect(List.of(chunk(0, frames.toArray(Frame[]::new))), threshold);

        assertThat(bottlenecks).hasSize(2);
        assertThat(bottlenecks.get(0).startTimeSeconds()).isEqualTo(0.0);
        assertThat(bottlenecks.get(0).endTimeSeconds()).isEqualTo(15.0);
        assertThat(bottlenecks.get(1).startTimeSeconds()).isEqualTo(20.0);
        assertThat(bottlenecks.get(1).endTimeSeconds()).isEqualTo(25.0);
    }

    @Test
    void mergesEventsWhenAtLeastHalfOfTheSmallerRenderedRectangleOverlaps() {
        List<Frame> frames = new ArrayList<>();
        addMovingEvent(frames, 0, new int[] {0, 1, 2, 3, 2, 1}, 1, 4, 5);
        frames.add(frame(6, 6));
        frames.add(frame(7, 7));
        addMovingEvent(frames, 8, new int[] {6, 5, 4, 3, 4, 5}, 1, 2, 1);

        var bottlenecks = detector.detect(List.of(chunk(0, frames.toArray(Frame[]::new))), threshold);

        assertThat(bottlenecks).hasSize(1);
        assertThat(bottlenecks.get(0).startTimeSeconds()).isEqualTo(0.0);
        assertThat(bottlenecks.get(0).endTimeSeconds()).isEqualTo(13.0);
    }

    @Test
    void keepsEventsSeparateWhenRenderedRectangleOverlapIsBelowHalf() {
        List<Frame> frames = new ArrayList<>();
        addMovingEvent(frames, 0, new int[] {0, 1, 2, 3, 2, 1}, 1, 4, 5);
        frames.add(frame(6, 6));
        frames.add(frame(7, 7));
        addMovingEvent(frames, 8, new int[] {10, 9, 8, 7, 8, 9}, 1, 6, 5);

        String json = heatmapJson(0, frames).replace("\"columns\":10", "\"columns\":12");

        assertThat(detector.detect(List.of(new HeatmapChunk(0, json)), threshold))
                .hasSize(2);
    }

    @Test
    void consolidatesOverlappingPersistentGroupsUsingTheirFinalObservedAreas() {
        List<Frame> frames = new ArrayList<>();
        addMovingEvent(frames, 0, new int[] {0, 1, 2, 3, 2, 1}, 1, 4, 5);
        frames.add(frame(6, 6));
        frames.add(frame(7, 7));
        addMovingEvent(frames, 8, new int[] {8, 7, 6, 5, 6, 7}, 1, 4, 3);
        frames.add(frame(14, 14));
        frames.add(frame(15, 15));
        addMovingEvent(frames, 16, new int[] {9, 8, 7, 6, 7, 8}, 1);

        var bottlenecks = detector.detect(List.of(chunk(0, frames.toArray(Frame[]::new))), threshold);

        assertThat(bottlenecks).hasSize(1);
        assertThat(bottlenecks.get(0).startTimeSeconds()).isEqualTo(0.0);
        assertThat(bottlenecks.get(0).endTimeSeconds()).isEqualTo(21.0);
    }

    @Test
    void mergesOverlappingPersistentResultsAfterAShortCandidateAnchorsTheFirstGroup() {
        List<Frame> frames = new ArrayList<>();
        frames.add(frame(0, 0, cell(1, 0, 4.0)));
        frames.add(frame(1, 1, cell(1, 0, 4.0)));
        frames.add(frame(2, 2));
        addMovingEvent(frames, 3, new int[] {3, 4, 5, 6, 5, 4}, 1, 7, 8);
        frames.add(frame(9, 9));
        addMovingEvent(frames, 10, new int[] {9, 8, 7, 6, 7, 8}, 1, 5, 4);

        var bottlenecks = detector.detect(List.of(chunk(0, frames.toArray(Frame[]::new))), threshold);

        assertThat(bottlenecks).hasSize(1);
        assertThat(bottlenecks.get(0).startTimeSeconds()).isEqualTo(0.0);
        assertThat(bottlenecks.get(0).endTimeSeconds()).isEqualTo(15.0);
    }

    @Test
    void keepsEventsSeparateWhenOnlyOneMergeConditionMatches() {
        List<Frame> longTimeGap = new ArrayList<>();
        for (int second = 0; second <= 16; second++) {
            if (second <= 5) {
                longTimeGap.add(frame(second, second, cell(1, 1, 4.0)));
            } else if (second >= 11) {
                longTimeGap.add(frame(second, second, cell(1, 3, 4.5)));
            } else {
                longTimeGap.add(frame(second, second));
            }
        }
        List<Frame> longSpatialGap = new ArrayList<>();
        for (int second = 0; second <= 15; second++) {
            if (second <= 5) {
                longSpatialGap.add(frame(second, second, cell(1, 1, 4.0)));
            } else if (second >= 10) {
                longSpatialGap.add(frame(second, second, cell(1, 5, 4.5)));
            } else {
                longSpatialGap.add(frame(second, second));
            }
        }

        assertThat(detector.detect(List.of(chunk(0, longTimeGap.toArray(Frame[]::new))), threshold))
                .hasSize(2);
        assertThat(detector.detect(List.of(chunk(0, longSpatialGap.toArray(Frame[]::new))), threshold))
                .hasSize(2);
    }

    @Test
    void expandsMergedRegionByOneMeterWithinGridBounds() {
        List<Frame> frames = new ArrayList<>();
        for (int second = 0; second <= 5; second++) {
            frames.add(frame(second, second, cell(0, 0, 4.0), cell(3, 3, 4.5)));
        }

        var bottlenecks = detector.detect(List.of(chunk(0, frames.toArray(Frame[]::new))), threshold);

        assertThat(bottlenecks).hasSize(2);
        assertThat(bottlenecks.get(0).geometry().x()).isEqualTo(10.0);
        assertThat(bottlenecks.get(0).geometry().y()).isEqualTo(20.0);
        assertThat(bottlenecks.get(0).geometry().width()).isEqualTo(3.0);
        assertThat(bottlenecks.get(0).geometry().height()).isEqualTo(3.0);
        assertThat(bottlenecks.get(1).geometry().x()).isEqualTo(15.0);
        assertThat(bottlenecks.get(1).geometry().y()).isEqualTo(25.0);
        assertThat(bottlenecks.get(1).geometry().width()).isEqualTo(4.0);
        assertThat(bottlenecks.get(1).geometry().height()).isEqualTo(4.0);
    }

    @Test
    void connectsAcrossShortMissingFrameGapBeforeDurationFilter() {
        var chunks = List.of(chunk(
                0,
                frame(0, 0, cell(1, 1, 4.0)),
                frame(1, 1, cell(1, 1, 4.0)),
                frame(3, 3, cell(1, 1, 4.0)),
                frame(4, 4, cell(1, 1, 4.0)),
                frame(5, 5, cell(1, 1, 4.0)),
                frame(6, 6, cell(1, 1, 4.0))));

        assertThat(detector.detect(chunks, threshold)).hasSize(1);
    }

    @Test
    void doesNotConnectAcrossLongMissingFrameGap() {
        var chunks = List.of(chunk(
                0,
                frame(0, 0, cell(1, 1, 4.0)),
                frame(1, 1, cell(1, 1, 4.0)),
                frame(8, 8, cell(1, 1, 4.0)),
                frame(9, 9, cell(1, 1, 4.0)),
                frame(10, 10, cell(1, 1, 4.0)),
                frame(11, 11, cell(1, 1, 4.0))));

        assertThat(detector.detect(chunks, threshold)).isEmpty();
    }

    @Test
    void rejectsUnsupportedHeatmapContract() {
        String json = heatmapJson(0, List.of(frame(0, 0, cell(1, 1, 4.0)))).replace("PERSON_PER_M2", "PEOPLE");

        assertThatThrownBy(() -> detector.detect(List.of(new HeatmapChunk(0, json)), threshold))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("히트맵");
    }

    @Test
    void rejectsInvalidThreshold() {
        var invalidThreshold = new DensityThreshold(BigDecimal.ZERO, "PERSON_PER_M2");

        assertThatThrownBy(() -> detector.detect(List.of(chunk(0, frame(0, 0))), invalidThreshold))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("밀집도 기준");
    }

    private static HeatmapChunk chunk(int sequence, Frame... frames) {
        return new HeatmapChunk(sequence, heatmapJson(sequence, List.of(frames)));
    }

    private static String heatmapJson(int sequence, List<Frame> frames) {
        String frameJson = frames.stream()
                .map(frame -> "{\"frameIndex\":" + frame.frameIndex() + ",\"timeSeconds\":" + frame.timeSeconds()
                        + ",\"cells\":[" + String.join(",", frame.cells()) + "]}")
                .reduce((left, right) -> left + "," + right)
                .orElse("");
        int startFrame = frames.isEmpty() ? 0 : frames.get(0).frameIndex();
        int endFrame = frames.isEmpty() ? 0 : frames.get(frames.size() - 1).frameIndex();
        return """
                {"schemaVersion":1,"analysisVersion":"GRID_COUNT_V1",\
                "coordinateSystem":"FLOOR_PLAN","coordinateUnit":"METER",\
                "densityMethod":"GRID_COUNT","densityUnit":"PERSON_PER_M2",\
                "frameRate":1,"chunkSequence":%d,"startFrame":%d,"endFrame":%d,\
                "grid":{"originX":10,"originY":20,"cellSize":2,"rows":10,"columns":10,\
                "cellOrder":"ROW_COLUMN_VALUE"},"frames":[%s]}
                """
                .formatted(sequence, startFrame, endFrame, frameJson);
    }

    private static Frame frame(int frameIndex, double timeSeconds, String... cells) {
        return new Frame(frameIndex, timeSeconds, List.of(cells));
    }

    private static void addMovingEvent(
            List<Frame> frames, int startSecond, int[] coreColumns, int row, int... supportColumns) {
        for (int offset = 0; offset < coreColumns.length; offset++) {
            int second = startSecond + offset;
            List<String> cells = new ArrayList<>();
            cells.add(cell(row, coreColumns[offset], 4.0));
            if (offset == 3) {
                for (int supportColumn : supportColumns) {
                    cells.add(cell(row, supportColumn, 3.0));
                }
            }
            frames.add(frame(second, second, cells.toArray(String[]::new)));
        }
    }

    private static Frame[] perimeterFrames(boolean reverseCellOrder) {
        int[][] perimeter = {
            {0, 0}, {0, 1}, {0, 2}, {0, 3}, {0, 4}, {1, 4}, {2, 4}, {3, 4},
            {4, 4}, {4, 3}, {4, 2}, {4, 1}, {4, 0}, {3, 0}, {2, 0}, {1, 0}
        };
        Frame[] frames = new Frame[perimeter.length];
        for (int index = 0; index < perimeter.length; index++) {
            int[] first = perimeter[index];
            int[] second = perimeter[(index + perimeter.length / 2) % perimeter.length];
            String firstCell = cell(first[0], first[1], 4.1);
            String secondCell = cell(second[0], second[1], 4.8);
            frames[index] = reverseCellOrder
                    ? frame(index, index, secondCell, firstCell)
                    : frame(index, index, firstCell, secondCell);
        }
        return frames;
    }

    private static String cell(int row, int column, double density) {
        return "[" + row + "," + column + "," + density + "]";
    }

    private record Frame(int frameIndex, double timeSeconds, List<String> cells) {}
}
