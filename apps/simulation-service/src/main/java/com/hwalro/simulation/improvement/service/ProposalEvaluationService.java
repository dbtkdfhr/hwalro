package com.hwalro.simulation.improvement.service;

import com.hwalro.simulation.improvement.domain.CorridorClearance;
import com.hwalro.simulation.improvement.domain.FabricChange;
import com.hwalro.simulation.improvement.domain.FabricState;
import com.hwalro.simulation.improvement.domain.ImprovementSource;
import com.hwalro.simulation.improvement.domain.ProposalCandidate;
import com.hwalro.simulation.improvement.domain.ProposalEvaluation;
import com.hwalro.simulation.improvement.geometry.RotatedRectangle;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/** 후보 배치 전후의 통로 폭과 주변 여유를 점수 입력으로 조립합니다. */
@Service
public class ProposalEvaluationService {
    private final CorridorClearanceEvaluator corridorClearanceEvaluator;
    private final NearbyClearanceEvaluator nearbyClearanceEvaluator;
    private final HeatmapOverlapEvaluator heatmapOverlapEvaluator;

    public ProposalEvaluationService(
            CorridorClearanceEvaluator corridorClearanceEvaluator,
            NearbyClearanceEvaluator nearbyClearanceEvaluator,
            HeatmapOverlapEvaluator heatmapOverlapEvaluator) {
        this.corridorClearanceEvaluator = corridorClearanceEvaluator;
        this.nearbyClearanceEvaluator = nearbyClearanceEvaluator;
        this.heatmapOverlapEvaluator = heatmapOverlapEvaluator;
    }

    /**
     * 모든 병목의 전후 평균 폭 차이를 계산합니다.
     *
     * <p>출구는 배치 제약에는 고정 장애물이지만, 통로 폭과 주변 여유 측정에서는 막힌 공간으로 보지 않습니다.
     */
    public ProposalEvaluation evaluate(ProposalCandidate candidate, ImprovementSource source) {
        List<RotatedRectangle> physicalFixed = source.fixedObstacles().stream()
                .filter(obstacle -> !source.exits().contains(obstacle))
                .toList();
        List<FabricState> changedFabrics = changedFabrics(candidate, source.fabrics());
        CorridorClearance before = averageClearance(source.fabrics(), source, physicalFixed);
        CorridorClearance after = averageClearance(changedFabrics, source, physicalFixed);

        return new ProposalEvaluation(
                candidate,
                after.bottleneckAverageWidth() - before.bottleneckAverageWidth(),
                after.routeAverageWidth() - before.routeAverageWidth(),
                heatmapOverlapEvaluator.overlapDecrease(candidate, source),
                nearbyClearanceEvaluator.maximumLoss(
                        candidate, source.fabrics(), physicalFixed, source.floorWidth(), source.floorHeight()));
    }

    private CorridorClearance averageClearance(
            List<FabricState> fabrics, ImprovementSource source, List<RotatedRectangle> physicalFixed) {
        List<RotatedRectangle> obstacles =
                List.of(physicalFixed.stream(), fabrics.stream().map(FabricState::bounds)).stream()
                        .flatMap(stream -> stream)
                        .toList();
        List<CorridorClearance> clearances = source.bottlenecks().stream()
                .map(bottleneck -> corridorClearanceEvaluator.evaluate(
                        bottleneck, obstacles, source.exits(), source.floorWidth(), source.floorHeight()))
                .toList();
        return new CorridorClearance(
                clearances.stream()
                        .mapToDouble(CorridorClearance::bottleneckAverageWidth)
                        .average()
                        .orElse(0),
                clearances.stream()
                        .mapToDouble(CorridorClearance::routeAverageWidth)
                        .average()
                        .orElse(0));
    }

    private List<FabricState> changedFabrics(ProposalCandidate candidate, List<FabricState> fabrics) {
        Map<Long, RotatedRectangle> changed =
                candidate.changes().stream().collect(Collectors.toMap(FabricChange::fabricId, FabricChange::after));
        return fabrics.stream()
                .map(fabric -> new FabricState(fabric.id(), changed.getOrDefault(fabric.id(), fabric.bounds())))
                .toList();
    }
}
