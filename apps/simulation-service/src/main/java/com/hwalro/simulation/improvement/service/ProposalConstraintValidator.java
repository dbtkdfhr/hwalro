package com.hwalro.simulation.improvement.service;

import com.hwalro.simulation.improvement.domain.FabricChange;
import com.hwalro.simulation.improvement.domain.FabricState;
import com.hwalro.simulation.improvement.domain.ProposalCandidate;
import com.hwalro.simulation.improvement.geometry.Geometry;
import com.hwalro.simulation.improvement.geometry.RotatedRectangle;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** 후보가 도면 밖으로 나가거나 현재 배치와 충돌하는지를 검사합니다. */
public final class ProposalConstraintValidator {
    private final double floorWidth;
    private final double floorHeight;
    private final List<RotatedRectangle> fixedObstacles;
    private final Map<Long, RotatedRectangle> fabricBounds;

    public ProposalConstraintValidator(
            double floorWidth, double floorHeight, List<RotatedRectangle> fixedObstacles, List<FabricState> fabrics) {
        this.floorWidth = floorWidth;
        this.floorHeight = floorHeight;
        this.fixedObstacles = List.copyOf(fixedObstacles);
        this.fabricBounds =
                fabrics.stream().collect(Collectors.toUnmodifiableMap(FabricState::id, FabricState::bounds));
    }

    /** 변경된 천만 검사해 기존 배치의 이미 존재하는 충돌이 후보를 막지 않게 합니다. */
    public boolean isValid(ProposalCandidate candidate) {
        Map<Long, RotatedRectangle> updatedBounds = candidate.changes().stream()
                .collect(Collectors.toUnmodifiableMap(FabricChange::fabricId, FabricChange::after));

        return candidate.changes().stream().allMatch(change -> isValidChange(change, updatedBounds));
    }

    private boolean isValidChange(FabricChange change, Map<Long, RotatedRectangle> updatedBounds) {
        RotatedRectangle after = change.after();
        // 방향 표식 없는 직사각형은 180도 회전해도 점유 영역이 바뀌지 않습니다.
        if (change.before().equals(after)
                || (change.before().center().equals(after.center())
                        && change.before().width() == after.width()
                        && change.before().height() == after.height()
                        && change.before().clockwiseDegrees() % 180 == after.clockwiseDegrees() % 180)
                || !fabricBounds.containsKey(change.fabricId())
                || !Geometry.isInside(after, floorWidth, floorHeight)
                || fixedObstacles.stream().anyMatch(obstacle -> Geometry.intersects(after, obstacle))) {
            return false;
        }

        // 이번 후보에서 변경한 천은 변경 후 좌표로, 나머지는 현재 좌표로 비교합니다.
        return fabricBounds.entrySet().stream()
                .filter(entry -> entry.getKey() != change.fabricId())
                .map(entry -> updatedBounds.getOrDefault(entry.getKey(), entry.getValue()))
                .noneMatch(other -> Geometry.intersects(after, other));
    }
}
