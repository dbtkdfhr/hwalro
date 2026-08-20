package com.hwalro.simulation.improvement.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hwalro.simulation.improvement.domain.ImprovementProposal;
import com.hwalro.simulation.improvement.dto.ImprovementProposalResponse;
import com.hwalro.simulation.improvement.mapper.ImprovementProposalMapper;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ImprovementProposalQueryServiceTest {
    @Mock
    private ImprovementProposalMapper improvementProposalMapper;

    @Test
    void returnsStoredJsonAsObjectsInsteadOfEscapedStrings() {
        ImprovementProposal proposal = new ImprovementProposal();
        proposal.setId(1L);
        proposal.setProposalOrder(1);
        proposal.setChangeData("{\"operations\":[{\"facilityId\":12}]}");
        proposal.setChangeSummary("{\"summary\":\"시설 이동\"}");
        when(improvementProposalMapper.findBySourceSimulationId(10L)).thenReturn(List.of(proposal));

        List<ImprovementProposalResponse> responses =
                new ImprovementProposalQueryService(improvementProposalMapper, new ObjectMapper()).list(10L);

        assertEquals(
                12,
                responses
                        .get(0)
                        .changeData()
                        .path("operations")
                        .get(0)
                        .path("facilityId")
                        .asInt());
        assertEquals("시설 이동", responses.get(0).changeSummary().path("summary").asText());
    }

    @Test
    void failsWhenStoredJsonIsNull() {
        ImprovementProposal proposal = new ImprovementProposal();
        proposal.setChangeData(null);
        when(improvementProposalMapper.findBySourceSimulationId(10L)).thenReturn(List.of(proposal));

        assertThrows(
                IllegalArgumentException.class,
                () -> new ImprovementProposalQueryService(improvementProposalMapper, new ObjectMapper()).list(10L));
    }

    @Test
    void failsWhenStoredJsonIsMalformed() {
        ImprovementProposal proposal = new ImprovementProposal();
        proposal.setChangeData("{");
        proposal.setChangeSummary("{}");
        when(improvementProposalMapper.findBySourceSimulationId(10L)).thenReturn(List.of(proposal));

        assertThrows(
                IllegalStateException.class,
                () -> new ImprovementProposalQueryService(improvementProposalMapper, new ObjectMapper()).list(10L));
    }
}
