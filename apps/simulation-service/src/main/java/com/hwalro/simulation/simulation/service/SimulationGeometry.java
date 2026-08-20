package com.hwalro.simulation.simulation.service;

import com.hwalro.simulation.drawing.domain.Fabric;
import com.hwalro.simulation.drawing.domain.LayoutExit;
import com.hwalro.simulation.drawing.domain.OutsideWall;
import com.hwalro.simulation.drawing.domain.Pillar;
import com.hwalro.simulation.drawing.domain.Wall;
import com.hwalro.simulation.simulation.dto.SimulationDtos.HazardZoneDto;
import com.hwalro.simulation.simulation.dto.SimulationDtos.PointDto;
import com.hwalro.simulation.simulation.exception.InvalidSimulationGeometryException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class SimulationGeometry {
    public static final double AGENT_RADIUS = 0.3;
    public static final double AGENT_SPACING = 0.6;
    public static final double MIN_GEOMETRY_CLEARANCE = AGENT_RADIUS + 0.001;
    public static final int MAX_AGENTS = 5_000;
    private static final double EPSILON = 1.0e-7;
    private static final BigDecimal MAX_VALUE = BigDecimal.valueOf(1_000_000);

    private SimulationGeometry() {}

    public static List<PointDto> assembleBoundary(List<OutsideWall> outsideWalls, BigDecimal width, BigDecimal height) {
        if (outsideWalls == null || outsideWalls.size() < 3) {
            throw invalid("외곽선은 3개 이상의 선분으로 이루어진 하나의 폐곡선이어야 합니다.");
        }

        Map<PointKey, List<PointKey>> graph = new HashMap<>();
        Set<EdgeKey> edges = new HashSet<>();
        for (OutsideWall wall : outsideWalls) {
            PointKey start = point(wall.getStartX(), wall.getStartY(), width, height);
            PointKey end = point(wall.getEndX(), wall.getEndY(), width, height);
            if (start.equals(end)) {
                throw invalid("외곽선에 길이가 0인 선분이 있습니다.");
            }
            EdgeKey edge = EdgeKey.of(start, end);
            if (!edges.add(edge)) {
                throw invalid("외곽선에 중복된 선분이 있습니다.");
            }
            graph.computeIfAbsent(start, ignored -> new ArrayList<>()).add(end);
            graph.computeIfAbsent(end, ignored -> new ArrayList<>()).add(start);
        }

        if (graph.size() < 3 || graph.values().stream().anyMatch(neighbors -> neighbors.size() != 2)) {
            throw invalid("외곽선의 모든 끝점은 정확히 두 선분과 연결되어야 합니다.");
        }
        graph.values().forEach(neighbors -> neighbors.sort(PointKey.ORDER));

        PointKey start = graph.keySet().stream().min(PointKey.ORDER).orElseThrow();
        List<PointKey> ordered = new ArrayList<>();
        Set<PointKey> visited = new HashSet<>();
        PointKey previous = null;
        PointKey current = start;
        while (true) {
            ordered.add(current);
            visited.add(current);
            List<PointKey> neighbors = graph.get(current);
            PointKey next =
                    previous == null || !neighbors.get(0).equals(previous) ? neighbors.get(0) : neighbors.get(1);
            if (next.equals(start)) {
                break;
            }
            if (visited.contains(next) || ordered.size() >= edges.size()) {
                throw invalid("외곽선이 하나의 연결된 폐곡선을 만들지 못합니다.");
            }
            previous = current;
            current = next;
        }
        if (ordered.size() != edges.size() || visited.size() != graph.size()) {
            throw invalid("외곽선은 서로 분리된 여러 영역을 포함할 수 없습니다.");
        }

        validateAreaAndIntersections(ordered);
        return ordered.stream().map(PointKey::toDto).toList();
    }

    public static void validateSetup(
            List<PointDto> agents,
            List<HazardZoneDto> hazards,
            List<PointDto> boundary,
            List<Wall> walls,
            List<Pillar> pillars,
            List<Fabric> fabrics,
            List<LayoutExit> exits) {
        if (agents.size() > MAX_AGENTS) {
            throw invalid("에이전트는 최대 5000명까지 배치할 수 있습니다.");
        }

        Map<Cell, List<PointDto>> occupied = new HashMap<>();
        for (PointDto agent : agents) {
            validatePoint(agent, "에이전트");
            if (!insidePolygon(agent, boundary)
                    || distanceToBoundary(agent, boundary) + EPSILON < MIN_GEOMETRY_CLEARANCE) {
                throw invalid("에이전트는 외곽선에서 0.301m 이상 안쪽에 있어야 합니다.");
            }
            if (walls.stream().anyMatch(wall -> distanceToSegment(agent, wall) + EPSILON < MIN_GEOMETRY_CLEARANCE)
                    || exits.stream()
                            .anyMatch(exit -> distanceToSegment(agent, exit) + EPSILON < MIN_GEOMETRY_CLEARANCE)
                    || pillars.stream()
                            .anyMatch(pillar -> distanceToRect(agent, pillar) + EPSILON < MIN_GEOMETRY_CLEARANCE)
                    || fabrics.stream()
                            .anyMatch(fabric -> distanceToRect(agent, fabric) + EPSILON < MIN_GEOMETRY_CLEARANCE)) {
                throw invalid("에이전트가 벽, 기둥, 구조물 또는 출입구와 겹칩니다.");
            }

            Cell cell = Cell.of(agent);
            for (long x = cell.x - 1; x <= cell.x + 1; x++) {
                for (long y = cell.y - 1; y <= cell.y + 1; y++) {
                    for (PointDto other : occupied.getOrDefault(new Cell(x, y), List.of())) {
                        if (distance(agent, other) + EPSILON < AGENT_SPACING) {
                            throw invalid("에이전트 중심 간 거리는 0.6m 이상이어야 합니다.");
                        }
                    }
                }
            }
            occupied.computeIfAbsent(cell, ignored -> new ArrayList<>()).add(agent);
        }

        for (HazardZoneDto hazard : hazards) {
            if (hazard == null || hazard.centerX() == null || hazard.centerY() == null || hazard.radius() == null) {
                throw invalid("위험구역 좌표와 반지름이 필요합니다.");
            }
            if (hazard.radius().signum() <= 0 || hazard.radius().compareTo(MAX_VALUE) > 0) {
                throw invalid("위험구역 반지름은 0보다 크고 1000000 이하여야 합니다.");
            }
            PointDto center = new PointDto(hazard.centerX(), hazard.centerY());
            validatePoint(center, "위험구역 중심");
            if (!insidePolygon(center, boundary) && distanceToBoundary(center, boundary) > EPSILON) {
                throw invalid("위험구역 중심은 외곽선 안에 있어야 합니다.");
            }
        }
    }

    // ---------------------------------------------------------------------
    // 에이전트 재배치 (relaxation)
    //
    // validateSetup이 "거부"하는 배치를 "고치는" 쪽이다. 두 코드가 반드시 같은 술어
    // (insidePolygon / distanceToSegment / distanceToRect / distance)를 쓰도록 같은
    // 클래스에 둔다. relaxAgents의 결과는 언제나 validateSetup을 통과해야 한다.
    // ---------------------------------------------------------------------

    // 좌표를 소수점 4자리로 반올림해 저장하므로 좌표당 최대 5e-5, 두 점 사이로는 최대
    // 1.42e-4까지 간격이 줄어들 수 있다. validateSetup의 EPSILON(1e-7)보다 크므로,
    // 임계값에 그보다 충분히 큰 여유를 두어야 반올림 후에도 검사를 통과한다.
    private static final double CLEARANCE_MARGIN = 1.0e-3;
    private static final double TARGET_CLEARANCE = AGENT_RADIUS + CLEARANCE_MARGIN;
    private static final double TARGET_SPACING = AGENT_SPACING + CLEARANCE_MARGIN;
    private static final int OUTPUT_SCALE = 4;
    private static final int MAX_RELAX_ITERATIONS = 5_000;
    // 위반을 정확히 임계값까지만 해소하면 부동소수점 잔차 때문에 매 반복 1e-16씩 다시 밀게 되어
    // 영원히 수렴하지 않는다. 이 정도 남은 위반은 해소된 것으로 본다. 임계값 자체가
    // validateSetup 기준보다 CLEARANCE_MARGIN(1e-3)만큼 여유가 있으므로 안전하다.
    private static final double RELAX_TOLERANCE = 1.0e-9;
    private static final double COINCIDENT_ANGLE_STEP = 0.618_033_988_749_894_9;

    /**
     * 장애물과 겹친 에이전트를 밀어내고, 그 때문에 서로 겹치게 된 에이전트를 연쇄적으로 다시 밀어낸다.
     *
     * <p>움직일 필요가 없던 에이전트는 입력 인스턴스를 그대로 돌려주므로 좌표가 한 자리도 바뀌지 않는다.
     * 난수를 쓰지 않고 인덱스 오름차순으로만 순회하므로 같은 입력에는 항상 같은 결과를 준다.
     *
     * @throws InvalidSimulationGeometryException 모두를 배치할 공간이 없어 수렴하지 못한 경우
     */
    public static List<PointDto> relaxAgents(
            List<PointDto> agents,
            List<PointDto> boundary,
            List<Wall> walls,
            List<Pillar> pillars,
            List<Fabric> fabrics,
            List<LayoutExit> exits) {
        if (agents.size() > MAX_AGENTS) {
            throw invalid("에이전트는 최대 5000명까지 배치할 수 있습니다.");
        }
        int count = agents.size();
        if (count == 0) {
            return List.of();
        }

        double[] xs = new double[count];
        double[] ys = new double[count];
        boolean[] moved = new boolean[count];
        for (int index = 0; index < count; index++) {
            PointDto agent = agents.get(index);
            validatePoint(agent, "에이전트");
            xs[index] = agent.x().doubleValue();
            ys[index] = agent.y().doubleValue();
        }

        double[] centroid = polygonCentroid(boundary);
        BitSet active = new BitSet(count);
        active.set(0, count);

        for (int iteration = 0; iteration < MAX_RELAX_ITERATIONS; iteration++) {
            BitSet dirty = new BitSet(count);
            pushOutOfStructures(xs, ys, moved, active, dirty, boundary, centroid, walls, pillars, fabrics, exits);
            separateAgents(xs, ys, moved, active, dirty, count);
            if (dirty.isEmpty()) {
                return finish(agents, xs, ys, moved, count);
            }
            active = withNeighbours(dirty, xs, ys, count);
        }
        throw invalid("이동한 구조물 때문에 에이전트를 자동으로 재배치하지 못했습니다." + " 구조물 사이에 여유 공간을 확보한 뒤 다시 채택해 주세요.");
    }

    /** 벽·외곽선·기둥·구조물은 움직이지 않는 구속이다. 매 반복마다 에이전트 분리보다 먼저 적용한다. */
    private static void pushOutOfStructures(
            double[] xs,
            double[] ys,
            boolean[] moved,
            BitSet active,
            BitSet dirty,
            List<PointDto> boundary,
            double[] centroid,
            List<Wall> walls,
            List<Pillar> pillars,
            List<Fabric> fabrics,
            List<LayoutExit> exits) {
        for (int index = active.nextSetBit(0); index >= 0; index = active.nextSetBit(index + 1)) {
            double x = xs[index];
            double y = ys[index];
            Escape deepest = escapeBoundary(x, y, boundary, centroid);
            for (Wall wall : walls) {
                deepest = deeper(
                        deepest,
                        escapeSegment(
                                x,
                                y,
                                wall.getStartX().doubleValue(),
                                wall.getStartY().doubleValue(),
                                wall.getEndX().doubleValue(),
                                wall.getEndY().doubleValue()));
            }
            for (LayoutExit exit : exits) {
                deepest = deeper(
                        deepest,
                        escapeSegment(
                                x,
                                y,
                                exit.getStartX().doubleValue(),
                                exit.getStartY().doubleValue(),
                                exit.getEndX().doubleValue(),
                                exit.getEndY().doubleValue()));
            }
            for (Pillar pillar : pillars) {
                deepest = deeper(
                        deepest,
                        escapeRect(
                                x,
                                y,
                                pillar.getStartX(),
                                pillar.getStartY(),
                                pillar.getEndX(),
                                pillar.getEndY(),
                                pillar.getRotation()));
            }
            for (Fabric fabric : fabrics) {
                deepest = deeper(
                        deepest,
                        escapeRect(
                                x,
                                y,
                                fabric.getStartX(),
                                fabric.getStartY(),
                                fabric.getEndX(),
                                fabric.getEndY(),
                                fabric.getRotation()));
            }
            if (deepest != null && deepest.depth() > RELAX_TOLERANCE) {
                xs[index] = deepest.x();
                ys[index] = deepest.y();
                moved[index] = true;
                dirty.set(index);
            }
        }
    }

    /**
     * 겹친 두 에이전트를 서로 반대로 절반씩 민다. 여기서 밀린 쪽이 dirty에 들어가면서 다음 반복의
     * 구조물 검사 대상이 되고, 그 결과 밀어내기가 이웃으로 연쇄된다.
     */
    private static void separateAgents(
            double[] xs, double[] ys, boolean[] moved, BitSet active, BitSet dirty, int count) {
        Map<Cell, List<Integer>> buckets = new HashMap<>();
        Cell[] cells = new Cell[count];
        for (int index = 0; index < count; index++) {
            cells[index] = Cell.of(xs[index], ys[index], TARGET_SPACING);
            buckets.computeIfAbsent(cells[index], ignored -> new ArrayList<>()).add(index);
        }

        BitSet candidates = (BitSet) active.clone();
        candidates.or(dirty);
        for (int index = candidates.nextSetBit(0); index >= 0; index = candidates.nextSetBit(index + 1)) {
            Cell cell = cells[index];
            for (long x = cell.x() - 1; x <= cell.x() + 1; x++) {
                for (long y = cell.y() - 1; y <= cell.y() + 1; y++) {
                    for (int other : buckets.getOrDefault(new Cell(x, y), List.of())) {
                        if (other == index) {
                            continue;
                        }
                        double dx = xs[other] - xs[index];
                        double dy = ys[other] - ys[index];
                        double gap = Math.hypot(dx, dy);
                        if (TARGET_SPACING - gap <= RELAX_TOLERANCE) {
                            continue;
                        }
                        double unitX;
                        double unitY;
                        if (gap > EPSILON) {
                            unitX = dx / gap;
                            unitY = dy / gap;
                        } else {
                            // 완전히 같은 좌표면 방향이 없다. 낮은 인덱스로 각도를 정해 결정적으로 가른다.
                            double angle = 2 * Math.PI * (Math.min(index, other) * COINCIDENT_ANGLE_STEP);
                            unitX = Math.cos(angle);
                            unitY = Math.sin(angle);
                        }
                        double push = (TARGET_SPACING - gap) / 2;
                        xs[index] -= push * unitX;
                        ys[index] -= push * unitY;
                        xs[other] += push * unitX;
                        ys[other] += push * unitY;
                        moved[index] = true;
                        moved[other] = true;
                        dirty.set(index);
                        dirty.set(other);
                    }
                }
            }
        }
    }

    /**
     * 다음 반복에서 다시 볼 대상은 이번에 움직인 에이전트와 그 이웃뿐이다. 구조물은 정지해 있으므로
     * 그 밖의 에이전트는 새 위반을 만들 수 없고, 덕분에 반복 비용이 전체 인원이 아니라 영향 범위에 비례한다.
     */
    private static BitSet withNeighbours(BitSet dirty, double[] xs, double[] ys, int count) {
        Map<Cell, List<Integer>> buckets = new HashMap<>();
        Cell[] cells = new Cell[count];
        for (int index = 0; index < count; index++) {
            cells[index] = Cell.of(xs[index], ys[index], TARGET_SPACING);
            buckets.computeIfAbsent(cells[index], ignored -> new ArrayList<>()).add(index);
        }
        BitSet next = (BitSet) dirty.clone();
        for (int index = dirty.nextSetBit(0); index >= 0; index = dirty.nextSetBit(index + 1)) {
            Cell cell = cells[index];
            for (long x = cell.x() - 1; x <= cell.x() + 1; x++) {
                for (long y = cell.y() - 1; y <= cell.y() + 1; y++) {
                    for (int other : buckets.getOrDefault(new Cell(x, y), List.of())) {
                        next.set(other);
                    }
                }
            }
        }
        return next;
    }

    private static List<PointDto> finish(List<PointDto> agents, double[] xs, double[] ys, boolean[] moved, int count) {
        List<PointDto> relaxed = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            if (!moved[index]) {
                relaxed.add(agents.get(index));
                continue;
            }
            relaxed.add(new PointDto(
                    BigDecimal.valueOf(xs[index]).setScale(OUTPUT_SCALE, RoundingMode.HALF_UP),
                    BigDecimal.valueOf(ys[index]).setScale(OUTPUT_SCALE, RoundingMode.HALF_UP)));
        }
        return List.copyOf(relaxed);
    }

    private static Escape deeper(Escape current, Escape candidate) {
        if (candidate == null) {
            return current;
        }
        return current == null || candidate.depth() > current.depth() ? candidate : current;
    }

    /** 위반을 해소하는 목표 좌표와 그 심각도. depth가 클수록 먼저 처리한다. */
    private record Escape(double x, double y, double depth) {}

    private static Escape escapeSegment(double px, double py, double sx, double sy, double ex, double ey) {
        double[] nearest = nearestOnSegment(px, py, sx, sy, ex, ey);
        double gap = Math.hypot(px - nearest[0], py - nearest[1]);
        if (gap >= TARGET_CLEARANCE) {
            return null;
        }
        double unitX;
        double unitY;
        if (gap > EPSILON) {
            unitX = (px - nearest[0]) / gap;
            unitY = (py - nearest[1]) / gap;
        } else {
            // 선분 위에 정확히 올라선 경우엔 방향이 없다. 결정적으로 왼쪽 법선을 쓴다.
            double dx = ex - sx;
            double dy = ey - sy;
            double length = Math.hypot(dx, dy);
            unitX = length > EPSILON ? -dy / length : 1;
            unitY = length > EPSILON ? dx / length : 0;
        }
        return new Escape(
                nearest[0] + unitX * TARGET_CLEARANCE, nearest[1] + unitY * TARGET_CLEARANCE, TARGET_CLEARANCE - gap);
    }

    /**
     * 회전 사각형에서 빠져나갈 목표 좌표. distanceToRect와 같은 회전 규약을 쓴다.
     * 사각형 안이면 가장 얕은 면으로 빼내고(minimum translation), 밖이면 최근접점 방향으로 밀어낸다.
     */
    private static Escape escapeRect(
            double px,
            double py,
            BigDecimal startX,
            BigDecimal startY,
            BigDecimal endX,
            BigDecimal endY,
            BigDecimal rotation) {
        double minX = Math.min(startX.doubleValue(), endX.doubleValue());
        double maxX = Math.max(startX.doubleValue(), endX.doubleValue());
        double minY = Math.min(startY.doubleValue(), endY.doubleValue());
        double maxY = Math.max(startY.doubleValue(), endY.doubleValue());
        double centerX = (minX + maxX) / 2;
        double centerY = (minY + maxY) / 2;
        double halfX = (maxX - minX) / 2;
        double halfY = (maxY - minY) / 2;
        double radians = Math.toRadians(-rotation.doubleValue());
        double dx = px - centerX;
        double dy = py - centerY;
        double localX = dx * Math.cos(radians) - dy * Math.sin(radians);
        double localY = dx * Math.sin(radians) + dy * Math.cos(radians);

        double targetX;
        double targetY;
        double depth;
        if (Math.abs(localX) > halfX || Math.abs(localY) > halfY) {
            double nearestX = Math.max(-halfX, Math.min(halfX, localX));
            double nearestY = Math.max(-halfY, Math.min(halfY, localY));
            double gap = Math.hypot(localX - nearestX, localY - nearestY);
            if (gap >= TARGET_CLEARANCE) {
                return null;
            }
            depth = TARGET_CLEARANCE - gap;
            targetX = nearestX + (localX - nearestX) / gap * TARGET_CLEARANCE;
            targetY = nearestY + (localY - nearestY) / gap * TARGET_CLEARANCE;
        } else {
            double toPlusX = halfX - localX;
            double toMinusX = halfX + localX;
            double toPlusY = halfY - localY;
            double toMinusY = halfY + localY;
            double shallowest = Math.min(Math.min(toPlusX, toMinusX), Math.min(toPlusY, toMinusY));
            // 내부는 언제나 근접 위반보다 심각하다. 동률은 +x, -x, +y, -y 순으로 고정한다.
            depth = TARGET_CLEARANCE + shallowest;
            targetX = localX;
            targetY = localY;
            if (shallowest == toPlusX) {
                targetX = halfX + TARGET_CLEARANCE;
            } else if (shallowest == toMinusX) {
                targetX = -halfX - TARGET_CLEARANCE;
            } else if (shallowest == toPlusY) {
                targetY = halfY + TARGET_CLEARANCE;
            } else {
                targetY = -halfY - TARGET_CLEARANCE;
            }
        }

        double inverse = -radians;
        return new Escape(
                centerX + targetX * Math.cos(inverse) - targetY * Math.sin(inverse),
                centerY + targetX * Math.sin(inverse) + targetY * Math.cos(inverse),
                depth);
    }

    /** 외곽선은 안쪽으로만 밀어낸다. 바깥으로 나간 에이전트는 어떤 위반보다 먼저 되돌린다. */
    private static Escape escapeBoundary(double px, double py, List<PointDto> boundary, double[] centroid) {
        boolean inside = insidePolygon(px, py, boundary);
        if (inside && distanceToBoundary(px, py, boundary) >= TARGET_CLEARANCE) {
            return null;
        }

        int nearestEdge = 0;
        double best = Double.POSITIVE_INFINITY;
        for (int index = 0; index < boundary.size(); index++) {
            double gap = distanceToSegment(px, py, boundary.get(index), boundary.get((index + 1) % boundary.size()));
            if (gap < best) {
                best = gap;
                nearestEdge = index;
            }
        }
        PointDto start = boundary.get(nearestEdge);
        PointDto end = boundary.get((nearestEdge + 1) % boundary.size());
        double sx = start.x().doubleValue();
        double sy = start.y().doubleValue();
        double ex = end.x().doubleValue();
        double ey = end.y().doubleValue();
        double[] nearest = nearestOnSegment(px, py, sx, sy, ex, ey);
        double gap = Math.hypot(px - nearest[0], py - nearest[1]);

        double unitX;
        double unitY;
        if (inside && gap > EPSILON) {
            unitX = (px - nearest[0]) / gap;
            unitY = (py - nearest[1]) / gap;
        } else {
            double dx = ex - sx;
            double dy = ey - sy;
            double length = Math.hypot(dx, dy);
            unitX = length > EPSILON ? -dy / length : 1;
            unitY = length > EPSILON ? dx / length : 0;
            boolean plusInside = insidePolygon(
                    nearest[0] + unitX * TARGET_CLEARANCE, nearest[1] + unitY * TARGET_CLEARANCE, boundary);
            boolean minusInside = insidePolygon(
                    nearest[0] - unitX * TARGET_CLEARANCE, nearest[1] - unitY * TARGET_CLEARANCE, boundary);
            boolean flip = minusInside && !plusInside;
            if (plusInside == minusInside) {
                // 얇거나 오목한 구간이라 양쪽 판정이 같으면 도형 중심 쪽을 택한다.
                flip = (centroid[0] - nearest[0]) * unitX + (centroid[1] - nearest[1]) * unitY < 0;
            }
            if (flip) {
                unitX = -unitX;
                unitY = -unitY;
            }
        }
        return new Escape(
                nearest[0] + unitX * TARGET_CLEARANCE,
                nearest[1] + unitY * TARGET_CLEARANCE,
                inside ? TARGET_CLEARANCE - gap : TARGET_CLEARANCE + gap);
    }

    private static double[] polygonCentroid(List<PointDto> boundary) {
        double x = 0;
        double y = 0;
        for (PointDto vertex : boundary) {
            x += vertex.x().doubleValue();
            y += vertex.y().doubleValue();
        }
        return new double[] {x / boundary.size(), y / boundary.size()};
    }

    private static PointKey point(BigDecimal x, BigDecimal y, BigDecimal width, BigDecimal height) {
        if (x == null || y == null) {
            throw invalid("외곽선 좌표가 누락되었습니다.");
        }
        if (x.signum() < 0 || y.signum() < 0 || x.compareTo(width) > 0 || y.compareTo(height) > 0) {
            throw invalid("외곽선 좌표가 도면 크기를 벗어났습니다.");
        }
        return new PointKey(x, y);
    }

    private static void validateAreaAndIntersections(List<PointKey> points) {
        BigDecimal twiceArea = BigDecimal.ZERO;
        for (int i = 0; i < points.size(); i++) {
            PointKey a = points.get(i);
            PointKey b = points.get((i + 1) % points.size());
            twiceArea = twiceArea.add(a.x.multiply(b.y).subtract(b.x.multiply(a.y)));
        }
        if (twiceArea.signum() == 0) {
            throw invalid("외곽선의 면적은 0보다 커야 합니다.");
        }

        int size = points.size();
        for (int i = 0; i < size; i++) {
            PointKey a = points.get(i);
            PointKey b = points.get((i + 1) % size);
            for (int j = i + 1; j < size; j++) {
                if (j == i + 1 || (i == 0 && j == size - 1)) {
                    continue;
                }
                PointKey c = points.get(j);
                PointKey d = points.get((j + 1) % size);
                if (segmentsIntersect(a, b, c, d)) {
                    throw invalid("외곽선은 자기 교차하거나 겹칠 수 없습니다.");
                }
            }
        }
    }

    private static boolean segmentsIntersect(PointKey a, PointKey b, PointKey c, PointKey d) {
        int abC = orientation(a, b, c);
        int abD = orientation(a, b, d);
        int cdA = orientation(c, d, a);
        int cdB = orientation(c, d, b);
        if (abC != abD && cdA != cdB) {
            return true;
        }
        return (abC == 0 && onSegment(a, b, c))
                || (abD == 0 && onSegment(a, b, d))
                || (cdA == 0 && onSegment(c, d, a))
                || (cdB == 0 && onSegment(c, d, b));
    }

    private static int orientation(PointKey a, PointKey b, PointKey c) {
        return b.x.subtract(a.x)
                .multiply(c.y.subtract(a.y))
                .subtract(b.y.subtract(a.y).multiply(c.x.subtract(a.x)))
                .signum();
    }

    private static boolean onSegment(PointKey a, PointKey b, PointKey p) {
        return p.x.compareTo(a.x.min(b.x)) >= 0
                && p.x.compareTo(a.x.max(b.x)) <= 0
                && p.y.compareTo(a.y.min(b.y)) >= 0
                && p.y.compareTo(a.y.max(b.y)) <= 0;
    }

    private static void validatePoint(PointDto point, String label) {
        if (point == null || point.x() == null || point.y() == null) {
            throw invalid(label + " 좌표가 필요합니다.");
        }
        if (point.x().abs().compareTo(MAX_VALUE) > 0 || point.y().abs().compareTo(MAX_VALUE) > 0) {
            throw invalid(label + " 좌표는 ±1000000 이하여야 합니다.");
        }
    }

    private static boolean insidePolygon(PointDto point, List<PointDto> polygon) {
        return insidePolygon(point.x().doubleValue(), point.y().doubleValue(), polygon);
    }

    private static boolean insidePolygon(double x, double y, List<PointDto> polygon) {
        boolean inside = false;
        for (int i = 0, j = polygon.size() - 1; i < polygon.size(); j = i++) {
            double xi = polygon.get(i).x().doubleValue();
            double yi = polygon.get(i).y().doubleValue();
            double xj = polygon.get(j).x().doubleValue();
            double yj = polygon.get(j).y().doubleValue();
            if ((yi > y) != (yj > y) && x < (xj - xi) * (y - yi) / (yj - yi) + xi) {
                inside = !inside;
            }
        }
        return inside;
    }

    private static double distanceToBoundary(PointDto point, List<PointDto> boundary) {
        return distanceToBoundary(point.x().doubleValue(), point.y().doubleValue(), boundary);
    }

    private static double distanceToBoundary(double x, double y, List<PointDto> boundary) {
        double minimum = Double.POSITIVE_INFINITY;
        for (int i = 0; i < boundary.size(); i++) {
            minimum = Math.min(
                    minimum, distanceToSegment(x, y, boundary.get(i), boundary.get((i + 1) % boundary.size())));
        }
        return minimum;
    }

    private static double distanceToSegment(PointDto point, Wall wall) {
        return distanceToSegment(
                point, new PointDto(wall.getStartX(), wall.getStartY()), new PointDto(wall.getEndX(), wall.getEndY()));
    }

    private static double distanceToSegment(PointDto point, LayoutExit exit) {
        return distanceToSegment(
                point, new PointDto(exit.getStartX(), exit.getStartY()), new PointDto(exit.getEndX(), exit.getEndY()));
    }

    private static double distanceToSegment(PointDto point, PointDto start, PointDto end) {
        return distanceToSegment(point.x().doubleValue(), point.y().doubleValue(), start, end);
    }

    private static double distanceToSegment(double px, double py, PointDto start, PointDto end) {
        double[] nearest = nearestOnSegment(
                px,
                py,
                start.x().doubleValue(),
                start.y().doubleValue(),
                end.x().doubleValue(),
                end.y().doubleValue());
        return Math.hypot(px - nearest[0], py - nearest[1]);
    }

    private static double[] nearestOnSegment(double px, double py, double sx, double sy, double ex, double ey) {
        double dx = ex - sx;
        double dy = ey - sy;
        if (dx == 0 && dy == 0) {
            return new double[] {sx, sy};
        }
        double t = Math.max(0, Math.min(1, ((px - sx) * dx + (py - sy) * dy) / (dx * dx + dy * dy)));
        return new double[] {sx + t * dx, sy + t * dy};
    }

    private static double distanceToRect(PointDto point, Pillar pillar) {
        return distanceToRect(
                point,
                pillar.getStartX(),
                pillar.getStartY(),
                pillar.getEndX(),
                pillar.getEndY(),
                pillar.getRotation());
    }

    private static double distanceToRect(PointDto point, Fabric fabric) {
        return distanceToRect(
                point,
                fabric.getStartX(),
                fabric.getStartY(),
                fabric.getEndX(),
                fabric.getEndY(),
                fabric.getRotation());
    }

    private static double distanceToRect(
            PointDto point,
            BigDecimal startX,
            BigDecimal startY,
            BigDecimal endX,
            BigDecimal endY,
            BigDecimal rotation) {
        double minX = Math.min(startX.doubleValue(), endX.doubleValue());
        double maxX = Math.max(startX.doubleValue(), endX.doubleValue());
        double minY = Math.min(startY.doubleValue(), endY.doubleValue());
        double maxY = Math.max(startY.doubleValue(), endY.doubleValue());
        double centerX = (minX + maxX) / 2;
        double centerY = (minY + maxY) / 2;
        double radians = Math.toRadians(-rotation.doubleValue());
        double dx = point.x().doubleValue() - centerX;
        double dy = point.y().doubleValue() - centerY;
        double localX = centerX + dx * Math.cos(radians) - dy * Math.sin(radians);
        double localY = centerY + dx * Math.sin(radians) + dy * Math.cos(radians);
        double outsideX = Math.max(Math.max(minX - localX, 0), localX - maxX);
        double outsideY = Math.max(Math.max(minY - localY, 0), localY - maxY);
        return Math.hypot(outsideX, outsideY);
    }

    private static double distance(PointDto left, PointDto right) {
        return Math.hypot(
                left.x().doubleValue() - right.x().doubleValue(),
                left.y().doubleValue() - right.y().doubleValue());
    }

    private static InvalidSimulationGeometryException invalid(String message) {
        return new InvalidSimulationGeometryException(message);
    }

    private record PointKey(BigDecimal x, BigDecimal y) {
        private static final Comparator<PointKey> ORDER =
                Comparator.comparing(PointKey::x).thenComparing(PointKey::y);

        private PointKey {
            x = x.stripTrailingZeros();
            y = y.stripTrailingZeros();
        }

        private PointDto toDto() {
            return new PointDto(x, y);
        }
    }

    private record EdgeKey(PointKey first, PointKey second) {
        private static EdgeKey of(PointKey left, PointKey right) {
            return PointKey.ORDER.compare(left, right) <= 0 ? new EdgeKey(left, right) : new EdgeKey(right, left);
        }
    }

    private record Cell(long x, long y) {
        private static Cell of(PointDto point) {
            return new Cell((long) Math.floor(point.x().doubleValue() / AGENT_SPACING), (long)
                    Math.floor(point.y().doubleValue() / AGENT_SPACING));
        }

        // 셀 한 변은 검사 임계값 이상이어야 3x3 이웃 조회가 모든 위반 쌍을 잡는다.
        // 재배치는 AGENT_SPACING보다 살짝 큰 TARGET_SPACING을 노리므로 전용 셀 크기가 필요하다.
        private static Cell of(double x, double y, double size) {
            return new Cell((long) Math.floor(x / size), (long) Math.floor(y / size));
        }
    }
}
