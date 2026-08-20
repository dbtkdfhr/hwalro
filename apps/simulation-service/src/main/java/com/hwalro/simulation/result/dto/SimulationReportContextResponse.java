package com.hwalro.simulation.result.dto;

import java.util.List;

public record SimulationReportContextResponse(
        Long simulationResultId,
        Long simulationId,
        String layoutTitle,
        List<Metric> metrics,
        List<Bottleneck> bottlenecks) {
    public record Metric(String metricType, double metricValue, String unit) {}

    public record Bottleneck(
            int order, double startTimeSeconds, double endTimeSeconds, double peakDensity, double thresholdValue) {}
}
