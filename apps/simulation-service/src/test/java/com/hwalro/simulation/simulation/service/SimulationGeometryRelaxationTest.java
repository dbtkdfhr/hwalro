package com.hwalro.simulation.simulation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hwalro.simulation.drawing.domain.Fabric;
import com.hwalro.simulation.drawing.domain.LayoutExit;
import com.hwalro.simulation.drawing.domain.OutsideWall;
import com.hwalro.simulation.drawing.domain.Pillar;
import com.hwalro.simulation.drawing.domain.Wall;
import com.hwalro.simulation.simulation.dto.SimulationDtos.PointDto;
import com.hwalro.simulation.simulation.exception.InvalidSimulationGeometryException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * relaxAgents의 계약은 하나다: 결과가 반드시 validateSetup을 통과해야 한다.
 * 그래서 대부분의 검증은 {@link #assertValid} 한 줄로 끝난다.
 */
class SimulationGeometryRelaxationTest {
    @Test
    void leavesAValidPlacementCompletelyUntouched() {
        List<PointDto> boundary = squareBoundary();
        List<PointDto> agents = List.of(point(2, 2), point(3, 2), point(5, 5));

        List<PointDto> relaxed =
                SimulationGeometry.relaxAgents(agents, boundary, List.of(), List.of(), List.of(), List.of());

        // 움직일 필요가 없으면 입력 인스턴스를 그대로 돌려준다 - 반올림으로 인한 잡음이 없어야 한다.
        assertThat(relaxed.get(0)).isSameAs(agents.get(0));
        assertThat(relaxed.get(1)).isSameAs(agents.get(1));
        assertThat(relaxed.get(2)).isSameAs(agents.get(2));
    }

    @Test
    void pushesAnAgentOutOfAFabricInterior() {
        List<PointDto> boundary = squareBoundary();
        List<Fabric> fabrics = List.of(fabric(3, 4, 7, 6, 0));

        List<PointDto> relaxed = SimulationGeometry.relaxAgents(
                List.of(point(5, 5)), boundary, List.of(), List.of(), fabrics, List.of());

        // 가장 얕은 면이 y축이므로 y로 빠져나간다.
        double y = relaxed.get(0).y().doubleValue();
        assertThat(y < 4.0 || y > 6.0).isTrue();
        assertValid(relaxed, boundary, List.of(), List.of(), fabrics, List.of());
    }

    @Test
    void pushesAnAgentOutOfARotatedFabric() {
        List<PointDto> boundary = squareBoundary();
        List<Fabric> fabrics = List.of(fabric(3, 4.5, 7, 5.5, 45));

        List<PointDto> relaxed = SimulationGeometry.relaxAgents(
                List.of(point(6.3, 6.3)), boundary, List.of(), List.of(), fabrics, List.of());

        assertThat(relaxed.get(0)).isNotEqualTo(point(6.3, 6.3));
        assertValid(relaxed, boundary, List.of(), List.of(), fabrics, List.of());
    }

    @Test
    void pushesAnAgentOutOfAPillar() {
        List<PointDto> boundary = squareBoundary();
        List<Pillar> pillars = List.of(pillar(4, 4, 6, 6, 0));

        List<PointDto> relaxed = SimulationGeometry.relaxAgents(
                List.of(point(5, 5)), boundary, List.of(), pillars, List.of(), List.of());

        assertValid(relaxed, boundary, List.of(), pillars, List.of(), List.of());
    }

    @Test
    void cascadesDisplacementThroughNeighbouringAgents() {
        List<PointDto> boundary = squareBoundary();
        // 0.61m 간격으로 촘촘히 세운 줄. 앞쪽 둘을 구조물이 덮으면 뒤쪽까지 연쇄로 밀려야 한다.
        List<PointDto> agents = new ArrayList<>();
        for (int index = 0; index < 8; index++) {
            agents.add(point(2.0 + index * 0.61, 5.0));
        }
        List<Fabric> fabrics = List.of(fabric(1.5, 4.5, 3.0, 5.5, 0));

        List<PointDto> relaxed =
                SimulationGeometry.relaxAgents(agents, boundary, List.of(), List.of(), fabrics, List.of());

        // 구조물에 직접 닿지 않은 뒤쪽 에이전트까지 움직였다는 것이 연쇄의 증거다.
        long movedCount = countMoved(agents, relaxed);
        assertThat(movedCount).isGreaterThan(2);
        assertValid(relaxed, boundary, List.of(), List.of(), fabrics, List.of());
    }

    @Test
    void separatesAgentsPlacedAtTheExactSamePoint() {
        List<PointDto> boundary = squareBoundary();
        List<PointDto> agents = List.of(point(5, 5), point(5, 5), point(5, 5));

        List<PointDto> relaxed =
                SimulationGeometry.relaxAgents(agents, boundary, List.of(), List.of(), List.of(), List.of());

        assertValid(relaxed, boundary, List.of(), List.of(), List.of(), List.of());
    }

    @Test
    void pushesAnAgentOffAWallItSitsExactlyOn() {
        List<PointDto> boundary = squareBoundary();
        List<Wall> walls = List.of(innerWall(2, 5, 8, 5));

        List<PointDto> relaxed =
                SimulationGeometry.relaxAgents(List.of(point(5, 5)), boundary, walls, List.of(), List.of(), List.of());

        assertThat(relaxed.get(0).y().doubleValue()).isNotEqualTo(5.0);
        assertValid(relaxed, boundary, walls, List.of(), List.of(), List.of());
    }

    @Test
    void pushesAnAgentOffAnExitItSitsOn() {
        List<PointDto> boundary = squareBoundary();
        List<LayoutExit> exits = List.of(layoutExit(4, 0, 6, 0));

        List<PointDto> relaxed = SimulationGeometry.relaxAgents(
                List.of(point(5, 0.1)), boundary, List.of(), List.of(), List.of(), exits);

        assertValid(relaxed, boundary, List.of(), List.of(), List.of(), exits);
    }

    @Test
    void keepsAgentsInsideTheBoundaryWhenSqueezedAgainstIt() {
        List<PointDto> boundary = squareBoundary();
        // 외곽선과 구조물 사이의 좁은 틈에 몰아넣는다. 벽을 뚫고 나가면 안 된다.
        List<PointDto> agents = List.of(point(5, 0.5), point(5.6, 0.5), point(6.2, 0.5));
        List<Fabric> fabrics = List.of(fabric(3, 0.9, 8, 4, 0));

        List<PointDto> relaxed =
                SimulationGeometry.relaxAgents(agents, boundary, List.of(), List.of(), fabrics, List.of());

        assertValid(relaxed, boundary, List.of(), List.of(), fabrics, List.of());
    }

    @Test
    void isDeterministic() {
        List<PointDto> boundary = squareBoundary();
        List<PointDto> agents = new ArrayList<>();
        for (int index = 0; index < 20; index++) {
            agents.add(point(2.0 + (index % 5) * 0.62, 3.0 + (index / 5) * 0.62));
        }
        List<Fabric> fabrics = List.of(fabric(2.5, 3.2, 4.0, 4.4, 0));

        List<PointDto> first =
                SimulationGeometry.relaxAgents(agents, boundary, List.of(), List.of(), fabrics, List.of());
        List<PointDto> second =
                SimulationGeometry.relaxAgents(agents, boundary, List.of(), List.of(), fabrics, List.of());

        assertThat(first).containsExactlyElementsOf(second);
    }

    @Test
    void roundedOutputStillSatisfiesTheEpsilonComparisons() {
        // 반올림(소수점 4자리)이 간격을 갉아먹어도 validateSetup을 통과해야 한다.
        List<PointDto> boundary = squareBoundary();
        List<PointDto> agents = new ArrayList<>();
        for (int x = 0; x < 12; x++) {
            for (int y = 0; y < 12; y++) {
                agents.add(point(1.0 + x * 0.61, 1.0 + y * 0.61));
            }
        }
        List<Fabric> fabrics = List.of(fabric(3, 3, 5, 5, 0));

        List<PointDto> relaxed =
                SimulationGeometry.relaxAgents(agents, boundary, List.of(), List.of(), fabrics, List.of());

        // 움직인 좌표만 4자리로 반올림된다. 그대로 둔 에이전트는 입력 인스턴스이므로 입력의 자릿수를 유지한다.
        for (int index = 0; index < agents.size(); index++) {
            if (relaxed.get(index) != agents.get(index)) {
                assertThat(relaxed.get(index).x().scale()).isLessThanOrEqualTo(4);
                assertThat(relaxed.get(index).y().scale()).isLessThanOrEqualTo(4);
            }
        }
        assertValid(relaxed, boundary, List.of(), List.of(), fabrics, List.of());
    }

    @Test
    void failsWhenThereIsNoRoomForEveryone() {
        List<PointDto> boundary = squareBoundary();
        List<PointDto> agents = new ArrayList<>();
        for (int index = 0; index < 60; index++) {
            agents.add(point(4.5 + (index % 8) * 0.01, 4.5 + (index / 8) * 0.01));
        }
        // 방을 거의 다 채우는 구조물. 60명이 들어갈 자리가 남지 않는다.
        List<Fabric> fabrics = List.of(fabric(0.5, 0.5, 9.5, 9.0, 0));

        assertThatThrownBy(() ->
                        SimulationGeometry.relaxAgents(agents, boundary, List.of(), List.of(), fabrics, List.of()))
                .isInstanceOf(InvalidSimulationGeometryException.class)
                .hasMessageContaining("재배치");
    }

    @Test
    void rejectsMoreThanFiveThousandAgents() {
        List<PointDto> boundary = squareBoundary();

        assertThatThrownBy(() -> SimulationGeometry.relaxAgents(
                        Collections.nCopies(5_001, point(5, 5)), boundary, List.of(), List.of(), List.of(), List.of()))
                .isInstanceOf(InvalidSimulationGeometryException.class);
    }

    @Test
    void handlesAnEmptyAgentList() {
        assertThat(SimulationGeometry.relaxAgents(
                        List.of(), squareBoundary(), List.of(), List.of(), List.of(), List.of()))
                .isEmpty();
    }

    private static long countMoved(List<PointDto> before, List<PointDto> after) {
        long moved = 0;
        for (int index = 0; index < before.size(); index++) {
            if (after.get(index) != before.get(index)) {
                moved++;
            }
        }
        return moved;
    }

    private static void assertValid(
            List<PointDto> agents,
            List<PointDto> boundary,
            List<Wall> walls,
            List<Pillar> pillars,
            List<Fabric> fabrics,
            List<LayoutExit> exits) {
        assertThatCode(() ->
                        SimulationGeometry.validateSetup(agents, List.of(), boundary, walls, pillars, fabrics, exits))
                .doesNotThrowAnyException();
    }

    private static List<PointDto> squareBoundary() {
        return SimulationGeometry.assembleBoundary(
                List.of(wall(0, 0, 10, 0), wall(10, 0, 10, 10), wall(10, 10, 0, 10), wall(0, 10, 0, 0)),
                decimal(10),
                decimal(10));
    }

    private static OutsideWall wall(double startX, double startY, double endX, double endY) {
        OutsideWall wall = new OutsideWall();
        wall.setName("outside");
        wall.setStartX(decimal(startX));
        wall.setStartY(decimal(startY));
        wall.setEndX(decimal(endX));
        wall.setEndY(decimal(endY));
        return wall;
    }

    private static Wall innerWall(double startX, double startY, double endX, double endY) {
        Wall wall = new Wall();
        wall.setName("wall");
        wall.setStartX(decimal(startX));
        wall.setStartY(decimal(startY));
        wall.setEndX(decimal(endX));
        wall.setEndY(decimal(endY));
        return wall;
    }

    private static LayoutExit layoutExit(double startX, double startY, double endX, double endY) {
        LayoutExit exit = new LayoutExit();
        exit.setName("exit");
        exit.setStartX(decimal(startX));
        exit.setStartY(decimal(startY));
        exit.setEndX(decimal(endX));
        exit.setEndY(decimal(endY));
        return exit;
    }

    private static Pillar pillar(double startX, double startY, double endX, double endY, double rotation) {
        Pillar pillar = new Pillar();
        pillar.setName("pillar");
        pillar.setStartX(decimal(startX));
        pillar.setStartY(decimal(startY));
        pillar.setEndX(decimal(endX));
        pillar.setEndY(decimal(endY));
        pillar.setRotation(decimal(rotation));
        return pillar;
    }

    private static Fabric fabric(double startX, double startY, double endX, double endY, double rotation) {
        Fabric fabric = new Fabric();
        fabric.setName("fabric");
        fabric.setStartX(decimal(startX));
        fabric.setStartY(decimal(startY));
        fabric.setEndX(decimal(endX));
        fabric.setEndY(decimal(endY));
        fabric.setRotation(decimal(rotation));
        return fabric;
    }

    private static PointDto point(double x, double y) {
        return new PointDto(decimal(x), decimal(y));
    }

    private static BigDecimal decimal(double value) {
        return BigDecimal.valueOf(value);
    }
}
