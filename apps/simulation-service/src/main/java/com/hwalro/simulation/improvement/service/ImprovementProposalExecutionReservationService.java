package com.hwalro.simulation.improvement.service;

import com.hwalro.simulation.improvement.domain.ImprovementProposal;
import com.hwalro.simulation.improvement.dto.ImprovementProposalExecutionResult;
import com.hwalro.simulation.improvement.mapper.ImprovementProposalMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 개선안 한 건을 잠근 뒤 결과 생성과 계보 연결을 하나의 트랜잭션으로 처리합니다. */
@Service
public class ImprovementProposalExecutionReservationService {
    private final ImprovementProposalMapper improvementProposalMapper;
    private final SimulationResultGenerationClient simulationResultGenerationClient;

    public ImprovementProposalExecutionReservationService(
            ImprovementProposalMapper improvementProposalMapper,
            SimulationResultGenerationClient simulationResultGenerationClient) {
        this.improvementProposalMapper = improvementProposalMapper;
        this.simulationResultGenerationClient = simulationResultGenerationClient;
    }

    @Transactional
    public ImprovementProposalExecutionResult execute(ImprovementProposal proposal, long requestedBy) {
        improvementProposalMapper.lockProposalId(proposal.getId());
        Long existingSimulationId = improvementProposalMapper.findSimulationIdByProposalId(proposal.getId());
        if (existingSimulationId != null && existingSimulationId > 0) {
            return new ImprovementProposalExecutionResult(
                    proposal.getId(), existingSimulationId, "ALREADY_REQUESTED", null);
        }
        SimulationResultGenerationClient.SimulationStartResult started = simulationResultGenerationClient.start(
                proposal.getSourceSimulationId(), proposal.getSavedLayoutVersionId(), requestedBy);
        improvementProposalMapper.insertSimulationLink(
                proposal.getId(), started.simulationId(), proposal.getSourceSimulationId());
        return new ImprovementProposalExecutionResult(proposal.getId(), started.simulationId(), started.status(), null);
    }
}
