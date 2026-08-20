package com.hwalro.simulation.improvement.geometry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class GeometryTest {

    @Test
    void detectsOverlapForRotatedRectangles() {
        RotatedRectangle first = RotatedRectangle.of(0, 0, 4, 2, 30);
        RotatedRectangle second = RotatedRectangle.of(2, 0, 6, 2, 0);

        assertTrue(Geometry.intersects(first, second));
    }

    @Test
    void rejectsSeparatedRectangles() {
        RotatedRectangle first = RotatedRectangle.of(0, 0, 2, 2, 30);
        RotatedRectangle second = RotatedRectangle.of(5, 0, 7, 2, 0);

        assertFalse(Geometry.intersects(first, second));
    }

    @Test
    void calculatesTheGapBetweenSeparatedRotatedRectangles() {
        RotatedRectangle first = RotatedRectangle.of(0, 0, 2, 2, 0);
        RotatedRectangle second = RotatedRectangle.of(5, 0, 7, 2, 0);

        assertEquals(3.0, Geometry.distance(first, second));
    }

    @Test
    void rotatesAroundTheFabricCenterClockwise() {
        RotatedRectangle rectangle = RotatedRectangle.of(0, 0, 4, 2, 0).rotateClockwiseBy(90);

        assertContainsPoint(rectangle, 1, 3);
        assertContainsPoint(rectangle, 3, -1);
    }

    @Test
    void containsOnlyPointsOnDegenerateRectangleSegments() {
        RotatedRectangle segment = RotatedRectangle.of(1, 1, 1, 5, 0);

        assertTrue(Geometry.contains(segment, new Point(1, 3)));
        assertFalse(Geometry.contains(segment, new Point(2, 3)));
        assertEquals(1.0, Geometry.distance(new Point(2, 3), segment));
    }

    @Test
    void containsOnlyTheCenterOfPointShapedRectangles() {
        RotatedRectangle point = RotatedRectangle.of(1, 1, 1, 1, 45);

        assertTrue(Geometry.contains(point, new Point(1, 1)));
        assertFalse(Geometry.contains(point, new Point(1, 2)));
        assertEquals(0.0, Geometry.distance(new Point(1, 1), point));
    }

    private void assertContainsPoint(RotatedRectangle rectangle, double expectedX, double expectedY) {
        Point point = rectangle.corners().stream()
                .filter(candidate -> Math.abs(candidate.x() - expectedX) < 0.000001)
                .filter(candidate -> Math.abs(candidate.y() - expectedY) < 0.000001)
                .findFirst()
                .orElseThrow();
        // 회전 계산에는 삼각함수가 사용되므로 부동소수점 오차를 허용합니다.
        assertEquals(expectedX, point.x(), 0.000001);
        assertEquals(expectedY, point.y(), 0.000001);
    }
}
