package com.hwalro.simulation.simulation.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class SimulationResult {
    private Long id;
    private Long simulationId;
    private String engineVersion;
    private String terminationReason;
    private BigDecimal frameIntervalSeconds;
    private String terminationDetail;
    private String recoveryDetail;
    private LocalDateTime createdAt;
    private Integer timelineChunkCount;
    private Integer heatmapChunkCount;
    private Integer timelineSchemaVersion;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getSimulationId() {
        return simulationId;
    }

    public void setSimulationId(Long simulationId) {
        this.simulationId = simulationId;
    }

    public String getEngineVersion() {
        return engineVersion;
    }

    public void setEngineVersion(String engineVersion) {
        this.engineVersion = engineVersion;
    }

    public String getTerminationReason() {
        return terminationReason;
    }

    public void setTerminationReason(String terminationReason) {
        this.terminationReason = terminationReason;
    }

    public BigDecimal getFrameIntervalSeconds() {
        return frameIntervalSeconds;
    }

    public void setFrameIntervalSeconds(BigDecimal frameIntervalSeconds) {
        this.frameIntervalSeconds = frameIntervalSeconds;
    }

    public String getTerminationDetail() {
        return terminationDetail;
    }

    public void setTerminationDetail(String terminationDetail) {
        this.terminationDetail = terminationDetail;
    }

    public String getRecoveryDetail() {
        return recoveryDetail;
    }

    public void setRecoveryDetail(String recoveryDetail) {
        this.recoveryDetail = recoveryDetail;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public Integer getTimelineChunkCount() {
        return timelineChunkCount;
    }

    public void setTimelineChunkCount(Integer timelineChunkCount) {
        this.timelineChunkCount = timelineChunkCount;
    }

    public Integer getHeatmapChunkCount() {
        return heatmapChunkCount;
    }

    public void setHeatmapChunkCount(Integer heatmapChunkCount) {
        this.heatmapChunkCount = heatmapChunkCount;
    }

    public Integer getTimelineSchemaVersion() {
        return timelineSchemaVersion;
    }

    public void setTimelineSchemaVersion(Integer timelineSchemaVersion) {
        this.timelineSchemaVersion = timelineSchemaVersion;
    }
}
