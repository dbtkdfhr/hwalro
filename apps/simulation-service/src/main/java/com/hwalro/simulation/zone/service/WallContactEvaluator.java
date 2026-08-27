package com.hwalro.simulation.zone.service;

import com.hwalro.simulation.drawing.domain.Fabric;
import com.hwalro.simulation.drawing.domain.OutsideWall;
import com.hwalro.simulation.drawing.domain.Wall;
import java.math.BigDecimal;
import java.util.List;

/** 회전 구조물 외곽선이 현재 도면의 내부벽 또는 외곽벽에 닿아 있는지 판정한다. */
public final class WallContactEvaluator {
    static final double CONTACT_TOLERANCE_METERS = 0.05;
    private static final double EPSILON = 1e-9;

    private WallContactEvaluator() {}

    public static boolean touches(Fabric fabric, List<Wall> walls, List<OutsideWall> outsideWalls) {
        List<Coordinate> corners = corners(fabric);
        return walls.stream()
                        .anyMatch(wall ->
                                touches(corners, wall.getStartX(), wall.getStartY(), wall.getEndX(), wall.getEndY()))
                || outsideWalls.stream()
                        .anyMatch(wall ->
                                touches(corners, wall.getStartX(), wall.getStartY(), wall.getEndX(), wall.getEndY()));
    }

    private static boolean touches(
            List<Coordinate> corners, BigDecimal startX, BigDecimal startY, BigDecimal endX, BigDecimal endY) {
        Coordinate wallStart = new Coordinate(startX.doubleValue(), startY.doubleValue());
        Coordinate wallEnd = new Coordinate(endX.doubleValue(), endY.doubleValue());
        for (int index = 0; index < corners.size(); index++) {
            if (segmentDistance(corners.get(index), corners.get((index + 1) % corners.size()), wallStart, wallEnd)
                    <= CONTACT_TOLERANCE_METERS + EPSILON) {
                return true;
            }
        }
        return false;
    }

    private static List<Coordinate> corners(Fabric fabric) {
        double startX = fabric.getStartX().doubleValue();
        double startY = fabric.getStartY().doubleValue();
        double endX = fabric.getEndX().doubleValue();
        double endY = fabric.getEndY().doubleValue();
        double centerX = (startX + endX) / 2;
        double centerY = (startY + endY) / 2;
        double halfWidth = Math.abs(endX - startX) / 2;
        double halfHeight = Math.abs(endY - startY) / 2;
        double radians = Math.toRadians(value(fabric.getRotation()));
        double cosine = Math.cos(radians);
        double sine = Math.sin(radians);
        return List.of(
                rotate(centerX, centerY, -halfWidth, -halfHeight, cosine, sine),
                rotate(centerX, centerY, halfWidth, -halfHeight, cosine, sine),
                rotate(centerX, centerY, halfWidth, halfHeight, cosine, sine),
                rotate(centerX, centerY, -halfWidth, halfHeight, cosine, sine));
    }

    private static Coordinate rotate(
            double centerX, double centerY, double localX, double localY, double cosine, double sine) {
        // 도면·Konva·Python 라우터와 같은 수치 좌표 회전이다. 화면의 Y축이 아래쪽이라 양수는 시각적으로 시계 방향이다.
        return new Coordinate(centerX + localX * cosine - localY * sine, centerY + localX * sine + localY * cosine);
    }

    private static double segmentDistance(
            Coordinate firstStart, Coordinate firstEnd, Coordinate secondStart, Coordinate secondEnd) {
        if (segmentsIntersect(firstStart, firstEnd, secondStart, secondEnd)) {
            return 0;
        }
        return Math.min(
                Math.min(
                        pointSegmentDistance(firstStart, secondStart, secondEnd),
                        pointSegmentDistance(firstEnd, secondStart, secondEnd)),
                Math.min(
                        pointSegmentDistance(secondStart, firstStart, firstEnd),
                        pointSegmentDistance(secondEnd, firstStart, firstEnd)));
    }

    private static boolean segmentsIntersect(Coordinate a, Coordinate b, Coordinate c, Coordinate d) {
        double abC = cross(a, b, c);
        double abD = cross(a, b, d);
        double cdA = cross(c, d, a);
        double cdB = cross(c, d, b);
        if (((abC > EPSILON && abD < -EPSILON) || (abC < -EPSILON && abD > EPSILON))
                && ((cdA > EPSILON && cdB < -EPSILON) || (cdA < -EPSILON && cdB > EPSILON))) {
            return true;
        }
        return Math.abs(abC) <= EPSILON && onSegment(a, b, c)
                || Math.abs(abD) <= EPSILON && onSegment(a, b, d)
                || Math.abs(cdA) <= EPSILON && onSegment(c, d, a)
                || Math.abs(cdB) <= EPSILON && onSegment(c, d, b);
    }

    private static boolean onSegment(Coordinate start, Coordinate end, Coordinate point) {
        return point.x() >= Math.min(start.x(), end.x()) - EPSILON
                && point.x() <= Math.max(start.x(), end.x()) + EPSILON
                && point.y() >= Math.min(start.y(), end.y()) - EPSILON
                && point.y() <= Math.max(start.y(), end.y()) + EPSILON;
    }

    private static double cross(Coordinate start, Coordinate end, Coordinate point) {
        return (end.x() - start.x()) * (point.y() - start.y()) - (end.y() - start.y()) * (point.x() - start.x());
    }

    private static double pointSegmentDistance(Coordinate point, Coordinate start, Coordinate end) {
        double deltaX = end.x() - start.x();
        double deltaY = end.y() - start.y();
        double lengthSquared = deltaX * deltaX + deltaY * deltaY;
        if (lengthSquared <= EPSILON) {
            return Math.hypot(point.x() - start.x(), point.y() - start.y());
        }
        double projection = ((point.x() - start.x()) * deltaX + (point.y() - start.y()) * deltaY) / lengthSquared;
        double ratio = Math.max(0, Math.min(1, projection));
        return Math.hypot(point.x() - (start.x() + ratio * deltaX), point.y() - (start.y() + ratio * deltaY));
    }

    private static double value(BigDecimal value) {
        return value == null ? 0 : value.doubleValue();
    }

    private record Coordinate(double x, double y) {}
}
