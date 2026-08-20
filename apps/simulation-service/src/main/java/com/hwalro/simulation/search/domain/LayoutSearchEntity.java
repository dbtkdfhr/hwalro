package com.hwalro.simulation.search.domain;

import java.time.LocalDateTime;

public class LayoutSearchEntity {
    private Long id;
    private Long baselineSimulationId;
    private Long baselineLayoutVersionId;
    private String plannerVersion;
    private String status;
    private String baselineMetrics;
    private String diagnosis;
    private String budget;
    private String constraints;
    private Long requestedBy;
    private LocalDateTime createdAt;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private String failureCode;
    private String failureMessage;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getBaselineSimulationId() {
        return baselineSimulationId;
    }

    public void setBaselineSimulationId(Long baselineSimulationId) {
        this.baselineSimulationId = baselineSimulationId;
    }

    public Long getBaselineLayoutVersionId() {
        return baselineLayoutVersionId;
    }

    public void setBaselineLayoutVersionId(Long baselineLayoutVersionId) {
        this.baselineLayoutVersionId = baselineLayoutVersionId;
    }

    public String getPlannerVersion() {
        return plannerVersion;
    }

    public void setPlannerVersion(String plannerVersion) {
        this.plannerVersion = plannerVersion;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getBaselineMetrics() {
        return baselineMetrics;
    }

    public void setBaselineMetrics(String baselineMetrics) {
        this.baselineMetrics = baselineMetrics;
    }

    public String getDiagnosis() {
        return diagnosis;
    }

    public void setDiagnosis(String diagnosis) {
        this.diagnosis = diagnosis;
    }

    public String getBudget() {
        return budget;
    }

    public void setBudget(String budget) {
        this.budget = budget;
    }

    public String getConstraints() {
        return constraints;
    }

    public void setConstraints(String constraints) {
        this.constraints = constraints;
    }

    public Long getRequestedBy() {
        return requestedBy;
    }

    public void setRequestedBy(Long requestedBy) {
        this.requestedBy = requestedBy;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(LocalDateTime startedAt) {
        this.startedAt = startedAt;
    }

    public LocalDateTime getFinishedAt() {
        return finishedAt;
    }

    public void setFinishedAt(LocalDateTime finishedAt) {
        this.finishedAt = finishedAt;
    }

    public String getFailureCode() {
        return failureCode;
    }

    public void setFailureCode(String failureCode) {
        this.failureCode = failureCode;
    }

    public String getFailureMessage() {
        return failureMessage;
    }

    public void setFailureMessage(String failureMessage) {
        this.failureMessage = failureMessage;
    }
}
