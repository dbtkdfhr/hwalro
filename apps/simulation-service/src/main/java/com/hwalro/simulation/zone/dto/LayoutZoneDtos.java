package com.hwalro.simulation.zone.dto;

import java.math.BigDecimal;
import java.util.List;

public final class LayoutZoneDtos {
    private LayoutZoneDtos() {}

    public record RectDto(BigDecimal x, BigDecimal y, BigDecimal width, BigDecimal height) {}

    /** 구역 구성원. kind는 {@code WALL | PILLAR | FABRIC}이고 id는 그 종류의 요소 ID다. */
    public record ZoneMemberDto(String kind, Long id) {}

    public record ZoneResponse(
            Long zoneId,
            String name,
            String zoneType,
            RectDto rect,
            Long assignedUserId,
            Long defaultExitId,
            List<ZoneMemberDto> members) {}

    public record ZoneCreateRequest(
            String name,
            String zoneType,
            BigDecimal x,
            BigDecimal y,
            BigDecimal width,
            BigDecimal height,
            Long assignedUserId,
            Long defaultExitId,
            List<ZoneMemberDto> members) {}

    /**
     * 부분 갱신. null 필드는 "변경 없음"을 뜻한다.
     *
     * <p>배정 해제·비상구 해제처럼 값을 비우려면 {@code clearAssignedUser}/{@code clearDefaultExit}를 쓴다. null 하나로
     * "변경 없음"과 "비우기"를 모두 표현할 수 없기 때문이다.
     */
    public record ZoneUpdateRequest(
            String name,
            String zoneType,
            BigDecimal x,
            BigDecimal y,
            BigDecimal width,
            BigDecimal height,
            Long assignedUserId,
            boolean clearAssignedUser,
            Long defaultExitId,
            boolean clearDefaultExit,
            Integer displayOrder,
            List<ZoneMemberDto> members) {}

    public record StructureConstraintDto(Long fabricId, Long zoneId, String movementPolicy) {}

    public record StructureConstraintUpdateRequest(String movementPolicy) {}

    public record LayoutMetadataResponse(
            Long layoutId,
            Long layoutVersionId,
            List<ZoneResponse> zones,
            List<StructureConstraintDto> structureConstraints) {}

    public record MyZoneResponse(
            Long zoneId,
            String zoneName,
            String zoneType,
            Long drawingId,
            String drawingTitle,
            Long layoutVersionId,
            Long defaultExitId,
            String defaultExitName) {}
}
