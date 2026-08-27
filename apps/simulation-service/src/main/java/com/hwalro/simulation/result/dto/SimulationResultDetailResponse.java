package com.hwalro.simulation.result.dto;

import java.util.List;

public record SimulationResultDetailResponse(
        Long simulationId,
        Long simulationResultId,
        Long layoutId,
        String title,
        String subtitle,
        double durationSeconds,
        int totalPeople,
        double maxDensity,
        double densityThreshold,
        Drawing drawing,
        List<HazardZone> hazardZones,
        List<Bottleneck> bottlenecks,
        Boolean isImprovement,
        Long sourceSimulationId) {

    public record Drawing(
            String name,
            double width,
            double height,
            List<Point> outsideBoundary,
            List<Segment> walls,
            List<Exit> exits,
            List<Rectangle> pillars,
            List<Rectangle> fabrics,
            List<LayoutText> layoutTexts,
            /** 시뮬레이션 시점의 도면 구역. 위험 구역 이름을 좌표로 추측하지 않고 이 이름을 쓴다. */
            List<Zone> zones) {}

    public record Zone(String name, double x, double y, double width, double height) {}

    public record Point(double x, double y) {}

    public record Segment(String name, double startX, double startY, double endX, double endY) {}

    public record Exit(Long id, String name, double startX, double startY, double endX, double endY, boolean active) {}

    public record Rectangle(String name, double startX, double startY, double endX, double endY, double rotation) {}

    public record LayoutText(String text, double x, double y) {}

    public record HazardZone(Long id, double centerX, double centerY, double radius) {}

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
