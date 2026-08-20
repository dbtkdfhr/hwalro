package com.hwalro.simulation.improvement.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hwalro.simulation.improvement.domain.FabricState;
import com.hwalro.simulation.improvement.domain.ProposalCandidate;
import com.hwalro.simulation.improvement.geometry.RotatedRectangle;
import java.util.List;
import org.junit.jupiter.api.Test;

class FabricCandidateGeneratorTest {
    private static final double EPSILON = 0.0000001;

    private final FabricCandidateGenerator generator = new FabricCandidateGenerator();

    @Test
    void generatesStationaryAndEightDirectionCandidatesAtTwoDistances() {
        FabricState fabric = new FabricState(1, RotatedRectangle.of(0, 0, 2, 1, 0));

        List<ProposalCandidate> candidates = generator.generateSingleChanges(fabric);

        assertEquals(612, candidates.size());
        assertTrue(candidates.stream().anyMatch(candidate -> isChange(candidate, 2.0, 0.5, 0.0)));
        assertTrue(candidates.stream().anyMatch(candidate -> isChange(candidate, 3.0, 0.5, 10.0)));
        assertTrue(candidates.stream()
                .anyMatch(candidate -> isChange(candidate, 1 + Math.sqrt(0.5), 0.5 + Math.sqrt(0.5), 0.0)));
        assertTrue(candidates.stream().anyMatch(candidate -> isChange(candidate, 1.0, 0.5, 30.0)));
    }

    @Test
    void neverAddsTheSameFabricAsTheSecondChange() {
        FabricState first = new FabricState(1, RotatedRectangle.of(0, 0, 2, 1, 0));
        FabricState second = new FabricState(2, RotatedRectangle.of(4, 0, 6, 1, 0));
        ProposalCandidate base = generator.generateSingleChanges(first).get(0);

        List<ProposalCandidate> candidates = generator.addSecondChanges(base, List.of(first, second));

        assertFalse(candidates.isEmpty());
        assertFalse(candidates.stream()
                .anyMatch(candidate -> candidate.changes().get(1).fabricId() == first.id()));
    }

    private boolean isChange(ProposalCandidate candidate, double expectedX, double expectedY, double expectedRotation) {
        RotatedRectangle after = candidate.changes().get(0).after();
        return Math.abs(after.center().x() - expectedX) < EPSILON
                && Math.abs(after.center().y() - expectedY) < EPSILON
                && Math.abs(after.clockwiseDegrees() - expectedRotation) < EPSILON;
    }
}
