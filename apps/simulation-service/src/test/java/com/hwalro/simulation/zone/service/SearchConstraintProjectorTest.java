package com.hwalro.simulation.zone.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.hwalro.simulation.drawing.domain.Fabric;
import com.hwalro.simulation.drawing.mapper.DrawingMapper;
import com.hwalro.simulation.search.domain.SearchConstraints;
import com.hwalro.simulation.zone.domain.LayoutZone;
import com.hwalro.simulation.zone.domain.LayoutZoneMember;
import com.hwalro.simulation.zone.domain.ZoneElementKind;
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

    private SearchConstraints project(
            List<Fabric> fabrics, List<LayoutZone> zones, List<LayoutZoneMember> memberships) {
        when(drawingMapper.findFabricsByVersionId(VERSION_ID)).thenReturn(fabrics);
        when(layoutZoneMapper.findZonesByVersionId(VERSION_ID)).thenReturn(zones);
        when(layoutZoneMapper.findZoneMembersByVersionId(VERSION_ID)).thenReturn(memberships);
        return new SearchConstraintProjector(drawingMapper, layoutZoneMapper).project(VERSION_ID);
    }

    private static Fabric fabric(Long id, String movementPolicy) {
        Fabric fabric = new Fabric();
        fabric.setId(id);
        fabric.setLayoutVersionId(VERSION_ID);
        fabric.setStartX(BigDecimal.ONE);
        fabric.setStartY(BigDecimal.ONE);
        fabric.setEndX(BigDecimal.valueOf(3));
        fabric.setEndY(BigDecimal.valueOf(2));
        fabric.setRotation(BigDecimal.ZERO);
        fabric.setMovementPolicy(movementPolicy);
        return fabric;
    }

    @Test
    void movementPoliciesAreProjectedByFabric() {
        SearchConstraints constraints =
                project(List.of(fabric(20L, "FREE"), fabric(21L, "FIXED")), List.of(), List.of());

        assertThat(constraints.movementPolicies()).containsEntry(20L, "FREE").containsEntry(21L, "FIXED");
    }

    @Test
    void withinZoneStructureReceivesItsZoneBounds() {
        LayoutZone work = zone(91L, ZoneType.WORK, 1.5, 2.5, 3, 4);
        LayoutZoneMember membership = LayoutZoneMember.of(VERSION_ID, 91L, ZoneElementKind.FABRIC, 20L);
        SearchConstraints constraints =
                project(List.of(fabric(20L, "WITHIN_ZONE")), List.of(work), List.of(membership));

        assertThat(constraints.movementZones()).containsEntry(20L, new SearchConstraints.MovementZone(1.5, 2.5, 3, 4));
    }

    @Test
    void withinZoneWithoutMembershipHasNoMovementArea() {
        SearchConstraints constraints = project(List.of(fabric(20L, "WITHIN_ZONE")), List.of(), List.of());

        assertThat(constraints.movementZones()).doesNotContainKey(20L);
    }

    @Test
    void exclusionZonesBecomeForbiddenZones() {
        SearchConstraints constraints = project(
                List.of(),
                List.of(
                        zone(91L, ZoneType.EXCLUSION, 1.5, 2.5, 3, 4),
                        // 매장 구역은 배치를 막지 않는다. 유형으로만 구분된다.
                        zone(92L, ZoneType.WORK, 10, 10, 5, 5)),
                List.of());

        assertThat(constraints.forbiddenZones())
                .containsExactly(new SearchConstraints.ForbiddenZone(1.5, 2.5, 3.0, 4.0));
    }

    private static LayoutZone zone(Long id, ZoneType type, double x, double y, double width, double height) {
        LayoutZone zone = new LayoutZone();
        zone.setId(id);
        zone.setLayoutVersionId(VERSION_ID);
        zone.setZoneType(type.name());
        zone.setX(BigDecimal.valueOf(x));
        zone.setY(BigDecimal.valueOf(y));
        zone.setWidth(BigDecimal.valueOf(width));
        zone.setHeight(BigDecimal.valueOf(height));
        return zone;
    }

    @Test
    void anEmptyLayoutProjectsToTheEmptyConstraints() {
        SearchConstraints constraints = project(List.of(), List.of(), List.of());

        assertThat(constraints.movementPolicies()).isEmpty();
        assertThat(constraints.movementZones()).isEmpty();
        assertThat(constraints.forbiddenZones()).isEmpty();
    }

    @Test
    void nullPolicyFallsBackToWithinZone() {
        SearchConstraints constraints = project(List.of(fabric(20L, null)), List.of(), List.of());

        assertThat(constraints.movementPolicies()).containsEntry(20L, "WITHIN_ZONE");
    }
}
