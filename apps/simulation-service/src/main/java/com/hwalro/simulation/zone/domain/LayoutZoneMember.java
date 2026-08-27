package com.hwalro.simulation.zone.domain;

/**
 * 구역과 벽·기둥·구조물 중 하나의 소속 관계. 멤버십이 없는 요소는 공용이다. 비상구·외각벽·텍스트는 구역 구성원이 될 수 없다.
 */
public class LayoutZoneMember {
    private Long id;
    private Long layoutVersionId;
    private Long zoneId;
    private Long wallId;
    private Long pillarId;
    private Long fabricId;

    public LayoutZoneMember() {}

    public static LayoutZoneMember of(Long layoutVersionId, Long zoneId, ZoneElementKind kind, Long elementId) {
        LayoutZoneMember member = new LayoutZoneMember();
        member.setLayoutVersionId(layoutVersionId);
        member.setZoneId(zoneId);
        switch (kind) {
            case WALL -> member.setWallId(elementId);
            case PILLAR -> member.setPillarId(elementId);
            case FABRIC -> member.setFabricId(elementId);
        }
        return member;
    }

    public ZoneElementKind getKind() {
        if (wallId != null) {
            return ZoneElementKind.WALL;
        }
        if (pillarId != null) {
            return ZoneElementKind.PILLAR;
        }
        if (fabricId != null) {
            return ZoneElementKind.FABRIC;
        }
        throw new IllegalStateException("구역 구성원은 벽·기둥·구조물 중 하나를 참조해야 합니다: id=" + id);
    }

    public Long elementId() {
        return switch (getKind()) {
            case WALL -> wallId;
            case PILLAR -> pillarId;
            case FABRIC -> fabricId;
        };
    }

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

    public Long getZoneId() {
        return zoneId;
    }

    public void setZoneId(Long zoneId) {
        this.zoneId = zoneId;
    }

    public Long getWallId() {
        return wallId;
    }

    public void setWallId(Long wallId) {
        this.wallId = wallId;
    }

    public Long getPillarId() {
        return pillarId;
    }

    public void setPillarId(Long pillarId) {
        this.pillarId = pillarId;
    }

    public Long getFabricId() {
        return fabricId;
    }

    public void setFabricId(Long fabricId) {
        this.fabricId = fabricId;
    }
}
