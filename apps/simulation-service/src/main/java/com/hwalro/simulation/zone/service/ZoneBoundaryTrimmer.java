package com.hwalro.simulation.zone.service;

import com.hwalro.simulation.simulation.dto.SimulationDtos.ExitDto;
import com.hwalro.simulation.simulation.dto.SimulationDtos.PointDto;
import com.hwalro.simulation.simulation.dto.SimulationDtos.SegmentDto;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class ZoneBoundaryTrimmer {
    private static final double WALL_PROXIMITY_TOLERANCE = 0.2;
    private static final double WALL_THICKNESS = 0.05;
    private static final double EPSILON = 1e-4;

    private ZoneBoundaryTrimmer() {}

    public static List<PointDto> trim(List<PointDto> route, ZoneBounds zone, ExitDto exit) {
        return trim(route, zone, exit, List.of(), List.of());
    }

    public static List<PointDto> trim(
            List<PointDto> route, ZoneBounds zone, ExitDto exit, List<SegmentDto> walls, List<Obstacle> obstacles) {
        if (route == null || route.isEmpty()) {
            return route != null ? route : List.of();
        }

        PointDto exitCenter = exitMidpoint(exit);
        List<Obstacle> blockers = obstacles == null ? List.of() : obstacles;
        if (route.size() < 2 || (exitCenter != null && zone != null && zone.contains(exitCenter))) {
            return orthogonalize(snapToExit(route, exitCenter), walls, blockers);
        }

        if (zone != null) {
            for (int index = 0; index < route.size() - 1; index++) {
                PointDto inside = route.get(index);
                PointDto outside = route.get(index + 1);
                if (!zone.contains(inside) || zone.contains(outside)) {
                    continue;
                }

                PointDto crossing = zone.exitIntersection(inside, outside);
                if (crossing == null) {
                    break;
                }
                List<PointDto> result = new ArrayList<>(route.subList(0, index + 1));
                PointDto entrance = zone.entranceCenter(crossing, walls != null ? walls : List.of());
                if (!samePoint(entrance, inside)
                        && !samePoint(entrance, outside)
                        && !segmentsBlock(inside, entrance, blockers)
                        && !segmentsBlock(entrance, outside, blockers)) {
                    result.add(entrance);
                }
                if (!samePoint(result.get(result.size() - 1), outside)) {
                    result.add(outside);
                }
                result.addAll(route.subList(index + 2, route.size()));
                return orthogonalize(snapToExit(result, exitCenter), walls, blockers);
            }
        }

        return orthogonalize(snapToExit(route, exitCenter), walls, blockers);
    }

    private static List<PointDto> orthogonalize(
            List<PointDto> points, List<SegmentDto> walls, List<Obstacle> obstacles) {
        if (points.size() < 2) {
            return points;
        }
        List<Obstacle> blockers = new ArrayList<>(obstacles == null ? List.of() : obstacles);
        if (walls != null) {
            for (SegmentDto wall : walls) {
                if (wall == null
                        || wall.startX() == null
                        || wall.startY() == null
                        || wall.endX() == null
                        || wall.endY() == null) {
                    continue;
                }
                double minX = Math.min(wall.startX().doubleValue(), wall.endX().doubleValue());
                double maxX = Math.max(wall.startX().doubleValue(), wall.endX().doubleValue());
                double minY = Math.min(wall.startY().doubleValue(), wall.endY().doubleValue());
                double maxY = Math.max(wall.startY().doubleValue(), wall.endY().doubleValue());
                blockers.add(new Obstacle(
                        minX - WALL_THICKNESS, minY - WALL_THICKNESS, maxX + WALL_THICKNESS, maxY + WALL_THICKNESS, 0));
            }
        }
        List<PointDto> result = new ArrayList<>(points);
        for (int index = 0; index < result.size() - 1; index++) {
            PointDto start = result.get(index);
            PointDto end = result.get(index + 1);
            boolean axisAlignedX = Math.abs(start.x().doubleValue() - end.x().doubleValue()) <= EPSILON;
            boolean axisAlignedY = Math.abs(start.y().doubleValue() - end.y().doubleValue()) <= EPSILON;
            if (axisAlignedX || axisAlignedY) {
                continue;
            }
            PointDto horizontalFirst = new PointDto(end.x(), start.y());
            PointDto verticalFirst = new PointDto(start.x(), end.y());
            PointDto bend = null;
            for (PointDto candidate : List.of(horizontalFirst, verticalFirst)) {
                if (!segmentsBlock(start, candidate, blockers) && !segmentsBlock(candidate, end, blockers)) {
                    bend = candidate;
                    break;
                }
            }
            if (bend != null) {
                result.add(index + 1, bend);
                index++;
            }
        }
        return List.copyOf(result);
    }

    private static boolean segmentsBlock(PointDto start, PointDto end, List<Obstacle> obstacles) {
        for (Obstacle obstacle : obstacles) {
            if (obstacle == null || obstacle.intersectsSegment(start, end)) {
                return true;
            }
        }
        return false;
    }

    private static List<PointDto> snapToExit(List<PointDto> route, PointDto exitCenter) {
        if (exitCenter == null || route == null || route.isEmpty()) {
            return route != null ? List.copyOf(route) : List.of();
        }
        List<PointDto> result = new ArrayList<>(route);
        PointDto last = result.get(result.size() - 1);
        if (!samePoint(last, exitCenter)) {
            if (result.size() >= 2 && samePoint(result.get(result.size() - 2), exitCenter)) {
                result.remove(result.size() - 1);
            } else {
                result.set(result.size() - 1, exitCenter);
            }
        }
        if (result.size() == 1 && !samePoint(route.get(0), exitCenter)) {
            result.add(exitCenter);
        }
        return List.copyOf(result);
    }

    private static PointDto exitMidpoint(ExitDto exit) {
        if (exit == null
                || exit.startX() == null
                || exit.endX() == null
                || exit.startY() == null
                || exit.endY() == null) {
            return null;
        }
        double midX = (exit.startX().doubleValue() + exit.endX().doubleValue()) / 2.0;
        double midY = (exit.startY().doubleValue() + exit.endY().doubleValue()) / 2.0;
        return new PointDto(BigDecimal.valueOf(midX), BigDecimal.valueOf(midY));
    }

    private static boolean samePoint(PointDto first, PointDto second) {
        if (first == null
                || second == null
                || first.x() == null
                || first.y() == null
                || second.x() == null
                || second.y() == null) {
            return false;
        }
        return Math.abs(first.x().doubleValue() - second.x().doubleValue()) < EPSILON
                && Math.abs(first.y().doubleValue() - second.y().doubleValue()) < EPSILON;
    }

    public record ZoneBounds(BigDecimal x, BigDecimal y, BigDecimal width, BigDecimal height) {
        boolean contains(PointDto point) {
            if (point == null
                    || point.x() == null
                    || point.y() == null
                    || x == null
                    || y == null
                    || width == null
                    || height == null) {
                return false;
            }
            double px = point.x().doubleValue();
            double py = point.y().doubleValue();
            double minX = x.doubleValue();
            double maxX = x.add(width).doubleValue();
            double minY = y.doubleValue();
            double maxY = y.add(height).doubleValue();
            return px >= minX - EPSILON && px <= maxX + EPSILON && py >= minY - EPSILON && py <= maxY + EPSILON;
        }

        PointDto exitIntersection(PointDto start, PointDto end) {
            if (start == null
                    || end == null
                    || start.x() == null
                    || start.y() == null
                    || end.x() == null
                    || end.y() == null) {
                return null;
            }
            double startX = start.x().doubleValue();
            double startY = start.y().doubleValue();
            double deltaX = end.x().doubleValue() - startX;
            double deltaY = end.y().doubleValue() - startY;
            double exitRatio = 1.0;

            if (deltaX > 0) {
                exitRatio = Math.min(exitRatio, (x.add(width).doubleValue() - startX) / deltaX);
            } else if (deltaX < 0) {
                exitRatio = Math.min(exitRatio, (x.doubleValue() - startX) / deltaX);
            }
            if (deltaY > 0) {
                exitRatio = Math.min(exitRatio, (y.add(height).doubleValue() - startY) / deltaY);
            } else if (deltaY < 0) {
                exitRatio = Math.min(exitRatio, (y.doubleValue() - startY) / deltaY);
            }
            if (exitRatio < 0 || exitRatio > 1) {
                return null;
            }
            return new PointDto(
                    BigDecimal.valueOf(startX + deltaX * exitRatio), BigDecimal.valueOf(startY + deltaY * exitRatio));
        }

        PointDto entranceCenter(PointDto crossing, List<SegmentDto> walls) {
            if (crossing == null || crossing.x() == null || crossing.y() == null) {
                return crossing;
            }
            double cX = crossing.x().doubleValue();
            double cY = crossing.y().doubleValue();
            double minX = x.doubleValue();
            double maxX = x.add(width).doubleValue();
            double minY = y.doubleValue();
            double maxY = y.add(height).doubleValue();

            boolean onLeft = Math.abs(cX - minX) <= EPSILON;
            boolean onRight = Math.abs(cX - maxX) <= EPSILON;
            boolean onTop = Math.abs(cY - minY) <= EPSILON;
            boolean onBottom = Math.abs(cY - maxY) <= EPSILON;

            if (onLeft || onRight) {
                double edgeX = onLeft ? minX : maxX;
                List<Interval> blocked = new ArrayList<>();
                if (walls != null) {
                    for (SegmentDto wall : walls) {
                        if (wall == null
                                || wall.startX() == null
                                || wall.endX() == null
                                || wall.startY() == null
                                || wall.endY() == null) {
                            continue;
                        }
                        double wStartX = wall.startX().doubleValue();
                        double wEndX = wall.endX().doubleValue();
                        double wStartY = wall.startY().doubleValue();
                        double wEndY = wall.endY().doubleValue();
                        if (Math.abs(wStartX - edgeX) <= WALL_PROXIMITY_TOLERANCE
                                && Math.abs(wEndX - edgeX) <= WALL_PROXIMITY_TOLERANCE) {
                            double wallMinY = Math.min(wStartY, wEndY);
                            double wallMaxY = Math.max(wStartY, wEndY);
                            double clampedStart = Math.max(minY, wallMinY);
                            double clampedEnd = Math.min(maxY, wallMaxY);
                            if (clampedStart < clampedEnd - EPSILON) {
                                blocked.add(new Interval(clampedStart, clampedEnd));
                            }
                        }
                    }
                }
                if (blocked.isEmpty()) {
                    return crossing;
                }
                List<Interval> open = findOpenIntervals(minY, maxY, blocked);
                for (Interval interval : open) {
                    if (cY >= interval.start - EPSILON && cY <= interval.end + EPSILON) {
                        double midY = (interval.start + interval.end) / 2.0;
                        return new PointDto(BigDecimal.valueOf(edgeX), BigDecimal.valueOf(midY));
                    }
                }
                return crossing;
            }

            if (onTop || onBottom) {
                double edgeY = onTop ? minY : maxY;
                List<Interval> blocked = new ArrayList<>();
                if (walls != null) {
                    for (SegmentDto wall : walls) {
                        if (wall == null
                                || wall.startX() == null
                                || wall.endX() == null
                                || wall.startY() == null
                                || wall.endY() == null) {
                            continue;
                        }
                        double wStartX = wall.startX().doubleValue();
                        double wEndX = wall.endX().doubleValue();
                        double wStartY = wall.startY().doubleValue();
                        double wEndY = wall.endY().doubleValue();
                        if (Math.abs(wStartY - edgeY) <= WALL_PROXIMITY_TOLERANCE
                                && Math.abs(wEndY - edgeY) <= WALL_PROXIMITY_TOLERANCE) {
                            double wallMinX = Math.min(wStartX, wEndX);
                            double wallMaxX = Math.max(wStartX, wEndX);
                            double clampedStart = Math.max(minX, wallMinX);
                            double clampedEnd = Math.min(maxX, wallMaxX);
                            if (clampedStart < clampedEnd - EPSILON) {
                                blocked.add(new Interval(clampedStart, clampedEnd));
                            }
                        }
                    }
                }
                if (blocked.isEmpty()) {
                    return crossing;
                }
                List<Interval> open = findOpenIntervals(minX, maxX, blocked);
                for (Interval interval : open) {
                    if (cX >= interval.start - EPSILON && cX <= interval.end + EPSILON) {
                        double midX = (interval.start + interval.end) / 2.0;
                        return new PointDto(BigDecimal.valueOf(midX), BigDecimal.valueOf(edgeY));
                    }
                }
                return crossing;
            }

            return crossing;
        }

        private static List<Interval> findOpenIntervals(double start, double end, List<Interval> blocked) {
            if (blocked.isEmpty()) {
                return List.of(new Interval(start, end));
            }
            blocked.sort(Comparator.comparingDouble(a -> a.start));
            List<Interval> merged = new ArrayList<>();
            Interval current = blocked.get(0);
            for (int i = 1; i < blocked.size(); i++) {
                Interval next = blocked.get(i);
                if (next.start <= current.end + EPSILON) {
                    current = new Interval(current.start, Math.max(current.end, next.end));
                } else {
                    merged.add(current);
                    current = next;
                }
            }
            merged.add(current);

            List<Interval> open = new ArrayList<>();
            double cur = start;
            for (Interval b : merged) {
                if (b.start > cur + EPSILON) {
                    open.add(new Interval(cur, b.start));
                }
                cur = Math.max(cur, b.end);
            }
            if (cur < end - EPSILON) {
                open.add(new Interval(cur, end));
            }
            return open;
        }

        private record Interval(double start, double end) {}
    }

    public record Obstacle(double minX, double minY, double maxX, double maxY, double rotationDegrees) {

        public boolean intersectsSegment(PointDto start, PointDto end) {
            if (start == null
                    || end == null
                    || start.x() == null
                    || start.y() == null
                    || end.x() == null
                    || end.y() == null) {
                return false;
            }
            double[] localStart = toLocalFrame(start);
            double[] localEnd = toLocalFrame(end);
            return segmentIntersectsBox(localStart[0], localStart[1], localEnd[0], localEnd[1], minX, minY, maxX, maxY);
        }

        private double[] toLocalFrame(PointDto point) {
            double centerX = (minX + maxX) / 2.0;
            double centerY = (minY + maxY) / 2.0;
            double radians = Math.toRadians(rotationDegrees);
            double deltaX = point.x().doubleValue() - centerX;
            double deltaY = point.y().doubleValue() - centerY;
            double cos = Math.cos(radians);
            double sin = Math.sin(radians);
            return new double[] {centerX + deltaX * cos + deltaY * sin, centerY - deltaX * sin + deltaY * cos};
        }

        private static boolean segmentIntersectsBox(
                double x1, double y1, double x2, double y2, double minX, double minY, double maxX, double maxY) {
            double deltaX = x2 - x1;
            double deltaY = y2 - y1;
            double entry = 0.0;
            double exit = 1.0;
            double[] deltas = {-deltaX, deltaX, -deltaY, deltaY};
            double[] gaps = {x1 - minX, maxX - x1, y1 - minY, maxY - y1};
            for (int side = 0; side < 4; side++) {
                if (Math.abs(deltas[side]) < 1e-12) {
                    if (gaps[side] < 0) {
                        return false;
                    }
                    continue;
                }
                double ratio = gaps[side] / deltas[side];
                if (deltas[side] < 0) {
                    if (ratio > exit) {
                        return false;
                    }
                    if (ratio > entry) {
                        entry = ratio;
                    }
                } else {
                    if (ratio < entry) {
                        return false;
                    }
                    if (ratio < exit) {
                        exit = ratio;
                    }
                }
            }
            return true;
        }
    }
}
