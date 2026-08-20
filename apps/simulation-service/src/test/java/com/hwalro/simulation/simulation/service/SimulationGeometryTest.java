package com.hwalro.simulation.simulation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hwalro.simulation.drawing.domain.Fabric;
import com.hwalro.simulation.drawing.domain.LayoutExit;
import com.hwalro.simulation.drawing.domain.OutsideWall;
import com.hwalro.simulation.drawing.domain.Pillar;
import com.hwalro.simulation.drawing.domain.Wall;
import com.hwalro.simulation.simulation.dto.SimulationDtos.HazardZoneDto;
import com.hwalro.simulation.simulation.dto.SimulationDtos.PointDto;
import com.hwalro.simulation.simulation.exception.InvalidSimulationGeometryException;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;

class SimulationGeometryTest {
    @Test
    void assemblesUnorderedSegmentsIntoOneBoundary() {
        List<PointDto> boundary = SimulationGeometry.assembleBoundary(
                List.of(wall(10, 10, 0, 10), wall(0, 0, 10, 0), wall(0, 10, 0, 0), wall(10, 0, 10, 10)),
                decimal(10),
                decimal(10));

        assertThat(boundary)
                .extracting(point -> List.of(point.x().doubleValue(), point.y().doubleValue()))
                .containsExactly(List.of(0.0, 0.0), List.of(0.0, 10.0), List.of(10.0, 10.0), List.of(10.0, 0.0));
    }

    @Test
    void rejectsDisconnectedAndSelfIntersectingBoundaries() {
        assertThatThrownBy(() -> SimulationGeometry.assembleBoundary(
                        List.of(
                                wall(0, 0, 2, 0),
                                wall(2, 0, 1, 1),
                                wall(1, 1, 0, 0),
                                wall(5, 5, 7, 5),
                                wall(7, 5, 6, 6),
                                wall(6, 6, 5, 5)),
                        decimal(10),
                        decimal(10)))
                .isInstanceOf(InvalidSimulationGeometryException.class);

        assertThatThrownBy(() -> SimulationGeometry.assembleBoundary(
                        List.of(wall(0, 0, 10, 10), wall(10, 10, 0, 10), wall(0, 10, 10, 0), wall(10, 0, 0, 0)),
                        decimal(10),
                        decimal(10)))
                .isInstanceOf(InvalidSimulationGeometryException.class);
    }

    @Test
    void rejectsRadiusOnlyClearanceButAcceptsOneMillimeterMarginAndExactSpacing() {
        List<PointDto> boundary = SimulationGeometry.assembleBoundary(
                List.of(wall(0, 0, 10, 0), wall(10, 0, 10, 10), wall(10, 10, 0, 10), wall(0, 10, 0, 0)),
                decimal(10),
                decimal(10));

        assertThatThrownBy(() -> SimulationGeometry.validateSetup(
                        List.of(point(0.3, 1)), List.of(), boundary, List.of(), List.of(), List.of(), List.of()))
                .isInstanceOf(InvalidSimulationGeometryException.class);

        SimulationGeometry.validateSetup(
                List.of(point(0.301, 1), point(0.901, 1)),
                List.of(),
                boundary,
                List.of(),
                List.of(),
                List.of(),
                List.of());
    }

    @Test
    void rejectsAgentsThatAreTooClose() {
        List<PointDto> boundary = SimulationGeometry.assembleBoundary(
                List.of(wall(0, 0, 10, 0), wall(10, 0, 10, 10), wall(10, 10, 0, 10), wall(0, 10, 0, 0)),
                decimal(10),
                decimal(10));

        assertThatThrownBy(() -> SimulationGeometry.validateSetup(
                        List.of(point(1, 1), point(1.59, 1)),
                        List.of(),
                        boundary,
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of()))
                .isInstanceOf(InvalidSimulationGeometryException.class);
    }

    @Test
    void acceptsHazardCenterOnBoundary() {
        List<PointDto> boundary = SimulationGeometry.assembleBoundary(
                List.of(wall(0, 0, 10, 0), wall(10, 0, 10, 10), wall(10, 10, 0, 10), wall(0, 10, 0, 0)),
                decimal(10),
                decimal(10));

        SimulationGeometry.validateSetup(
                List.of(),
                List.of(new HazardZoneDto(null, decimal(0), decimal(5), decimal(1))),
                boundary,
                List.of(),
                List.of(),
                List.of(),
                List.of());
    }

    @Test
    void rejectsMoreThanFiveThousandAgents() {
        assertThatThrownBy(() -> SimulationGeometry.validateSetup(
                        Collections.nCopies(5_001, point(1, 1)),
                        List.of(),
                        squareBoundary(),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of()))
                .isInstanceOf(InvalidSimulationGeometryException.class);
    }

    @Test
    void rejectsAgentCloserThanPointThreeMetersToWallOrExit() {
        List<PointDto> boundary = squareBoundary();

        assertThatThrownBy(() -> SimulationGeometry.validateSetup(
                        List.of(point(5, 5)),
                        List.of(),
                        boundary,
                        List.of(innerWall(4, 5.299999, 6, 5.299999)),
                        List.of(),
                        List.of(),
                        List.of()))
                .isInstanceOf(InvalidSimulationGeometryException.class);

        assertThatThrownBy(() -> SimulationGeometry.validateSetup(
                        List.of(point(5, 5)),
                        List.of(),
                        boundary,
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(layoutExit(4, 5.299999, 6, 5.299999))))
                .isInstanceOf(InvalidSimulationGeometryException.class);
    }

    @Test
    void rejectsRadiusOnlyObstacleClearanceButAcceptsOneMillimeterMargin() {
        List<PointDto> boundary = squareBoundary();

        assertThatThrownBy(() -> SimulationGeometry.validateSetup(
                        List.of(point(5, 5)),
                        List.of(),
                        boundary,
                        List.of(innerWall(4, 5.3, 6, 5.3)),
                        List.of(),
                        List.of(),
                        List.of()))
                .isInstanceOf(InvalidSimulationGeometryException.class);

        SimulationGeometry.validateSetup(
                List.of(point(5, 5)),
                List.of(),
                boundary,
                List.of(innerWall(4, 5.301, 6, 5.301)),
                List.of(),
                List.of(),
                List.of());
    }

    @Test
    void rejectsAgentInsideRotatedPillarOrFabric() {
        List<PointDto> boundary = squareBoundary();

        assertThatThrownBy(() -> SimulationGeometry.validateSetup(
                        List.of(point(6.3, 6.3)),
                        List.of(),
                        boundary,
                        List.of(),
                        List.of(pillar(3, 4.5, 7, 5.5, 45)),
                        List.of(),
                        List.of()))
                .isInstanceOf(InvalidSimulationGeometryException.class);

        assertThatThrownBy(() -> SimulationGeometry.validateSetup(
                        List.of(point(6.3, 6.3)),
                        List.of(),
                        boundary,
                        List.of(),
                        List.of(),
                        List.of(fabric(3, 4.5, 7, 5.5, 45)),
                        List.of()))
                .isInstanceOf(InvalidSimulationGeometryException.class);
    }

    @Test
    void rejectsOutsideWallBeyondFloorBounds() {
        assertThatThrownBy(() -> SimulationGeometry.assembleBoundary(
                        List.of(wall(0, 0, 10.1, 0), wall(10.1, 0, 10, 10), wall(10, 10, 0, 10), wall(0, 10, 0, 0)),
                        decimal(10),
                        decimal(10)))
                .isInstanceOf(InvalidSimulationGeometryException.class);
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
