package com.hwalro.simulation.simulation.domain;

import java.math.BigDecimal;

public class LayoutSimulationContext {
    private Long layoutVersionId;
    private Long layoutId;
    private Long createdBy;
    private String title;
    private Integer layoutVersionNumber;
    private String layoutVersionStatus;
    private BigDecimal width;
    private BigDecimal height;

    public Long getLayoutVersionId() {
        return layoutVersionId;
    }

    public void setLayoutVersionId(Long layoutVersionId) {
        this.layoutVersionId = layoutVersionId;
    }

    public Long getLayoutId() {
        return layoutId;
    }

    public void setLayoutId(Long layoutId) {
        this.layoutId = layoutId;
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

    public Integer getLayoutVersionNumber() {
        return layoutVersionNumber;
    }

    public void setLayoutVersionNumber(Integer layoutVersionNumber) {
        this.layoutVersionNumber = layoutVersionNumber;
    }

    public String getLayoutVersionStatus() {
        return layoutVersionStatus;
    }

    public void setLayoutVersionStatus(String layoutVersionStatus) {
        this.layoutVersionStatus = layoutVersionStatus;
    }

    public BigDecimal getWidth() {
        return width;
    }

    public void setWidth(BigDecimal width) {
        this.width = width;
    }

    public BigDecimal getHeight() {
        return height;
    }

    public void setHeight(BigDecimal height) {
        this.height = height;
    }
}
