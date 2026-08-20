package com.hwalro.simulation.improvement.geometry;

import java.util.List;

/**
 * start/end로 만든 직사각형을 중심 기준 시계 방향으로 회전한 도형입니다.
 *
 * <p>도면 데이터가 이미 미터 단위이므로 이 클래스에서는 좌표 축척 변환을 수행하지 않습니다.
 */
public record RotatedRectangle(Point center, double width, double height, double clockwiseDegrees) {

    /** start/end 좌표와 시계 방향 회전 각도로 도형을 만듭니다. */
    public static RotatedRectangle of(double startX, double startY, double endX, double endY, double clockwiseDegrees) {
        double width = Math.abs(endX - startX);
        double height = Math.abs(endY - startY);
        Point center = new Point((startX + endX) / 2, (startY + endY) / 2);
        return new RotatedRectangle(center, width, height, normalizeDegrees(clockwiseDegrees));
    }

    /** 충돌 계산에 쓰는 네 꼭짓점을 시계 방향 순서로 반환합니다. */
    public List<Point> corners() {
        double halfWidth = width / 2;
        double halfHeight = height / 2;
        return List.of(
                rotate(-halfWidth, -halfHeight),
                rotate(halfWidth, -halfHeight),
                rotate(halfWidth, halfHeight),
                rotate(-halfWidth, halfHeight));
    }

    /** 동일한 크기와 각도를 유지한 채 중심점만 이동합니다. */
    public RotatedRectangle moveBy(double deltaX, double deltaY) {
        return new RotatedRectangle(
                new Point(center.x() + deltaX, center.y() + deltaY), width, height, clockwiseDegrees);
    }

    /** 중심점을 유지한 채 시계 방향으로 추가 회전합니다. */
    public RotatedRectangle rotateClockwiseBy(double degrees) {
        return new RotatedRectangle(center, width, height, normalizeDegrees(clockwiseDegrees + degrees));
    }

    private Point rotate(double localX, double localY) {
        // 수학 좌표계에서 시계 방향 회전 행렬을 사용합니다.
        double radians = Math.toRadians(clockwiseDegrees);
        double cosine = Math.cos(radians);
        double sine = Math.sin(radians);
        return new Point(center.x() + localX * cosine + localY * sine, center.y() - localX * sine + localY * cosine);
    }

    private static double normalizeDegrees(double degrees) {
        double normalized = degrees % 360;
        return normalized < 0 ? normalized + 360 : normalized;
    }
}
