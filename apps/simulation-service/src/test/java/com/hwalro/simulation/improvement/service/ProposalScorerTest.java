package com.hwalro.simulation.improvement.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.hwalro.simulation.improvement.domain.FabricChange;
import com.hwalro.simulation.improvement.domain.ProposalCandidate;
import com.hwalro.simulation.improvement.domain.ProposalEvaluation;
import com.hwalro.simulation.improvement.geometry.RotatedRectangle;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProposalScorerTest {
    private final ProposalScorer scorer = new ProposalScorer();

    @Test
    void appliesTheAgreedWeightsAndClearanceLossPenalty() {
        RotatedRectangle before = RotatedRectangle.of(0, 0, 2, 1, 0);
        ProposalCandidate candidate = new ProposalCandidate(List.of(new FabricChange(1, before, before.moveBy(1, 0))));
        ProposalEvaluation evaluation = new ProposalEvaluation(candidate, 2, 1, 0.5, 0.5);

        assertEquals(70, scorer.score(evaluation));
    }
}
