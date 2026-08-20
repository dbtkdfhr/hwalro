package com.hwalro.simulation.improvement.service;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hwalro.simulation.improvement.domain.FabricChange;
import com.hwalro.simulation.improvement.domain.FabricState;
import com.hwalro.simulation.improvement.domain.ProposalCandidate;
import com.hwalro.simulation.improvement.geometry.RotatedRectangle;
import java.util.List;
import org.junit.jupiter.api.Test;

class NearbyClearanceEvaluatorTest {
    @Test
    void detectsWhenAMovedFabricNarrowsNearbySpace() {
        FabricState fabric = new FabricState(1, RotatedRectangle.of(4, 8, 6, 12, 0));
        ProposalCandidate candidate = new ProposalCandidate(
                List.of(new FabricChange(1, fabric.bounds(), RotatedRectangle.of(10, 8, 12, 12, 0))));
        double loss = new NearbyClearanceEvaluator()
                .maximumLoss(candidate, List.of(fabric), List.of(RotatedRectangle.of(14, 0, 15, 20, 0)), 20, 20);

        assertTrue(loss > 0);
    }

    @Test
    void includesLossNearProjectedCornersOfRotatedFabric() {
        FabricState fabric = new FabricState(1, RotatedRectangle.of(14, 9, 16, 11, 0));
        ProposalCandidate candidate = new ProposalCandidate(
                List.of(new FabricChange(1, fabric.bounds(), RotatedRectangle.of(9, 9, 11, 11, 45))));

        double loss = new NearbyClearanceEvaluator().maximumLoss(candidate, List.of(fabric), List.of(), 20, 20);

        assertTrue(loss > 0);
    }
}
