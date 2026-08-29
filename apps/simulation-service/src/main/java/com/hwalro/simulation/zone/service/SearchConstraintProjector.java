package com.hwalro.simulation.zone.service;

import com.hwalro.simulation.drawing.domain.Fabric;
import com.hwalro.simulation.drawing.domain.MovementPolicy;
import com.hwalro.simulation.drawing.mapper.DrawingMapper;
import com.hwalro.simulation.search.domain.SearchConstraints;
import com.hwalro.simulation.zone.domain.LayoutZone;
import com.hwalro.simulation.zone.domain.ZoneType;
import com.hwalro.simulation.zone.mapper.LayoutZoneMapper;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;

/**
 * 도면에 저장된 배치 제약을 배치 개선안 탐색 엔진이 이해하는 {@link SearchConstraints}로 옮긴다.
 *
 * <p>탐색 실행마다 사용자가 제약을 다시 입력하지 않는다. 저장된 값이 유일한 진실 원천이며, 탐색 시작 시점에 이 투영 결과를 스냅샷으로 남겨 과거 실행의 재현성을
 * 지킨다.
 */
@Component
public class SearchConstraintProjector {
    private final DrawingMapper drawingMapper;
    private final LayoutZoneMapper layoutZoneMapper;

    public SearchConstraintProjector(DrawingMapper drawingMapper, LayoutZoneMapper layoutZoneMapper) {
        this.drawingMapper = drawingMapper;
        this.layoutZoneMapper = layoutZoneMapper;
    }

    public SearchConstraints project(Long layoutVersionId) {
        Map<Long, String> movementPolicies = new LinkedHashMap<>();
        Map<Long, SearchConstraints.MovementZone> movementZones = new LinkedHashMap<>();
        List<Fabric> fabrics = drawingMapper.findFabricsByVersionId(layoutVersionId);
        Map<Long, LayoutZone> zonesById = layoutZoneMapper.findZonesByVersionId(layoutVersionId).stream()
                .collect(java.util.stream.Collectors.toMap(LayoutZone::getId, zone -> zone));
        Map<Long, Long> zoneIdByFabric = new LinkedHashMap<>();
        layoutZoneMapper.findZoneMembersByVersionId(layoutVersionId).stream()
                .filter(member -> member.getFabricId() != null)
                .forEach(member -> zoneIdByFabric.put(member.getFabricId(), member.getZoneId()));

        for (Fabric fabric : fabrics) {
            MovementPolicy policy = MovementPolicy.from(fabric.getMovementPolicy());
            movementPolicies.put(fabric.getId(), policy.name());
            if (policy == MovementPolicy.WITHIN_ZONE) {
                LayoutZone zone = zonesById.get(zoneIdByFabric.get(fabric.getId()));
                if (zone != null && ZoneType.EXCLUSION != ZoneType.from(zone.getZoneType())) {
                    movementZones.put(fabric.getId(), toMovementZone(zone));
                }
            }
        }

        // 배치 제외 영역은 EXCLUSION 유형 구역이다. 사각형을 두 종류로 나눠 관리하지 않는다.
        List<SearchConstraints.ForbiddenZone> forbiddenZones =
                layoutZoneMapper.findZonesByVersionId(layoutVersionId).stream()
                        .filter(zone -> ZoneType.EXCLUSION == ZoneType.from(zone.getZoneType()))
                        .map(SearchConstraintProjector::toForbiddenZone)
                        .toList();

        return new SearchConstraints(
                Map.copyOf(movementPolicies), Map.copyOf(movementZones), List.copyOf(forbiddenZones));
    }

    private static SearchConstraints.ForbiddenZone toForbiddenZone(LayoutZone zone) {
        return new SearchConstraints.ForbiddenZone(
                zone.getX().doubleValue(),
                zone.getY().doubleValue(),
                zone.getWidth().doubleValue(),
                zone.getHeight().doubleValue());
    }

    private static SearchConstraints.MovementZone toMovementZone(LayoutZone zone) {
        return new SearchConstraints.MovementZone(
                zone.getX().doubleValue(),
                zone.getY().doubleValue(),
                zone.getWidth().doubleValue(),
                zone.getHeight().doubleValue());
    }
}
