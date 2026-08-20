package com.hwalro.simulation.improvement.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hwalro.simulation.common.jwt.JwtUser;
import com.hwalro.simulation.improvement.dto.ImprovementProposalExecutionRequest;
import com.hwalro.simulation.improvement.dto.ImprovementProposalExecutionResponse;
import com.hwalro.simulation.improvement.dto.ImprovementProposalResponse;
import com.hwalro.simulation.improvement.service.ImprovementProposalExecutionService;
import com.hwalro.simulation.improvement.service.ImprovementProposalGenerationService;
import com.hwalro.simulation.improvement.service.ImprovementProposalQueryService;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class ImprovementProposalControllerTest {
    @Mock
    private ImprovementProposalQueryService improvementProposalQueryService;

    @Mock
    private ImprovementProposalGenerationService improvementProposalGenerationService;

    @Mock
    private ImprovementProposalExecutionService improvementProposalExecutionService;

    @Test
    void regeneratesThenReturnsTheLatestProposalList() {
        List<ImprovementProposalResponse> expected = List.of();
        when(improvementProposalQueryService.list(1L)).thenReturn(expected);

        List<ImprovementProposalResponse> result = controller().regenerate(1L);

        assertEquals(expected, result);
        verify(improvementProposalGenerationService).regenerate(1L);
        verify(improvementProposalQueryService).list(1L);
    }

    @Test
    void reportsConflictWhenSavedProposalsBlockRegeneration() {
        org.mockito.Mockito.doThrow(new IllegalStateException("저장된 개선안이 있습니다."))
                .when(improvementProposalGenerationService)
                .regenerate(1L);

        ResponseStatusException exception =
                assertThrows(ResponseStatusException.class, () -> controller().regenerate(1L));

        assertEquals(HttpStatus.CONFLICT, exception.getStatusCode());
    }

    @Test
    void executesSelectedProposalsForTheAuthenticatedUser() {
        ImprovementProposalExecutionResponse expected = new ImprovementProposalExecutionResponse(List.of());
        when(improvementProposalExecutionService.execute(1L, List.of(11L), user()))
                .thenReturn(expected);

        ImprovementProposalExecutionResponse result =
                controller().execute(1L, new ImprovementProposalExecutionRequest(List.of(11L)), user());

        assertEquals(expected, result);
        verify(improvementProposalExecutionService).execute(1L, List.of(11L), user());
    }

    @Test
    void reportsInvalidExecutionRequestAsBadRequest() {
        when(improvementProposalExecutionService.execute(1L, List.of(), user()))
                .thenThrow(new IllegalArgumentException("개선안은 1개 이상 3개 이하로 선택해야 합니다."));

        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () -> controller()
                .execute(1L, new ImprovementProposalExecutionRequest(List.of()), user()));

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
    }

    private ImprovementProposalController controller() {
        return new ImprovementProposalController(
                improvementProposalQueryService,
                improvementProposalGenerationService,
                improvementProposalExecutionService);
    }

    private JwtUser user() {
        return new JwtUser(9L, Set.of("OPERATOR"));
    }
}
