package com.hwalro.simulation.analysis.domain;

public record DetectedBottleneck(
        int order,
        double startTimeSeconds,
        double endTimeSeconds,
        double peakDensity,
        double thresholdValue,
        RectangleGeometry geometry,
        String analysisVersion) {
    public record RectangleGeometry(String type, String name, double x, double y, double width, double height) {}
}
