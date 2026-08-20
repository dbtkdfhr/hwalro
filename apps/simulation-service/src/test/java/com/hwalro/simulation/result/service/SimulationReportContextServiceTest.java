package com.hwalro.simulation.result.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.hwalro.simulation.common.jwt.JwtUser;
import com.hwalro.simulation.result.dto.SimulationReportContextResponse;
import com.hwalro.simulation.result.exception.SimulationResultNotFoundException;
import com.hwalro.simulation.result.mapper.SimulationReportContextMapper;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class SimulationReportContextServiceTest {
    @Mock
    private SimulationReportContextMapper mapper;

    @Test
    void returnsContextsInRequestedOrderWithMetricsAndBottlenecks() {
        when(mapper.findSummaries(List.of(20L, 10L), 7L))
                .thenReturn(List.of(
                        new SimulationReportContextMapper.SummaryRow(10L, 100L, 7L, "현재 배치안", "현재 시뮬레이션"),
                        new SimulationReportContextMapper.SummaryRow(20L, 200L, 7L, "비교 배치안", "비교 시뮬레이션")));
        when(mapper.findMetrics(List.of(20L, 10L)))
                .thenReturn(List.of(
                        new SimulationReportContextMapper.MetricRow(10L, "TOTAL_EVACUATION_TIME_SECONDS", 264, "s"),
                        new SimulationReportContextMapper.MetricRow(20L, "MAX_DENSITY", 5.2, "persons/m2")));
        when(mapper.findBottlenecks(List.of(20L, 10L)))
                .thenReturn(List.of(new SimulationReportContextMapper.BottleneckRow(10L, 1, 12, 72, 4.8, 3.5)));

        List<SimulationReportContextResponse> contexts = new SimulationReportContextService(mapper)
                .findAll(List.of(20L, 10L), new JwtUser(7L, Set.of("OPERATOR")));

        assertThat(contexts)
                .extracting(SimulationReportContextResponse::simulationResultId)
                .containsExactly(20L, 10L);
        assertThat(contexts.get(0).metrics())
                .containsExactly(new SimulationReportContextResponse.Metric("MAX_DENSITY", 5.2, "persons/m2"));
        assertThat(contexts.get(1).bottlenecks())
                .containsExactly(new SimulationReportContextResponse.Bottleneck(1, 12, 72, 4.8, 3.5));
    }

    @Test
    void rejectsInvalidIdsBeforeQuerying() {
        SimulationReportContextService service = new SimulationReportContextService(mapper);

        JwtUser user = new JwtUser(7L, Set.of("OPERATOR"));
        assertThatThrownBy(() -> service.findAll(List.of(), user)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.findAll(List.of(1L, 1L), user)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.findAll(List.of(0L), user)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.findAll(List.of(1L, 2L, 3L, 4L, 5L, 6L, 7L), user))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(mapper);
    }

    @Test
    void rejectsMissingSimulationResult() {
        when(mapper.findSummaries(List.of(10L, 20L), 7L))
                .thenReturn(List.of(new SimulationReportContextMapper.SummaryRow(10L, 100L, 7L, "현재 배치안", "현재 시뮬레이션")));

        assertThatThrownBy(() -> new SimulationReportContextService(mapper)
                        .findAll(List.of(10L, 20L), new JwtUser(7L, Set.of("OPERATOR"))))
                .isInstanceOf(SimulationResultNotFoundException.class)
                .hasMessageContaining("20");
    }

    @Test
    void operatorCannotReadAnotherUsersResult() {
        when(mapper.findSummaries(List.of(10L), 7L)).thenReturn(List.of());

        assertThatThrownBy(() -> new SimulationReportContextService(mapper)
                        .findAll(List.of(10L), new JwtUser(7L, Set.of("OPERATOR"))))
                .isInstanceOf(SimulationResultNotFoundException.class);
    }
}
