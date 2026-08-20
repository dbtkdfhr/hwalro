package com.hwalro.simulation.drawing.domain;

import java.math.BigDecimal;

public class OutsideWall {
    private Long id;
    private Long layoutVersionId;
    private String name;
    private BigDecimal startX;
    private BigDecimal startY;
    private BigDecimal endX;
    private BigDecimal endY;

    public OutsideWall() {}

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

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public BigDecimal getStartX() {
        return startX;
    }

    public void setStartX(BigDecimal startX) {
        this.startX = startX;
    }

    public BigDecimal getStartY() {
        return startY;
    }

    public void setStartY(BigDecimal startY) {
        this.startY = startY;
    }

    public BigDecimal getEndX() {
        return endX;
    }

    public void setEndX(BigDecimal endX) {
        this.endX = endX;
    }

    public BigDecimal getEndY() {
        return endY;
    }

    public void setEndY(BigDecimal endY) {
        this.endY = endY;
    }
}
