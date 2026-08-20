package com.hwalro.simulation.improvement.service;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.hwalro.simulation.improvement.domain.FabricState;
import com.hwalro.simulation.improvement.domain.ProposalCandidate;
import com.hwalro.simulation.improvement.geometry.RotatedRectangle;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProposalBeamSearchTest {
    private final ProposalBeamSearch beamSearch = new ProposalBeamSearch(new FabricCandidateGenerator());

    @Test
    void returnsOnlyThreeValidTwoFabricCandidates() {
        List<FabricState> fabrics = List.of(
                new FabricState(1, RotatedRectangle.of(0, 0, 2, 1, 0)),
                new FabricState(2, RotatedRectangle.of(4, 0, 6, 1, 0)),
                new FabricState(3, RotatedRectangle.of(8, 0, 10, 1, 0)));

        List<ProposalCandidate> results =
                beamSearch.findTopCandidates(fabrics, candidate -> true, candidate -> -candidate.totalMoveDistance());

        assertEquals(3, results.size());
        assertEquals(2, results.get(0).changes().size());
    }

    @Test
    void fallsBackToSingleFabricCandidatesWhenNoSecondChangeExists() {
        List<FabricState> fabrics = List.of(new FabricState(1, RotatedRectangle.of(4, 4, 6, 6, 0)));

        List<ProposalCandidate> results =
                beamSearch.findTopCandidates(fabrics, candidate -> true, candidate -> -candidate.totalMoveDistance());

        assertEquals(3, results.size());
        assertEquals(1, results.get(0).changes().size());
    }

    @Test
    void treatsRotationsSeparatedByOneHundredEightyDegreesAsTheSameLayout() {
        FabricState fabric = new FabricState(1, RotatedRectangle.of(4, 4, 6, 8, 0));

        List<ProposalCandidate> results = beamSearch.findTopCandidates(
                List.of(fabric),
                candidate -> isStationaryRotation(candidate, fabric, 10, 190),
                candidate -> candidate.changes().get(0).after().clockwiseDegrees());

        assertEquals(1, results.size());
    }

    private boolean isStationaryRotation(ProposalCandidate candidate, FabricState fabric, double... rotations) {
        RotatedRectangle after = candidate.changes().get(0).after();
        return after.center().equals(fabric.bounds().center())
                && java.util.Arrays.stream(rotations).anyMatch(rotation -> after.clockwiseDegrees() == rotation);
    }
}
