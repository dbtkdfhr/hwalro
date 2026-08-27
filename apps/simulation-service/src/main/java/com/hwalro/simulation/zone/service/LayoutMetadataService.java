package com.hwalro.simulation.zone.service;

import com.hwalro.simulation.common.jwt.ForbiddenException;
import com.hwalro.simulation.common.jwt.JwtUser;
import com.hwalro.simulation.drawing.domain.Fabric;
import com.hwalro.simulation.drawing.service.DrawingService;
import com.hwalro.simulation.zone.client.EmployeeDirectoryClient;
import com.hwalro.simulation.zone.domain.LayoutZone;
import com.hwalro.simulation.zone.domain.LayoutZoneMember;
import com.hwalro.simulation.zone.domain.ZoneElementKind;
import com.hwalro.simulation.zone.dto.AssignedZoneRow;
import com.hwalro.simulation.zone.dto.LayoutZoneDtos.LayoutMetadataResponse;
import com.hwalro.simulation.zone.dto.LayoutZoneDtos.MyZoneResponse;
import com.hwalro.simulation.zone.dto.LayoutZoneDtos.RectDto;
import com.hwalro.simulation.zone.dto.LayoutZoneDtos.StructureConstraintDto;
import com.hwalro.simulation.zone.dto.LayoutZoneDtos.StructureConstraintUpdateRequest;
import com.hwalro.simulation.zone.dto.LayoutZoneDtos.ZoneCreateRequest;
import com.hwalro.simulation.zone.dto.LayoutZoneDtos.ZoneMemberDto;
import com.hwalro.simulation.zone.dto.LayoutZoneDtos.ZoneResponse;
import com.hwalro.simulation.zone.dto.LayoutZoneDtos.ZoneUpdateRequest;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * 구역 API의 인가와 응답 투영을 담당한다. 영속화·검증은 {@link LayoutZoneService}가 맡는다.
 *
 * <p>인가 규칙은 전부 여기서 서버 측으로 강제한다. 프론트엔드 가드는 UX 목적일 뿐 보안 경계가 아니다.
 *
 * <ul>
 *   <li>권한 역할(ADMIN/SAFETY_REVIEWER/OPERATOR)은 기존 도면 접근 규칙을 그대로 따른다.
 *   <li>매장 직원 전용 사용자는 자기에게 배정된 구역이 있는 도면만 읽을 수 있다.
 *   <li>구역 생성·수정·삭제·배정과 배치 제외는 컨트롤러의 {@code @RequireRole}이 이미 막지만, 여기서도 도면 접근을 다시 확인한다.
 * </ul>
 */
@Service
public class LayoutMetadataService {
    private final LayoutZoneService layoutZoneService;
    private final DrawingService drawingService;
    private final EmployeeDirectoryClient employeeDirectoryClient;
    private final EvacuationRouteWarmer evacuationRouteWarmer;

    public LayoutMetadataService(
            LayoutZoneService layoutZoneService,
            DrawingService drawingService,
            EmployeeDirectoryClient employeeDirectoryClient,
            EvacuationRouteWarmer evacuationRouteWarmer) {
        this.layoutZoneService = layoutZoneService;
        this.drawingService = drawingService;
        this.employeeDirectoryClient = employeeDirectoryClient;
        this.evacuationRouteWarmer = evacuationRouteWarmer;
    }

    public LayoutMetadataResponse readMetadata(Long layoutId, JwtUser user) {
        Long versionId = layoutZoneService.currentVersionId(layoutId);
        boolean privileged = DrawingService.isPrivileged(user);
        // 권한 역할은 기존 도면 접근 규칙, 직원은 배정 여부로 판단한다. 둘 다 requireAccessible이 처리한다.
        drawingService.requireAccessible(layoutId, user);

        List<LayoutZone> zones = layoutZoneService.zones(versionId);
        List<LayoutZoneMember> memberships = layoutZoneService.memberships(versionId);
        List<Fabric> fabrics = layoutZoneService.fabrics(versionId);
        Map<Long, Boolean> wallContacts = layoutZoneService.wallContacts(versionId, fabrics);

        List<LayoutZone> visibleZones = LayoutZoneService.visibleZones(zones, user);
        Set<Long> visibleZoneIds = visibleZones.stream().map(LayoutZone::getId).collect(Collectors.toSet());

        Map<Long, List<ZoneMemberDto>> membersByZone = new LinkedHashMap<>();
        // 벽·기둥·구조물의 ID는 서로 겹치는 독립 AUTO_INCREMENT다. 구조물 제약 투영은 반드시
        // fabric 종류 멤버십으로만 만든 맵을 써야 한다. 그렇지 않으면 벽 7번의 구역이
        // 구조물 7번의 제약에 붙고, 직원에게는 그게 가시성 필터가 된다.
        Map<Long, Long> zoneIdByFabric = new LinkedHashMap<>();
        for (LayoutZoneMember membership : memberships) {
            Long elementId = membership.elementId();
            membersByZone
                    .computeIfAbsent(membership.getZoneId(), key -> new java.util.ArrayList<>())
                    .add(new ZoneMemberDto(membership.getKind().name(), elementId));
            if (membership.getKind() == ZoneElementKind.FABRIC && visibleZoneIds.contains(membership.getZoneId())) {
                zoneIdByFabric.put(elementId, membership.getZoneId());
            }
        }

        // 직원에게는 자기 구역 소속 구조물의 제약만 보인다. 공용 구조물과 남의 구역은 응답에 담지 않는다.
        List<StructureConstraintDto> constraints = fabrics.stream()
                .filter(fabric -> privileged || visibleZoneIds.contains(zoneIdByFabric.get(fabric.getId())))
                .map(fabric -> new StructureConstraintDto(
                        fabric.getId(),
                        zoneIdByFabric.get(fabric.getId()),
                        fabric.getMovable(),
                        fabric.getMaxMovementDistance(),
                        fabric.getRotationLocked(),
                        Boolean.TRUE.equals(fabric.getKeepAgainstWall())
                                && wallContacts.getOrDefault(fabric.getId(), false),
                        wallContacts.getOrDefault(fabric.getId(), false)))
                .toList();

        List<ZoneResponse> zoneResponses = visibleZones.stream()
                .map(zone -> toZoneResponse(zone, membersByZone.get(zone.getId())))
                .toList();
        return new LayoutMetadataResponse(layoutId, versionId, zoneResponses, constraints);
    }

