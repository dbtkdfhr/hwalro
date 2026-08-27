package com.hwalro.simulation.zone.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 도면 버전이 소유하는 운영 구역. 축 정렬 직사각형이며 담당 직원 0~1명과 기본 비상구를 가진다.
 *
 * <p>{@code assignedUserId}는 auth-service의 사용자 ID다. 서비스 경계를 넘지 않기 위해 DB FK를 만들지 않고 배정 시점에 auth-service API로 검증한다.
 */
public class LayoutZone {
    private Long id;
    private Long layoutVersionId;
    private String name;
    private String zoneType;
    private BigDecimal x;
    private BigDecimal y;
    private BigDecimal width;
    private BigDecimal height;
    private Long assignedUserId;
    private Long defaultExitId;
    private Integer displayOrder;
    private LocalDateTime createdAt;

    public LayoutZone() {}

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

    public String getZoneType() {
        return zoneType;
    }

    public void setZoneType(String zoneType) {
        this.zoneType = zoneType;
    }

    public BigDecimal getX() {
        return x;
    }

    public void setX(BigDecimal x) {
        this.x = x;
    }

    public BigDecimal getY() {
        return y;
    }

    public void setY(BigDecimal y) {
        this.y = y;
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

    public Long getAssignedUserId() {
        return assignedUserId;
    }

    public void setAssignedUserId(Long assignedUserId) {
        this.assignedUserId = assignedUserId;
    }

    public Long getDefaultExitId() {
        return defaultExitId;
    }

    public void setDefaultExitId(Long defaultExitId) {
        this.defaultExitId = defaultExitId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public Integer getDisplayOrder() {
        return displayOrder;
    }

    public void setDisplayOrder(Integer displayOrder) {
        this.displayOrder = displayOrder;
    }

    /** 대피 경로 미리보기의 출발점. 직원 위치 개념이 없으므로 구역 중심점을 쓴다. */
    public BigDecimal centerX() {
        return x.add(width.divide(BigDecimal.valueOf(2)));
    }

    public BigDecimal centerY() {
        return y.add(height.divide(BigDecimal.valueOf(2)));
    }
}
