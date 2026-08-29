package com.hwalro.simulation.zone.service;

import com.hwalro.simulation.common.jwt.JwtUser;
import com.hwalro.simulation.drawing.domain.Fabric;
import com.hwalro.simulation.drawing.domain.FloorPlan;
import com.hwalro.simulation.drawing.domain.Layout;
import com.hwalro.simulation.drawing.domain.LayoutVersion;
import com.hwalro.simulation.drawing.domain.MovementPolicy;
import com.hwalro.simulation.drawing.exception.DrawingNotFoundException;
import com.hwalro.simulation.drawing.mapper.DrawingMapper;
import com.hwalro.simulation.drawing.service.DrawingService;
import com.hwalro.simulation.zone.domain.LayoutZone;
import com.hwalro.simulation.zone.domain.LayoutZoneMember;
import com.hwalro.simulation.zone.domain.ZoneElementKind;
import com.hwalro.simulation.zone.domain.ZoneType;
import com.hwalro.simulation.zone.dto.AssignedZoneRow;
import com.hwalro.simulation.zone.dto.LayoutZoneDtos.StructureConstraintUpdateRequest;
import com.hwalro.simulation.zone.dto.LayoutZoneDtos.ZoneCreateRequest;
import com.hwalro.simulation.zone.dto.LayoutZoneDtos.ZoneMemberDto;
import com.hwalro.simulation.zone.dto.LayoutZoneDtos.ZoneUpdateRequest;
import com.hwalro.simulation.zone.mapper.LayoutZoneMapper;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * 구역·구조물 배치 제약·배치 제외의 영속화와 검증을 담당한다.
 *
 * <p>동시성 정책: 이 클래스의 변경은 {@code layout_versions.optimistic_lock}을 올리지 않는다. 구역 메타데이터는 도면 기하와 생명주기가 달라 기하 저장과
 * 경쟁시키지 않는다. 대신 갱신·삭제가 0행이면 즉시 실패시켜 사라진 대상에 대한 무음 성공을 막는다.
 *
 * <p>잠긴({@code 잠금}) 도면 버전에서도 이 메타데이터는 수정할 수 있다. 과거 탐색의 재현성은 탐색 시작 시점의 제약 스냅샷이 보장한다.
 */
@Service
public class LayoutZoneService {
    private static final int MAX_ZONE_NAME_LENGTH = 200;
    private static final int MAX_ZONES_PER_VERSION = 1_000;

    private final LayoutZoneMapper layoutZoneMapper;
    private final DrawingMapper drawingMapper;

    public LayoutZoneService(LayoutZoneMapper layoutZoneMapper, DrawingMapper drawingMapper) {
        this.layoutZoneMapper = layoutZoneMapper;
        this.drawingMapper = drawingMapper;
    }

    public Long currentVersionId(Long layoutId) {
        Layout layout = drawingMapper.findLayoutById(layoutId);
        if (layout == null || layout.getCurrentVersionId() == null) {
            throw new DrawingNotFoundException(layoutId);
        }
        return layout.getCurrentVersionId();
    }

    /** 구역이 속한 도면 ID. 구역은 버전을 알고 있지만 도면 접근 규칙은 도면 ID로 판단한다. */
    public Long layoutIdOfVersion(Long layoutVersionId) {
        LayoutVersion version = drawingMapper.findLayoutVersionById(layoutVersionId);
        if (version == null) {
            throw new DrawingNotFoundException(layoutVersionId);
        }
        return version.getLayoutId();
    }

    public List<LayoutZone> zones(Long layoutVersionId) {
        return layoutZoneMapper.findZonesByVersionId(layoutVersionId);
    }

    public List<LayoutZoneMember> memberships(Long layoutVersionId) {
        return layoutZoneMapper.findZoneMembersByVersionId(layoutVersionId);
    }

    public List<Fabric> fabrics(Long layoutVersionId) {
        return drawingMapper.findFabricsByVersionId(layoutVersionId);
    }

