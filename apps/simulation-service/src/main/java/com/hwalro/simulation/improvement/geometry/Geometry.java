package com.hwalro.simulation.improvement.geometry;

import java.util.List;

/** 충돌 검사에 필요한 최소한의 회전 직사각형 연산입니다. */
public final class Geometry {
    private static final double EPSILON = 0.0000001;

    private Geometry() {}

    /** 두 회전 직사각형이 겹치거나 닿는지 분리 축 정리(SAT)로 판정합니다. */
    public static boolean intersects(RotatedRectangle first, RotatedRectangle second) {
        return overlapsOnEveryAxis(first.corners(), second.corners())
                && overlapsOnEveryAxis(second.corners(), first.corners());
    }

    /** 회전한 모든 꼭짓점이 도면의 실제 미터 범위 안에 있는지 확인합니다. */
    public static boolean isInside(RotatedRectangle rectangle, double floorWidth, double floorHeight) {
        return rectangle.corners().stream()
                .allMatch(point -> point.x() >= -EPSILON
                        && point.x() <= floorWidth + EPSILON
                        && point.y() >= -EPSILON
                        && point.y() <= floorHeight + EPSILON);
    }

    /**
     * 두 회전 직사각형의 가장 가까운 거리를 실제 도면 단위로 반환합니다.
     *
     * <p>겹치면 0이고, 그렇지 않으면 모든 꼭짓점과 반대편 모서리의 최단 거리를 비교합니다.
     */
    public static double distance(RotatedRectangle first, RotatedRectangle second) {
        if (intersects(first, second)) {
            return 0;
        }
        return Math.min(
                distanceToEdges(first.corners(), second.corners()), distanceToEdges(second.corners(), first.corners()));
    }

    /** 점이 도형 안이나 경계에 있으면 true를 반환합니다. */
    public static boolean contains(RotatedRectangle rectangle, Point point) {
        if (rectangle.width() <= EPSILON || rectangle.height() <= EPSILON) {
            List<Point> corners = rectangle.corners();
            return distanceToSegment(point, corners.get(0), corners.get(2)) <= EPSILON;
        }
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

    /** 점에서 도형 외곽까지의 최단 거리입니다. 내부 점은 0입니다. */
    public static double distance(Point point, RotatedRectangle rectangle) {
        return contains(rectangle, point) ? 0 : distanceToPolygon(point, rectangle.corners());
    }

    private static boolean overlapsOnEveryAxis(List<Point> axisSource, List<Point> first, List<Point> second) {
        for (int index = 0; index < axisSource.size(); index++) {
            Point current = axisSource.get(index);
            Point next = axisSource.get((index + 1) % axisSource.size());
            Point axis = new Point(-(next.y() - current.y()), next.x() - current.x());
            if (!overlaps(project(first, axis), project(second, axis))) {
                return false;
            }
        }
        return true;
    }

    private static boolean overlapsOnEveryAxis(List<Point> first, List<Point> second) {
        return overlapsOnEveryAxis(first, first, second);
    }

    private static Interval project(List<Point> points, Point axis) {
        double minimum = dot(points.get(0), axis);
        double maximum = minimum;
        for (int index = 1; index < points.size(); index++) {
            double value = dot(points.get(index), axis);
            minimum = Math.min(minimum, value);
            maximum = Math.max(maximum, value);
        }
        return new Interval(minimum, maximum);
    }

    private static double dot(Point point, Point axis) {
        return point.x() * axis.x() + point.y() * axis.y();
    }

    private static double cross(double firstX, double firstY, double secondX, double secondY) {
        return firstX * secondY - firstY * secondX;
    }

    private static boolean overlaps(Interval first, Interval second) {
        return first.maximum + EPSILON >= second.minimum && second.maximum + EPSILON >= first.minimum;
    }

    private static double distanceToEdges(List<Point> points, List<Point> edges) {
        return points.stream()
                .mapToDouble(point -> distanceToPolygon(point, edges))
                .min()
                .orElseThrow();
    }

    private static double distanceToPolygon(Point point, List<Point> corners) {
        double minimum = Double.MAX_VALUE;
        for (int index = 0; index < corners.size(); index++) {
            minimum = Math.min(
                    minimum, distanceToSegment(point, corners.get(index), corners.get((index + 1) % corners.size())));
        }
        return minimum;
    }

    private static double distanceToSegment(Point point, Point start, Point end) {
        double deltaX = end.x() - start.x();
        double deltaY = end.y() - start.y();
        double lengthSquared = deltaX * deltaX + deltaY * deltaY;
        if (lengthSquared < EPSILON) {
            return Math.hypot(point.x() - start.x(), point.y() - start.y());
        }
        double projection = ((point.x() - start.x()) * deltaX + (point.y() - start.y()) * deltaY) / lengthSquared;
        double ratio = Math.max(0, Math.min(1, projection));
        return Math.hypot(point.x() - (start.x() + ratio * deltaX), point.y() - (start.y() + ratio * deltaY));
    }

    private record Interval(double minimum, double maximum) {}
}
