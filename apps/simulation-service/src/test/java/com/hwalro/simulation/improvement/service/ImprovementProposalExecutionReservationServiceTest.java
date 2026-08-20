package com.hwalro.simulation.improvement.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hwalro.simulation.improvement.domain.ImprovementProposal;
import com.hwalro.simulation.improvement.dto.ImprovementProposalExecutionResult;
import com.hwalro.simulation.improvement.mapper.ImprovementProposalMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ImprovementProposalExecutionReservationServiceTest {
    @Mock
    private ImprovementProposalMapper improvementProposalMapper;

    @Mock
    private SimulationResultGenerationClient simulationResultGenerationClient;

    @Test
    void locksTheProposalBeforeStartingAndLinkingItsSimulation() {
        when(simulationResultGenerationClient.start(10L, 101L, 9L))
                .thenReturn(new SimulationResultGenerationClient.SimulationStartResult(1_101L, "REQUESTED"));

        ImprovementProposalExecutionResult result = reservationService().execute(proposal(), 9L);

        assertEquals(1_101L, result.simulationId());
        InOrder inOrder = inOrder(improvementProposalMapper, simulationResultGenerationClient);
        inOrder.verify(improvementProposalMapper).lockProposalId(1L);
        inOrder.verify(improvementProposalMapper).findSimulationIdByProposalId(1L);
        inOrder.verify(simulationResultGenerationClient).start(10L, 101L, 9L);
        inOrder.verify(improvementProposalMapper).insertSimulationLink(1L, 1_101L, 10L);
    }

    @Test
    void returnsAnExistingSimulationWithoutStartingAnotherOne() {
        when(improvementProposalMapper.findSimulationIdByProposalId(1L)).thenReturn(1_101L);

        ImprovementProposalExecutionResult result = reservationService().execute(proposal(), 9L);

        assertEquals("ALREADY_REQUESTED", result.status());
        assertEquals(1_101L, result.simulationId());
        verify(simulationResultGenerationClient, never()).start(10L, 101L, 9L);
    }

    private ImprovementProposalExecutionReservationService reservationService() {
        return new ImprovementProposalExecutionReservationService(
                improvementProposalMapper, simulationResultGenerationClient);
    }

    private ImprovementProposal proposal() {
        ImprovementProposal proposal = new ImprovementProposal();
        proposal.setId(1L);
        proposal.setSourceSimulationId(10L);
        proposal.setSavedLayoutVersionId(101L);
        return proposal;
    }
}
