package com.hwalro.simulation.simulation.domain;

public class SimulationMetric {
    private Long id;
    private Long simulationResultId;
    private String unit;
    private String metricType;
    private double metricValue;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getSimulationResultId() {
        return simulationResultId;
    }

    public void setSimulationResultId(Long simulationResultId) {
        this.simulationResultId = simulationResultId;
    }

    public String getUnit() {
        return unit;
    }

    public void setUnit(String unit) {
        this.unit = unit;
    }

    public String getMetricType() {
        return metricType;
    }

    public void setMetricType(String metricType) {
        this.metricType = metricType;
    }

    public double getMetricValue() {
        return metricValue;
    }

    public void setMetricValue(double metricValue) {
        this.metricValue = metricValue;
    }
}
