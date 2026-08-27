package com.hwalro.simulation.drawing.domain;

import java.math.BigDecimal;

public class Fabric {
    private Long id;
    private Long layoutVersionId;
    private String name;
    private BigDecimal startX;
    private BigDecimal startY;
    private BigDecimal endX;
    private BigDecimal endY;
    private BigDecimal rotation;
    // 배치 개선안 탐색에 투영되는 배치 제약. 도면 기하 저장이 아니라 별도 제약 API가 소유한다.
    private Boolean movable;
    private BigDecimal maxMovementDistance;
    private Boolean rotationLocked;
    private Boolean keepAgainstWall;
    private Integer displayOrder;

    public Fabric() {}

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

    public BigDecimal getRotation() {
        return rotation;
    }

    public void setRotation(BigDecimal rotation) {
        this.rotation = rotation;
    }

    public Boolean getMovable() {
        return movable;
    }

    public void setMovable(Boolean movable) {
        this.movable = movable;
    }

    public BigDecimal getMaxMovementDistance() {
        return maxMovementDistance;
    }

    public void setMaxMovementDistance(BigDecimal maxMovementDistance) {
        this.maxMovementDistance = maxMovementDistance;
    }

    public Boolean getRotationLocked() {
        return rotationLocked;
    }

    public void setRotationLocked(Boolean rotationLocked) {
        this.rotationLocked = rotationLocked;
    }

    public Boolean getKeepAgainstWall() {
        return keepAgainstWall;
    }

    public void setKeepAgainstWall(Boolean keepAgainstWall) {
        this.keepAgainstWall = keepAgainstWall;
    }

    public Integer getDisplayOrder() {
        return displayOrder;
    }

    public void setDisplayOrder(Integer displayOrder) {
        this.displayOrder = displayOrder;
    }
}
