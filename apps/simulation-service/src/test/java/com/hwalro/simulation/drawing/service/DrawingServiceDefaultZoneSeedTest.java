package com.hwalro.simulation.drawing.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hwalro.simulation.drawing.DefaultDrawingData;
import com.hwalro.simulation.drawing.DefaultDrawingData.DefaultDrawing;
import com.hwalro.simulation.drawing.DefaultDrawingData.DefaultExit;
import com.hwalro.simulation.drawing.DefaultDrawingData.DefaultFabric;
import com.hwalro.simulation.drawing.DefaultDrawingData.DefaultPillar;
import com.hwalro.simulation.drawing.DefaultDrawingData.DefaultWall;
import com.hwalro.simulation.drawing.DefaultDrawingData.DefaultZone;
import com.hwalro.simulation.drawing.DefaultDrawingData.DefaultZoneMember;
import com.hwalro.simulation.drawing.domain.FloorPlan;
import com.hwalro.simulation.drawing.domain.Layout;
import com.hwalro.simulation.drawing.domain.LayoutVersion;
import com.hwalro.simulation.drawing.dto.DrawingCreateRequest;
import com.hwalro.simulation.drawing.mapper.DrawingMapper;
import com.hwalro.simulation.zone.domain.LayoutZone;
import com.hwalro.simulation.zone.domain.LayoutZoneMember;
import com.hwalro.simulation.zone.mapper.LayoutZoneMapper;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * 기본 도면에 딸려 온 구역이 새 도면에 제대로 심기는지 본다.
 *
 * <p>요소는 새 ID로 삽입되므로 기본 도면 데이터는 인덱스로만 가리킬 수 있다. 인덱스를 새 ID로 옮기는 자리가 어긋나면 소속이 조용히 엉뚱한 요소에 붙는다.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DrawingServiceDefaultZoneSeedTest {
    private static final Long LAYOUT_ID = 802L;
    private static final Long VERSION_ID = 803L;
    private static final Long ZONE_ID = 900L;
    // 벽·기둥·구조물의 ID는 서로 겹친다. 종류를 헷갈리면 겹치는 숫자 때문에 조용히 잘못 붙는다.
    private static final List<Long> WALL_IDS = List.of(11L, 12L);
    private static final List<Long> PILLAR_IDS = List.of(11L, 21L);
    private static final List<Long> FABRIC_IDS = List.of(12L, 31L);
    private static final List<Long> EXIT_IDS = List.of(41L, 42L);

    @Mock
    private DrawingMapper drawingMapper;

    @Mock
    private DefaultDrawingData defaultDrawingData;

    @Mock
    private LayoutGeometryValidator geometryValidator;

    @Mock
    private LayoutMetadataCopier layoutMetadataCopier;

    @Mock
    private LayoutZoneMapper layoutZoneMapper;

    @BeforeEach
    void setUp() {
        when(drawingMapper.insertFloorPlan(any())).thenAnswer(invocation -> {
            invocation.getArgument(0, FloorPlan.class).setId(801L);
            return 1;
        });
        when(drawingMapper.insertLayout(any())).thenAnswer(invocation -> {
            invocation.getArgument(0, Layout.class).setId(LAYOUT_ID);
            return 1;
        });
        when(drawingMapper.insertLayoutVersion(any())).thenAnswer(invocation -> {
            invocation.getArgument(0, LayoutVersion.class).setId(VERSION_ID);
            return 1;
        });
        when(drawingMapper.findWallIdsByVersionId(VERSION_ID)).thenReturn(WALL_IDS);
        when(drawingMapper.findPillarIdsByVersionId(VERSION_ID)).thenReturn(PILLAR_IDS);
        when(drawingMapper.findFabricIdsByVersionId(VERSION_ID)).thenReturn(FABRIC_IDS);
        when(drawingMapper.findLayoutExitIdsByVersionId(VERSION_ID)).thenReturn(EXIT_IDS);
        when(layoutZoneMapper.insertZone(any())).thenAnswer(invocation -> {
            invocation.getArgument(0, LayoutZone.class).setId(ZONE_ID);
            return 1;
        });

        Layout saved = new Layout();
        saved.setId(LAYOUT_ID);
        saved.setFloorPlanId(801L);
        saved.setCurrentVersionId(VERSION_ID);
        saved.setTitle("더현대 지하 2층");
        LayoutVersion version = new LayoutVersion();
        version.setId(VERSION_ID);
        version.setLayoutId(LAYOUT_ID);
        version.setVersion(1);
        version.setStatus("초안");
        version.setOptimisticLock(0);
        FloorPlan floorPlan = new FloorPlan();
        floorPlan.setId(801L);
        floorPlan.setWidth(BigDecimal.valueOf(170));
        floorPlan.setHeight(BigDecimal.valueOf(100));
        when(drawingMapper.findLayoutById(LAYOUT_ID)).thenReturn(saved);
        when(drawingMapper.findLayoutVersionById(VERSION_ID)).thenReturn(version);
        when(drawingMapper.findFloorPlanById(801L)).thenReturn(floorPlan);
        when(drawingMapper.findWallsByVersionId(anyLong())).thenReturn(List.of());
        when(drawingMapper.findPillarsByVersionId(anyLong())).thenReturn(List.of());
        when(drawingMapper.findFabricsByVersionId(anyLong())).thenReturn(List.of());
        when(drawingMapper.findOutsideWallsByVersionId(anyLong())).thenReturn(List.of());
        when(drawingMapper.findLayoutTextsByVersionId(anyLong())).thenReturn(List.of());
        when(drawingMapper.findLayoutExitsByVersionId(anyLong())).thenReturn(List.of());
    }

    private DrawingService service() {
        return new DrawingService(
                drawingMapper, defaultDrawingData, geometryValidator, layoutMetadataCopier, layoutZoneMapper);
    }

    private void stubDefaultDrawing(List<DefaultZone> zones) {
        when(defaultDrawingData.get())
                .thenReturn(new DefaultDrawing(
                        "더현대 지하 2층",
                        BigDecimal.valueOf(170),
                        BigDecimal.valueOf(100),
                        List.of(wall("벽 1", 0), wall("벽 2", 1)),
                        List.of(),
                        List.of(pillar("기둥 1", 2), pillar("기둥 2", 3)),
                        List.of(fabric("구조물 1", 4), fabric("구조물 2", 5)),
                        List.of(exit("비상구 1"), exit("비상구 2")),
                        List.of(),
                        zones));
    }

    private static DefaultWall wall(String name, int displayOrder) {
        return new DefaultWall(name, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ONE, BigDecimal.ONE, displayOrder);
    }

    private static DefaultPillar pillar(String name, int displayOrder) {
        return new DefaultPillar(
                name, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ZERO, displayOrder);
    }

    private static DefaultFabric fabric(String name, int displayOrder) {
        return new DefaultFabric(
                name, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ZERO, displayOrder);
    }

    private static DefaultExit exit(String name) {
        return new DefaultExit(name, BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ONE, BigDecimal.ZERO);
    }

    private static DefaultZone zone(Integer defaultExitIndex, List<DefaultZoneMember> members) {
        return new DefaultZone(
                "팝마트",
                "WORK",
                BigDecimal.ONE,
                BigDecimal.ONE,
                BigDecimal.TEN,
                BigDecimal.TEN,
                7,
                defaultExitIndex,
                members);
    }

    @Test
    void zoneMembersResolveToTheNewlyInsertedElementOfTheirOwnKind() {
        stubDefaultDrawing(List.of(zone(
                1,
                List.of(
                        new DefaultZoneMember("WALL", 0),
                        new DefaultZoneMember("PILLAR", 1),
                        new DefaultZoneMember("FABRIC", 0)))));

        service().create(new DrawingCreateRequest("도면", null, true), 7L);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<LayoutZoneMember>> members = ArgumentCaptor.forClass(List.class);
        verify(layoutZoneMapper).insertZoneMembers(members.capture());
        assertThat(members.getValue())
                .extracting(LayoutZoneMember::getWallId, LayoutZoneMember::getPillarId, LayoutZoneMember::getFabricId)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple(11L, null, null),
                        org.assertj.core.groups.Tuple.tuple(null, 21L, null),
                        org.assertj.core.groups.Tuple.tuple(null, null, 12L));
    }

    @Test
    void zoneCarriesItsGeometryAndDefaultExit() {
        stubDefaultDrawing(List.of(zone(1, List.of())));

        service().create(new DrawingCreateRequest("도면", null, true), 7L);

        ArgumentCaptor<LayoutZone> zone = ArgumentCaptor.forClass(LayoutZone.class);
        verify(layoutZoneMapper).insertZone(zone.capture());
        assertThat(zone.getValue().getName()).isEqualTo("팝마트");
        assertThat(zone.getValue().getZoneType()).isEqualTo("WORK");
        assertThat(zone.getValue().getDisplayOrder()).isEqualTo(7);
        assertThat(zone.getValue().getDefaultExitId()).isEqualTo(42L);
        assertThat(zone.getValue().getLayoutVersionId()).isEqualTo(VERSION_ID);
        // 담당 직원 배정은 기본 도면에 담지 않는다. 사용자 ID는 환경마다 다르다.
        assertThat(zone.getValue().getAssignedUserId()).isNull();
    }

    /** 조용히 엉뚱한 요소에 붙느니 도면 생성이 실패하는 편이 낫다. */
    @Test
    void outOfRangeMemberIndexFailsLoudly() {
        stubDefaultDrawing(List.of(zone(null, List.of(new DefaultZoneMember("FABRIC", 9)))));

        assertThatThrownBy(() -> service().create(new DrawingCreateRequest("도면", null, true), 7L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("팝마트")
                .hasMessageContaining("9");
    }

    @Test
    void drawingWithoutDefaultDataGetsNoZones() {
        stubDefaultDrawing(List.of(zone(0, List.of(new DefaultZoneMember("WALL", 0)))));

        service().create(new DrawingCreateRequest("빈 도면", null, false), 7L);

        verify(layoutZoneMapper, org.mockito.Mockito.never()).insertZone(any());
        verify(layoutZoneMapper, org.mockito.Mockito.never()).insertZoneMembers(any());
    }
}
