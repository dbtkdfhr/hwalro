package com.hwalro.simulation.search.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hwalro.simulation.common.jwt.JwtUser;
import com.hwalro.simulation.search.config.LayoutSearchProperties;
import com.hwalro.simulation.search.domain.LayoutSearchCandidateEntity;
import com.hwalro.simulation.search.dto.LayoutSearchMonitorItem;
import com.hwalro.simulation.search.mapper.LayoutSearchMapper;
import com.hwalro.simulation.simulation.mapper.SimulationMapper;
import com.hwalro.simulation.simulation.service.SimulationService;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LayoutSearchQueryServiceTest {
    @Mock
    private LayoutSearchMapper layoutSearchMapper;

    @Mock
    private SimulationService simulationService;

    @Mock
    private SimulationMapper simulationMapper;

    @Mock
    private LayoutSearchProperties properties;

    private LayoutSearchQueryService service;

    @BeforeEach
    void setUp() {
        service = new LayoutSearchQueryService(
                layoutSearchMapper, simulationService, simulationMapper, properties, new ObjectMapper());
    }

    @Test
    void returnsOnlyCurrentUsersLayoutSearchesForCompletionMonitoring() {
        JwtUser user = new JwtUser(7L, Set.of("OPERATOR"));
        List<LayoutSearchMonitorItem> searches =
                List.of(new LayoutSearchMonitorItem(10L, 42L, "테스트 시뮬레이션", "GENERATING"));
        when(layoutSearchMapper.findMonitorItems(7L)).thenReturn(searches);

        assertThat(service.getMonitor(user)).isEqualTo(searches);
        verify(layoutSearchMapper).findMonitorItems(7L);
    }

    @Test
    void exposesMeasuredCandidatesThatWereNotRecommended() {
        LayoutSearchCandidateEntity worsened = new LayoutSearchCandidateEntity();
        worsened.setId(21L);
        worsened.setStatus("NOT_IMPROVED");
        LayoutSearchCandidateEntity failed = new LayoutSearchCandidateEntity();
        failed.setId(22L);
        failed.setStatus("FAILED");
        LayoutSearchCandidateEntity recommended = new LayoutSearchCandidateEntity();
        recommended.setId(23L);
        recommended.setStatus("EVALUATED");

        assertThat(LayoutSearchQueryService.isMeasuredComparison(worsened, Map.of()))
                .isTrue();
        assertThat(LayoutSearchQueryService.isMeasuredComparison(failed, Map.of()))
                .isTrue();
        assertThat(LayoutSearchQueryService.isMeasuredComparison(recommended, Map.of(23L, List.of("TOTAL_TIME"))))
                .isFalse();
    }
}
