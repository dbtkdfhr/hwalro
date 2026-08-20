package com.hwalro.simulation.improvement.service;

import com.hwalro.simulation.improvement.domain.FabricChange;
import com.hwalro.simulation.improvement.domain.FabricState;
import com.hwalro.simulation.improvement.domain.ProposalCandidate;
import java.util.ArrayList;
import java.util.List;

/**
 * 정해진 이동·회전 규칙만으로 fabric 변경 후보를 생성합니다.
 *
 * <p>후보의 적합성은 이 클래스가 아니라 제약 검사 단계에서 판단합니다.
 */
public final class FabricCandidateGenerator {
    private static final double[] MOVE_DISTANCES_METERS = {1.0, 2.0};
    private static final double DIAGONAL_COMPONENT = Math.sqrt(0.5);
    private static final int ROTATION_STEP_DEGREES = 10;
    private static final List<Direction> DIRECTIONS = List.of(
            new Direction(1, 0),
            new Direction(-1, 0),
            new Direction(0, 1),
            new Direction(0, -1),
            new Direction(DIAGONAL_COMPONENT, DIAGONAL_COMPONENT),
            new Direction(DIAGONAL_COMPONENT, -DIAGONAL_COMPONENT),
            new Direction(-DIAGONAL_COMPONENT, DIAGONAL_COMPONENT),
            new Direction(-DIAGONAL_COMPONENT, -DIAGONAL_COMPONENT));

    /** 정지 1개와 8방향·1m/2m 이동 16개에 각 10도 회전을 조합한 612개 상태를 생성합니다. */
    public List<ProposalCandidate> generateSingleChanges(FabricState fabric) {
        List<ProposalCandidate> candidates = new ArrayList<>();
        addCandidates(fabric, 0, 0, candidates);
        for (double distance : MOVE_DISTANCES_METERS) {
            for (Direction direction : DIRECTIONS) {
                addCandidates(fabric, direction.x() * distance, direction.y() * distance, candidates);
            }
        }
        return candidates;
    }

    /** 기존 후보에 아직 변경하지 않은 시설물 하나를 추가합니다. */
    public List<ProposalCandidate> addSecondChanges(ProposalCandidate base, List<FabricState> fabrics) {
        List<ProposalCandidate> candidates = new ArrayList<>();
        for (FabricState fabric : fabrics) {
            if (base.changesFabric(fabric.id())) {
                continue;
            }
            for (ProposalCandidate single : generateSingleChanges(fabric)) {
                List<FabricChange> changes = new ArrayList<>(base.changes());
                changes.add(single.changes().get(0));
                candidates.add(new ProposalCandidate(changes));
            }
        }
        return candidates;
    }

    private void addCandidates(FabricState fabric, double deltaX, double deltaY, List<ProposalCandidate> candidates) {
        for (int degrees = 0; degrees < 360; degrees += ROTATION_STEP_DEGREES) {
            // 정지·0도는 상태 공간에는 포함되지만 제약 검사에서 무변경 후보로 제외됩니다.
            candidates.add(new ProposalCandidate(List.of(new FabricChange(
                    fabric.id(),
                    fabric.bounds(),
                    fabric.bounds().moveBy(deltaX, deltaY).rotateClockwiseBy(degrees)))));
        }
    }

    private record Direction(double x, double y) {}
}
