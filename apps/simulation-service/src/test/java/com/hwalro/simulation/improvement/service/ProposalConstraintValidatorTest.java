package com.hwalro.simulation.improvement.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hwalro.simulation.improvement.domain.FabricChange;
import com.hwalro.simulation.improvement.domain.FabricState;
import com.hwalro.simulation.improvement.domain.ProposalCandidate;
import com.hwalro.simulation.improvement.geometry.RotatedRectangle;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProposalConstraintValidatorTest {

    private final FabricState first = new FabricState(1, RotatedRectangle.of(1, 1, 3, 3, 0));
    private final FabricState second = new FabricState(2, RotatedRectangle.of(6, 1, 8, 3, 0));

    @Test
    void acceptsCandidateInsideFloorWithoutCollision() {
        ProposalConstraintValidator validator = validator(List.of());

        assertTrue(validator.isValid(candidate(first, RotatedRectangle.of(3, 3, 5, 5, 30))));
    }

    @Test
    void rejectsStationaryEquivalentRotationStates() {
        ProposalConstraintValidator validator = validator(List.of());

        assertFalse(validator.isValid(candidate(first, first.bounds())));
        assertFalse(validator.isValid(candidate(first, RotatedRectangle.of(1, 1, 3, 3, 180))));
    }

    @Test
    void rejectsCandidateOutsideFloorAfterRotation() {
        ProposalConstraintValidator validator = validator(List.of());

        assertFalse(validator.isValid(candidate(first, RotatedRectangle.of(-1, 1, 1, 3, 45))));
    }

    @Test
    void rejectsCandidateThatCollidesWithFixedObstacle() {
        ProposalConstraintValidator validator = validator(List.of(RotatedRectangle.of(3, 3, 5, 5, 0)));

        assertFalse(validator.isValid(candidate(first, RotatedRectangle.of(3, 3, 5, 5, 0))));
    }

    @Test
    void rejectsCandidateThatCollidesWithAnotherFabric() {
        ProposalConstraintValidator validator = validator(List.of());

        assertFalse(validator.isValid(candidate(first, RotatedRectangle.of(6, 1, 8, 3, 0))));
    }

    private ProposalConstraintValidator validator(List<RotatedRectangle> fixedObstacles) {
        return new ProposalConstraintValidator(10, 10, fixedObstacles, List.of(first, second));
    }

    private ProposalCandidate candidate(FabricState fabric, RotatedRectangle after) {
        return new ProposalCandidate(List.of(new FabricChange(fabric.id(), fabric.bounds(), after)));
    }
}
