package com.hwalro.simulation.improvement.service;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hwalro.simulation.improvement.domain.BottleneckArea;
import com.hwalro.simulation.improvement.domain.FabricChange;
import com.hwalro.simulation.improvement.domain.FabricState;
import com.hwalro.simulation.improvement.domain.ImprovementSource;
import com.hwalro.simulation.improvement.domain.ProposalCandidate;
import com.hwalro.simulation.improvement.domain.ProposalEvaluation;
import com.hwalro.simulation.improvement.geometry.RotatedRectangle;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProposalEvaluationServiceTest {
    private final ProposalEvaluationService evaluationService = new ProposalEvaluationService(
            new CorridorClearanceEvaluator(),
            new NearbyClearanceEvaluator(),
            new HeatmapOverlapEvaluator(new ObjectMapper()));

    @Test
    void measuresCandidateCorridorImprovementAcrossTheBottleneck() {
        FabricState fabric = new FabricState(1, RotatedRectangle.of(11, 9, 12, 11, 0));
        ProposalCandidate candidate = new ProposalCandidate(
                List.of(new FabricChange(1, fabric.bounds(), RotatedRectangle.of(18, 9, 20, 11, 0))));
        ImprovementSource source = new ImprovementSource(
                1,
                30,
                20,
                List.of(fabric),
                List.of(RotatedRectangle.of(28, 8, 28, 12, 0)),
                List.of(RotatedRectangle.of(28, 8, 28, 12, 0)),
                List.of(new BottleneckArea(RotatedRectangle.of(10, 8, 12, 12, 0), 0, 10)),
                List.of());

        ProposalEvaluation evaluation = evaluationService.evaluate(candidate, source);

        assertTrue(evaluation.bottleneckWidthIncrease() > 0);
    }
}
