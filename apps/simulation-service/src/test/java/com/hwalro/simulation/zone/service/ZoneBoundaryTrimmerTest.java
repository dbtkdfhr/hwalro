package com.hwalro.simulation.zone.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.hwalro.simulation.simulation.dto.SimulationDtos.ExitDto;
import com.hwalro.simulation.simulation.dto.SimulationDtos.PointDto;
import com.hwalro.simulation.simulation.dto.SimulationDtos.SegmentDto;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class ZoneBoundaryTrimmerTest {
    private static BigDecimal m(double value) {
        return BigDecimal.valueOf(value);
    }

    private static PointDto point(double x, double y) {
        return new PointDto(m(x), m(y));
    }

    private static ExitDto exit(double x, double y) {
        return new ExitDto(1L, "비상구", m(x), m(y), m(x), m(y));
    }

    private static ExitDto exitSegment(double startX, double startY, double endX, double endY) {
        return new ExitDto(1L, "비상구", m(startX), m(startY), m(endX), m(endY));
    }

    @Test
    void 구역_중심에서_출발한_경로를_유지하고_경계_통과점을_거쳐_비상구_중점에_도착한다() {
        List<PointDto> route = List.of(point(5, 5), point(8, 7), point(12, 9), point(15, 9));

        List<PointDto> trimmed = ZoneBoundaryTrimmer.trim(
                route, new ZoneBoundaryTrimmer.ZoneBounds(m(0), m(0), m(10), m(10)), exitSegment(20, 8, 20, 10));

        // crossing은 (10, 8), 비상구 중점은 (20, 9)
        assertThat(trimmed)
                .containsExactly(
                        point(5, 5),
                        point(8, 5),
                        point(8, 7),
                        point(10, 7),
                        point(10, 8),
                        point(12, 8),
                        point(12, 9),
                        point(20, 9));
    }

    @Test
    void 구역_경계에_벽이_있을_때_출입_개구부의_중앙을_경유한다() {
        List<PointDto> route = List.of(point(5, 5), point(8, 7), point(12, 8), point(15, 8));
        // X=10 변에 벽이 (10, 0)~(10, 6) 및 (10, 8)~(10, 10)에 배치되어 (10, 6)~(10, 8) 개구부 형성 (중점 Y=7.0)
        List<SegmentDto> walls = List.of(
                new SegmentDto("벽1", m(10), m(0), m(10), m(6)), new SegmentDto("벽2", m(10), m(8), m(10), m(10)));

        List<PointDto> trimmed = ZoneBoundaryTrimmer.trim(
                route, new ZoneBoundaryTrimmer.ZoneBounds(m(0), m(0), m(10), m(10)), exit(20, 8), walls, List.of());

        // crossing은 (10, 7.666..)이었지만 개구부(6~8)의 중앙인 (10, 7.0)을 경유
        assertThat(trimmed)
                .containsExactly(
                        point(5, 5), point(8, 5), point(8, 7), point(10, 7), point(12, 7), point(12, 8), point(20, 8));
    }

    @Test
    void 치환_지점이_기둥을_뚫으면_원래_경로를_유지한다() {
        List<PointDto> route = List.of(point(8, 6), point(13, 9));

        List<ZoneBoundaryTrimmer.Obstacle> pillars = List.of(new ZoneBoundaryTrimmer.Obstacle(9.7, 6.8, 10.3, 7.6, 0));

        List<PointDto> trimmed = ZoneBoundaryTrimmer.trim(
                route,
                new ZoneBoundaryTrimmer.ZoneBounds(m(0), m(0), m(10), m(10)),
                exitSegment(20, 8, 20, 10),
                List.of(),
                pillars);

        assertThat(trimmed).containsExactly(point(8, 6), point(20, 6), point(20, 9));
    }

    @Test
    void 비상구가_구역_안에_있으면_경로를_자르지_않고_비상구_중점에_스냅한다() {
        List<PointDto> route = List.of(point(5, 5), point(8, 5));

        List<PointDto> trimmed = ZoneBoundaryTrimmer.trim(
                route, new ZoneBoundaryTrimmer.ZoneBounds(m(0), m(0), m(10), m(10)), exitSegment(8, 4, 8, 6));

        // 비상구 중점 (8, 5)에 스냅
        assertThat(trimmed).containsExactly(point(5, 5), point(8, 5));
    }

    @Test
    void 경계_교차점이_없으면_원래_경로를_유지하되_비상구_중점으로_연결된다() {
        List<PointDto> route = List.of(point(12, 5), point(15, 5));

        List<PointDto> trimmed = ZoneBoundaryTrimmer.trim(
                route, new ZoneBoundaryTrimmer.ZoneBounds(m(0), m(0), m(10), m(10)), exit(20, 5));

        assertThat(trimmed).containsExactly(point(12, 5), point(20, 5));
    }
}
