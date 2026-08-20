package com.hwalro.simulation.improvement.domain;

import java.time.LocalDateTime;

/**
 * 원본 시뮬레이션에서 생성된 배치 개선안입니다.
 *
 * <p>공식 안전 지표는 이 객체에 저장하지 않습니다. 변경 내용과 후속 검증에 필요한 설명만 보관합니다.
 */
public class ImprovementProposal {
    private Long id;
    private Long sourceSimulationId;
    private Long savedLayoutVersionId;
    private Integer proposalOrder;
    private String proposalType;
    private String title;
    private String description;
    private String changeData;
    private String changeSummary;
    private LocalDateTime createdAt;
    private LocalDateTime savedAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getSourceSimulationId() {
        return sourceSimulationId;
    }

    public void setSourceSimulationId(Long sourceSimulationId) {
        this.sourceSimulationId = sourceSimulationId;
    }

    public Long getSavedLayoutVersionId() {
        return savedLayoutVersionId;
    }

    public void setSavedLayoutVersionId(Long savedLayoutVersionId) {
        this.savedLayoutVersionId = savedLayoutVersionId;
    }

    public Integer getProposalOrder() {
        return proposalOrder;
    }

    public void setProposalOrder(Integer proposalOrder) {
        this.proposalOrder = proposalOrder;
    }

    public String getProposalType() {
        return proposalType;
    }

    public void setProposalType(String proposalType) {
        this.proposalType = proposalType;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getChangeData() {
        return changeData;
    }

    public void setChangeData(String changeData) {
        this.changeData = changeData;
    }

    public String getChangeSummary() {
        return changeSummary;
    }

    public void setChangeSummary(String changeSummary) {
        this.changeSummary = changeSummary;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getSavedAt() {
        return savedAt;
    }

    public void setSavedAt(LocalDateTime savedAt) {
        this.savedAt = savedAt;
    }
}
