package com.hwalro.simulation.zone.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.hwalro.simulation.simulation.dto.SimulationDtos.PointDto;
import com.hwalro.simulation.simulation.engine.SimulationEngineRunner.RouteCoverage;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class ZoneCoverageBranchExtractorTest {
    private static BigDecimal m(double value) {
        return BigDecimal.valueOf(value);
    }

    @Test
    void 구역을_잘라_비상구별_대표점을_하나씩_고른다() {
        RouteCoverage coverage = coverage(List.of(0, 0, 1, 1, 0, 0, 1, 1, 0, 0, 1, 1));

        ZoneCoverageBranchExtractor.Result result = ZoneCoverageBranchExtractor.extract(
                coverage, new ZoneCoverageBranchExtractor.ZoneBounds(m(0), m(0), m(3), m(2)));

        assertThat(result.branches())
                .extracting(ZoneCoverageBranchExtractor.Branch::exitId)
                .containsExactly(10L, 20L);
        assertThat(result.branches())
                .extracting(ZoneCoverageBranchExtractor.Branch::representativePoint)
                .containsExactly(new PointDto(m(0), m(1)), new PointDto(m(2), m(1)));
        assertThat(result.unassignedSampleCount()).isZero();
        assertThat(result.isolated()).isFalse();
    }

    @Test
    void 라벨이_없는_샘플을_미지정으로_센다() {
        RouteCoverage coverage = coverage(List.of(0, -1, 1, 1, 0, -1, 1, 1, 0, 0, 1, 1));

        ZoneCoverageBranchExtractor.Result result = ZoneCoverageBranchExtractor.extract(
                coverage, new ZoneCoverageBranchExtractor.ZoneBounds(m(0), m(0), m(2), m(1)));

        assertThat(result.unassignedSampleCount()).isEqualTo(2);
        assertThat(result.isolated()).isFalse();
    }

    @Test
    void 구역의_모든_샘플이_미지정이면_고립으로_판정한다() {
        RouteCoverage coverage = coverage(List.of(-1, -1, 1, 1, -1, -1, 1, 1, 0, 0, 1, 1));

        ZoneCoverageBranchExtractor.Result result = ZoneCoverageBranchExtractor.extract(
                coverage, new ZoneCoverageBranchExtractor.ZoneBounds(m(0), m(0), m(1), m(1)));

        assertThat(result.branches()).isEmpty();
        assertThat(result.sampleCount()).isEqualTo(4);
        assertThat(result.unassignedSampleCount()).isEqualTo(4);
        assertThat(result.isolated()).isTrue();
    }

    private static RouteCoverage coverage(List<Integer> labels) {
        return new RouteCoverage(m(0), m(0), m(1), 4, 3, labels, List.of(10L, 20L));
    }
}
