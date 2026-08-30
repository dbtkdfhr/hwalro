package com.hwalro.simulation.result.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.hwalro.simulation.common.jwt.JwtUser;
import com.hwalro.simulation.result.dto.SimulationReportVisualContextResponse;
import com.hwalro.simulation.result.dto.SimulationResultDetailResponse.Bottleneck;
import com.hwalro.simulation.result.dto.SimulationResultDetailResponse.Bounds;
import com.hwalro.simulation.result.dto.SimulationResultDetailResponse.Drawing;
import com.hwalro.simulation.result.mapper.SimulationReportContextMapper;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SimulationReportVisualContextServiceTest {
    @Mock
    private SimulationReportContextMapper mapper;

    @Mock
    private SimulationResultDetailService detailService;

    @Test
    void returnsVisualContextsInRequestedOrder() {
        Drawing firstDrawing = drawing("비교 도면");
        Drawing secondDrawing = drawing("기준 도면");
        Bottleneck bottleneck = new Bottleneck(30L, 1, "병목 1", 12, 72, 4.8, 3.5, new Bounds(1, 2, 3, 4));
        when(mapper.findSummaries(List.of(20L, 10L), 7L))
                .thenReturn(List.of(
                        new SimulationReportContextMapper.SummaryRow(10L, 100L, 7L, 300L, 400L, "현재 배치안", "현재 시뮬레이션"),
                        new SimulationReportContextMapper.SummaryRow(20L, 200L, 7L, 300L, 400L, "비교 배치안", "비교 시뮬레이션")));
        when(detailService.findDrawing(200L)).thenReturn(firstDrawing);
        when(detailService.findDrawing(100L)).thenReturn(secondDrawing);
        when(detailService.findBottlenecks(20L)).thenReturn(List.of(bottleneck));
        when(detailService.findBottlenecks(10L)).thenReturn(List.of());

        List<SimulationReportVisualContextResponse> contexts = new SimulationReportVisualContextService(
                        mapper, detailService)
                .findAll(List.of(20L, 10L), new JwtUser(7L, Set.of("OPERATOR")));

        assertThat(contexts)
                .extracting(SimulationReportVisualContextResponse::simulationResultId)
                .containsExactly(20L, 10L);
        assertThat(contexts)
                .extracting(SimulationReportVisualContextResponse::layoutVersionId)
                .containsExactly(400L, 400L);
        assertThat(contexts.get(0).drawing()).isSameAs(firstDrawing);
        assertThat(contexts.get(0).bottlenecks()).containsExactly(bottleneck);
        assertThat(contexts.get(1).drawing()).isSameAs(secondDrawing);
    }

    @Test
    void rejectsInvalidIdsBeforeQuerying() {
        SimulationReportVisualContextService service = new SimulationReportVisualContextService(mapper, detailService);
        JwtUser user = new JwtUser(7L, Set.of("OPERATOR"));

        assertThatThrownBy(() -> service.findAll(List.of(), user)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.findAll(List.of(1L, 1L), user)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.findAll(List.of(0L), user)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.findAll(List.of(1L, 2L, 3L, 4L, 5L, 6L, 7L), user))
                .isInstanceOf(IllegalArgumentException.class);

        verifyNoInteractions(mapper, detailService);
    }

    @Test
    void rejectsResultsOutsideOperatorScope() {
        when(mapper.findSummaries(List.of(10L), 7L)).thenReturn(List.of());

        assertThatThrownBy(() -> new SimulationReportVisualContextService(mapper, detailService)
                        .findAll(List.of(10L), new JwtUser(7L, Set.of("OPERATOR"))))
                .isInstanceOf(com.hwalro.simulation.result.exception.SimulationResultNotFoundException.class)
                .hasMessageContaining("10");
        verifyNoInteractions(detailService);
    }

    private Drawing drawing(String name) {
        return new Drawing(name, 20, 10, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
    }
}
