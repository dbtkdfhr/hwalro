package com.hwalro.simulation.drawing.service;

import com.hwalro.simulation.zone.domain.LayoutZone;
import com.hwalro.simulation.zone.domain.LayoutZoneMember;
import com.hwalro.simulation.zone.domain.ZoneElementKind;
import com.hwalro.simulation.zone.mapper.LayoutZoneMapper;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 도면 복제와 개선안 채택이 공유하는 구역 메타데이터 복사기.
 *
 * <p>두 경로 모두 새 도면 버전에 벽·기둥·구조물과 비상구를 새 ID로 다시 만든다. 구역의 비상구 참조와 멤버십의 요소 참조는 그 새 ID를 가리켜야 하므로, 호출자가
 * 만든 원본→대상 ID 맵을 받아 치환한다. 이름이나 좌표로 짝을 찾지 않는다 — 이름이 같은 요소가 둘이면 잘못된 짝을 고를 수 있다.
 */
@Component
public class LayoutMetadataCopier {
    private final LayoutZoneMapper layoutZoneMapper;

    public LayoutMetadataCopier(LayoutZoneMapper layoutZoneMapper) {
        this.layoutZoneMapper = layoutZoneMapper;
    }

    /**
     * 구역·멤버십·배치 제외를 원본 버전에서 대상 버전으로 복사한다.
     *
     * <p>담당 직원 배정은 그대로 복사한다. 복사본이 원본과 같은 것이 가장 덜 놀라운 동작이다.
     *
     * @param exitIdMap 원본 비상구 ID → 대상 비상구 ID
     * @param elementIdMaps 종류별 원본 요소 ID → 대상 요소 ID. 멤버십은 벽·기둥·구조물의 ID가 서로 겹치므로 종류로 키를 나눈 맵으로만 짝지울 수 있다.
     */
    public void copy(
            Long sourceVersionId,
            Long targetVersionId,
            Map<Long, Long> exitIdMap,
            Map<ZoneElementKind, Map<Long, Long>> elementIdMaps) {
        List<LayoutZone> sourceZones = layoutZoneMapper.findZonesByVersionId(sourceVersionId);
        if (!sourceZones.isEmpty()) {
            copyZonesAndMemberships(sourceVersionId, targetVersionId, sourceZones, exitIdMap, elementIdMaps);
        }
    }

    private void copyZonesAndMemberships(
            Long sourceVersionId,
            Long targetVersionId,
            List<LayoutZone> sourceZones,
            Map<Long, Long> exitIdMap,
            Map<ZoneElementKind, Map<Long, Long>> elementIdMaps) {
        Map<Long, Long> zoneIdMap = new LinkedHashMap<>();
        for (LayoutZone source : sourceZones) {
            LayoutZone target = new LayoutZone();
            target.setLayoutVersionId(targetVersionId);
            target.setName(source.getName());
            target.setZoneType(source.getZoneType());
            target.setX(source.getX());
            target.setY(source.getY());
            target.setWidth(source.getWidth());
            target.setHeight(source.getHeight());
            target.setAssignedUserId(source.getAssignedUserId());
            target.setDefaultExitId(remap(exitIdMap, source.getDefaultExitId(), "비상구"));
            target.setDisplayOrder(source.getDisplayOrder());
            layoutZoneMapper.insertZone(target);
            zoneIdMap.put(source.getId(), target.getId());
        }

        List<LayoutZoneMember> members = new ArrayList<>();
        for (LayoutZoneMember source : layoutZoneMapper.findZoneMembersByVersionId(sourceVersionId)) {
            ZoneElementKind kind = source.getKind();
            Long targetElementId = elementIdMap(elementIdMaps, kind).get(source.elementId());
            if (targetElementId == null) {
                // 이번 저장에서 지워진 요소다. 소속만 남겨 둘 곳이 없으니 함께 버린다.
                // 여기서 실패시키면 구역에 속한 기물을 지운 사람은 도면을 영영 저장하지 못한다.
                continue;
            }
            members.add(LayoutZoneMember.of(
                    targetVersionId, remap(zoneIdMap, source.getZoneId(), "구역"), kind, targetElementId));
        }
        if (!members.isEmpty()) {
            layoutZoneMapper.insertZoneMembers(members);
        }
    }

    private Map<Long, Long> elementIdMap(Map<ZoneElementKind, Map<Long, Long>> elementIdMaps, ZoneElementKind kind) {
        Map<Long, Long> idMap = elementIdMaps.get(kind);
        if (idMap == null) {
            throw new IllegalStateException("복사에 필요한 " + kindLabel(kind) + " ID 맵이 없습니다.");
        }
        return idMap;
    }

    private String kindLabel(ZoneElementKind kind) {
        return switch (kind) {
            case WALL -> "벽";
            case PILLAR -> "기둥";
            case FABRIC -> "구조물";
        };
    }

    /** 매핑되지 않은 참조는 조용히 NULL로 만들지 않고 즉시 실패시킨다. 복사본이 원본과 다른 곳을 가리키는 것이 더 나쁘다. */
    private Long remap(Map<Long, Long> idMap, Long sourceId, String label) {
        if (sourceId == null) {
            return null;
        }
        Long targetId = idMap.get(sourceId);
        if (targetId == null) {
            throw new IllegalStateException("복사 대상 " + label + "을(를) 새 도면 버전에서 찾지 못했습니다: " + sourceId);
        }
        return targetId;
    }
}
