package com.hwalro.simulation.improvement.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hwalro.simulation.drawing.domain.Fabric;
import com.hwalro.simulation.drawing.domain.FloorPlan;
import com.hwalro.simulation.drawing.domain.Layout;
import com.hwalro.simulation.drawing.domain.LayoutExit;
import com.hwalro.simulation.drawing.domain.LayoutVersion;
import com.hwalro.simulation.drawing.domain.OutsideWall;
import com.hwalro.simulation.drawing.domain.Pillar;
import com.hwalro.simulation.drawing.domain.Wall;
import com.hwalro.simulation.drawing.mapper.DrawingMapper;
import com.hwalro.simulation.improvement.domain.HeatmapChunk;
import com.hwalro.simulation.improvement.domain.ImprovementSource;
import com.hwalro.simulation.improvement.domain.StoredBottleneck;
import com.hwalro.simulation.improvement.mapper.ImprovementSourceMapper;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ImprovementSourceLoaderTest {
    @Mock
    private DrawingMapper drawingMapper;

    @Mock
    private ImprovementSourceMapper improvementSourceMapper;

    @Test
    void loadsDrawingAndResultDataIntoCandidateSearchInput() {
        long simulationId = 10L;
        long layoutVersionId = 20L;
        when(improvementSourceMapper.findLayoutVersionIdBySimulationId(simulationId))
                .thenReturn(layoutVersionId);
        when(drawingMapper.findLayoutVersionById(layoutVersionId)).thenReturn(layoutVersion());
        when(drawingMapper.findLayoutById(30L)).thenReturn(layout());
        when(drawingMapper.findFloorPlanById(40L)).thenReturn(floorPlan());
        when(drawingMapper.findFabricsByVersionId(layoutVersionId)).thenReturn(List.of(fabric()));
        when(drawingMapper.findWallsByVersionId(layoutVersionId)).thenReturn(List.of(rectangle(new Wall())));
        when(drawingMapper.findPillarsByVersionId(layoutVersionId)).thenReturn(List.of(rotatedPillar()));
        when(drawingMapper.findOutsideWallsByVersionId(layoutVersionId))
                .thenReturn(List.of(rectangle(new OutsideWall())));
        when(drawingMapper.findLayoutExitsByVersionId(layoutVersionId))
                .thenReturn(List.of(rectangle(new LayoutExit())));
        when(improvementSourceMapper.findBottlenecksBySimulationId(simulationId))
                .thenReturn(List.of(bottleneck()));
        when(improvementSourceMapper.findHeatmapChunksBySimulationId(simulationId))
                .thenReturn(List.of(new HeatmapChunk(0, "{}")));

        ImprovementSource source = new ImprovementSourceLoader(
                        drawingMapper, improvementSourceMapper, new ObjectMapper())
                .load(simulationId);

        assertEquals(100.0, source.floorWidth());
        assertEquals(80.0, source.floorHeight());
        assertEquals(1, source.fabrics().size());
        assertEquals(4, source.fixedObstacles().size());
        assertEquals(1, source.exits().size());
        assertEquals(1, source.bottlenecks().size());
        assertEquals(40.0, source.bottlenecks().get(0).bounds().center().x());
        assertEquals(1, source.heatmapChunks().size());
    }

    private static LayoutVersion layoutVersion() {
        LayoutVersion value = new LayoutVersion();
        value.setLayoutId(30L);
        return value;
    }

    private static Layout layout() {
        Layout value = new Layout();
        value.setFloorPlanId(40L);
        return value;
    }

    private static FloorPlan floorPlan() {
        FloorPlan value = new FloorPlan();
        value.setWidth(BigDecimal.valueOf(100));
        value.setHeight(BigDecimal.valueOf(80));
        return value;
    }

    private static Fabric fabric() {
        Fabric value = rectangle(new Fabric());
        value.setId(50L);
        value.setRotation(BigDecimal.valueOf(30));
        return value;
    }

    private static Pillar rotatedPillar() {
        Pillar value = rectangle(new Pillar());
        value.setRotation(BigDecimal.valueOf(45));
        return value;
    }

    private static StoredBottleneck bottleneck() {
        StoredBottleneck value = new StoredBottleneck();
        value.setStartTimeSeconds(2.0);
        value.setEndTimeSeconds(8.0);
        value.setGeometry(
                """
                {"schemaVersion":1,"coordinateSystem":"FLOOR_PLAN","coordinateUnit":"METER",
                 "geometryType":"RECTANGLE","startX":30.0,"startY":10.0,"endX":50.0,"endY":20.0}
                """);
        return value;
    }

    private static <T> T rectangle(T value) {
        if (value instanceof Fabric fabric) {
            fabric.setStartX(BigDecimal.ONE);
            fabric.setStartY(BigDecimal.ONE);
            fabric.setEndX(BigDecimal.TEN);
            fabric.setEndY(BigDecimal.TEN);
        } else if (value instanceof Pillar pillar) {
            pillar.setStartX(BigDecimal.ONE);
            pillar.setStartY(BigDecimal.ONE);
            pillar.setEndX(BigDecimal.TEN);
            pillar.setEndY(BigDecimal.TEN);
        } else if (value instanceof Wall wall) {
            wall.setStartX(BigDecimal.ONE);
            wall.setStartY(BigDecimal.ONE);
            wall.setEndX(BigDecimal.TEN);
            wall.setEndY(BigDecimal.TEN);
        } else if (value instanceof OutsideWall wall) {
            wall.setStartX(BigDecimal.ONE);
            wall.setStartY(BigDecimal.ONE);
            wall.setEndX(BigDecimal.TEN);
            wall.setEndY(BigDecimal.TEN);
        } else if (value instanceof LayoutExit layoutExit) {
            layoutExit.setStartX(BigDecimal.ONE);
            layoutExit.setStartY(BigDecimal.ONE);
            layoutExit.setEndX(BigDecimal.TEN);
            layoutExit.setEndY(BigDecimal.TEN);
        }
        return value;
    }
}
