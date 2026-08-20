package com.hwalro.simulation.improvement.service;

import com.hwalro.simulation.improvement.domain.ImprovementProposal;
import com.hwalro.simulation.improvement.domain.ImprovementSource;
import com.hwalro.simulation.improvement.domain.ProposalCandidate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

/** 원본 시뮬레이션 입력에서 탐색한 상위 개선안을 저장 가능한 형태로 재생성합니다. */
@Service
public class ImprovementProposalGenerationService {
    private final ImprovementSourceLoader improvementSourceLoader;
    private final BottleneckFabricSelector bottleneckFabricSelector;
    private final ProposalEvaluationService proposalEvaluationService;
    private final ImprovementProposalService improvementProposalService;
    private final ProposalBeamSearch proposalBeamSearch = new ProposalBeamSearch(new FabricCandidateGenerator());
    private final ProposalScorer proposalScorer = new ProposalScorer();

    public ImprovementProposalGenerationService(
            ImprovementSourceLoader improvementSourceLoader,
            BottleneckFabricSelector bottleneckFabricSelector,
            ProposalEvaluationService proposalEvaluationService,
            ImprovementProposalService improvementProposalService) {
        this.improvementSourceLoader = improvementSourceLoader;
        this.bottleneckFabricSelector = bottleneckFabricSelector;
        this.proposalEvaluationService = proposalEvaluationService;
        this.improvementProposalService = improvementProposalService;
    }

    /** 병목 주변 후보를 점수순으로 최대 3개 생성하고, 저장되지 않은 이전 결과를 교체합니다. */
    public List<ImprovementProposal> regenerate(long simulationId) {
        ImprovementSource source = improvementSourceLoader.load(simulationId);
        requireSearchInput(source);

        Map<ProposalCandidate, Double> scores = new HashMap<>();
        ProposalConstraintValidator validator = new ProposalConstraintValidator(
                source.floorWidth(), source.floorHeight(), source.fixedObstacles(), source.fabrics());
        List<ProposalCandidate> candidates = proposalBeamSearch.findTopCandidates(
                bottleneckFabricSelector.select(source.fabrics(), source.bottlenecks()),
                validator::isValid,
                candidate -> scores.computeIfAbsent(
                        candidate, value -> proposalScorer.score(proposalEvaluationService.evaluate(value, source))));
        return improvementProposalService.replaceUnsaved(simulationId, candidates);
    }

    private void requireSearchInput(ImprovementSource source) {
        // 병목 또는 출구가 없으면 측정 기준 자체가 없으므로 기존 제안도 교체하지 않습니다.
        if (source.bottlenecks().isEmpty()) {
            throw new IllegalArgumentException("개선안 탐색을 위한 병목 결과가 없습니다.");
        }
        if (source.exits().isEmpty()) {
            throw new IllegalArgumentException("개선안 탐색을 위한 출구 정보가 없습니다.");
        }
    }
}
