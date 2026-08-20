package com.hwalro.simulation.improvement.service;

import com.hwalro.simulation.improvement.domain.FabricState;
import com.hwalro.simulation.improvement.domain.ProposalCandidate;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;
import java.util.stream.Collectors;

/**
 * 단일 변경 상위 5개만 확장하는 고정 폭 빔 서치입니다.
 *
 * <p>제약 검사와 점수 계산은 입력 데이터에 따라 달라지므로 호출부에서 제공합니다.
 */
public final class ProposalBeamSearch {
    private static final int BEAM_WIDTH = 5;
    private static final int RESULT_LIMIT = 3;

    private final FabricCandidateGenerator candidateGenerator;

    public ProposalBeamSearch(FabricCandidateGenerator candidateGenerator) {
        this.candidateGenerator = candidateGenerator;
    }

    /** 유효한 단일 변경을 확장해 최종 상위 3개 후보를 반환합니다. */
    public List<ProposalCandidate> findTopCandidates(
            List<FabricState> fabrics,
            Predicate<ProposalCandidate> isValid,
            ToDoubleFunction<ProposalCandidate> score) {
        List<ProposalCandidate> firstBeam = rank(
                fabrics.stream()
                        .flatMap(fabric -> candidateGenerator.generateSingleChanges(fabric).stream())
                        .toList(),
                isValid,
                score,
                BEAM_WIDTH);

        List<ProposalCandidate> secondChanges = firstBeam.stream()
                .flatMap(candidate -> candidateGenerator.addSecondChanges(candidate, fabrics).stream())
                .toList();
        List<ProposalCandidate> rankedSecondChanges = rank(secondChanges, isValid, score, RESULT_LIMIT);
        // 주변에 한 개만 있거나 두 번째 변경이 모두 충돌하면, 유효한 단일 변경을 버리지 않습니다.
        return rankedSecondChanges.isEmpty()
                ? firstBeam.stream().limit(RESULT_LIMIT).toList()
                : rankedSecondChanges;
    }

    private List<ProposalCandidate> rank(
            List<ProposalCandidate> candidates,
            Predicate<ProposalCandidate> isValid,
            ToDoubleFunction<ProposalCandidate> score,
            int limit) {
        Set<String> rankedLayouts = new HashSet<>();
        return candidates.stream()
                .filter(isValid)
                .sorted(Comparator.<ProposalCandidate>comparingDouble(score)
                        .reversed()
                        .thenComparingInt(candidate -> candidate.changes().size())
                        .thenComparingDouble(ProposalCandidate::totalMoveDistance)
                        .thenComparingLong(this::lowestFabricId))
                // 방향 표식이 없는 직사각형 fabric은 180도 회전해도 같은 점유 영역입니다.
                .filter(candidate -> rankedLayouts.add(layoutKey(candidate)))
                .limit(limit)
                .toList();
    }

    private String layoutKey(ProposalCandidate candidate) {
        return candidate.changes().stream()
                .sorted(Comparator.comparingLong(change -> change.fabricId()))
                .map(change -> change.fabricId() + ":" + change.after().center().x() + ":"
                        + change.after().center().y() + ":" + change.after().width() + ":"
                        + change.after().height() + ":" + change.after().clockwiseDegrees() % 180)
                .collect(Collectors.joining("|"));
    }

    private long lowestFabricId(ProposalCandidate candidate) {
        return candidate.changes().stream()
                .mapToLong(change -> change.fabricId())
                .min()
                .orElse(Long.MAX_VALUE);
    }
}
