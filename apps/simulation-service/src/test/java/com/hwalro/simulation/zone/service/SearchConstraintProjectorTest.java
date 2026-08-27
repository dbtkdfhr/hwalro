package com.hwalro.simulation.zone.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.hwalro.simulation.drawing.domain.Fabric;
import com.hwalro.simulation.drawing.domain.Wall;
import com.hwalro.simulation.drawing.mapper.DrawingMapper;
import com.hwalro.simulation.search.domain.SearchConstraints;
import com.hwalro.simulation.zone.domain.LayoutZone;
import com.hwalro.simulation.zone.domain.ZoneType;
import com.hwalro.simulation.zone.mapper.LayoutZoneMapper;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SearchConstraintProjectorTest {
    private static final Long VERSION_ID = 803L;

    @Mock
    private DrawingMapper drawingMapper;

    @Mock
    private LayoutZoneMapper layoutZoneMapper;

    private SearchConstraints project(List<Fabric> fabrics, List<LayoutZone> zones) {
        return project(fabrics, zones, List.of());
    }

    private SearchConstraints project(List<Fabric> fabrics, List<LayoutZone> zones, List<Wall> walls) {
        when(drawingMapper.findFabricsByVersionId(VERSION_ID)).thenReturn(fabrics);
        when(drawingMapper.findWallsByVersionId(VERSION_ID)).thenReturn(walls);
        when(drawingMapper.findOutsideWallsByVersionId(VERSION_ID)).thenReturn(List.of());
        when(layoutZoneMapper.findZonesByVersionId(VERSION_ID)).thenReturn(zones);
        return new SearchConstraintProjector(drawingMapper, layoutZoneMapper).project(VERSION_ID);
    }

    private static Fabric fabric(Long id, Boolean movable, BigDecimal maxDistance, Boolean locked, Boolean wall) {
        Fabric fabric = new Fabric();
        fabric.setId(id);
        fabric.setLayoutVersionId(VERSION_ID);
        fabric.setStartX(BigDecimal.ONE);
        fabric.setStartY(BigDecimal.ONE);
        fabric.setEndX(BigDecimal.valueOf(3));
        fabric.setEndY(BigDecimal.valueOf(2));
        fabric.setRotation(BigDecimal.ZERO);
        fabric.setMovable(movable);
        fabric.setMaxMovementDistance(maxDistance);
        fabric.setRotationLocked(locked);
        fabric.setKeepAgainstWall(wall);
        return fabric;
    }

    @Test
    void immovableStructureBecomesAZeroRadius() {
        SearchConstraints constraints = project(List.of(fabric(20L, false, null, false, false)), List.of());

        assertThat(constraints.moveRadii()).containsEntry(20L, 0.0);
    }

    @Test
    void movableStructureWithoutADistanceGetsNoRadiusKey() {
        // 키가 없는 것이 엔진에서 "무제한"이다. 0.0으로 채우면 고정과 구분되지 않는다.
        SearchConstraints constraints = project(List.of(fabric(20L, true, null, false, false)), List.of());

        assertThat(constraints.moveRadii()).doesNotContainKey(20L);
    }

    @Test
    void movableStructureWithADistanceKeepsThatDistance() {
        SearchConstraints constraints =
                project(List.of(fabric(20L, true, BigDecimal.valueOf(2.5), false, false)), List.of());

        assertThat(constraints.moveRadii()).containsEntry(20L, 2.5);
    }

    @Test
    void rotationLockedInvertsIntoRotationAllowedFalse() {
        SearchConstraints constraints = project(
                List.of(fabric(20L, true, null, true, false), fabric(21L, true, null, false, false)), List.of());

        assertThat(constraints.rotationAllowed()).containsEntry(20L, false).containsEntry(21L, true);
    }

    @Test
    void keepAgainstWallIsKeptOnlyWhileTheStructureStillTouchesAWall() {
        SearchConstraints constraints = project(
                List.of(fabric(20L, true, null, false, true), fabric(21L, true, null, false, false)),
                List.of(),
                List.of(wall(0, 2, 5, 2)));

        assertThat(constraints.wallAnchored()).containsEntry(20L, true).containsEntry(21L, false);
    }

    @Test
    void staleWallConstraintIsNormalizedToFalse() {
        SearchConstraints constraints = project(List.of(fabric(20L, true, null, false, true)), List.of());

        assertThat(constraints.wallAnchored()).containsEntry(20L, false);
    }

    @Test
    void exclusionZonesBecomeForbiddenZones() {
        SearchConstraints constraints = project(
                List.of(),
                List.of(
                        zone(ZoneType.EXCLUSION, 1.5, 2.5, 3, 4),
                        // 매장 구역은 배치를 막지 않는다. 유형으로만 구분된다.
                        zone(ZoneType.WORK, 10, 10, 5, 5)));

        assertThat(constraints.forbiddenZones())
                .containsExactly(new SearchConstraints.ForbiddenZone(1.5, 2.5, 3.0, 4.0));
    }

    private static LayoutZone zone(ZoneType type, double x, double y, double width, double height) {
        LayoutZone zone = new LayoutZone();
        zone.setLayoutVersionId(VERSION_ID);
        zone.setZoneType(type.name());
        zone.setX(BigDecimal.valueOf(x));
        zone.setY(BigDecimal.valueOf(y));
        zone.setWidth(BigDecimal.valueOf(width));
        zone.setHeight(BigDecimal.valueOf(height));
        return zone;
    }

    private static Wall wall(double startX, double startY, double endX, double endY) {
        Wall wall = new Wall();
        wall.setStartX(BigDecimal.valueOf(startX));
        wall.setStartY(BigDecimal.valueOf(startY));
        wall.setEndX(BigDecimal.valueOf(endX));
        wall.setEndY(BigDecimal.valueOf(endY));
        return wall;
    }

    @Test
    void anEmptyLayoutProjectsToTheEmptyConstraints() {
        SearchConstraints constraints = project(List.of(), List.of());

        assertThat(constraints.moveRadii()).isEmpty();
        assertThat(constraints.forbiddenZones()).isEmpty();
        assertThat(constraints.rotationAllowed()).isEmpty();
        assertThat(constraints.wallAnchored()).isEmpty();
    }

    @Test
    void nullFlagsFallBackToTheSchemaDefaults() {
        // 마이그레이션 직후 기존 행은 DB 기본값(movable=TRUE, 나머지 FALSE)을 갖는다.
        // 매퍼가 null을 돌려주는 경우에도 같은 의미로 읽혀야 한다.
        SearchConstraints constraints = project(List.of(fabric(20L, null, null, null, null)), List.of());

        assertThat(constraints.moveRadii()).doesNotContainKey(20L);
        assertThat(constraints.rotationAllowed()).containsEntry(20L, true);
        assertThat(constraints.wallAnchored()).containsEntry(20L, false);
    }
}