    public ZoneResponse createZone(Long layoutId, ZoneCreateRequest request, JwtUser user, String authorization) {
        drawingService.requireAccessible(layoutId, user);
        employeeDirectoryClient.requireEmployee(request.assignedUserId(), authorization);
        LayoutZone zone = layoutZoneService.createZone(layoutId, request);
        evacuationRouteWarmer.warm(layoutId);
        return toZoneResponse(zone, request.members() == null ? List.of() : request.members());
    }

    public ZoneResponse updateZone(
            Long layoutId, Long zoneId, ZoneUpdateRequest request, JwtUser user, String authorization) {
        drawingService.requireAccessible(layoutId, user);
        if (!request.clearAssignedUser()) {
            employeeDirectoryClient.requireEmployee(request.assignedUserId(), authorization);
        }
        LayoutZone zone = layoutZoneService.updateZone(layoutId, zoneId, request);
        evacuationRouteWarmer.warm(layoutId);
        Long versionId = zone.getLayoutVersionId();
        List<ZoneMemberDto> members = layoutZoneService.memberships(versionId).stream()
                .filter(membership -> membership.getZoneId().equals(zoneId))
                .map(membership -> new ZoneMemberDto(membership.getKind().name(), membership.elementId()))
                .toList();
        return toZoneResponse(zone, members);
    }

    public void deleteZone(Long layoutId, Long zoneId, JwtUser user) {
        drawingService.requireAccessible(layoutId, user);
        layoutZoneService.deleteZone(layoutId, zoneId);
        evacuationRouteWarmer.warm(layoutId);
    }

    public void updateStructureConstraints(
            Long layoutId, Long fabricId, StructureConstraintUpdateRequest request, JwtUser user) {
        if (!DrawingService.isPrivileged(user)) {
            throw new ForbiddenException("기획/운영 담당자와 안전 검토 권한만 구조물 제약을 수정할 수 있습니다.");
        }
        drawingService.requireAccessible(layoutId, user);
        layoutZoneService.updateStructureConstraints(layoutId, fabricId, request);
    }

    public List<MyZoneResponse> myZones(JwtUser user) {
        List<AssignedZoneRow> assignedZones = layoutZoneService.assignedZones(user.userId());
        assignedZones.stream()
                .map(AssignedZoneRow::layoutId)
                .filter(Objects::nonNull)
                .distinct()
                .forEach(evacuationRouteWarmer::warm);
        return assignedZones.stream().map(LayoutMetadataService::toMyZone).toList();
    }

    /** 직원은 자기 구역에 속한 구조물만 만질 수 있다. 소속 구역이 없는 공용 구조물도 막는다. */
    private void requireOwnedStructure(Long versionId, Long fabricId, JwtUser user) {
        Long zoneId = layoutZoneService.zoneIdOfFabric(versionId, fabricId);
        if (zoneId == null) {
            throw new ForbiddenException("이 구조물은 특정 구역에 속해 있지 않습니다.");
        }
        LayoutZone zone = layoutZoneService.zoneOrThrow(zoneId);
        if (!Objects.equals(zone.getAssignedUserId(), user.userId())) {
            throw new ForbiddenException("담당 구역의 구조물만 수정할 수 있습니다.");
        }
    }

    private static ZoneResponse toZoneResponse(LayoutZone zone, List<ZoneMemberDto> members) {
        return new ZoneResponse(
                zone.getId(),
                zone.getName(),
                zone.getZoneType(),
                new RectDto(zone.getX(), zone.getY(), zone.getWidth(), zone.getHeight()),
                zone.getAssignedUserId(),
                zone.getDefaultExitId(),
                members == null ? List.of() : members);
    }

    private static MyZoneResponse toMyZone(AssignedZoneRow row) {
        return new MyZoneResponse(
                row.zoneId(),
                row.zoneName(),
                row.zoneType(),
                row.layoutId(),
                row.layoutTitle(),
                row.layoutVersionId(),
                row.defaultExitId(),
                row.defaultExitName());
    }
}
