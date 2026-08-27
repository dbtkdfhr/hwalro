package com.hwalro.simulation.drawing.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hwalro.simulation.common.jwt.JwtUser;
import com.hwalro.simulation.drawing.DefaultDrawingData;
import com.hwalro.simulation.drawing.domain.Fabric;
import com.hwalro.simulation.drawing.domain.FloorPlan;
import com.hwalro.simulation.drawing.domain.Layout;
import com.hwalro.simulation.drawing.domain.LayoutVersion;
import com.hwalro.simulation.drawing.domain.Wall;
import com.hwalro.simulation.drawing.dto.DrawingUpdateRequest;
import com.hwalro.simulation.drawing.dto.FabricDto;
import com.hwalro.simulation.drawing.dto.PillarDto;
import com.hwalro.simulation.drawing.dto.WallDto;
import com.hwalro.simulation.drawing.mapper.DrawingMapper;
import com.hwalro.simulation.zone.mapper.LayoutZoneMapper;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
class DrawingServiceDisplayOrderTest {
    private static final Long LAYOUT_ID = 802L;
    private static final Long VERSION_ID = 803L;
    private static final Long NEXT_VERSION_ID = 804L;
    private static final JwtUser OWNER = new JwtUser(7L, Set.of("OPERATOR"));

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
        Layout layout = new Layout();
        layout.setId(LAYOUT_ID);
        layout.setFloorPlanId(801L);
        layout.setCurrentVersionId(VERSION_ID);
        layout.setCreatedBy(OWNER.userId());
        layout.setTitle("도면");

        FloorPlan floorPlan = new FloorPlan();
        floorPlan.setId(801L);
        floorPlan.setWidth(BigDecimal.valueOf(100));
        floorPlan.setHeight(BigDecimal.valueOf(100));

        LayoutVersion version = new LayoutVersion();
        version.setId(VERSION_ID);
        version.setLayoutId(LAYOUT_ID);
        version.setStatus("초안");
        version.setOptimisticLock(3);

        when(drawingMapper.findLayoutById(LAYOUT_ID)).thenReturn(layout);
        when(drawingMapper.findFloorPlanById(801L)).thenReturn(floorPlan);
        when(drawingMapper.findLayoutVersionById(VERSION_ID)).thenReturn(version);
        when(drawingMapper.updateLayout(any())).thenReturn(1);
        when(drawingMapper.updateLayoutVersionLock(anyLong(), anyInt(), anyInt()))
                .thenReturn(1);
        // 저장은 도면을 잠그고 새 초안 버전을 만든 뒤 그 버전에 요소를 넣는다.
        LayoutVersion nextVersion = new LayoutVersion();
        nextVersion.setId(NEXT_VERSION_ID);
        nextVersion.setLayoutId(LAYOUT_ID);
        nextVersion.setVersion(2);
        nextVersion.setStatus("초안");
        nextVersion.setOptimisticLock(4);
        when(drawingMapper.lockLayout(LAYOUT_ID)).thenReturn(LAYOUT_ID);
        when(drawingMapper.findNextLayoutVersionNumber(LAYOUT_ID)).thenReturn(2);
        when(drawingMapper.findLayoutVersionById(NEXT_VERSION_ID)).thenReturn(nextVersion);
        when(drawingMapper.insertLayoutVersion(any())).thenAnswer(invocation -> {
            invocation.getArgument(0, LayoutVersion.class).setId(NEXT_VERSION_ID);
            return 1;
        });

