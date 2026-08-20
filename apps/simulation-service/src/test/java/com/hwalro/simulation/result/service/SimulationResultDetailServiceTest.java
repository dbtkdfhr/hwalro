package com.hwalro.simulation.result.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hwalro.simulation.analysis.service.DensityThresholdProvider;
import com.hwalro.simulation.analysis.service.DensityThresholdProvider.DensityThreshold;
import com.hwalro.simulation.common.jwt.ForbiddenException;
import com.hwalro.simulation.common.jwt.JwtUser;
import com.hwalro.simulation.drawing.domain.LayoutText;
import com.hwalro.simulation.drawing.domain.OutsideWall;
import com.hwalro.simulation.drawing.mapper.DrawingMapper;
import com.hwalro.simulation.result.mapper.SimulationResultDetailMapper;
import com.hwalro.simulation.simulation.exception.SimulationNotFoundException;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SimulationResultDetailServiceTest {
    @Mock
    private SimulationResultDetailMapper mapper;

    @Mock
    private DensityThresholdProvider densityThresholdProvider;

    @Mock
    private DrawingMapper drawingMapper;

    private SimulationResultDetailService service;

    @BeforeEach
    void setUp() {
        service =
                new SimulationResultDetailService(mapper, drawingMapper, new ObjectMapper(), densityThresholdProvider);
    }

    @Test
    void assemblesStaticResultSummaryWithoutLoadingPlaybackChunks() {
        long simulationId = 9201L;
        when(mapper.findSummary(simulationId))
                .thenReturn(new SimulationResultDetailMapper.SummaryRow(
                        9301L, simulationId, 9001L, 9100L, "행사장", "테스트 시뮬레이션", "지하 2층", 20, 10, 100));
        when(mapper.findMetrics(9301L))
                .thenReturn(List.of(
                        new SimulationResultDetailMapper.MetricRow("SIMULATION_DURATION_SECONDS", 264),
                        new SimulationResultDetailMapper.MetricRow("MAX_DENSITY", 4.8)));
        when(densityThresholdProvider.getCurrent())
                .thenReturn(new DensityThreshold(new BigDecimal("3.5"), "PERSON_PER_M2"));
        when(mapper.findWalls(9100L))
                .thenReturn(List.of(new SimulationResultDetailMapper.SegmentRow("벽", 0, 0, 10, 0, 0)));
        when(mapper.findExits(9100L))
                .thenReturn(List.of(new SimulationResultDetailMapper.SegmentRow("출구", 10, 0, 10, 2, 0)));
        when(mapper.findPillars(9100L)).thenReturn(List.of());
        when(mapper.findFabrics(9100L)).thenReturn(List.of());
        when(mapper.findHazardZones(simulationId))
                .thenReturn(List.of(new SimulationResultDetailMapper.HazardZoneRow(31L, 7.5, 4.25, 1.5)));
        when(drawingMapper.findOutsideWallsByVersionId(9100L)).thenReturn(rectangularBoundary());
        when(drawingMapper.findLayoutTextsByVersionId(9100L)).thenReturn(List.of(layoutText("중앙 통로", 4, 5)));
        when(mapper.findBottlenecks(9301L))
                .thenReturn(List.of(new SimulationResultDetailMapper.BottleneckRow(
                        1L,
                        1,
                        68,
                        140,
                        4.8,
                        3.5,
                        "{\"name\":\"중앙 통로\",\"x\":93,\"y\":36,\"width\":31,\"height\":27}")));
        var result = service.find(simulationId, new JwtUser(9001L, Set.of("OPERATOR")));

        assertThat(result.simulationResultId()).isEqualTo(9301L);
        assertThat(result.durationSeconds()).isEqualTo(264);
        assertThat(result.totalPeople()).isEqualTo(100);
        assertThat(result.densityThreshold()).isEqualTo(3.5);
        assertThat(result.drawing().outsideBoundary())
                .extracting(point -> point.x() + "," + point.y())
                .containsExactly("0.0,0.0", "0.0,10.0", "20.0,10.0", "20.0,0.0");
        assertThat(result.drawing().walls()).hasSize(1);
        assertThat(result.hazardZones()).singleElement().satisfies(hazard -> {
            assertThat(hazard.id()).isEqualTo(31L);
            assertThat(hazard.centerX()).isEqualTo(7.5);
            assertThat(hazard.centerY()).isEqualTo(4.25);
            assertThat(hazard.radius()).isEqualTo(1.5);
        });
        assertThat(result.drawing().layoutTexts()).singleElement().satisfies(text -> {
            assertThat(text.text()).isEqualTo("중앙 통로");
            assertThat(text.x()).isEqualTo(4);
            assertThat(text.y()).isEqualTo(5);
        });
        assertThat(result.bottlenecks().get(0).name()).isEqualTo("중앙 통로");
    }

    @Test
    void rejectsAnotherOperatorsSimulation() {
        when(mapper.findSummary(9201L))
                .thenReturn(new SimulationResultDetailMapper.SummaryRow(
                        9301L, 9201L, 9001L, 9100L, "행사장", "테스트 시뮬레이션", "지하 2층", 20, 10, 100));

        assertThatThrownBy(() -> service.find(9201L, new JwtUser(7L, Set.of("OPERATOR"))))
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void rejectsMissingSimulation() {
        when(mapper.findSummary(9999L)).thenReturn(null);

        assertThatThrownBy(() -> service.find(9999L, new JwtUser(9001L, Set.of("OPERATOR"))))
                .isInstanceOf(SimulationNotFoundException.class)
                .hasMessageContaining("시뮬레이션을 찾을 수 없습니다");
    }

    @Test
    void returnsComparableSimulationPageUsingResultOwnerForReviewer() {
        long simulationId = 9201L;
        when(mapper.findSummary(simulationId))
                .thenReturn(new SimulationResultDetailMapper.SummaryRow(
                        9301L, simulationId, 9001L, 9100L, "행사장", "테스트 시뮬레이션", "지하 2층", 20, 10, 100));
        when(mapper.countComparableSimulations(simulationId, 9001L)).thenReturn(7L);
        when(mapper.findComparableSimulationPage(simulationId, 9001L, 5, 5))
                .thenReturn(List.of(new SimulationResultDetailMapper.ComparableRow(9202L, 9302L, "비교안", 221)));

        var result = service.findComparableSimulations(simulationId, 2, 5, new JwtUser(77L, Set.of("SAFETY_REVIEWER")));

        assertThat(result.totalCount()).isEqualTo(7);
        assertThat(result.page()).isEqualTo(2);
        assertThat(result.size()).isEqualTo(5);
        assertThat(result.hasNext()).isFalse();
        assertThat(result.items()).singleElement().satisfies(item -> {
            assertThat(item.id()).isEqualTo(9202L);
            assertThat(item.simulationResultId()).isEqualTo(9302L);
            assertThat(item.name()).isEqualTo("비교안");
        });
        verify(mapper).findComparableSimulationPage(simulationId, 9001L, 5, 5);
    }

    @Test
    void rejectsResultWithoutRequiredSimulationDurationMetric() {
        long simulationId = 9201L;
        when(mapper.findSummary(simulationId))
                .thenReturn(new SimulationResultDetailMapper.SummaryRow(
                        9301L, simulationId, 9001L, 9100L, "행사장", "테스트 시뮬레이션", "지하 2층", 20, 10, 100));
        when(mapper.findMetrics(9301L))
                .thenReturn(List.of(new SimulationResultDetailMapper.MetricRow("MAX_DENSITY", 4.8)));

        assertThatThrownBy(() -> service.find(simulationId, new JwtUser(9001L, Set.of("OPERATOR"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("SIMULATION_DURATION_SECONDS");
    }

    private List<OutsideWall> rectangularBoundary() {
        return List.of(
                outsideWall(0, 0, 20, 0),
                outsideWall(20, 10, 0, 10),
                outsideWall(20, 0, 20, 10),
                outsideWall(0, 10, 0, 0));
    }

    private OutsideWall outsideWall(double startX, double startY, double endX, double endY) {
        OutsideWall wall = new OutsideWall();
        wall.setStartX(BigDecimal.valueOf(startX));
        wall.setStartY(BigDecimal.valueOf(startY));
        wall.setEndX(BigDecimal.valueOf(endX));
        wall.setEndY(BigDecimal.valueOf(endY));
        return wall;
    }

    private LayoutText layoutText(String value, double x, double y) {
        LayoutText text = new LayoutText();
        text.setText(value);
        text.setX(BigDecimal.valueOf(x));
        text.setY(BigDecimal.valueOf(y));
        return text;
    }
}
