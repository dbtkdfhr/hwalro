package com.hwalro.simulation.improvement.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.hwalro.simulation.improvement.domain.BottleneckArea;
import com.hwalro.simulation.improvement.domain.CorridorClearance;
import com.hwalro.simulation.improvement.geometry.RotatedRectangle;
import java.util.List;
import org.junit.jupiter.api.Test;

class CorridorClearanceEvaluatorTest {
    private final CorridorClearanceEvaluator evaluator = new CorridorClearanceEvaluator();
    private final BottleneckArea bottleneck = new BottleneckArea(RotatedRectangle.of(10, 5, 20, 15, 0), 0, 10);
    private final RotatedRectangle exit = RotatedRectangle.of(50, 8, 50, 12, 0);

    @Test
    void measuresTheFullBottleneckAndRouteWidths() {
        CorridorClearance clearance = evaluator.evaluate(bottleneck, List.of(), List.of(exit), 100, 20);

        assertEquals(20.0, clearance.bottleneckAverageWidth());
        assertEquals(20.0, clearance.routeAverageWidth());
    }

    @Test
    void includesNarrowingNearTheFarEdgeOfTheBottleneck() {
        RotatedRectangle obstacle = RotatedRectangle.of(18, 12, 19, 15, 0);

        CorridorClearance clearance = evaluator.evaluate(bottleneck, List.of(obstacle), List.of(exit), 100, 20);

        assertTrue(clearance.bottleneckAverageWidth() < 20.0);
        assertTrue(clearance.routeAverageWidth() < 20.0);
    }
}