        // 넣은 만큼 그대로 다시 읽혀야 구역 이월이 원본→대상 ID를 위치로 짝지을 수 있다.
        stubInsertedIds();
        when(drawingMapper.findWallsByVersionId(anyLong())).thenReturn(List.of());
        when(drawingMapper.findPillarsByVersionId(anyLong())).thenReturn(List.of());
        when(drawingMapper.findFabricsByVersionId(anyLong())).thenReturn(List.of());
        when(drawingMapper.findOutsideWallsByVersionId(anyLong())).thenReturn(List.of());
        when(drawingMapper.findLayoutTextsByVersionId(anyLong())).thenReturn(List.of());
        when(drawingMapper.findLayoutExitsByVersionId(anyLong())).thenReturn(List.of());
    }

    @Test
    void requestArrayIndexesAreStoredAsDisplayOrder() {
        service()
                .update(
                        LAYOUT_ID,
                        new DrawingUpdateRequest(
                                "도면",
                                null,
                                List.of(wallDto(null), wallDto(null), wallDto(null)),
                                List.of(),
                                List.of(pillarDto(null), pillarDto(null)),
                                List.of(fabricDto(null), fabricDto(null), fabricDto(null)),
                                List.of(),
                                List.of(),
                                3),
                        OWNER);

        assertThat(insertedWalls()).extracting(Wall::getDisplayOrder).containsExactly(0, 1, 2);
        assertThat(insertedFabrics()).extracting(Fabric::getDisplayOrder).containsExactly(0, 1, 2);
        assertThat(insertedPillars())
                .extracting(com.hwalro.simulation.drawing.domain.Pillar::getDisplayOrder)
                .containsExactly(0, 1);
    }

    @Test
    void explicitDisplayOrderIsStoredAcrossKinds() {
        service()
                .update(
                        LAYOUT_ID,
                        new DrawingUpdateRequest(
                                "도면",
                                null,
                                List.of(wallDto(null, 2), wallDto(null, 0)),
                                List.of(),
                                List.of(pillarDto(null, 3)),
                                List.of(fabricDto(null, 1)),
                                List.of(),
                                List.of(),
                                3),
                        OWNER);

        assertThat(insertedWalls()).extracting(Wall::getDisplayOrder).containsExactly(2, 0);
        assertThat(insertedFabrics()).singleElement().satisfies(fabric -> assertThat(fabric.getDisplayOrder())
                .isEqualTo(1));

        assertThat(insertedPillars()).singleElement().satisfies(pillar -> assertThat(pillar.getDisplayOrder())
                .isEqualTo(3));
    }

    @Test
    void metadataIdentityMappingFollowsDisplayOrderInsteadOfRequestOrder() {
        when(drawingMapper.findWallIdsByVersionId(NEXT_VERSION_ID)).thenReturn(List.of(1001L, 1000L));

        service()
                .update(
                        LAYOUT_ID,
                        new DrawingUpdateRequest(
                                "도면",
                                null,
                                List.of(wallDto(30L, 1), wallDto(31L, 0)),
                                List.of(),
                                List.of(),
                                List.of(),
                                List.of(),
                                List.of(),
                                3),
                        OWNER);

        verify(layoutMetadataCopier)
                .copy(
                        eq(VERSION_ID),
                        eq(NEXT_VERSION_ID),
                        eq(Map.of()),
                        org.mockito.ArgumentMatchers.argThat(
                                maps -> maps.get(com.hwalro.simulation.zone.domain.ZoneElementKind.WALL)
                                        .equals(Map.of(31L, 1001L, 30L, 1000L))));
    }

    private List<Wall> insertedWalls() {
        ArgumentCaptor<List<Wall>> captor = batchCaptor();
        verify(drawingMapper).insertWalls(captor.capture());
        return captor.getValue();
    }

    private List<Fabric> insertedFabrics() {
        ArgumentCaptor<List<Fabric>> captor = batchCaptor();
        verify(drawingMapper).insertFabrics(captor.capture());
        return captor.getValue();
    }

    private List<com.hwalro.simulation.drawing.domain.Pillar> insertedPillars() {
        ArgumentCaptor<List<com.hwalro.simulation.drawing.domain.Pillar>> captor = batchCaptor();
        verify(drawingMapper).insertPillars(captor.capture());
        return captor.getValue();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static <T> ArgumentCaptor<List<T>> batchCaptor() {
        return (ArgumentCaptor<List<T>>) (ArgumentCaptor) ArgumentCaptor.forClass(List.class);
    }

    /** 삽입한 개수만큼 ID를 돌려주도록 흉내낸다. 실제 DB가 하는 일이고, 구역 이월이 이 전제 위에 있다. */
    private void stubInsertedIds() {
        List<Long> wallIds = new ArrayList<>();
        List<Long> pillarIds = new ArrayList<>();
        List<Long> fabricIds = new ArrayList<>();
        List<Long> exitIds = new ArrayList<>();
        when(drawingMapper.insertWalls(any())).thenAnswer(collectIds(wallIds, 1000L));
        when(drawingMapper.insertPillars(any())).thenAnswer(collectIds(pillarIds, 2000L));
        when(drawingMapper.insertFabrics(any())).thenAnswer(collectIds(fabricIds, 3000L));
        when(drawingMapper.insertLayoutExits(any())).thenAnswer(collectIds(exitIds, 4000L));
        when(drawingMapper.findWallIdsByVersionId(any())).thenAnswer(invocation -> List.copyOf(wallIds));
        when(drawingMapper.findPillarIdsByVersionId(any())).thenAnswer(invocation -> List.copyOf(pillarIds));
        when(drawingMapper.findFabricIdsByVersionId(any())).thenAnswer(invocation -> List.copyOf(fabricIds));
        when(drawingMapper.findLayoutExitIdsByVersionId(any())).thenAnswer(invocation -> List.copyOf(exitIds));
    }

    private static org.mockito.stubbing.Answer<Integer> collectIds(List<Long> sink, long base) {
        return invocation -> {
            List<?> inserted = invocation.getArgument(0, List.class);
            for (int index = 0; index < inserted.size(); index++) {
                sink.add(base + sink.size());
            }
            return inserted.size();
        };
    }

    private DrawingService service() {
        return new DrawingService(
                drawingMapper, defaultDrawingData, geometryValidator, layoutMetadataCopier, layoutZoneMapper);
    }

    private static WallDto wallDto(Long id) {
        return wallDto(id, null);
    }

    private static WallDto wallDto(Long id, Integer displayOrder) {
        return new WallDto(id, "", BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ONE, BigDecimal.ONE, displayOrder);
    }

    private static PillarDto pillarDto(Long id) {
        return pillarDto(id, null);
    }

    private static PillarDto pillarDto(Long id, Integer displayOrder) {
        return new PillarDto(
                id,
                "",
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ONE,
                BigDecimal.ONE,
                BigDecimal.ZERO,
                displayOrder);
    }

    private static FabricDto fabricDto(Long id) {
        return fabricDto(id, null);
    }

    private static FabricDto fabricDto(Long id, Integer displayOrder) {
        return new FabricDto(
                id,
                "",
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ONE,
                BigDecimal.ONE,
                BigDecimal.ZERO,
                displayOrder);
    }
}
