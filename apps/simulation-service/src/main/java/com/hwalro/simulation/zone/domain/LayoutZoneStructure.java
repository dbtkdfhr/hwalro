package com.hwalro.simulation.zone.domain;

/** 구역과 구조물의 소속 관계. 멤버십이 없는 구조물은 공용 구조물이다. */
public class LayoutZoneStructure {
    private Long layoutVersionId;
    private Long zoneId;
    private Long fabricId;

    public LayoutZoneStructure() {}

    public LayoutZoneStructure(Long layoutVersionId, Long zoneId, Long fabricId) {
        this.layoutVersionId = layoutVersionId;
        this.zoneId = zoneId;
        this.fabricId = fabricId;
    }

    public Long getLayoutVersionId() {
        return layoutVersionId;
    }

    public void setLayoutVersionId(Long layoutVersionId) {
        this.layoutVersionId = layoutVersionId;
    }

    public Long getZoneId() {
        return zoneId;
    }

    public void setZoneId(Long zoneId) {
        this.zoneId = zoneId;
    }

    public Long getFabricId() {
        return fabricId;
    }

    public void setFabricId(Long fabricId) {
        this.fabricId = fabricId;
    }
}
