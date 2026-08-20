package com.hwalro.simulation.search.domain;

public class StoredBottleneck {
    private Double startTimeSeconds;
    private Double endTimeSeconds;
    private Double peakDensity;
    private Double thresholdValue;
    private String geometry;

    public Double getStartTimeSeconds() {
        return startTimeSeconds;
    }

    public void setStartTimeSeconds(Double startTimeSeconds) {
        this.startTimeSeconds = startTimeSeconds;
    }

    public Double getEndTimeSeconds() {
        return endTimeSeconds;
    }

    public void setEndTimeSeconds(Double endTimeSeconds) {
        this.endTimeSeconds = endTimeSeconds;
    }

    public Double getPeakDensity() {
        return peakDensity;
    }

    public void setPeakDensity(Double peakDensity) {
        this.peakDensity = peakDensity;
    }

    public Double getThresholdValue() {
        return thresholdValue;
    }

    public void setThresholdValue(Double thresholdValue) {
        this.thresholdValue = thresholdValue;
    }

    public String getGeometry() {
        return geometry;
    }

    public void setGeometry(String geometry) {
        this.geometry = geometry;
    }
}
