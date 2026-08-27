package com.hwalro.simulation.simulation.domain;

import java.time.LocalDateTime;

public class Simulation {
    private Long id;
    private Long layoutVersionId;
    private Long parentSimulationId;
    private Long createdBy;
    private String title;
    private String status;
    private LocalDateTime createdAt;
    private LocalDateTime requestedAt;
    private LocalDateTime startedAt;
    private LocalDateTime finishedAt;
    private String failureMessage;
    private String failureDetail;
    private Integer totalPeople;
    private Long layoutId;
    private String layoutTitle;
    private Integer layoutVersionNumber;
    private String terminationReason;
    private Boolean isImprovement;
    private Boolean hasLayoutSearch;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getLayoutVersionId() {
        return layoutVersionId;
    }

    public void setLayoutVersionId(Long layoutVersionId) {
        this.layoutVersionId = layoutVersionId;
    }

    public Long getParentSimulationId() {
        return parentSimulationId;
    }

    public void setParentSimulationId(Long parentSimulationId) {
        this.parentSimulationId = parentSimulationId;
    }

    public Long getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(Long createdBy) {
        this.createdBy = createdBy;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getRequestedAt() {
        return requestedAt;
    }

    public void setRequestedAt(LocalDateTime requestedAt) {
        this.requestedAt = requestedAt;
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

    public String getFailureMessage() {
        return failureMessage;
    }

    public void setFailureMessage(String failureMessage) {
        this.failureMessage = failureMessage;
    }

    public String getFailureDetail() {
        return failureDetail;
    }

    public void setFailureDetail(String failureDetail) {
        this.failureDetail = failureDetail;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public Integer getTotalPeople() {
        return totalPeople;
    }

    public void setTotalPeople(Integer totalPeople) {
        this.totalPeople = totalPeople;
    }

    public Long getLayoutId() {
        return layoutId;
    }

    public void setLayoutId(Long layoutId) {
        this.layoutId = layoutId;
    }

    public String getLayoutTitle() {
        return layoutTitle;
    }

    public void setLayoutTitle(String layoutTitle) {
        this.layoutTitle = layoutTitle;
    }

    public Integer getLayoutVersionNumber() {
        return layoutVersionNumber;
    }

    public void setLayoutVersionNumber(Integer layoutVersionNumber) {
        this.layoutVersionNumber = layoutVersionNumber;
    }

    public String getTerminationReason() {
        return terminationReason;
    }

    public void setTerminationReason(String terminationReason) {
        this.terminationReason = terminationReason;
    }

    public Boolean getIsImprovement() {
        return isImprovement;
    }

    public void setIsImprovement(Boolean isImprovement) {
        this.isImprovement = isImprovement;
    }

    public Boolean getHasLayoutSearch() {
        return hasLayoutSearch;
    }

    public void setHasLayoutSearch(Boolean hasLayoutSearch) {
        this.hasLayoutSearch = hasLayoutSearch;
    }
}
