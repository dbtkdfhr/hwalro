package com.hwalro.simulation.drawing.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hwalro.simulation.common.jwt.ForbiddenException;
import com.hwalro.simulation.common.jwt.JwtUser;
import com.hwalro.simulation.drawing.DefaultDrawingData;
import com.hwalro.simulation.drawing.domain.FloorPlan;
import com.hwalro.simulation.drawing.domain.Layout;
import com.hwalro.simulation.drawing.domain.LayoutVersion;
import com.hwalro.simulation.drawing.domain.Wall;
import com.hwalro.simulation.drawing.dto.DrawingResponse;
import com.hwalro.simulation.drawing.dto.DrawingUpdateRequest;
import com.hwalro.simulation.drawing.dto.DrawingVersionSummary;
import com.hwalro.simulation.drawing.dto.WallDto;
import com.hwalro.simulation.drawing.exception.DrawingConflictException;
import com.hwalro.simulation.drawing.exception.DrawingLockedException;
import com.hwalro.simulation.drawing.exception.DrawingNotFoundException;
import com.hwalro.simulation.drawing.mapper.DrawingMapper;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DrawingServiceTest {
    private static final long LAYOUT_ID = 1L;
    private static final long FLOOR_PLAN_ID = 10L;
    private static final long OTHER_VERSION_ID = 11L;
    private static final long CURRENT_VERSION_ID = 12L;
    private static final long NEW_VERSION_ID = 13L;

    @Mock
    private DrawingMapper drawingMapper;

    @Mock
    private DefaultDrawingData defaultDrawingData;

    @Mock
    private LayoutGeometryValidator geometryValidator;

    @Mock
    private com.hwalro.simulation.drawing.service.LayoutMetadataCopier layoutMetadataCopier;

    @Mock
    private com.hwalro.simulation.zone.mapper.LayoutZoneMapper layoutZoneMapper;

    private DrawingService service;
    private JwtUser operator;

    @BeforeEach
    void setUp() {
        service = new DrawingService(
                drawingMapper, defaultDrawingData, geometryValidator, layoutMetadataCopier, layoutZoneMapper);
        operator = new JwtUser(7L, Set.of("OPERATOR"));
    }

    @Test
    void listVersionsMapsVersionRowsToSummariesInMapperOrder() {
        Layout layout = layout(LAYOUT_ID, CURRENT_VERSION_ID);
        when(drawingMapper.findLayoutById(LAYOUT_ID)).thenReturn(layout);
        when(drawingMapper.findLayoutVersionsByLayoutId(LAYOUT_ID))
                .thenReturn(List.of(version(CURRENT_VERSION_ID, 2, "초안"), version(OTHER_VERSION_ID, 1, "잠금")));

        List<DrawingVersionSummary> items = service.listVersions(LAYOUT_ID, operator);

        assertThat(items).hasSize(2);
        assertThat(items.get(0).layoutVersionId()).isEqualTo(CURRENT_VERSION_ID);
        assertThat(items.get(0).version()).isEqualTo(2);
        assertThat(items.get(0).status()).isEqualTo("초안");
        assertThat(items.get(1).status()).isEqualTo("잠금");
    }

    @Test
    void listVersionsRejectsLayoutOwnedByAnotherOperator() {
        Layout layout = layout(LAYOUT_ID, CURRENT_VERSION_ID);
        layout.setCreatedBy(8L);
        when(drawingMapper.findLayoutById(LAYOUT_ID)).thenReturn(layout);

        assertThatThrownBy(() -> service.listVersions(LAYOUT_ID, operator)).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void deleteClearsZoneExitReferencesBeforeDeletingLayout() {
        Layout layout = layout(LAYOUT_ID, CURRENT_VERSION_ID);
        when(drawingMapper.findLayoutById(LAYOUT_ID)).thenReturn(layout);

        service.delete(LAYOUT_ID, operator);

        InOrder order = inOrder(drawingMapper);
        order.verify(drawingMapper).nullifyZoneExitReferencesByLayoutId(LAYOUT_ID);
        order.verify(drawingMapper).deleteLayoutById(LAYOUT_ID);
        order.verify(drawingMapper).deleteFloorPlanById(FLOOR_PLAN_ID);
    }

    @Test
    void updateCreatesNewDraftVersionPerSaveAndKeepsPreviousSnapshot() {
        Layout layout = layout(LAYOUT_ID, CURRENT_VERSION_ID);
        when(drawingMapper.findLayoutById(LAYOUT_ID)).thenReturn(layout);
        when(drawingMapper.findLayoutVersionById(CURRENT_VERSION_ID)).thenReturn(version(CURRENT_VERSION_ID, 1, "초안"));
        when(drawingMapper.findLayoutVersionById(NEW_VERSION_ID)).thenReturn(version(NEW_VERSION_ID, 2, "초안"));
        when(drawingMapper.lockLayout(LAYOUT_ID)).thenReturn(LAYOUT_ID);
        when(drawingMapper.findNextLayoutVersionNumber(LAYOUT_ID)).thenReturn(2);
        when(drawingMapper.insertLayoutVersion(any())).thenAnswer(invocation -> {
            invocation.getArgument(0, LayoutVersion.class).setId(NEW_VERSION_ID);
            return 1;
        });
        when(drawingMapper.findFloorPlanById(FLOOR_PLAN_ID)).thenReturn(floorPlan());

        DrawingUpdateRequest request = new DrawingUpdateRequest(
                "수정 제목",
                null,
                List.of(new WallDto(null, "새 벽", BigDecimal.ONE, BigDecimal.ONE, BigDecimal.TEN, BigDecimal.TEN, null)),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                0);

        // 새 버전에 넣은 만큼 그대로 다시 읽힌다. 구역 이월이 원본→대상 ID를 위치로 짝짓는 전제다.
        when(drawingMapper.findWallIdsByVersionId(NEW_VERSION_ID)).thenReturn(List.of(901L));

        DrawingResponse response = service.update(LAYOUT_ID, request, operator);

        ArgumentCaptor<LayoutVersion> versionCaptor = ArgumentCaptor.forClass(LayoutVersion.class);
        verify(drawingMapper).insertLayoutVersion(versionCaptor.capture());
        LayoutVersion created = versionCaptor.getValue();
        assertThat(created.getLayoutId()).isEqualTo(LAYOUT_ID);
        assertThat(created.getVersion()).isEqualTo(2);
        assertThat(created.getStatus()).isEqualTo("초안");
        assertThat(created.getOptimisticLock()).isEqualTo(1);

        verify(drawingMapper, never()).deleteWallsByVersionId(any());
        verify(drawingMapper, never()).deletePillarsByVersionId(any());
        verify(drawingMapper, never()).deleteFabricsByVersionId(any());
        verify(drawingMapper, never()).deleteOutsideWallsByVersionId(any());
        verify(drawingMapper, never()).deleteLayoutTextsByVersionId(any());
        verify(drawingMapper, never()).deleteLayoutExitsByVersionId(any());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Wall>> wallCaptor = ArgumentCaptor.forClass((Class) List.class);
        verify(drawingMapper).insertWalls(wallCaptor.capture());
        Wall inserted = wallCaptor.getValue().get(0);
        assertThat(inserted.getLayoutVersionId()).isEqualTo(NEW_VERSION_ID);

        assertThat(layout.getCurrentVersionId()).isEqualTo(NEW_VERSION_ID);
        assertThat(response.layoutVersionId()).isEqualTo(NEW_VERSION_ID);
        assertThat(response.layoutVersionNumber()).isEqualTo(2);
    }

    @Test
    void updateRejectsStaleExpectedVersionWithoutCreatingSnapshot() {
        Layout layout = layout(LAYOUT_ID, CURRENT_VERSION_ID);
        when(drawingMapper.findLayoutById(LAYOUT_ID)).thenReturn(layout);
        when(drawingMapper.findLayoutVersionById(CURRENT_VERSION_ID)).thenReturn(version(CURRENT_VERSION_ID, 1, "초안"));
        when(drawingMapper.lockLayout(LAYOUT_ID)).thenReturn(LAYOUT_ID);
        DrawingUpdateRequest request = new DrawingUpdateRequest(
                "수정 제목", null, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), 5);

        assertThatThrownBy(() -> service.update(LAYOUT_ID, request, operator))
                .isInstanceOf(DrawingConflictException.class);
        verify(drawingMapper, never()).insertLayoutVersion(any());
    }

    @Test
    void updateRejectsLockedCurrentVersion() {
        Layout layout = layout(LAYOUT_ID, CURRENT_VERSION_ID);
        when(drawingMapper.findLayoutById(LAYOUT_ID)).thenReturn(layout);
        when(drawingMapper.findLayoutVersionById(CURRENT_VERSION_ID)).thenReturn(version(CURRENT_VERSION_ID, 1, "잠금"));
        when(drawingMapper.lockLayout(LAYOUT_ID)).thenReturn(LAYOUT_ID);
        DrawingUpdateRequest request = new DrawingUpdateRequest(
                "수정 제목", null, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), 0);

        assertThatThrownBy(() -> service.update(LAYOUT_ID, request, operator))
                .isInstanceOf(DrawingLockedException.class);
        verify(drawingMapper, never()).insertLayoutVersion(any());
    }

    @Test
    void restoreVersionCreatesNextDraftVersionCopyingShapesAndSwitchesCurrent() {
        Layout layout = layout(LAYOUT_ID, CURRENT_VERSION_ID);
        when(drawingMapper.findLayoutById(LAYOUT_ID)).thenReturn(layout);
        when(drawingMapper.findLayoutVersionById(OTHER_VERSION_ID)).thenReturn(version(OTHER_VERSION_ID, 1, "잠금"));
        when(drawingMapper.findLayoutVersionById(CURRENT_VERSION_ID)).thenReturn(version(CURRENT_VERSION_ID, 2, "초안"));
        when(drawingMapper.findLayoutVersionById(NEW_VERSION_ID)).thenReturn(version(NEW_VERSION_ID, 3, "초안"));
        when(drawingMapper.lockLayout(LAYOUT_ID)).thenReturn(LAYOUT_ID);
        when(drawingMapper.findNextLayoutVersionNumber(LAYOUT_ID)).thenReturn(3);
        when(drawingMapper.insertLayoutVersion(any())).thenAnswer(invocation -> {
            invocation.getArgument(0, LayoutVersion.class).setId(NEW_VERSION_ID);
            return 1;
        });
        when(drawingMapper.findFloorPlanById(FLOOR_PLAN_ID)).thenReturn(floorPlan());

        DrawingResponse response = service.restoreVersion(LAYOUT_ID, OTHER_VERSION_ID, operator);

        ArgumentCaptor<LayoutVersion> versionCaptor = ArgumentCaptor.forClass(LayoutVersion.class);
        verify(drawingMapper).insertLayoutVersion(versionCaptor.capture());
        LayoutVersion created = versionCaptor.getValue();
        assertThat(created.getLayoutId()).isEqualTo(LAYOUT_ID);
        assertThat(created.getVersion()).isEqualTo(3);
        assertThat(created.getStatus()).isEqualTo("초안");
        assertThat(created.getOptimisticLock()).isEqualTo(1);

        // 외각벽·텍스트는 아무도 ID로 참조하지 않아 대량 복사로 충분하다.
        verify(drawingMapper).copyOutsideWalls(OTHER_VERSION_ID, NEW_VERSION_ID);
        verify(drawingMapper).copyLayoutTexts(OTHER_VERSION_ID, NEW_VERSION_ID);
        // 벽·기둥·구조물·비상구는 구역과 멤버십이 ID로 참조하므로 원본→대상 ID 맵을 만들어 함께 옮긴다.
        verify(layoutMetadataCopier).copy(eq(OTHER_VERSION_ID), eq(NEW_VERSION_ID), anyMap(), anyMap());

        verify(drawingMapper).updateLayoutCurrentVersion(any());
        assertThat(layout.getCurrentVersionId()).isEqualTo(NEW_VERSION_ID);
        assertThat(response.layoutVersionId()).isEqualTo(NEW_VERSION_ID);
        assertThat(response.layoutVersionNumber()).isEqualTo(3);
        assertThat(response.layoutVersionStatus()).isEqualTo("초안");
    }

    @Test
    void restoreVersionRejectsWhenCurrentVersionIsLocked() {
        Layout layout = layout(LAYOUT_ID, CURRENT_VERSION_ID);
        when(drawingMapper.findLayoutById(LAYOUT_ID)).thenReturn(layout);
        when(drawingMapper.findLayoutVersionById(CURRENT_VERSION_ID)).thenReturn(version(CURRENT_VERSION_ID, 2, "잠금"));
        when(drawingMapper.lockLayout(LAYOUT_ID)).thenReturn(LAYOUT_ID);

        assertThatThrownBy(() -> service.restoreVersion(LAYOUT_ID, CURRENT_VERSION_ID, operator))
                .isInstanceOf(DrawingLockedException.class);
        verify(drawingMapper, never()).insertLayoutVersion(any());
        verify(drawingMapper, never()).copyWalls(any(), any());
    }

    @Test
    void restoreVersionRejectsVersionBelongingToAnotherLayout() {
        Layout layout = layout(LAYOUT_ID, CURRENT_VERSION_ID);
        when(drawingMapper.findLayoutById(LAYOUT_ID)).thenReturn(layout);
        LayoutVersion otherLayoutVersion = version(OTHER_VERSION_ID, 1, "잠금");
        otherLayoutVersion.setLayoutId(99L);
        when(drawingMapper.findLayoutVersionById(OTHER_VERSION_ID)).thenReturn(otherLayoutVersion);

        assertThatThrownBy(() -> service.restoreVersion(LAYOUT_ID, OTHER_VERSION_ID, operator))
                .isInstanceOf(DrawingNotFoundException.class);
    }

    @Test
    void restoreVersionRejectsLayoutOwnedByAnotherOperator() {
        Layout layout = layout(LAYOUT_ID, CURRENT_VERSION_ID);
        layout.setCreatedBy(8L);
        when(drawingMapper.findLayoutById(LAYOUT_ID)).thenReturn(layout);

        assertThatThrownBy(() -> service.restoreVersion(LAYOUT_ID, OTHER_VERSION_ID, operator))
                .isInstanceOf(ForbiddenException.class);
    }

    private Layout layout(Long id, Long currentVersionId) {
        Layout layout = new Layout();
        layout.setId(id);
        layout.setFloorPlanId(FLOOR_PLAN_ID);
        layout.setCreatedBy(7L);
        layout.setCurrentVersionId(currentVersionId);
        layout.setTitle("테스트 도면");
        layout.setDescription("설명");
        layout.setCreatedAt(LocalDateTime.of(2026, 8, 24, 9, 0));
        return layout;
    }

    private LayoutVersion version(Long id, int number, String status) {
        LayoutVersion version = new LayoutVersion();
        version.setId(id);
        version.setLayoutId(LAYOUT_ID);
        version.setVersion(number);
        version.setStatus(status);
        version.setOptimisticLock(0);
        version.setCreatedAt(LocalDateTime.of(2026, 8, 24, 9, 0));
        return version;
    }

    private FloorPlan floorPlan() {
        FloorPlan floorPlan = new FloorPlan();
        floorPlan.setId(FLOOR_PLAN_ID);
        floorPlan.setName("테스트 도면");
        floorPlan.setWidth(BigDecimal.valueOf(20));
        floorPlan.setHeight(BigDecimal.valueOf(30));
        return floorPlan;
    }
}
