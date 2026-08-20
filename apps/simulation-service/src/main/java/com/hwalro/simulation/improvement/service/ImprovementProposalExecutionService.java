package com.hwalro.simulation.improvement.service;

import com.hwalro.simulation.common.jwt.ForbiddenException;
import com.hwalro.simulation.common.jwt.JwtUser;
import com.hwalro.simulation.improvement.domain.ImprovementProposal;
import com.hwalro.simulation.improvement.dto.ImprovementProposalExecutionResponse;
import com.hwalro.simulation.improvement.dto.ImprovementProposalExecutionResult;
import com.hwalro.simulation.improvement.mapper.ImprovementProposalMapper;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import org.springframework.stereotype.Service;

@Service
public class ImprovementProposalExecutionService {
    private static final int MAX_PROPOSALS = 3;
    private static final String EXECUTION_START_FAILED_MESSAGE = "시뮬레이션 실행을 시작할 수 없습니다.";

    private final ImprovementProposalMapper improvementProposalMapper;
    private final ImprovementProposalExecutionReservationService improvementProposalExecutionReservationService;

    public ImprovementProposalExecutionService(
            ImprovementProposalMapper improvementProposalMapper,
            ImprovementProposalExecutionReservationService improvementProposalExecutionReservationService) {
        this.improvementProposalMapper = improvementProposalMapper;
        this.improvementProposalExecutionReservationService = improvementProposalExecutionReservationService;
    }

    public ImprovementProposalExecutionResponse execute(long sourceSimulationId, List<Long> proposalIds, JwtUser user) {
        validateProposalIds(proposalIds);
        requireSourceSimulationAccess(sourceSimulationId, user);
        Map<Long, ImprovementProposal> proposals = proposalsById(sourceSimulationId);
        List<ImprovementProposal> selected =
                proposalIds.stream().map(id -> selectedProposal(proposals, id)).toList();

        List<CompletableFuture<ImprovementProposalExecutionResult>> executions = selected.stream()
                .map(proposal -> CompletableFuture.supplyAsync(() -> executeOne(proposal, user.userId())))
                .toList();
        return new ImprovementProposalExecutionResponse(
                executions.stream().map(CompletableFuture::join).toList());
    }

    private Map<Long, ImprovementProposal> proposalsById(long sourceSimulationId) {
        Map<Long, ImprovementProposal> proposals = new HashMap<>();
        for (ImprovementProposal proposal : improvementProposalMapper.findBySourceSimulationId(sourceSimulationId)) {
            proposals.put(proposal.getId(), proposal);
        }
        return proposals;
    }

    private ImprovementProposal selectedProposal(Map<Long, ImprovementProposal> proposals, Long proposalId) {
        ImprovementProposal proposal = proposals.get(proposalId);
        if (proposal == null) {
            throw new IllegalArgumentException("원본 시뮬레이션의 개선안만 실행할 수 있습니다.");
        }
        if (proposal.getSavedLayoutVersionId() == null) {
            throw new IllegalArgumentException("저장된 배치 버전이 있는 개선안만 실행할 수 있습니다.");
        }
        return proposal;
    }

    private ImprovementProposalExecutionResult executeOne(ImprovementProposal proposal, long requestedBy) {
        try {
            return improvementProposalExecutionReservationService.execute(proposal, requestedBy);
        } catch (RuntimeException exception) {
            return new ImprovementProposalExecutionResult(
                    proposal.getId(), null, "FAILED", EXECUTION_START_FAILED_MESSAGE);
        }
    }

    private void requireSourceSimulationAccess(long sourceSimulationId, JwtUser user) {
        Long createdBy = improvementProposalMapper.findCreatedByBySimulationId(sourceSimulationId);
        if (createdBy == null) {
            throw new IllegalArgumentException("원본 시뮬레이션을 찾을 수 없습니다.");
        }
        if (user.roles().contains("ADMIN") || user.roles().contains("SAFETY_REVIEWER")) {
            return;
        }
        if (user.roles().contains("OPERATOR") && user.userId().equals(createdBy)) {
            return;
        }
        throw new ForbiddenException("이 시뮬레이션의 개선안을 실행할 권한이 없습니다.");
    }

    private void validateProposalIds(List<Long> proposalIds) {
        if (proposalIds == null || proposalIds.isEmpty() || proposalIds.size() > MAX_PROPOSALS) {
            throw new IllegalArgumentException("개선안은 1개 이상 3개 이하로 선택해야 합니다.");
        }
        if (proposalIds.stream().anyMatch(id -> id == null || id <= 0)
                || proposalIds.stream().distinct().count() != proposalIds.size()) {
            throw new IllegalArgumentException("개선안 ID는 중복 없이 전달해야 합니다.");
        }
    }
}