    /**
     * 그 사용자에게 보여도 되는 구역만 고른다.
     *
     * <p>권한 역할은 도면의 모든 구역을 보고, 일반 직원은 자신에게 배정된 구역만 본다. 도면 메타데이터 조회와 대피 경로
     * 조회가 같은 규칙을 써야 화면마다 보이는 구역이 달라지지 않는다.
     */
    public static List<LayoutZone> visibleZones(List<LayoutZone> zones, JwtUser user) {
        if (DrawingService.isPrivileged(user)) {
            return zones;
        }
        return zones.stream()
                .filter(zone -> user.userId().equals(zone.getAssignedUserId()))
                .toList();
    }

    public List<AssignedZoneRow> assignedZones(Long userId) {
        return layoutZoneMapper.findAssignedZonesByUserId(userId);
    }

    public LayoutZone zoneOrThrow(Long zoneId) {
        LayoutZone zone = layoutZoneMapper.findZoneById(zoneId);
        if (zone == null) {
            throw new DrawingNotFoundException(zoneId);
        }
        return zone;
    }

    public Long zoneIdOfFabric(Long layoutVersionId, Long fabricId) {
        return layoutZoneMapper.findZoneIdByFabricId(layoutVersionId, fabricId);
    }

    @Transactional
    public LayoutZone createZone(Long layoutId, ZoneCreateRequest request) {
        Long versionId = currentVersionId(layoutId);
        List<LayoutZone> existing = layoutZoneMapper.findZonesByVersionId(versionId);
        if (existing.size() >= MAX_ZONES_PER_VERSION) {
            throw new IllegalArgumentException("한 도면 버전에 만들 수 있는 구역 수를 초과했습니다.");
        }

        LayoutZone zone = new LayoutZone();
        zone.setLayoutVersionId(versionId);
        zone.setName(validateName(request.name(), existing, null));
        zone.setZoneType(ZoneType.from(request.zoneType()).name());
        applyRect(zone, request.x(), request.y(), request.width(), request.height(), layoutId);
        zone.setAssignedUserId(request.assignedUserId());
        applyExits(zone, request.defaultExitId(), versionId);
        layoutZoneMapper.insertZone(zone);

        if (request.members() != null) {
            replaceMembers(versionId, zone.getId(), request.members());
        }
        return zone;
    }

    @Transactional
    public LayoutZone updateZone(Long layoutId, Long zoneId, ZoneUpdateRequest request) {
        Long versionId = currentVersionId(layoutId);
        LayoutZone zone = zoneOrThrow(zoneId);
        requireSameVersion(zone, versionId);
        List<LayoutZone> existing = layoutZoneMapper.findZonesByVersionId(versionId);

        if (request.name() != null) {
            zone.setName(validateName(request.name(), existing, zoneId));
        }
        if (request.zoneType() != null) {
            zone.setZoneType(ZoneType.from(request.zoneType()).name());
        }
        if (request.x() != null || request.y() != null || request.width() != null || request.height() != null) {
            applyRect(
                    zone,
                    request.x() == null ? zone.getX() : request.x(),
                    request.y() == null ? zone.getY() : request.y(),
                    request.width() == null ? zone.getWidth() : request.width(),
                    request.height() == null ? zone.getHeight() : request.height(),
                    layoutId);
        }
        if (request.clearAssignedUser()) {
            zone.setAssignedUserId(null);
        } else if (request.assignedUserId() != null) {
            zone.setAssignedUserId(request.assignedUserId());
        }
        if (request.displayOrder() != null) {
            zone.setDisplayOrder(request.displayOrder());
        }

        Long nextDefault = request.clearDefaultExit()
                ? null
                : (request.defaultExitId() == null ? zone.getDefaultExitId() : request.defaultExitId());
        applyExits(zone, nextDefault, versionId);

        if (layoutZoneMapper.updateZone(zone) == 0) {
            throw new IllegalStateException("구역을 갱신하지 못했습니다. 다시 불러온 뒤 시도해 주세요: zoneId=" + zoneId);
        }
        if (request.members() != null) {
            replaceMembers(versionId, zoneId, request.members());
        }
        return zone;
    }

