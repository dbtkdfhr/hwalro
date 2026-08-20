package com.hwalro.simulation.improvement.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hwalro.simulation.improvement.domain.ImprovementProposal;
import com.hwalro.simulation.improvement.dto.ImprovementProposalResponse;
import com.hwalro.simulation.improvement.mapper.ImprovementProposalMapper;
import java.util.List;
import org.springframework.stereotype.Service;

/** DB JSON 문자열을 API에서 바로 사용할 구조화된 개선안으로 변환합니다. */
@Service
public class ImprovementProposalQueryService {
    private final ImprovementProposalMapper improvementProposalMapper;
    private final ObjectMapper objectMapper;

    public ImprovementProposalQueryService(
            ImprovementProposalMapper improvementProposalMapper, ObjectMapper objectMapper) {
        this.improvementProposalMapper = improvementProposalMapper;
        this.objectMapper = objectMapper;
    }

    /** 원본 시뮬레이션의 개선안을 순위순으로 조회합니다. */
    public List<ImprovementProposalResponse> list(long sourceSimulationId) {
        return improvementProposalMapper.findBySourceSimulationId(sourceSimulationId).stream()
                .map(this::toResponse)
                .toList();
    }

    private ImprovementProposalResponse toResponse(ImprovementProposal proposal) {
        return new ImprovementProposalResponse(
                proposal.getId(),
                proposal.getSavedLayoutVersionId(),
                proposal.getProposalOrder(),
                proposal.getProposalType(),
                proposal.getTitle(),
                proposal.getDescription(),
                readJson(proposal.getChangeData()),
                readJson(proposal.getChangeSummary()),
                proposal.getCreatedAt(),
                proposal.getSavedAt());
    }

    private JsonNode readJson(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (JsonProcessingException exception) {
            // DB JSON 컬럼이 손상되면 일부 필드를 숨기지 않고 요청 전체를 실패시킵니다.
            throw new IllegalStateException("저장된 개선안 JSON을 읽을 수 없습니다.", exception);
        }
    }
}
