package com.hwalro.simulation.improvement.domain;

/** DB의 병목 행에서 개선안 탐색에 필요한 원본 JSON과 시간 범위만 읽는 조회 모델입니다. */
public class StoredBottleneck {
    private Double startTimeSeconds;
    private Double endTimeSeconds;
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

    public String getGeometry() {
        return geometry;
    }

    public void setGeometry(String geometry) {
        this.geometry = geometry;
    }
}
