package com.hwalro.simulation.search.domain;

import java.time.LocalDateTime;

public class LayoutSearchCandidateEntity {
    private Long id;
    private Long studyId;
    private Long parentCandidateId;
    private Integer roundIndex;
    private Integer candidateOrder;
    private String originFindingType;
    private String operatorType;
    private String status;
    private String changeSet;
    private String rationale;
    private Double proxyScore;
    private String metricDelta;
    private String rejectReason;
    private String constraintsSnapshot;
    private Long adoptedLayoutVersionId;
    private Long preparedSimulationId;
    private String preparedSimulationStatus;
    private LocalDateTime adoptedAt;
    private LocalDateTime createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getStudyId() {
        return studyId;
    }

    public void setStudyId(Long studyId) {
        this.studyId = studyId;
    }

    public Long getParentCandidateId() {
        return parentCandidateId;
    }

    public void setParentCandidateId(Long parentCandidateId) {
        this.parentCandidateId = parentCandidateId;
    }

    public Integer getRoundIndex() {
        return roundIndex;
    }

    public void setRoundIndex(Integer roundIndex) {
        this.roundIndex = roundIndex;
    }

    public Integer getCandidateOrder() {
        return candidateOrder;
    }

    public void setCandidateOrder(Integer candidateOrder) {
        this.candidateOrder = candidateOrder;
    }

    public String getOriginFindingType() {
        return originFindingType;
    }

    public void setOriginFindingType(String originFindingType) {
        this.originFindingType = originFindingType;
    }

    public String getOperatorType() {
        return operatorType;
    }

    public void setOperatorType(String operatorType) {
        this.operatorType = operatorType;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getChangeSet() {
        return changeSet;
    }

    public void setChangeSet(String changeSet) {
        this.changeSet = changeSet;
    }

    public String getRationale() {
        return rationale;
    }

    public void setRationale(String rationale) {
        this.rationale = rationale;
    }

    public Double getProxyScore() {
        return proxyScore;
    }

    public void setProxyScore(Double proxyScore) {
        this.proxyScore = proxyScore;
    }

    public String getMetricDelta() {
        return metricDelta;
    }

    public void setMetricDelta(String metricDelta) {
        this.metricDelta = metricDelta;
    }

    public String getRejectReason() {
        return rejectReason;
    }

    public void setRejectReason(String rejectReason) {
        this.rejectReason = rejectReason;
    }

    public String getConstraintsSnapshot() {
        return constraintsSnapshot;
    }

    public void setConstraintsSnapshot(String constraintsSnapshot) {
        this.constraintsSnapshot = constraintsSnapshot;
    }

    public Long getAdoptedLayoutVersionId() {
        return adoptedLayoutVersionId;
    }

    public void setAdoptedLayoutVersionId(Long adoptedLayoutVersionId) {
        this.adoptedLayoutVersionId = adoptedLayoutVersionId;
    }

    public Long getPreparedSimulationId() {
        return preparedSimulationId;
    }

    public void setPreparedSimulationId(Long preparedSimulationId) {
        this.preparedSimulationId = preparedSimulationId;
    }

    public String getPreparedSimulationStatus() {
        return preparedSimulationStatus;
    }

    public void setPreparedSimulationStatus(String preparedSimulationStatus) {
        this.preparedSimulationStatus = preparedSimulationStatus;
    }

    public LocalDateTime getAdoptedAt() {
        return adoptedAt;
    }

    public void setAdoptedAt(LocalDateTime adoptedAt) {
        this.adoptedAt = adoptedAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
