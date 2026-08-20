package com.hwalro.regulation.report.dto;

import java.util.List;

public record ReportVisualContextResponse(
        Long simulationResultId, Long simulationId, String layoutTitle, Drawing drawing, List<Bottleneck> bottlenecks) {
    public record Drawing(
            String name,
            double width,
            double height,
            List<Point> outsideBoundary,
            List<Segment> walls,
            List<Segment> exits,
            List<Rectangle> pillars,
            List<Rectangle> fabrics,
            List<LayoutText> layoutTexts) {}

    public record Point(double x, double y) {}

    public record Segment(String name, double startX, double startY, double endX, double endY) {}

    public record Rectangle(String name, double startX, double startY, double endX, double endY, double rotation) {}

    public record LayoutText(String text, double x, double y) {}

    public record Bottleneck(
            Long id,
            int order,
            String name,
            double startTimeSeconds,
            double endTimeSeconds,
            double peakDensity,
            double thresholdValue,
            Bounds geometry) {}

    public record Bounds(double x, double y, double width, double height) {}
}
