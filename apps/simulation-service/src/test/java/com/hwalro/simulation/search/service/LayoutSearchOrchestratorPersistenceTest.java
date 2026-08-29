package com.hwalro.simulation.search.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hwalro.simulation.drawing.mapper.DrawingMapper;
import com.hwalro.simulation.search.config.LayoutSearchProperties;
import com.hwalro.simulation.search.domain.ChangeOp;
import com.hwalro.simulation.search.domain.LayoutSearchCandidateEntity;
import com.hwalro.simulation.search.domain.SearchResult;
import com.hwalro.simulation.search.mapper.LayoutSearchMapper;
import com.hwalro.simulation.search.mapper.LayoutSearchSourceMapper;
import com.hwalro.simulation.simulation.service.SimulationService;
import com.hwalro.simulation.zone.service.SearchConstraintProjector;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.transaction.support.TransactionTemplate;

class LayoutSearchOrchestratorPersistenceTest {

    @Test
    void doesNotQueueCandidateWhenCancellationWinsAfterPlannerResultBeforePersistence() {
        LayoutSearchMapper mapper = mock(LayoutSearchMapper.class);
        when(mapper.findCandidatesBySearchId(30L)).thenReturn(List.of());
        when(mapper.insertCandidateIfSearchActive(any())).thenReturn(0);
        LayoutSearchOrchestrator orchestrator = orchestrator(mapper);
        List<LayoutSearchCandidateEntity> queued = new ArrayList<>();

        orchestrator.persistCandidates(30L, 1, result(), queued, null);

        assertThat(queued).isEmpty();
        verify(mapper).insertCandidateIfSearchActive(any());
    }

    private static LayoutSearchOrchestrator orchestrator(LayoutSearchMapper mapper) {
        return new LayoutSearchOrchestrator(
                mapper,
                mock(LayoutSearchSourceMapper.class),
                mock(LayoutSearchSourceLoader.class),
                mock(LayoutSearchRunner.class),
                mock(CandidateTrialService.class),
                mock(SimulationService.class),
                mock(DrawingMapper.class),
                mock(SearchConstraintProjector.class),
                new ObjectMapper(),
                mock(TransactionTemplate.class),
                new LayoutSearchProperties(),
                mock(ThreadPoolTaskExecutor.class),
                mock(ThreadPoolTaskExecutor.class));
    }

    private static SearchResult result() {
        ChangeOp.FabricTransform before = transform("0", "0", "1", "2");
        ChangeOp.FabricTransform after = transform("3", "4", "4", "6");
        ChangeOp operation = new ChangeOp("MOVE_FABRIC", 5L, before, after);
        SearchResult.SearchCandidate candidate = new SearchResult.SearchCandidate(
                "IDEAL_ROUTE",
                "BOUNDARY_DOCKING",
                null,
                1.0,
                1.0,
                List.of(operation),
                new ObjectMapper().createObjectNode());
        return new SearchResult("IDEAL_ROUTE_DOCKING_V2", List.of(candidate), List.of());
    }

    private static ChangeOp.FabricTransform transform(String startX, String startY, String endX, String endY) {
        return new ChangeOp.FabricTransform(
                new BigDecimal(startX),
                new BigDecimal(startY),
                new BigDecimal(endX),
                new BigDecimal(endY),
                BigDecimal.ZERO);
    }
}
