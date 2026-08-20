package com.hwalro.simulation.improvement.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hwalro.simulation.improvement.domain.FabricChange;
import com.hwalro.simulation.improvement.domain.ImprovementProposal;
import com.hwalro.simulation.improvement.domain.ProposalCandidate;
import com.hwalro.simulation.improvement.geometry.RotatedRectangle;
import com.hwalro.simulation.improvement.mapper.ImprovementProposalMapper;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ImprovementProposalServiceTest {
    @Mock
    private ImprovementProposalMapper improvementProposalMapper;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void replacesUnsavedCandidatesWithRankedProposalTypesAndChangeData() throws Exception {
        when(improvementProposalMapper.existsSavedBySourceSimulationId(1L)).thenReturn(false);
        ImprovementProposalService service = new ImprovementProposalService(improvementProposalMapper, objectMapper);

        List<ImprovementProposal> proposals =
                service.replaceUnsaved(1L, List.of(candidate(10), candidate(20), candidate(30)));

        ArgumentCaptor<ImprovementProposal> captor = ArgumentCaptor.forClass(ImprovementProposal.class);
        InOrder inOrder = inOrder(improvementProposalMapper);
        inOrder.verify(improvementProposalMapper).lockSourceSimulationId(1L);
        inOrder.verify(improvementProposalMapper).existsSavedBySourceSimulationId(1L);
        verify(improvementProposalMapper).deleteUnsavedBySourceSimulationId(1L);
        verify(improvementProposalMapper, org.mockito.Mockito.times(3)).insert(captor.capture());
        assertEquals(
                List.of("MINIMAL", "BALANCED", "MAXIMUM"),
                proposals.stream().map(ImprovementProposal::getProposalType).toList());
        JsonNode operation = objectMapper
                .readTree(captor.getAllValues().get(0).getChangeData())
                .path("operations")
                .get(0);
        assertEquals("FABRIC", operation.path("facilityType").asText());
        assertEquals(30.0, operation.path("after").path("rotation").asDouble());
    }

    @Test
    void blocksRegenerationWhenAnyProposalWasSaved() {
        when(improvementProposalMapper.existsSavedBySourceSimulationId(1L)).thenReturn(true);
        ImprovementProposalService service = new ImprovementProposalService(improvementProposalMapper, objectMapper);

        assertThrows(IllegalStateException.class, () -> service.replaceUnsaved(1L, List.of(candidate(10))));

        verify(improvementProposalMapper, never()).deleteUnsavedBySourceSimulationId(any());
        verify(improvementProposalMapper, never()).insert(any());
    }

    private static ProposalCandidate candidate(long fabricId) {
        return new ProposalCandidate(List.of(new FabricChange(
                fabricId, RotatedRectangle.of(10, 10, 14, 12, 0), RotatedRectangle.of(12, 10, 16, 12, 30))));
    }
}
