package com.hwalro.simulation.zone.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hwalro.simulation.drawing.domain.Fabric;
import com.hwalro.simulation.drawing.domain.FloorPlan;
import com.hwalro.simulation.drawing.domain.Layout;
import com.hwalro.simulation.drawing.domain.Wall;
import com.hwalro.simulation.drawing.mapper.DrawingMapper;
import com.hwalro.simulation.zone.domain.LayoutZone;
import com.hwalro.simulation.zone.domain.LayoutZoneMember;
import com.hwalro.simulation.zone.domain.ZoneElementKind;
import com.hwalro.simulation.zone.dto.LayoutZoneDtos.StructureConstraintUpdateRequest;
import com.hwalro.simulation.zone.dto.LayoutZoneDtos.ZoneCreateRequest;
import com.hwalro.simulation.zone.dto.LayoutZoneDtos.ZoneMemberDto;
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

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LayoutZoneServiceTest {
    private static final Long LAYOUT_ID = 802L;
    private static final Long VERSION_ID = 803L;

    @Mock
    private LayoutZoneMapper layoutZoneMapper;

    @Mock
    private DrawingMapper drawingMapper;

    private LayoutZoneService service;

    @BeforeEach
    void setUp() {
        Layout layout = new Layout();
        layout.setId(LAYOUT_ID);
        layout.setFloorPlanId(801L);
        layout.setCurrentVersionId(VERSION_ID);
        FloorPlan floorPlan = new FloorPlan();
        floorPlan.setId(801L);
        floorPlan.setWidth(BigDecimal.valueOf(100));
        floorPlan.setHeight(BigDecimal.valueOf(100));

        when(drawingMapper.findLayoutById(LAYOUT_ID)).thenReturn(layout);
        when(drawingMapper.findFloorPlanById(801L)).thenReturn(floorPlan);
        when(drawingMapper.findLayoutExitIdsByVersionId(VERSION_ID)).thenReturn(List.of(910L, 911L));
        when(drawingMapper.findFabricIdsByVersionId(VERSION_ID)).thenReturn(List.of(20L, 21L));
        when(drawingMapper.findWallIdsByVersionId(VERSION_ID)).thenReturn(List.of(10L, 11L));
        when(drawingMapper.findPillarIdsByVersionId(VERSION_ID)).thenReturn(List.of(15L));
        when(layoutZoneMapper.findZonesByVersionId(VERSION_ID)).thenReturn(List.of());
        when(layoutZoneMapper.findZoneMembersByVersionId(VERSION_ID)).thenReturn(List.of());

        service = new LayoutZoneService(layoutZoneMapper, drawingMapper);
    }

    private ZoneCreateRequest zone(
            String name, BigDecimal x, BigDecimal y, BigDecimal w, BigDecimal h, Long defaultExit) {
        return new ZoneCreateRequest(name, "WORK", x, y, w, h, null, defaultExit, null);
    }

    private static ZoneMemberDto fabric() {
        return new ZoneMemberDto("FABRIC", 20L);
    }

    @Test
    void rejectsBlankAndOverlongZoneNames() {
        ZoneCreateRequest blank = zone("   ", ten(), ten(), ten(), ten(), null);
        assertThatThrownBy(() -> service.createZone(LAYOUT_ID, blank))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("이름");

        ZoneCreateRequest tooLong = zone("가".repeat(201), ten(), ten(), ten(), ten(), null);
        assertThatThrownBy(() -> service.createZone(LAYOUT_ID, tooLong))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("깁니다");
    }

    @Test
    void rejectsDuplicateZoneNameInTheSameVersion() {
        LayoutZone existing = new LayoutZone();
        existing.setId(900L);
        existing.setName("작업 구역");
        when(layoutZoneMapper.findZonesByVersionId(VERSION_ID)).thenReturn(List.of(existing));

        ZoneCreateRequest request = zone("작업 구역", ten(), ten(), ten(), ten(), null);
        assertThatThrownBy(() -> service.createZone(LAYOUT_ID, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("이미 있는");
    }

    @Test
    void rejectsRectangleOutsideTheFloorPlan() {
        ZoneCreateRequest outside = zone("작업 구역", BigDecimal.valueOf(95), ten(), ten(), ten(), null);
        assertThatThrownBy(() -> service.createZone(LAYOUT_ID, outside))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("벗어");

        ZoneCreateRequest zeroWidth = zone("작업 구역", ten(), ten(), BigDecimal.ZERO, ten(), null);
        assertThatThrownBy(() -> service.createZone(LAYOUT_ID, zeroWidth))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("0보다");
    }

    @Test
    void rejectsExitFromAnotherLayoutVersion() {
        ZoneCreateRequest request = zone("작업 구역", ten(), ten(), ten(), ten(), 999L);
        assertThatThrownBy(() -> service.createZone(LAYOUT_ID, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("없는 비상구");
    }

    @Test
    void rejectsStructureAlreadyOwnedByAnotherZone() {
        when(layoutZoneMapper.findZoneMembersByVersionId(VERSION_ID))
                .thenReturn(List.of(LayoutZoneMember.of(VERSION_ID, 900L, ZoneElementKind.FABRIC, 20L)));
        ZoneCreateRequest request =
                new ZoneCreateRequest("작업 구역", "WORK", ten(), ten(), ten(), ten(), null, null, List.of(fabric()));

        assertThatThrownBy(() -> service.createZone(LAYOUT_ID, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("이미 다른 구역");
    }

    @Test
    void rejectsStructureFromAnotherLayoutVersion() {
        ZoneCreateRequest request = new ZoneCreateRequest(
                "작업 구역", "WORK", ten(), ten(), ten(), ten(), null, null, List.of(new ZoneMemberDto("FABRIC", 999L)));

        assertThatThrownBy(() -> service.createZone(LAYOUT_ID, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("없는 구조물");
    }

    @Test
    void rejectsWallFromAnotherLayoutVersion() {
        ZoneCreateRequest request = new ZoneCreateRequest(
                "작업 구역", "WORK", ten(), ten(), ten(), ten(), null, null, List.of(new ZoneMemberDto("WALL", 999L)));

        assertThatThrownBy(() -> service.createZone(LAYOUT_ID, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("없는 벽");
    }

    @Test
    void rejectsUnknownMemberKind() {
        ZoneCreateRequest request = new ZoneCreateRequest(
                "작업 구역", "WORK", ten(), ten(), ten(), ten(), null, null, List.of(new ZoneMemberDto("EXIT", 910L)));

        assertThatThrownBy(() -> service.createZone(LAYOUT_ID, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("알 수 없는 구역 구성원 종류");
    }

    @Test
    void storesMixedKindMembers() {
        ZoneCreateRequest request = new ZoneCreateRequest(
                "작업 구역",
                "WORK",
                ten(),
                ten(),
                ten(),
                ten(),
                null,
                null,
                List.of(new ZoneMemberDto("WALL", 10L), new ZoneMemberDto("PILLAR", 15L), fabric()));

        service.createZone(LAYOUT_ID, request);

        ArgumentCaptor<List<LayoutZoneMember>> saved = ArgumentCaptor.captor();
        verify(layoutZoneMapper).insertZoneMembers(saved.capture());
        assertThat(saved.getValue())
                .extracting(LayoutZoneMember::getKind, LayoutZoneMember::elementId)
                .containsExactlyInAnyOrder(
                        tuple(ZoneElementKind.WALL, 10L),
                        tuple(ZoneElementKind.PILLAR, 15L),
                        tuple(ZoneElementKind.FABRIC, 20L));
    }

    @Test
    void immovableStructureStoresNoMovementDistance() {
        Fabric fabric = new Fabric();
        fabric.setId(20L);
        fabric.setLayoutVersionId(VERSION_ID);
        when(drawingMapper.findFabricsByVersionId(VERSION_ID)).thenReturn(List.of(fabric));
        when(drawingMapper.updateFabricConstraints(any())).thenReturn(1);

        service.updateStructureConstraints(
                LAYOUT_ID, 20L, new StructureConstraintUpdateRequest(false, BigDecimal.valueOf(5), false, null, null));

        ArgumentCaptor<Fabric> saved = ArgumentCaptor.forClass(Fabric.class);
        verify(drawingMapper).updateFabricConstraints(saved.capture());
        assertThat(saved.getValue().getMovable()).isFalse();
        assertThat(saved.getValue().getMaxMovementDistance()).isNull();
    }

    @Test
    void rejectsNonPositiveMovementDistance() {
        Fabric fabric = new Fabric();
        fabric.setId(20L);
        fabric.setLayoutVersionId(VERSION_ID);
        when(drawingMapper.findFabricsByVersionId(VERSION_ID)).thenReturn(List.of(fabric));
        StructureConstraintUpdateRequest request =
                new StructureConstraintUpdateRequest(true, BigDecimal.ZERO, false, null, null);

        assertThatThrownBy(() -> service.updateStructureConstraints(LAYOUT_ID, 20L, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("0보다");
        verify(drawingMapper, never()).updateFabricConstraints(any());
    }

    @Test
    void rejectsWallConstraintForStructureThatDoesNotTouchAWall() {
        Fabric fabric = positionedFabric();
        when(drawingMapper.findFabricsByVersionId(VERSION_ID)).thenReturn(List.of(fabric));
        when(drawingMapper.findWallsByVersionId(VERSION_ID)).thenReturn(List.of());
        when(drawingMapper.findOutsideWallsByVersionId(VERSION_ID)).thenReturn(List.of());
        StructureConstraintUpdateRequest request = new StructureConstraintUpdateRequest(null, null, false, null, true);

        assertThatThrownBy(() -> service.updateStructureConstraints(LAYOUT_ID, 20L, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("벽에 닿아 있지 않은 구조물");
        verify(drawingMapper, never()).updateFabricConstraints(any());
    }

    @Test
    void acceptsWallConstraintForStructureThatTouchesAWall() {
        Fabric fabric = positionedFabric();
        Wall wall = new Wall();
        wall.setStartX(BigDecimal.ZERO);
        wall.setStartY(BigDecimal.valueOf(2));
        wall.setEndX(BigDecimal.valueOf(5));
        wall.setEndY(BigDecimal.valueOf(2));
        when(drawingMapper.findFabricsByVersionId(VERSION_ID)).thenReturn(List.of(fabric));
        when(drawingMapper.findWallsByVersionId(VERSION_ID)).thenReturn(List.of(wall));
        when(drawingMapper.findOutsideWallsByVersionId(VERSION_ID)).thenReturn(List.of());
        when(drawingMapper.updateFabricConstraints(any())).thenReturn(1);

        service.updateStructureConstraints(
                LAYOUT_ID, 20L, new StructureConstraintUpdateRequest(null, null, false, null, true));

        ArgumentCaptor<Fabric> saved = ArgumentCaptor.forClass(Fabric.class);
        verify(drawingMapper).updateFabricConstraints(saved.capture());
        assertThat(saved.getValue().getKeepAgainstWall()).isTrue();
    }

    private static Fabric positionedFabric() {
        Fabric fabric = new Fabric();
        fabric.setId(20L);
        fabric.setLayoutVersionId(VERSION_ID);
        fabric.setStartX(BigDecimal.ONE);
        fabric.setStartY(BigDecimal.ONE);
        fabric.setEndX(BigDecimal.valueOf(3));
        fabric.setEndY(BigDecimal.valueOf(2));
        fabric.setRotation(BigDecimal.ZERO);
        return fabric;
    }

    private static BigDecimal ten() {
        return BigDecimal.TEN;
    }
}
