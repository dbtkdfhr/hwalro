package com.hwalro.simulation.drawing.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hwalro.simulation.zone.domain.LayoutZone;
import com.hwalro.simulation.zone.domain.LayoutZoneMember;
import com.hwalro.simulation.zone.domain.ZoneElementKind;
import com.hwalro.simulation.zone.mapper.LayoutZoneMapper;
import java.math.BigDecimal;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LayoutMetadataCopierTest {
    private static final Long SOURCE_VERSION_ID = 10L;
    private static final Long TARGET_VERSION_ID = 11L;
    private static final Long ZONE_ID = 615L;
    private static final Long KEPT_FABRIC_ID = 523L;
    private static final Long DELETED_FABRIC_ID = 524L;

    @Mock
    private LayoutZoneMapper layoutZoneMapper;

    private Map<ZoneElementKind, Map<Long, Long>> elementIdMaps(Map<Long, Long> fabricIdMap) {
        Map<ZoneElementKind, Map<Long, Long>> maps = new EnumMap<>(ZoneElementKind.class);
        maps.put(ZoneElementKind.WALL, Map.of());
        maps.put(ZoneElementKind.PILLAR, Map.of());
        maps.put(ZoneElementKind.FABRIC, fabricIdMap);
        return maps;
    }

    private LayoutZone sourceZone() {
        LayoutZone zone = new LayoutZone();
        zone.setId(ZONE_ID);
        zone.setLayoutVersionId(SOURCE_VERSION_ID);
        zone.setName("팝마트");
        zone.setZoneType("WORK");
        zone.setX(BigDecimal.ONE);
        zone.setY(BigDecimal.ONE);
        zone.setWidth(BigDecimal.TEN);
        zone.setHeight(BigDecimal.TEN);
        return zone;
    }

    /**
     * 구역에 속한 기물을 지우고 저장하면 그 소속은 가리킬 요소가 없다. 여기서 실패시키면 그 사람은 도면을 영영 저장하지 못한다. 남은 소속만 옮기고 넘어가야 한다.
     */
    @Test
    void membershipOfAnElementDeletedInThisSaveIsDroppedInsteadOfFailingTheSave() {
        when(layoutZoneMapper.findZonesByVersionId(SOURCE_VERSION_ID)).thenReturn(List.of(sourceZone()));
        when(layoutZoneMapper.findZoneMembersByVersionId(SOURCE_VERSION_ID))
                .thenReturn(List.of(
                        LayoutZoneMember.of(SOURCE_VERSION_ID, ZONE_ID, ZoneElementKind.FABRIC, KEPT_FABRIC_ID),
                        LayoutZoneMember.of(SOURCE_VERSION_ID, ZONE_ID, ZoneElementKind.FABRIC, DELETED_FABRIC_ID)));
        ArgumentCaptor<LayoutZone> insertedZone = ArgumentCaptor.forClass(LayoutZone.class);
        when(layoutZoneMapper.insertZone(insertedZone.capture())).thenAnswer(invocation -> {
            insertedZone.getValue().setId(900L);
            return 1;
        });

        new LayoutMetadataCopier(layoutZoneMapper)
                .copy(
                        SOURCE_VERSION_ID,
                        TARGET_VERSION_ID,
                        Map.of(),
                        // 지워진 구조물은 요청에 실려 오지 않아 ID 맵에도 없다.
                        elementIdMaps(Map.of(KEPT_FABRIC_ID, 1523L)));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<LayoutZoneMember>> members = ArgumentCaptor.forClass(List.class);
        verify(layoutZoneMapper).insertZoneMembers(members.capture());
        assertThat(members.getValue()).extracting(LayoutZoneMember::getFabricId).containsExactly(1523L);
        assertThat(members.getValue()).extracting(LayoutZoneMember::getZoneId).containsExactly(900L);
    }

    /** 옮길 소속이 하나도 남지 않으면 빈 목록으로 INSERT를 부르지 않는다. */
    @Test
    void noMembershipSurvivesWhenEveryMemberElementWasDeleted() {
        when(layoutZoneMapper.findZonesByVersionId(SOURCE_VERSION_ID)).thenReturn(List.of(sourceZone()));
        when(layoutZoneMapper.findZoneMembersByVersionId(SOURCE_VERSION_ID))
                .thenReturn(List.of(
                        LayoutZoneMember.of(SOURCE_VERSION_ID, ZONE_ID, ZoneElementKind.FABRIC, DELETED_FABRIC_ID)));
        when(layoutZoneMapper.insertZone(any())).thenReturn(1);

        new LayoutMetadataCopier(layoutZoneMapper)
                .copy(SOURCE_VERSION_ID, TARGET_VERSION_ID, Map.of(), elementIdMaps(Map.of()));

        verify(layoutZoneMapper, never()).insertZoneMembers(any());
    }
}
