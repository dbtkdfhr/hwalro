package com.hwalro.simulation.improvement.domain;

import java.util.List;

/** 제약 검사와 점수 계산 전 단계의 배치 개선 후보입니다. */
public record ProposalCandidate(List<FabricChange> changes) {

    public ProposalCandidate {
        changes = List.copyOf(changes);
    }

    /** 같은 시설물을 두 번 변경하지 않도록 빔 서치 확장 전에 사용합니다. */
    public boolean changesFabric(long fabricId) {
        return changes.stream().anyMatch(change -> change.fabricId() == fabricId);
    }

    /** 점수 감점에 쓰는 총 이동 거리입니다. */
    public double totalMoveDistance() {
        return changes.stream().mapToDouble(FabricChange::moveDistance).sum();
    }
}
