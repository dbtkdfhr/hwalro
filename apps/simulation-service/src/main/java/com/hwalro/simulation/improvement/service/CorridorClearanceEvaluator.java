package com.hwalro.simulation.improvement.service;

import com.hwalro.simulation.improvement.domain.BottleneckArea;
import com.hwalro.simulation.improvement.domain.CorridorClearance;
import com.hwalro.simulation.improvement.geometry.Point;
import com.hwalro.simulation.improvement.geometry.RotatedRectangle;
import java.util.List;
import org.springframework.stereotype.Component;

/** 병목 전체와 출구까지의 진행 방향 수직 단면 폭을 1m 간격으로 평균합니다. */
@Component
public class CorridorClearanceEvaluator {
    private static final double SAMPLE_INTERVAL_METERS = 1;
    private static final double EPSILON = 0.0000001;

    /**
     * 병목 반대편 경계부터 가장 가까운 출구 중심까지의 평균 단면 폭을 측정합니다.
     *
     * <p>병목 구간 평균에는 병목 rectangle 내부 샘플만, 경로 평균에는 병목을 포함한 전체 샘플을 사용합니다.
     */
    public CorridorClearance evaluate(
            BottleneckArea bottleneck,
            List<RotatedRectangle> obstacles,
            List<RotatedRectangle> exits,
            double floorWidth,
            double floorHeight) {
        RotatedRectangle exit = nearestExit(bottleneck.bounds(), exits);
        Direction direction = direction(bottleneck.bounds().center(), exit.center());
        double rearDistance = distanceToBoundary(bottleneck.bounds(), direction.reverse());
        double forwardDistance = distanceToBoundary(bottleneck.bounds(), direction);
        Point start = move(bottleneck.bounds().center(), direction.reverse(), rearDistance);
        double bottleneckLength = rearDistance + forwardDistance;
        double routeLength = distance(start, exit.center());

        return new CorridorClearance(
                averageWidth(start, direction, bottleneckLength, obstacles, floorWidth, floorHeight),
                averageWidth(start, direction, routeLength, obstacles, floorWidth, floorHeight));
    }

    private RotatedRectangle nearestExit(RotatedRectangle bottleneck, List<RotatedRectangle> exits) {
        return exits.stream()
                .min((first, second) -> Double.compare(
                        distance(bottleneck.center(), first.center()), distance(bottleneck.center(), second.center())))
                .orElseThrow(() -> new IllegalArgumentException("병목 통로 폭을 측정할 출구가 없습니다."));
    }

    private Direction direction(Point start, Point end) {
        double length = distance(start, end);
        if (length < EPSILON) {
            throw new IllegalArgumentException("병목과 출구의 중심이 겹칩니다.");
        }
        return new Direction((end.x() - start.x()) / length, (end.y() - start.y()) / length);
    }

    private double distanceToBoundary(RotatedRectangle rectangle, Direction direction) {
        double horizontal = Math.abs(direction.x()) < EPSILON
                ? Double.POSITIVE_INFINITY
                : rectangle.width() / 2 / Math.abs(direction.x());
        double vertical = Math.abs(direction.y()) < EPSILON
                ? Double.POSITIVE_INFINITY
                : rectangle.height() / 2 / Math.abs(direction.y());
        return Math.min(horizontal, vertical);
    }

    private double averageWidth(
            Point start,
            Direction direction,
            double length,
            List<RotatedRectangle> obstacles,
            double floorWidth,
            double floorHeight) {
        int samples = Math.max(1, (int) Math.ceil(length / SAMPLE_INTERVAL_METERS));
        double total = 0;
        for (int index = 0; index <= samples; index++) {
            Point point = move(start, direction, length * index / samples);
            total += widthAt(point, direction.normal(), obstacles, floorWidth, floorHeight);
        }
        return total / (samples + 1);
    }

    private double widthAt(
            Point point, Direction normal, List<RotatedRectangle> obstacles, double floorWidth, double floorHeight) {
        if (obstacles.stream().anyMatch(obstacle -> contains(obstacle, point))) {
            return 0;
        }
        return rayDistance(point, normal, obstacles, floorWidth, floorHeight)
                + rayDistance(point, normal.reverse(), obstacles, floorWidth, floorHeight);
    }

    private double rayDistance(
            Point point, Direction direction, List<RotatedRectangle> obstacles, double floorWidth, double floorHeight) {
        double closest = distanceToFloorBoundary(point, direction, floorWidth, floorHeight);
        for (RotatedRectangle obstacle : obstacles) {
            List<Point> corners = obstacle.corners();
            for (int index = 0; index < corners.size(); index++) {
                double intersection = raySegmentDistance(
                        point, direction, corners.get(index), corners.get((index + 1) % corners.size()));
                if (intersection > EPSILON) {
                    closest = Math.min(closest, intersection);
                }
            }
        }
        return closest;
    }

    private double distanceToFloorBoundary(Point point, Direction direction, double floorWidth, double floorHeight) {
        double horizontal = direction.x() > EPSILON
                ? (floorWidth - point.x()) / direction.x()
                : direction.x() < -EPSILON ? -point.x() / direction.x() : Double.POSITIVE_INFINITY;
        double vertical = direction.y() > EPSILON
                ? (floorHeight - point.y()) / direction.y()
                : direction.y() < -EPSILON ? -point.y() / direction.y() : Double.POSITIVE_INFINITY;
        return Math.min(horizontal, vertical);
    }

    private double raySegmentDistance(Point point, Direction direction, Point start, Point end) {
        double edgeX = end.x() - start.x();
        double edgeY = end.y() - start.y();
        double denominator = cross(direction.x(), direction.y(), edgeX, edgeY);
        if (Math.abs(denominator) < EPSILON) {
            return Double.POSITIVE_INFINITY;
        }
        double offsetX = start.x() - point.x();
        double offsetY = start.y() - point.y();
        double ray = cross(offsetX, offsetY, edgeX, edgeY) / denominator;
        double segment = cross(offsetX, offsetY, direction.x(), direction.y()) / denominator;
        return ray >= 0 && segment >= 0 && segment <= 1 ? ray : Double.POSITIVE_INFINITY;
    }

    private boolean contains(RotatedRectangle rectangle, Point point) {
        Boolean direction = null;
        List<Point> corners = rectangle.corners();
        for (int index = 0; index < corners.size(); index++) {
            Point first = corners.get(index);
            Point second = corners.get((index + 1) % corners.size());
            boolean current =
                    cross(second.x() - first.x(), second.y() - first.y(), point.x() - first.x(), point.y() - first.y())
                            >= -EPSILON;
            if (direction != null && direction != current) {
                return false;
            }
            direction = current;
        }
        return true;
    }

    private Point move(Point point, Direction direction, double distance) {
        return new Point(point.x() + direction.x() * distance, point.y() + direction.y() * distance);
    }

    private double distance(Point first, Point second) {
        return Math.hypot(first.x() - second.x(), first.y() - second.y());
    }

    private double cross(double firstX, double firstY, double secondX, double secondY) {
        return firstX * secondY - firstY * secondX;
    }

    private record Direction(double x, double y) {
        private Direction reverse() {
            return new Direction(-x, -y);
        }

        private Direction normal() {
            return new Direction(-y, x);
        }
    }
}
