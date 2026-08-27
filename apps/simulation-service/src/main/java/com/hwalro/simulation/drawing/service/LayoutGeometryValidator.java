package com.hwalro.simulation.drawing.service;

import com.hwalro.simulation.drawing.dto.ExitDto;
import com.hwalro.simulation.drawing.dto.FabricDto;
import com.hwalro.simulation.drawing.dto.OutsideWallDto;
import com.hwalro.simulation.drawing.dto.PillarDto;
import com.hwalro.simulation.drawing.dto.ValidationProblem;
import com.hwalro.simulation.drawing.dto.WallDto;
import com.hwalro.simulation.drawing.exception.DrawingValidationException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class LayoutGeometryValidator {

    private static final double EPSILON = 0.1;
    private static final double EXACT_EPSILON = 1e-6;
    private static final double MIN_POLYGON_AREA = EPSILON * EPSILON;
    private static final int MAX_VALIDATION_SEGMENTS = 3000;

    public void validate(
            List<OutsideWallDto> outsideWalls,
            List<WallDto> walls,
            List<PillarDto> pillars,
            List<FabricDto> fabrics,
            List<ExitDto> exits) {
        if (exits.isEmpty()) {
            throw new IllegalArgumentException("도면에 비상구가 없습니다. 비상구를 배치해 주세요.");
        }
        if (walls.size() + outsideWalls.size() > MAX_VALIDATION_SEGMENTS) {
            throw new IllegalArgumentException(
                    "벽과 외곽벽의 총 개수가 " + MAX_VALIDATION_SEGMENTS + "개를 초과하여 기하 검증을 수행할 수 없습니다.");
        }
        List<Walk> frameCandidates = findBoundedWalks(outsideWalls, List.of()).stream()
                .filter(walk -> walk.area() > 0)
                .toList();
        if (frameCandidates.isEmpty()) {
            throw new DrawingValidationException(
                    "외곽벽으로 둘러싸인 닫힌 다각형이 없습니다. 외곽벽을 이어 하나의 닫힌 틀을 만들어 주세요.", allOutsideWallProblems(outsideWalls));
        }
        if (frameCandidates.size() > 1) {
            throw new DrawingValidationException(
                    "외곽벽으로 둘러싸인 닫힌 다각형이 2개 이상입니다. 외곽벽 틀은 정확히 1개만 있어야 합니다.", allOutsideWallProblems(outsideWalls));
        }
        Walk frame = frameCandidates.get(0);
        List<String> messages = new ArrayList<>();
        List<ValidationProblem> problems = new ArrayList<>();
        Set<String> reportedWalls = new HashSet<>();
        List<Walk> regionWalks = findBoundedWalks(outsideWalls, walls);
        for (Walk walk : regionWalks) {
            if (!walk.hasOutsideEdge()) {
                messages.add("내부 벽이 닫힌 다각형을 만들어 벽으로 둘러싸인 공간이 생겼습니다. 내부 벽이 다각형을 만들지 않도록 수정해 주세요.");
                addWallProblems(problems, reportedWalls, walk.innerNames());
            }
        }
        collectInsideFrameProblems(frame, pillars, fabrics, exits, messages, problems);
        collectExitReachabilityProblems(regionWalks, pillars, fabrics, exits, messages, problems);
        if (!messages.isEmpty()) {
            throw new DrawingValidationException(String.join(" ", messages), problems);
        }
    }

    private List<Walk> findBoundedWalks(List<OutsideWallDto> outsideWalls, List<WallDto> walls) {
        List<TaggedSegment> taggedSegments = new ArrayList<>();
        for (OutsideWallDto outsideWall : outsideWalls) {
            if (outsideWall == null) {
                continue;
            }
            Point a = new Point(
                    outsideWall.startX().doubleValue(), outsideWall.startY().doubleValue());
            Point b = new Point(
                    outsideWall.endX().doubleValue(), outsideWall.endY().doubleValue());
            if (a.distanceTo(b) >= EPSILON) {
                taggedSegments.add(new TaggedSegment(new Segment(a, b), true, outsideWall.name()));
            }
        }
        for (WallDto wall : walls) {
            if (wall == null) {
                continue;
            }
            Point a = new Point(wall.startX().doubleValue(), wall.startY().doubleValue());
            Point b = new Point(wall.endX().doubleValue(), wall.endY().doubleValue());
            if (a.distanceTo(b) >= EPSILON) {
                taggedSegments.add(new TaggedSegment(new Segment(a, b), false, wall.name()));
            }
        }
        if (taggedSegments.isEmpty()) {
            return List.of();
        }
        PointIndex pointIndex = new PointIndex();
        for (TaggedSegment tagged : taggedSegments) {
            pointIndex.add(tagged.segment().a());
            pointIndex.add(tagged.segment().b());
        }
        for (int i = 0; i < taggedSegments.size(); i++) {
            for (int j = i + 1; j < taggedSegments.size(); j++) {
                Segment s1 = taggedSegments.get(i).segment();
                Segment s2 = taggedSegments.get(j).segment();
                if (!segmentsMayTouch(s1, s2)) {
                    continue;
                }
                Point crossing = properCrossing(s1, s2);
                if (crossing != null) {
                    pointIndex.add(crossing);
                }
            }
        }
        List<Point> points = pointIndex.points();
        List<TaggedSegment> subSegments = new ArrayList<>();
        for (TaggedSegment tagged : taggedSegments) {
            Segment segment = tagged.segment();
            List<Point> onSegment = new ArrayList<>();
            double minX = Math.min(segment.a().x(), segment.b().x()) - EPSILON;
            double maxX = Math.max(segment.a().x(), segment.b().x()) + EPSILON;
            double minY = Math.min(segment.a().y(), segment.b().y()) - EPSILON;
            double maxY = Math.max(segment.a().y(), segment.b().y()) + EPSILON;
            for (Point point : points) {
                if (point.x() < minX || point.x() > maxX || point.y() < minY || point.y() > maxY) {
                    continue;
                }
                if (onSegment(point, segment.a(), segment.b())) {
                    onSegment.add(point);
                }
            }
            onSegment.sort(Comparator.comparingDouble(point -> projection(point, segment.a(), segment.b())));
            for (int i = 0; i + 1 < onSegment.size(); i++) {
                Point from = onSegment.get(i);
                Point to = onSegment.get(i + 1);
                if (from.distanceTo(to) >= EPSILON) {
                    subSegments.add(new TaggedSegment(new Segment(from, to), tagged.outside(), tagged.sourceName()));
                }
            }
        }
        Map<Edge, Boolean> edgeOutside = new HashMap<>();
        Map<Edge, String> edgeInnerNames = new HashMap<>();
        for (TaggedSegment sub : subSegments) {
            int a = pointIndex.idOf(sub.segment().a());
            int b = pointIndex.idOf(sub.segment().b());
            if (a != b) {
                Edge edge = new Edge(Math.min(a, b), Math.max(a, b));
                edgeOutside.merge(edge, sub.outside(), Boolean::logicalOr);
                if (!sub.outside()
                        && sub.sourceName() != null
                        && !sub.sourceName().isBlank()) {
                    edgeInnerNames.putIfAbsent(edge, sub.sourceName());
                }
            }
        }
        if (edgeOutside.isEmpty()) {
            return List.of();
        }
        int n = points.size();
        List<List<Integer>> adjacency = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            adjacency.add(new ArrayList<>());
        }
        for (Edge edge : edgeOutside.keySet()) {
            adjacency.get(edge.a()).add(edge.b());
            adjacency.get(edge.b()).add(edge.a());
        }
        for (int i = 0; i < n; i++) {
            List<Integer> neighbors = adjacency.get(i);
            Point origin = points.get(i);
            neighbors.sort(Comparator.comparingDouble(neighbor -> Math.atan2(
                    points.get(neighbor).y() - origin.y(), points.get(neighbor).x() - origin.x())));
        }
        List<Walk> walks = new ArrayList<>();
        Set<Long> visited = new HashSet<>();
        for (int startU = 0; startU < n; startU++) {
            for (int startV : adjacency.get(startU)) {
                long startKey = directedKey(startU, startV);
                if (visited.contains(startKey)) {
                    continue;
                }
                List<Point> walkPoints = new ArrayList<>();
                List<Edge> walkEdges = new ArrayList<>();
                Set<String> walkInnerNames = new LinkedHashSet<>();
                int u = startU;
                int v = startV;
                while (true) {
                    walkPoints.add(points.get(u));
                    Edge edge = new Edge(Math.min(u, v), Math.max(u, v));
                    walkEdges.add(edge);
                    String innerName = edgeInnerNames.get(edge);
                    if (innerName != null) {
                        walkInnerNames.add(innerName);
                    }
                    visited.add(directedKey(u, v));
                    List<Integer> neighbors = adjacency.get(v);
                    int pos = neighbors.indexOf(u);
                    int next = neighbors.get((pos + 1) % neighbors.size());
                    u = v;
                    v = next;
                    if (u == startU && v == startV) {
                        break;
                    }
                }
                double area = signedArea(walkPoints);
                if (Math.abs(area) >= MIN_POLYGON_AREA) {
                    boolean hasOutsideEdge =
                            walkEdges.stream().anyMatch(edge -> Boolean.TRUE.equals(edgeOutside.get(edge)));
                    walks.add(new Walk(walkPoints, walkEdges, area, hasOutsideEdge, List.copyOf(walkInnerNames)));
                }
            }
        }
        return walks;
    }

    private void collectInsideFrameProblems(
            Walk frame,
            List<PillarDto> pillars,
            List<FabricDto> fabrics,
            List<ExitDto> exits,
            List<String> messages,
            List<ValidationProblem> problems) {
        for (PillarDto pillar : pillars) {
            if (pillar == null) {
                continue;
            }
            if (!rectInsideFrame(
                    frame,
                    rectCorners(pillar.startX(), pillar.startY(), pillar.endX(), pillar.endY(), pillar.rotation()))) {
                messages.add("외곽벽 밖에 위치한 시설물이 있습니다: " + describe(pillar.name(), "기둥"));
                addProblem(problems, "pillar", pillar.name());
            }
        }
        for (FabricDto fabric : fabrics) {
            if (fabric == null) {
                continue;
            }
            if (!rectInsideFrame(
                    frame,
                    rectCorners(fabric.startX(), fabric.startY(), fabric.endX(), fabric.endY(), fabric.rotation()))) {
                messages.add("외곽벽 밖에 위치한 시설물이 있습니다: " + describe(fabric.name(), "구조물"));
                addProblem(problems, "fabric", fabric.name());
            }
        }
        for (ExitDto exit : exits) {
            if (exit == null) {
                continue;
            }
            Point start = new Point(exit.startX().doubleValue(), exit.startY().doubleValue());
            Point end = new Point(exit.endX().doubleValue(), exit.endY().doubleValue());
            if (!contains(frame, start) || !contains(frame, end) || crossesFrame(frame, start, end)) {
                messages.add("외곽벽 밖에 위치한 비상구가 있습니다: " + describe(exit.name(), "비상구"));
                addProblem(problems, "exit", exit.name());
            }
        }
    }

    private boolean rectInsideFrame(Walk frame, List<Point> corners) {
        for (Point corner : corners) {
            if (!contains(frame, corner)) {
                return false;
            }
        }
        for (int i = 0; i < 4; i++) {
            if (crossesFrame(frame, corners.get(i), corners.get((i + 1) % 4))) {
                return false;
            }
        }
        return true;
    }

    private boolean crossesFrame(Walk frame, Point a, Point b) {
        List<Point> vertices = frame.points();
        for (int i = 0; i < vertices.size(); i++) {
            if (properCrossing(new Segment(a, b), new Segment(vertices.get(i), vertices.get((i + 1) % vertices.size())))
                    != null) {
                return true;
            }
        }
        return false;
    }

    private String describe(String name, String fallback) {
        if (name == null || name.isBlank()) {
            return fallback;
        }
        return fallback + " '" + name + "'";
    }

    private void collectExitReachabilityProblems(
            List<Walk> regionWalks,
            List<PillarDto> pillars,
            List<FabricDto> fabrics,
            List<ExitDto> exits,
            List<String> messages,
            List<ValidationProblem> problems) {
        Set<String> reportedWalls = new HashSet<>();
        for (Walk region : regionWalks) {
            boolean reachable =
                    exits.stream().filter(exit -> exit != null).anyMatch(exit -> contains(region, midpoint(exit)));
            if (!reachable) {
                messages.add("비상구로 갈 수 없는 공간이 있습니다. 벽으로 나뉜 모든 공간에서 비상구에 닿도록 배치해 주세요.");
                addWallProblems(problems, reportedWalls, region.innerNames());
            }
        }
        List<PillarDto> pillarList = new ArrayList<>();
        List<List<Point>> pillarRects = new ArrayList<>();
        for (PillarDto pillar : pillars) {
            if (pillar != null) {
                pillarList.add(pillar);
                pillarRects.add(
                        rectCorners(pillar.startX(), pillar.startY(), pillar.endX(), pillar.endY(), pillar.rotation()));
            }
        }
        List<FabricDto> fabricList = new ArrayList<>();
        List<List<Point>> fabricRects = new ArrayList<>();
        for (FabricDto fabric : fabrics) {
            if (fabric != null) {
                fabricList.add(fabric);
                fabricRects.add(
                        rectCorners(fabric.startX(), fabric.startY(), fabric.endX(), fabric.endY(), fabric.rotation()));
            }
        }
        for (ExitDto exit : exits) {
            if (exit == null) {
                continue;
            }
            Point start = new Point(exit.startX().doubleValue(), exit.startY().doubleValue());
            Point end = new Point(exit.endX().doubleValue(), exit.endY().doubleValue());
            for (int i = 0; i < pillarList.size(); i++) {
                if (exitBlockedByRect(start, end, pillarRects.get(i))) {
                    messages.add("비상구가 기둥에 막혀 있습니다: " + describe(exit.name(), "비상구"));
                    addProblem(problems, "exit", exit.name());
                    addProblem(problems, "pillar", pillarList.get(i).name());
                }
            }
            for (int i = 0; i < fabricList.size(); i++) {
                if (exitBlockedByRect(start, end, fabricRects.get(i))) {
                    messages.add("비상구가 구조물에 막혀 있습니다: " + describe(exit.name(), "비상구"));
                    addProblem(problems, "exit", exit.name());
                    addProblem(problems, "fabric", fabricList.get(i).name());
                }
            }
        }
    }

    private void addWallProblems(List<ValidationProblem> problems, Set<String> reported, List<String> names) {
        for (String name : names) {
            if (name != null && !name.isBlank() && reported.add(name)) {
                problems.add(new ValidationProblem("wall", name));
            }
        }
    }

    private void addProblem(List<ValidationProblem> problems, String kind, String name) {
        if (name != null && !name.isBlank()) {
            problems.add(new ValidationProblem(kind, name));
        }
    }

    private List<ValidationProblem> allOutsideWallProblems(List<OutsideWallDto> outsideWalls) {
        List<ValidationProblem> problems = new ArrayList<>();
        for (OutsideWallDto outsideWall : outsideWalls) {
            if (outsideWall != null
                    && outsideWall.name() != null
                    && !outsideWall.name().isBlank()) {
                problems.add(new ValidationProblem("outsideWall", outsideWall.name()));
            }
        }
        return problems;
    }

    private Point midpoint(ExitDto exit) {
        return new Point(
                (exit.startX().doubleValue() + exit.endX().doubleValue()) / 2,
                (exit.startY().doubleValue() + exit.endY().doubleValue()) / 2);
    }

    private boolean exitBlockedByRect(Point start, Point end, List<Point> rect) {
        double segMinX = Math.min(start.x(), end.x());
        double segMaxX = Math.max(start.x(), end.x());
        double segMinY = Math.min(start.y(), end.y());
        double segMaxY = Math.max(start.y(), end.y());
        double rectMinX = Double.MAX_VALUE;
        double rectMaxX = -Double.MAX_VALUE;
        double rectMinY = Double.MAX_VALUE;
        double rectMaxY = -Double.MAX_VALUE;
        for (Point corner : rect) {
            rectMinX = Math.min(rectMinX, corner.x());
            rectMaxX = Math.max(rectMaxX, corner.x());
            rectMinY = Math.min(rectMinY, corner.y());
            rectMaxY = Math.max(rectMaxY, corner.y());
        }
        if (segMaxX < rectMinX || segMinX > rectMaxX || segMaxY < rectMinY || segMinY > rectMaxY) {
            return false;
        }
        if (pointInRect(start, rect) || pointInRect(end, rect)) {
            return true;
        }
        for (int i = 0; i < 4; i++) {
            if (segmentsIntersect(start, end, rect.get(i), rect.get((i + 1) % 4))) {
                return true;
            }
        }
        return false;
    }

    private boolean pointInRect(Point point, List<Point> rect) {
        double prev = 0;
        boolean nonDegenerate = false;
        for (int i = 0; i < 4; i++) {
            double c = cross(rect.get(i), rect.get((i + 1) % 4), point);
            if (Math.abs(c) > EXACT_EPSILON) {
                nonDegenerate = true;
                if (prev != 0 && (c > 0) != (prev > 0)) {
                    return false;
                }
                prev = c;
            }
        }
        return nonDegenerate;
    }

    private boolean segmentsIntersect(Point a1, Point a2, Point b1, Point b2) {
        double d1 = cross(a1, a2, b1);
        double d2 = cross(a1, a2, b2);
        double d3 = cross(b1, b2, a1);
        double d4 = cross(b1, b2, a2);
        if (Math.abs(d1) <= EXACT_EPSILON && onSegmentExact(b1, a1, a2)) {
            return true;
        }
        if (Math.abs(d2) <= EXACT_EPSILON && onSegmentExact(b2, a1, a2)) {
            return true;
        }
        if (Math.abs(d3) <= EXACT_EPSILON && onSegmentExact(a1, b1, b2)) {
            return true;
        }
        if (Math.abs(d4) <= EXACT_EPSILON && onSegmentExact(a2, b1, b2)) {
            return true;
        }
        return (d1 > EXACT_EPSILON && d2 < -EXACT_EPSILON || d1 < -EXACT_EPSILON && d2 > EXACT_EPSILON)
                && (d3 > EXACT_EPSILON && d4 < -EXACT_EPSILON || d3 < -EXACT_EPSILON && d4 > EXACT_EPSILON);
    }

    private boolean onSegmentExact(Point point, Point a, Point b) {
        double length = a.distanceTo(b);
        if (length < EXACT_EPSILON) {
            return point.distanceTo(a) <= EXACT_EPSILON;
        }
        if (Math.abs(cross(a, b, point)) / length > EXACT_EPSILON) {
            return false;
        }
        return point.x() >= Math.min(a.x(), b.x()) - EXACT_EPSILON
                && point.x() <= Math.max(a.x(), b.x()) + EXACT_EPSILON
                && point.y() >= Math.min(a.y(), b.y()) - EXACT_EPSILON
                && point.y() <= Math.max(a.y(), b.y()) + EXACT_EPSILON;
    }

    private double projection(Point point, Point a, Point b) {
        double dx = b.x() - a.x();
        double dy = b.y() - a.y();
        double lengthSq = dx * dx + dy * dy;
        if (lengthSq == 0) {
            return 0;
        }
        return ((point.x() - a.x()) * dx + (point.y() - a.y()) * dy) / lengthSq;
    }

    private boolean onSegment(Point point, Point a, Point b) {
        double length = a.distanceTo(b);
        if (length < EXACT_EPSILON) {
            return point.distanceTo(a) <= EPSILON;
        }
        if (Math.abs(cross(a, b, point)) / length > EPSILON) {
            return false;
        }
        return point.x() >= Math.min(a.x(), b.x()) - EPSILON
                && point.x() <= Math.max(a.x(), b.x()) + EPSILON
                && point.y() >= Math.min(a.y(), b.y()) - EPSILON
                && point.y() <= Math.max(a.y(), b.y()) + EPSILON;
    }

    private double cross(Point a, Point b, Point c) {
        return (b.x() - a.x()) * (c.y() - a.y()) - (b.y() - a.y()) * (c.x() - a.x());
    }

    private boolean segmentsMayTouch(Segment s1, Segment s2) {
        return Math.max(s1.a().x(), s1.b().x()) >= Math.min(s2.a().x(), s2.b().x()) - EPSILON
                && Math.min(s1.a().x(), s1.b().x())
                        <= Math.max(s2.a().x(), s2.b().x()) + EPSILON
                && Math.max(s1.a().y(), s1.b().y())
                        >= Math.min(s2.a().y(), s2.b().y()) - EPSILON
                && Math.min(s1.a().y(), s1.b().y())
                        <= Math.max(s2.a().y(), s2.b().y()) + EPSILON;
    }

    private Point properCrossing(Segment s1, Segment s2) {
        double d1 = cross(s1.a(), s1.b(), s2.a());
        double d2 = cross(s1.a(), s1.b(), s2.b());
        double d3 = cross(s2.a(), s2.b(), s1.a());
        double d4 = cross(s2.a(), s2.b(), s1.b());
        if (Math.abs(d1) <= EPSILON || Math.abs(d2) <= EPSILON || Math.abs(d3) <= EPSILON || Math.abs(d4) <= EPSILON) {
            return null;
        }
        if (d1 * d2 > 0 || d3 * d4 > 0) {
            return null;
        }
        double t = d3 / (d3 - d4);
        return new Point(
                s1.a().x() + t * (s1.b().x() - s1.a().x()),
                s1.a().y() + t * (s1.b().y() - s1.a().y()));
    }

    private double signedArea(List<Point> points) {
        double sum = 0;
        for (int i = 0; i < points.size(); i++) {
            Point a = points.get(i);
            Point b = points.get((i + 1) % points.size());
            sum += a.x() * b.y() - b.x() * a.y();
        }
        return sum / 2;
    }

    private boolean contains(Walk region, Point point) {
        List<Point> vertices = region.points();
        int count = 0;
        for (int i = 0, j = vertices.size() - 1; i < vertices.size(); j = i++) {
            Point a = vertices.get(i);
            Point b = vertices.get(j);
            if (onSegment(point, a, b)) {
                return true;
            }
            boolean crosses = (a.y() > point.y()) != (b.y() > point.y());
            if (crosses) {
                double xIntersection = (b.x() - a.x()) * (point.y() - a.y()) / (b.y() - a.y()) + a.x();
                if (point.x() < xIntersection) {
                    count++;
                }
            }
        }
        return count % 2 == 1;
    }

    private List<Point> rectCorners(
            BigDecimal startX, BigDecimal startY, BigDecimal endX, BigDecimal endY, BigDecimal rotation) {
        double sx = startX.doubleValue();
        double sy = startY.doubleValue();
        double ex = endX.doubleValue();
        double ey = endY.doubleValue();
        double cx = (sx + ex) / 2;
        double cy = (sy + ey) / 2;
        double theta = Math.toRadians(rotation == null ? 0 : rotation.doubleValue());
        double cos = Math.cos(theta);
        double sin = Math.sin(theta);
        List<Point> corners = new ArrayList<>(4);
        corners.add(rotate(sx, sy, cx, cy, cos, sin));
        corners.add(rotate(sx, ey, cx, cy, cos, sin));
        corners.add(rotate(ex, ey, cx, cy, cos, sin));
        corners.add(rotate(ex, sy, cx, cy, cos, sin));
        return corners;
    }

    private Point rotate(double x, double y, double cx, double cy, double cos, double sin) {
        double dx = x - cx;
        double dy = y - cy;
        return new Point(cx + dx * cos - dy * sin, cy + dx * sin + dy * cos);
    }

    private static long directedKey(int u, int v) {
        return ((long) u << 32) | (v & 0xffffffffL);
    }

    private record Point(double x, double y) {
        double distanceTo(Point other) {
            double dx = x - other.x;
            double dy = y - other.y;
            return Math.sqrt(dx * dx + dy * dy);
        }
    }

    private record Segment(Point a, Point b) {}

    private record TaggedSegment(Segment segment, boolean outside, String sourceName) {}

    private record Edge(int a, int b) {}

    private record Walk(
            List<Point> points, List<Edge> edges, double area, boolean hasOutsideEdge, List<String> innerNames) {}

    private static final class PointIndex {
        private final Map<Long, List<Integer>> cellToPointIds = new HashMap<>();
        private final List<Point> points = new ArrayList<>();

        int add(Point point) {
            Integer existing = findNearby(point);
            if (existing != null) {
                return existing;
            }
            int id = points.size();
            points.add(point);
            cellToPointIds
                    .computeIfAbsent(cellKey(point), unused -> new ArrayList<>())
                    .add(id);
            return id;
        }

        int idOf(Point point) {
            Integer id = findNearby(point);
            return id == null ? add(point) : id;
        }

        List<Point> points() {
            return points;
        }

        private Integer findNearby(Point point) {
            int cx = (int) Math.floor(point.x() / EPSILON);
            int cy = (int) Math.floor(point.y() / EPSILON);
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (Integer id : cellToPointIds.getOrDefault(cellKey(cx + dx, cy + dy), List.of())) {
                        if (points.get(id).distanceTo(point) <= EPSILON) {
                            return id;
                        }
                    }
                }
            }
            return null;
        }

        private static long cellKey(Point point) {
            int cx = (int) Math.floor(point.x() / EPSILON);
            int cy = (int) Math.floor(point.y() / EPSILON);
            return cellKey(cx, cy);
        }

        private static long cellKey(int cx, int cy) {
            return ((long) cx << 32) ^ (cy & 0xffffffffL);
        }
    }
}
