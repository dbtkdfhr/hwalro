package com.hwalro.simulation.improvement.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hwalro.simulation.common.jwt.ForbiddenException;
import com.hwalro.simulation.common.jwt.JwtUser;
import com.hwalro.simulation.improvement.domain.ImprovementProposal;
import com.hwalro.simulation.improvement.dto.ImprovementProposalExecutionResponse;
import com.hwalro.simulation.improvement.dto.ImprovementProposalExecutionResult;
import com.hwalro.simulation.improvement.mapper.ImprovementProposalMapper;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ImprovementProposalExecutionServiceTest {
    @Mock
    private ImprovementProposalMapper improvementProposalMapper;

    @Mock
    private ImprovementProposalExecutionReservationService reservationService;

    @Test
    void startsEverySelectedProposalAfterCheckingOperatorOwnership() {
        when(improvementProposalMapper.findCreatedByBySimulationId(10L)).thenReturn(9L);
        when(improvementProposalMapper.findBySourceSimulationId(10L))
                .thenReturn(List.of(proposal(1L, 101L), proposal(2L, 102L), proposal(3L, 103L)));
        when(reservationService.execute(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(9L)))
                .thenAnswer(invocation -> {
                    ImprovementProposal proposal = invocation.getArgument(0);
                    return new ImprovementProposalExecutionResult(
                            proposal.getId(), proposal.getSavedLayoutVersionId() + 1_000L, "REQUESTED", null);
                });

        ImprovementProposalExecutionResponse response = service().execute(10L, List.of(1L, 2L, 3L), operator());

        assertEquals(
                List.of(1_101L, 1_102L, 1_103L),
                response.results().stream()
                        .map(ImprovementProposalExecutionResult::simulationId)
                        .toList());
        verify(reservationService)
                .execute(
                        org.mockito.ArgumentMatchers.argThat(proposal -> proposal.getId() == 1L),
                        org.mockito.ArgumentMatchers.eq(9L));
        verify(reservationService)
                .execute(
                        org.mockito.ArgumentMatchers.argThat(proposal -> proposal.getId() == 2L),
                        org.mockito.ArgumentMatchers.eq(9L));
        verify(reservationService)
                .execute(
                        org.mockito.ArgumentMatchers.argThat(proposal -> proposal.getId() == 3L),
                        org.mockito.ArgumentMatchers.eq(9L));
    }

    @Test
    void rejectsInvalidOrUnsavedProposalsBeforeReservation() {
        assertThrows(IllegalArgumentException.class, () -> service().execute(10L, List.of(), operator()));
        assertThrows(IllegalArgumentException.class, () -> service().execute(10L, List.of(1L, 1L), operator()));
        assertThrows(IllegalArgumentException.class, () -> service().execute(10L, List.of(1L, 2L, 3L, 4L), operator()));

        when(improvementProposalMapper.findCreatedByBySimulationId(10L)).thenReturn(9L);
        when(improvementProposalMapper.findBySourceSimulationId(10L)).thenReturn(List.of(proposal(1L, null)));
        assertThrows(IllegalArgumentException.class, () -> service().execute(10L, List.of(1L), operator()));
        assertThrows(IllegalArgumentException.class, () -> service().execute(10L, List.of(2L), operator()));
    }

    @Test
    void preventsAnOperatorFromExecutingAnotherUsersProposal() {
        when(improvementProposalMapper.findCreatedByBySimulationId(10L)).thenReturn(8L);

        assertThrows(ForbiddenException.class, () -> service().execute(10L, List.of(1L), operator()));
    }

    @Test
    void allowsSafetyReviewersToExecuteAnySourceSimulation() {
        when(improvementProposalMapper.findCreatedByBySimulationId(10L)).thenReturn(8L);
        when(improvementProposalMapper.findBySourceSimulationId(10L)).thenReturn(List.of(proposal(1L, 101L)));
        when(reservationService.execute(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(9L)))
                .thenReturn(new ImprovementProposalExecutionResult(1L, 1_101L, "REQUESTED", null));

        ImprovementProposalExecutionResponse response =
                service().execute(10L, List.of(1L), new JwtUser(9L, Set.of("SAFETY_REVIEWER")));

        assertEquals("REQUESTED", response.results().get(0).status());
    }

    @Test
    void hidesInternalExecutionErrorsFromThePublicResult() {
        when(improvementProposalMapper.findCreatedByBySimulationId(10L)).thenReturn(9L);
        when(improvementProposalMapper.findBySourceSimulationId(10L)).thenReturn(List.of(proposal(1L, 101L)));
        when(reservationService.execute(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.eq(9L)))
                .thenThrow(new IllegalStateException("database host and credentials"));

        ImprovementProposalExecutionResponse response = service().execute(10L, List.of(1L), operator());

        assertEquals("FAILED", response.results().get(0).status());
        assertEquals("시뮬레이션 실행을 시작할 수 없습니다.", response.results().get(0).errorMessage());
    }

    private ImprovementProposalExecutionService service() {
        return new ImprovementProposalExecutionService(improvementProposalMapper, reservationService);
    }

    private JwtUser operator() {
        return new JwtUser(9L, Set.of("OPERATOR"));
    }

    private static ImprovementProposal proposal(long id, Long savedLayoutVersionId) {
        ImprovementProposal proposal = new ImprovementProposal();
        proposal.setId(id);
        proposal.setSourceSimulationId(10L);
        proposal.setSavedLayoutVersionId(savedLayoutVersionId);
        return proposal;
    }
}
