package com.hwalro.simulation.simulation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hwalro.simulation.drawing.domain.Fabric;
import com.hwalro.simulation.drawing.domain.LayoutExit;
import com.hwalro.simulation.drawing.domain.OutsideWall;
import com.hwalro.simulation.drawing.domain.Pillar;
import com.hwalro.simulation.drawing.domain.Wall;
import com.hwalro.simulation.simulation.dto.SimulationDtos.PointDto;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class SimulationGeometryScrum151RegressionTest {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final long MOVED_FABRIC_ID = 1671L;

    private static List<PointDto> agents;
    private static List<Fabric> fabrics;
    private static List<Wall> walls;
    private static List<Pillar> pillars;
    private static List<LayoutExit> exits;
    private static List<PointDto> boundary;

    @BeforeAll
    static void loadSimulation13Drawing() throws IOException {
        agents = loadAgents("scrum151/agents.json");
        fabrics = loadFabrics("scrum151/fabrics.json");
        walls = loadWalls("scrum151/walls.json");
        pillars = loadPillars("scrum151/pillars.json");
        exits = loadExits("scrum151/exits.json");

        JsonNode floorPlan = MAPPER.readTree(resource("scrum151/floorplan.json"));
        BigDecimal width = floorPlan.path("w").decimalValue();
        BigDecimal height = floorPlan.path("h").decimalValue();
        List<OutsideWall> outsideWalls = loadOutsideWalls("scrum151/outwall.json");
        boundary = SimulationGeometry.assembleBoundary(outsideWalls, width, height);
    }

    @Test
    void searchCandidate5ShiftsFabric1671Down4mAndStillPlacesEveryone() {
        assertFabricOntoCrowdStillPlacesEveryone(new BigDecimal("66.3164"), new BigDecimal("66.9297"));
    }

    @Test
    void searchCandidate6ShiftsFabric1671Down2mAndStillPlacesEveryone() {
        assertFabricOntoCrowdStillPlacesEveryone(new BigDecimal("68.3164"), new BigDecimal("68.9297"));
    }

    private static void assertFabricOntoCrowdStillPlacesEveryone(BigDecimal shiftedStartY, BigDecimal shiftedEndY) {
        List<Fabric> mutatedFabrics = shiftFabric(fabrics, MOVED_FABRIC_ID, shiftedStartY, shiftedEndY);

        List<PointDto> relaxed =
                SimulationGeometry.relaxAgents(agents, boundary, walls, pillars, mutatedFabrics, exits);

        assertThatCode(() -> SimulationGeometry.validateSetup(
                        relaxed, List.of(), boundary, walls, pillars, mutatedFabrics, exits))
                .doesNotThrowAnyException();

        assertThat(relaxed).hasSameSizeAs(agents);
        long movedCount = 0;
        for (int index = 0; index < agents.size(); index++) {
            if (relaxed.get(index) != agents.get(index)) {
                movedCount++;
            }
        }
        assertThat(movedCount).isPositive();
    }

    private static List<Fabric> shiftFabric(List<Fabric> source, long fabricId, BigDecimal startY, BigDecimal endY) {
        List<Fabric> copy = new ArrayList<>(source.size());
        for (Fabric fabric : source) {
            if (!Objects.equals(fabric.getId(), fabricId)) {
                copy.add(fabric);
                continue;
            }
            Fabric shifted = new Fabric();
            shifted.setId(fabric.getId());
            shifted.setName(fabric.getName());
            shifted.setStartX(fabric.getStartX());
            shifted.setEndX(fabric.getEndX());
            shifted.setStartY(startY);
            shifted.setEndY(endY);
            shifted.setRotation(fabric.getRotation());
            copy.add(shifted);
        }
        return List.copyOf(copy);
    }

    private static InputStream resource(String path) {
        InputStream stream =
                SimulationGeometryScrum151RegressionTest.class.getClassLoader().getResourceAsStream(path);
        if (stream == null) {
            throw new IllegalStateException("테스트 리소스가 없습니다: " + path);
        }
        return stream;
    }

    private static List<PointDto> loadAgents(String path) throws IOException {
        JsonNode root = MAPPER.readTree(resource(path));
        List<PointDto> points = new ArrayList<>(root.size());
        for (JsonNode pair : root) {
            points.add(new PointDto(pair.get(0).decimalValue(), pair.get(1).decimalValue()));
        }
        return List.copyOf(points);
    }

    private static List<Fabric> loadFabrics(String path) throws IOException {
        JsonNode root = MAPPER.readTree(resource(path));
        List<Fabric> fabrics = new ArrayList<>(root.size());
        for (JsonNode row : root) {
            Fabric fabric = new Fabric();
            fabric.setId(row.get(0).asLong());
            fabric.setStartX(row.get(1).decimalValue());
            fabric.setStartY(row.get(2).decimalValue());
            fabric.setEndX(row.get(3).decimalValue());
            fabric.setEndY(row.get(4).decimalValue());
            fabric.setRotation(row.get(5).decimalValue());
            fabrics.add(fabric);
        }
        return List.copyOf(fabrics);
    }

    private static List<Wall> loadWalls(String path) throws IOException {
        JsonNode root = MAPPER.readTree(resource(path));
        List<Wall> loaded = new ArrayList<>(root.size());
        for (JsonNode row : root) {
            Wall wall = new Wall();
            wall.setStartX(row.get(0).decimalValue());
            wall.setStartY(row.get(1).decimalValue());
            wall.setEndX(row.get(2).decimalValue());
            wall.setEndY(row.get(3).decimalValue());
            loaded.add(wall);
        }
        return List.copyOf(loaded);
    }

    private static List<Pillar> loadPillars(String path) throws IOException {
        JsonNode root = MAPPER.readTree(resource(path));
        List<Pillar> pillars = new ArrayList<>(root.size());
        for (JsonNode row : root) {
            Pillar pillar = new Pillar();
            pillar.setStartX(row.get(0).decimalValue());
            pillar.setStartY(row.get(1).decimalValue());
            pillar.setEndX(row.get(2).decimalValue());
            pillar.setEndY(row.get(3).decimalValue());
            pillar.setRotation(row.get(4).decimalValue());
            pillars.add(pillar);
        }
        return List.copyOf(pillars);
    }

    private static List<LayoutExit> loadExits(String path) throws IOException {
        JsonNode root = MAPPER.readTree(resource(path));
        List<LayoutExit> exits = new ArrayList<>(root.size());
        for (JsonNode row : root) {
            LayoutExit exit = new LayoutExit();
            exit.setStartX(row.get(0).decimalValue());
            exit.setStartY(row.get(1).decimalValue());
            exit.setEndX(row.get(2).decimalValue());
            exit.setEndY(row.get(3).decimalValue());
            exits.add(exit);
        }
        return List.copyOf(exits);
    }

    private static List<OutsideWall> loadOutsideWalls(String path) throws IOException {
        JsonNode root = MAPPER.readTree(resource(path));
        List<OutsideWall> outsideWalls = new ArrayList<>(root.size());
        for (JsonNode row : root) {
            OutsideWall outsideWall = new OutsideWall();
            outsideWall.setStartX(row.get(0).decimalValue());
            outsideWall.setStartY(row.get(1).decimalValue());
            outsideWall.setEndX(row.get(2).decimalValue());
            outsideWall.setEndY(row.get(3).decimalValue());
            outsideWalls.add(outsideWall);
        }
        return List.copyOf(outsideWalls);
    }
}