    @Transactional
    public void deleteZone(Long layoutId, Long zoneId) {
        Long versionId = currentVersionId(layoutId);
        LayoutZone zone = zoneOrThrow(zoneId);
        requireSameVersion(zone, versionId);
        // 멤버십은 FK CASCADE로 함께 사라진다. 구조물 자체는 남는다(공용 구조물이 된다).
        if (layoutZoneMapper.deleteZoneById(zoneId) == 0) {
            throw new IllegalStateException("구역을 삭제하지 못했습니다: zoneId=" + zoneId);
        }
    }

    @Transactional
    public void updateStructureConstraints(Long layoutId, Long fabricId, StructureConstraintUpdateRequest request) {
        Long versionId = currentVersionId(layoutId);
        Fabric fabric = drawingMapper.findFabricsByVersionId(versionId).stream()
                .filter(candidate -> candidate.getId().equals(fabricId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("이 도면 버전에 없는 구조물입니다: " + fabricId));

        if (request.movementPolicy() == null) {
            throw new IllegalArgumentException("이동 수준을 선택해 주세요.");
        }
        fabric.setMovementPolicy(MovementPolicy.from(request.movementPolicy()).name());
        fabric.setLayoutVersionId(versionId);

        if (drawingMapper.updateFabricConstraints(fabric) == 0) {
            throw new IllegalStateException("구조물 제약을 갱신하지 못했습니다: fabricId=" + fabricId);
        }
    }

    private void replaceMembers(Long versionId, Long zoneId, List<ZoneMemberDto> members) {
        Set<String> requestedKeys = new LinkedHashSet<>();
        Map<ZoneElementKind, Set<Long>> requestedIdsByKind = new HashMap<>();
        for (ZoneMemberDto member : members) {
            ZoneElementKind kind = ZoneElementKind.from(member.kind());
            if (member.id() == null) {
                throw new IllegalArgumentException(kindLabel(kind) + " ID가 비어 있습니다.");
            }
            if (!requestedKeys.add(kind + ":" + member.id())) {
                throw new IllegalArgumentException("구역 구성원이 중복되었습니다: " + kindLabel(kind) + " " + member.id());
            }
            requestedIdsByKind
                    .computeIfAbsent(kind, key -> new java.util.LinkedHashSet<>())
                    .add(member.id());
        }
        validateMembersExistInVersion(versionId, requestedIdsByKind);
        validateMembersNotOwnedByOtherZones(versionId, zoneId, requestedIdsByKind);

        layoutZoneMapper.deleteZoneMembersByZoneId(zoneId);
        if (!requestedKeys.isEmpty()) {
            List<LayoutZoneMember> rows = members.stream()
                    .map(member ->
                            LayoutZoneMember.of(versionId, zoneId, ZoneElementKind.from(member.kind()), member.id()))
                    .toList();
            layoutZoneMapper.insertZoneMembers(rows);
        }
    }

    private void validateMembersExistInVersion(Long versionId, Map<ZoneElementKind, Set<Long>> requestedIdsByKind) {
        for (Map.Entry<ZoneElementKind, Set<Long>> entry : requestedIdsByKind.entrySet()) {
            Set<Long> versionElementIds =
                    switch (entry.getKey()) {
                        case WALL -> Set.copyOf(drawingMapper.findWallIdsByVersionId(versionId));
                        case PILLAR -> Set.copyOf(drawingMapper.findPillarIdsByVersionId(versionId));
                        case FABRIC -> Set.copyOf(drawingMapper.findFabricIdsByVersionId(versionId));
                    };
            for (Long elementId : entry.getValue()) {
                if (!versionElementIds.contains(elementId)) {
                    throw new IllegalArgumentException(
                            "이 도면 버전에 없는 " + kindLabel(entry.getKey()) + "입니다: " + elementId);
                }
            }
        }
    }

    private void validateMembersNotOwnedByOtherZones(
            Long versionId, Long zoneId, Map<ZoneElementKind, Set<Long>> requestedIdsByKind) {
        // 다른 구역이 이미 가진 요소인지 확인한다. DB 유니크 인덱스도 같은 규칙을 막지만, 사용자에게는
        // 제약 위반 대신 어느 요소가 문제인지 알려주는 편이 낫다.
        for (LayoutZoneMember existing : layoutZoneMapper.findZoneMembersByVersionId(versionId)) {
            if (existing.getZoneId().equals(zoneId)) {
                continue;
            }
            Set<Long> requested = requestedIdsByKind.get(existing.getKind());
            if (requested != null && requested.contains(existing.elementId())) {
                throw new IllegalArgumentException(
                        "이미 다른 구역에 속한 " + kindLabel(existing.getKind()) + "입니다: " + existing.elementId());
            }
        }
    }

    private String kindLabel(ZoneElementKind kind) {
        return switch (kind) {
            case WALL -> "벽";
            case PILLAR -> "기둥";
            case FABRIC -> "구조물";
        };
    }

    private String validateName(String rawName, List<LayoutZone> existing, Long selfZoneId) {
        if (!StringUtils.hasText(rawName)) {
            throw new IllegalArgumentException("구역 이름이 필요합니다.");
        }
        String name = rawName.trim();
        if (name.length() > MAX_ZONE_NAME_LENGTH) {
            throw new IllegalArgumentException("구역 이름이 너무 깁니다.");
        }
        boolean duplicated = existing.stream()
                .anyMatch(zone -> zone.getName().equals(name) && !zone.getId().equals(selfZoneId));
        if (duplicated) {
            throw new IllegalArgumentException("같은 도면에 이미 있는 구역 이름입니다: " + name);
        }
        return name;
    }

    private void applyRect(
            LayoutZone zone, BigDecimal x, BigDecimal y, BigDecimal width, BigDecimal height, Long layoutId) {
        validateRect(x, y, width, height, floorPlanOf(layoutId), "구역");
        zone.setX(x);
        zone.setY(y);
        zone.setWidth(width);
        zone.setHeight(height);
    }

    private void validateRect(
            BigDecimal x, BigDecimal y, BigDecimal width, BigDecimal height, FloorPlan floorPlan, String label) {
        if (x == null || y == null || width == null || height == null) {
            throw new IllegalArgumentException(label + " 좌표와 크기가 필요합니다.");
        }
        if (width.signum() <= 0 || height.signum() <= 0) {
            throw new IllegalArgumentException(label + " 크기는 0보다 커야 합니다.");
        }
        if (x.signum() < 0 || y.signum() < 0) {
            throw new IllegalArgumentException(label + "이(가) 도면 밖에 있습니다.");
        }
        if (x.add(width).compareTo(floorPlan.getWidth()) > 0 || y.add(height).compareTo(floorPlan.getHeight()) > 0) {
            throw new IllegalArgumentException(label + "이(가) 도면 밖으로 벗어났습니다.");
        }
    }

    private void applyExits(LayoutZone zone, Long defaultExitId, Long versionId) {
        if (defaultExitId == null) {
            zone.setDefaultExitId(null);
            return;
        }
        Set<Long> versionExitIds = Set.copyOf(drawingMapper.findLayoutExitIdsByVersionId(versionId));
        if (!versionExitIds.contains(defaultExitId)) {
            throw new IllegalArgumentException("이 도면 버전에 없는 비상구입니다: " + defaultExitId);
        }
        zone.setDefaultExitId(defaultExitId);
    }

    private void requireSameVersion(LayoutZone zone, Long versionId) {
        if (!zone.getLayoutVersionId().equals(versionId)) {
            throw new IllegalArgumentException("이 도면의 현재 버전에 속한 구역이 아닙니다: zoneId=" + zone.getId());
        }
    }

    private FloorPlan floorPlanOf(Long layoutId) {
        Layout layout = drawingMapper.findLayoutById(layoutId);
        if (layout == null) {
            throw new DrawingNotFoundException(layoutId);
        }
        FloorPlan floorPlan = drawingMapper.findFloorPlanById(layout.getFloorPlanId());
        if (floorPlan == null) {
            throw new DrawingNotFoundException(layoutId);
        }
        return floorPlan;
    }
}
