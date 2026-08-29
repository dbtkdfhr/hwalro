package com.hwalro.simulation.search.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hwalro.simulation.common.jwt.JwtUser;
import com.hwalro.simulation.drawing.mapper.DrawingMapper;
import com.hwalro.simulation.search.config.LayoutSearchProperties;
import com.hwalro.simulation.search.domain.LayoutSearchEntity;
import com.hwalro.simulation.search.mapper.LayoutSearchMapper;
import com.hwalro.simulation.search.mapper.LayoutSearchSourceMapper;
import com.hwalro.simulation.simulation.service.SimulationService;
import com.hwalro.simulation.zone.service.SearchConstraintProjector;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.transaction.support.TransactionTemplate;

class LayoutSearchOrchestratorCancellationTest {

    @Test
    void forwardsAcceptedCancellationToTheRunningPythonProcessRegistry() {
        LayoutSearchMapper mapper = mock(LayoutSearchMapper.class);
        LayoutSearchRunner runner = mock(LayoutSearchRunner.class);
        SimulationService simulationService = mock(SimulationService.class);
        TransactionTemplate transactionTemplate = mock(TransactionTemplate.class);
        LayoutSearchEntity search = activeSearch(20L, 30L);
        when(mapper.findSearchById(20L)).thenReturn(search);
        when(transactionTemplate.execute(any())).thenReturn(true);

        LayoutSearchOrchestrator orchestrator = new LayoutSearchOrchestrator(
                mapper,
                mock(LayoutSearchSourceMapper.class),
                mock(LayoutSearchSourceLoader.class),
                runner,
                mock(CandidateTrialService.class),
                simulationService,
                mock(DrawingMapper.class),
                mock(SearchConstraintProjector.class),
                new ObjectMapper(),
                transactionTemplate,
                new LayoutSearchProperties(),
                mock(ThreadPoolTaskExecutor.class),
                mock(ThreadPoolTaskExecutor.class));

        LayoutSearchEntity cancelled = orchestrator.cancel(20L, new JwtUser(1L, Set.of("OPERATOR")));

        assertThat(cancelled).isSameAs(search);
        verify(runner).cancel(20L);
    }

    private static LayoutSearchEntity activeSearch(long searchId, long simulationId) {
        LayoutSearchEntity search = new LayoutSearchEntity();
        search.setId(searchId);
        search.setBaselineSimulationId(simulationId);
        search.setStatus("GENERATING");
        return search;
    }
}
