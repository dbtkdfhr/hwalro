package com.hwalro.simulation.improvement.service;

import com.hwalro.simulation.improvement.domain.FabricChange;
import com.hwalro.simulation.improvement.domain.FabricState;
import com.hwalro.simulation.improvement.domain.ProposalCandidate;
import com.hwalro.simulation.improvement.geometry.Geometry;
import com.hwalro.simulation.improvement.geometry.Point;
import com.hwalro.simulation.improvement.geometry.RotatedRectangle;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/** 후보 변경 전후 5m 주변의 가장 큰 보행 여유 감소를 측정합니다. */
@Component
public class NearbyClearanceEvaluator {
    private static final double RADIUS = 5;

    /** 기존에 보행 가능했던 1m 격자점에서의 최대 여유 감소를 반환합니다. */
    public double maximumLoss(
            ProposalCandidate candidate,
            List<FabricState> fabrics,
            List<RotatedRectangle> fixed,
            double width,
            double height) {
        Map<Long, RotatedRectangle> changed =
                candidate.changes().stream().collect(Collectors.toMap(FabricChange::fabricId, FabricChange::after));
        List<RotatedRectangle> before = obstacles(fabrics, fixed, Map.of());
        List<RotatedRectangle> after = obstacles(fabrics, fixed, changed);
        double loss = 0;
        for (FabricChange change : candidate.changes()) {
            List<Point> corners = change.after().corners();
            double minimumX = corners.stream().mapToDouble(Point::x).min().orElseThrow();
            double maximumX = corners.stream().mapToDouble(Point::x).max().orElseThrow();
            double minimumY = corners.stream().mapToDouble(Point::y).min().orElseThrow();
            double maximumY = corners.stream().mapToDouble(Point::y).max().orElseThrow();
            for (double x = Math.max(0, Math.floor(minimumX - RADIUS));
                    x <= Math.min(width, Math.ceil(maximumX + RADIUS));
                    x++) {
                for (double y = Math.max(0, Math.floor(minimumY - RADIUS));
                        y <= Math.min(height, Math.ceil(maximumY + RADIUS));
                        y++) {
                    Point point = new Point(x, y);
                    if (!before.stream().anyMatch(obstacle -> Geometry.contains(obstacle, point))) {
                        loss = Math.max(
                                loss, clearance(point, before, width, height) - clearance(point, after, width, height));
                    }
                }
            }
        }
        return loss;
    }

    private List<RotatedRectangle> obstacles(
            List<FabricState> fabrics, List<RotatedRectangle> fixed, Map<Long, RotatedRectangle> changed) {
        return List.of(
                        fixed.stream(),
                        fabrics.stream().map(fabric -> changed.getOrDefault(fabric.id(), fabric.bounds())))
                .stream()
                .flatMap(stream -> stream)
                .toList();
    }

    private double clearance(Point point, List<RotatedRectangle> obstacles, double width, double height) {
        return Math.min(
                Math.min(Math.min(point.x(), width - point.x()), Math.min(point.y(), height - point.y())),
                obstacles.stream()
                        .mapToDouble(obstacle -> Geometry.distance(point, obstacle))
                        .min()
                        .orElse(Double.POSITIVE_INFINITY));
    }
}
