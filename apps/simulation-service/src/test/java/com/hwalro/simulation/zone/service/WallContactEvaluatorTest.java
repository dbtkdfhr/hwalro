package com.hwalro.simulation.zone.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.hwalro.simulation.drawing.domain.Fabric;
import com.hwalro.simulation.drawing.domain.OutsideWall;
import com.hwalro.simulation.drawing.domain.Wall;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

class WallContactEvaluatorTest {
    @Test
    void detectsContactWithAnInternalWallWithinFiveCentimeters() {
        Fabric fabric = fabric(1, 1, 3, 2, 0);

        assertThat(WallContactEvaluator.touches(fabric, List.of(wall(0, 2.04, 5, 2.04)), List.of()))
                .isTrue();
        assertThat(WallContactEvaluator.touches(fabric, List.of(wall(0, 2.06, 5, 2.06)), List.of()))
                .isFalse();
    }

    @Test
    void usesTheSamePositiveRotationDirectionAsTheDrawingAndRouteEngine() {
        Fabric fabric = fabric(0, 0, 4, 1, 30);
        OutsideWall outsideWall = new OutsideWall();
        outsideWall.setStartX(m(3.98));
        outsideWall.setStartY(m(1.067));
        outsideWall.setEndX(m(4.5));
        outsideWall.setEndY(m(1.067));

        assertThat(WallContactEvaluator.touches(fabric, List.of(), List.of(outsideWall)))
                .isTrue();
    }

    private static Fabric fabric(double startX, double startY, double endX, double endY, double rotation) {
        Fabric fabric = new Fabric();
        fabric.setStartX(m(startX));
        fabric.setStartY(m(startY));
        fabric.setEndX(m(endX));
        fabric.setEndY(m(endY));
        fabric.setRotation(m(rotation));
        return fabric;
    }

    private static Wall wall(double startX, double startY, double endX, double endY) {
        Wall wall = new Wall();
        wall.setStartX(m(startX));
        wall.setStartY(m(startY));
        wall.setEndX(m(endX));
        wall.setEndY(m(endY));
        return wall;
    }

    private static BigDecimal m(double value) {
        return BigDecimal.valueOf(value);
    }
}
