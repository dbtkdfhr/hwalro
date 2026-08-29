package com.hwalro.simulation.drawing.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hwalro.simulation.drawing.DefaultDrawingData;
import com.hwalro.simulation.drawing.domain.Fabric;
import com.hwalro.simulation.drawing.domain.LayoutExit;
import com.hwalro.simulation.drawing.domain.Pillar;
import com.hwalro.simulation.drawing.domain.Wall;
import com.hwalro.simulation.drawing.mapper.DrawingMapper;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DrawingServiceIdentitySyncTest {
    private static final Long VERSION_ID = 803L;

    @Mock
    private DrawingMapper drawingMapper;

    @Mock
    private DefaultDrawingData defaultDrawingData;

    @Mock
    private LayoutGeometryValidator geometryValidator;

    @Mock
    private LayoutMetadataCopier layoutMetadataCopier;

    @Mock
    private com.hwalro.simulation.zone.mapper.LayoutZoneMapper layoutZoneMapper;

    private DrawingService service() {
        return new DrawingService(
                drawingMapper, defaultDrawingData, geometryValidator, layoutMetadataCopier, layoutZoneMapper);
    }

    @Test
    void keepsPersistedIdentityForExistingStructuresInsteadOfReinserting() {
        when(drawingMapper.findFabricIdsByVersionId(VERSION_ID)).thenReturn(List.of(10L, 11L));

        service().syncFabrics(VERSION_ID, List.of(fabric(10L, "구조물 1"), fabric(11L, "구조물 2")));

        verify(drawingMapper, never()).insertFabric(any());
        verify(drawingMapper, never()).deleteFabricsByIds(anyLong(), any());
        ArgumentCaptor<Fabric> updated = ArgumentCaptor.forClass(Fabric.class);
        verify(drawingMapper, org.mockito.Mockito.times(2)).updateFabricGeometry(updated.capture());
        assertThat(updated.getAllValues()).extracting(Fabric::getId).containsExactly(10L, 11L);
    }

    @Test
    void insertsOnlyStructuresWithoutIdentity() {
        when(drawingMapper.findFabricIdsByVersionId(VERSION_ID)).thenReturn(List.of(10L));

        service().syncFabrics(VERSION_ID, List.of(fabric(10L, "구조물 1"), fabric(null, "구조물 2")));

        ArgumentCaptor<Fabric> inserted = ArgumentCaptor.forClass(Fabric.class);
        verify(drawingMapper).insertFabric(inserted.capture());
        assertThat(inserted.getValue().getName()).isEqualTo("구조물 2");
        verify(drawingMapper).updateFabricGeometry(any());
        verify(drawingMapper, never()).deleteFabricsByIds(anyLong(), any());
    }

    @Test
    void deletesStructuresMissingFromTheRequest() {
        when(drawingMapper.findFabricIdsByVersionId(VERSION_ID)).thenReturn(List.of(10L, 11L, 12L));

        service().syncFabrics(VERSION_ID, List.of(fabric(11L, "구조물 2")));

        verify(drawingMapper).deleteFabricsByIds(VERSION_ID, List.of(10L, 12L));
    }

    @Test
    void rejectsDuplicateStructureIdentity() {
        when(drawingMapper.findFabricIdsByVersionId(VERSION_ID)).thenReturn(List.of(10L));
        DrawingService service = service();
        List<Fabric> requested = List.of(fabric(10L, "구조물 1"), fabric(10L, "구조물 1 사본"));

        assertThatThrownBy(() -> service.syncFabrics(VERSION_ID, requested))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("중복");
    }

    @Test
    void rejectsIdentityThatDoesNotBelongToThisLayoutVersion() {
        when(drawingMapper.findFabricIdsByVersionId(VERSION_ID)).thenReturn(List.of(10L));
        DrawingService service = service();
        List<Fabric> requested = List.of(fabric(999L, "다른 버전 구조물"));

        assertThatThrownBy(() -> service.syncFabrics(VERSION_ID, requested))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("없는");
    }

    @Test
    void clearsZoneExitReferencesBeforeDeletingExits() {
        when(drawingMapper.findLayoutExitIdsByVersionId(VERSION_ID)).thenReturn(List.of(20L, 21L));

        service().syncExits(VERSION_ID, List.of(exit(20L, "비상구 1")));

        org.mockito.InOrder order = org.mockito.Mockito.inOrder(drawingMapper);
        order.verify(drawingMapper).nullifyZoneExitReferences(VERSION_ID, List.of(21L));
        order.verify(drawingMapper).deleteLayoutExitsByIds(VERSION_ID, List.of(21L));
    }

    @Test
    void doesNotTouchZoneReferencesWhenNoExitIsRemoved() {
        when(drawingMapper.findLayoutExitIdsByVersionId(VERSION_ID)).thenReturn(List.of(20L));

        service().syncExits(VERSION_ID, List.of(exit(20L, "비상구 1"), exit(null, "비상구 2")));

        verify(drawingMapper, never()).nullifyZoneExitReferences(anyLong(), any());
        verify(drawingMapper, never()).deleteLayoutExitsByIds(anyLong(), any());
        verify(drawingMapper).insertLayoutExit(any());
        verify(drawingMapper).updateLayoutExitGeometry(any());
    }

    @Test
    void geometrySaveNeverWritesPlacementConstraints() {
        when(drawingMapper.findFabricIdsByVersionId(VERSION_ID)).thenReturn(List.of(10L));

        service().syncFabrics(VERSION_ID, List.of(fabric(10L, "구조물 1")));

        // 제약 컬럼은 updateFabricGeometry SQL에 없다. 도메인 값도 채우지 않는다.
        ArgumentCaptor<Fabric> updated = ArgumentCaptor.forClass(Fabric.class);
        verify(drawingMapper).updateFabricGeometry(updated.capture());
        assertThat(updated.getValue().getMovementPolicy()).isNull();
        verify(drawingMapper, never()).deleteFabricsByVersionId(eq(VERSION_ID));
    }

    @Test
    void keepsPersistedWallIdentityInsteadOfReinserting() {
        when(drawingMapper.findWallIdsByVersionId(VERSION_ID)).thenReturn(List.of(30L, 31L));

        service().syncWalls(VERSION_ID, List.of(wall(30L, "벽 1"), wall(31L, "벽 2")));

        verify(drawingMapper, never()).insertWall(any());
        verify(drawingMapper, never()).deleteWallsByIds(anyLong(), any());
        ArgumentCaptor<Wall> updated = ArgumentCaptor.forClass(Wall.class);
        verify(drawingMapper, org.mockito.Mockito.times(2)).updateWallGeometry(updated.capture());
        assertThat(updated.getAllValues()).extracting(Wall::getId).containsExactly(30L, 31L);
    }

    @Test
    void insertsOnlyWallsWithoutIdentity() {
        when(drawingMapper.findWallIdsByVersionId(VERSION_ID)).thenReturn(List.of(30L));

        service().syncWalls(VERSION_ID, List.of(wall(30L, "벽 1"), wall(null, "벽 2")));

        ArgumentCaptor<Wall> inserted = ArgumentCaptor.forClass(Wall.class);
        verify(drawingMapper).insertWall(inserted.capture());
        assertThat(inserted.getValue().getName()).isEqualTo("벽 2");
        verify(drawingMapper).updateWallGeometry(any());
        verify(drawingMapper, never()).deleteWallsByIds(anyLong(), any());
    }

    @Test
    void deletesWallsMissingFromTheRequest() {
        when(drawingMapper.findWallIdsByVersionId(VERSION_ID)).thenReturn(List.of(30L, 31L, 32L));

        service().syncWalls(VERSION_ID, List.of(wall(31L, "벽 2")));

        verify(drawingMapper).deleteWallsByIds(VERSION_ID, List.of(30L, 32L));
    }

    @Test
    void rejectsDuplicateWallIdentity() {
        when(drawingMapper.findWallIdsByVersionId(VERSION_ID)).thenReturn(List.of(30L));

        assertThatThrownBy(() -> service().syncWalls(VERSION_ID, List.of(wall(30L, "벽 1"), wall(30L, "벽 1 사본"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("중복");
    }

    @Test
    void rejectsWallIdentityThatDoesNotBelongToThisLayoutVersion() {
        when(drawingMapper.findWallIdsByVersionId(VERSION_ID)).thenReturn(List.of(30L));

        assertThatThrownBy(() -> service().syncWalls(VERSION_ID, List.of(wall(999L, "다른 버전 벽"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("없는");
    }

    @Test
    void keepsPersistedPillarIdentityInsteadOfReinserting() {
        when(drawingMapper.findPillarIdsByVersionId(VERSION_ID)).thenReturn(List.of(40L, 41L));

        service().syncPillars(VERSION_ID, List.of(pillar(40L), pillar(41L)));

        verify(drawingMapper, never()).insertPillar(any());
        verify(drawingMapper, never()).deletePillarsByIds(anyLong(), any());
        ArgumentCaptor<Pillar> updated = ArgumentCaptor.forClass(Pillar.class);
        verify(drawingMapper, org.mockito.Mockito.times(2)).updatePillarGeometry(updated.capture());
        assertThat(updated.getAllValues()).extracting(Pillar::getId).containsExactly(40L, 41L);
    }

    @Test
    void deletesPillarsMissingFromTheRequest() {
        when(drawingMapper.findPillarIdsByVersionId(VERSION_ID)).thenReturn(List.of(40L, 41L));

        service().syncPillars(VERSION_ID, List.of(pillar(41L)));

        verify(drawingMapper).deletePillarsByIds(VERSION_ID, List.of(40L));
    }

    private static Wall wall(Long id, String name) {
        Wall wall = new Wall();
        wall.setId(id);
        wall.setLayoutVersionId(VERSION_ID);
        wall.setName(name);
        wall.setStartX(BigDecimal.ZERO);
        wall.setStartY(BigDecimal.ZERO);
        wall.setEndX(BigDecimal.ONE);
        wall.setEndY(BigDecimal.TEN);
        return wall;
    }

    private static Pillar pillar(Long id) {
        Pillar pillar = new Pillar();
        pillar.setId(id);
        pillar.setLayoutVersionId(VERSION_ID);
        pillar.setName("기둥");
        pillar.setStartX(BigDecimal.ZERO);
        pillar.setStartY(BigDecimal.ZERO);
        pillar.setEndX(BigDecimal.ONE);
        pillar.setEndY(BigDecimal.ONE);
        pillar.setRotation(BigDecimal.ZERO);
        return pillar;
    }

    private static Fabric fabric(Long id, String name) {
        Fabric fabric = new Fabric();
        fabric.setId(id);
        fabric.setLayoutVersionId(VERSION_ID);
        fabric.setName(name);
        fabric.setStartX(BigDecimal.ONE);
        fabric.setStartY(BigDecimal.ONE);
        fabric.setEndX(BigDecimal.TEN);
        fabric.setEndY(BigDecimal.TEN);
        fabric.setRotation(BigDecimal.ZERO);
        return fabric;
    }

    private static LayoutExit exit(Long id, String name) {
        LayoutExit exit = new LayoutExit();
        exit.setId(id);
        exit.setLayoutVersionId(VERSION_ID);
        exit.setName(name);
        exit.setStartX(BigDecimal.ZERO);
        exit.setStartY(BigDecimal.ZERO);
        exit.setEndX(BigDecimal.ONE);
        exit.setEndY(BigDecimal.ZERO);
        return exit;
    }
}
