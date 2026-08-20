package com.hwalro.simulation.improvement.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hwalro.simulation.improvement.domain.BottleneckArea;
import com.hwalro.simulation.improvement.domain.FabricState;
import com.hwalro.simulation.improvement.domain.ImprovementProposal;
import com.hwalro.simulation.improvement.domain.ImprovementSource;
import com.hwalro.simulation.improvement.domain.ProposalCandidate;
import com.hwalro.simulation.improvement.domain.ProposalEvaluation;
import com.hwalro.simulation.improvement.geometry.RotatedRectangle;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ImprovementProposalGenerationServiceTest {
    @Mock
    private ImprovementSourceLoader improvementSourceLoader;

    @Mock
    private BottleneckFabricSelector bottleneckFabricSelector;

    @Mock
    private ProposalEvaluationService proposalEvaluationService;

    @Mock
    private ImprovementProposalService improvementProposalService;

    @Test
    void searchesScoresAndStoresCandidatesNearBottlenecks() {
        ImprovementSource source = source();
        when(improvementSourceLoader.load(1L)).thenReturn(source);
        when(bottleneckFabricSelector.select(source.fabrics(), source.bottlenecks()))
                .thenReturn(source.fabrics());
        when(proposalEvaluationService.evaluate(any(ProposalCandidate.class), eq(source)))
                .thenAnswer(invocation -> new ProposalEvaluation(invocation.getArgument(0), 1, 1, 0, 0));
        when(improvementProposalService.replaceUnsaved(eq(1L), any())).thenReturn(List.of());

        List<ImprovementProposal> proposals = service().regenerate(1L);

        assertEquals(List.of(), proposals);
        verify(proposalEvaluationService, atLeastOnce()).evaluate(any(ProposalCandidate.class), eq(source));
        verify(improvementProposalService).replaceUnsaved(eq(1L), any());
    }

    @Test
    void keepsExistingProposalsUntouchedWhenNoBottleneckExists() {
        ImprovementSource source =
                new ImprovementSource(1, 100, 100, List.of(), List.of(), List.of(), List.of(), List.of());
        when(improvementSourceLoader.load(1L)).thenReturn(source);

        assertThrows(IllegalArgumentException.class, () -> service().regenerate(1L));

        verify(improvementProposalService, never()).replaceUnsaved(anyLong(), any());
    }

    private ImprovementProposalGenerationService service() {
        return new ImprovementProposalGenerationService(
                improvementSourceLoader,
                bottleneckFabricSelector,
                proposalEvaluationService,
                improvementProposalService);
    }

    private ImprovementSource source() {
        List<FabricState> fabrics = List.of(
                new FabricState(1, RotatedRectangle.of(10, 10, 12, 12, 0)),
                new FabricState(2, RotatedRectangle.of(20, 10, 22, 12, 0)));
        RotatedRectangle exit = RotatedRectangle.of(90, 45, 90, 55, 0);
        return new ImprovementSource(
                1,
                100,
                100,
                fabrics,
                List.of(exit),
                List.of(exit),
                List.of(new BottleneckArea(RotatedRectangle.of(15, 10, 17, 12, 0), 0, 10)),
                List.of());
    }
}
