package com.hwalro.simulation.zone.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hwalro.simulation.common.jwt.ForbiddenException;
import com.hwalro.simulation.common.jwt.JwtUser;
import com.hwalro.simulation.drawing.domain.Fabric;
import com.hwalro.simulation.drawing.service.DrawingService;
import com.hwalro.simulation.zone.client.EmployeeDirectoryClient;
import com.hwalro.simulation.zone.domain.LayoutZone;
import com.hwalro.simulation.zone.domain.LayoutZoneMember;
import com.hwalro.simulation.zone.domain.ZoneElementKind;
import com.hwalro.simulation.zone.dto.AssignedZoneRow;
import com.hwalro.simulation.zone.dto.LayoutZoneDtos.LayoutMetadataResponse;
import com.hwalro.simulation.zone.dto.LayoutZoneDtos.StructureConstraintDto;
import com.hwalro.simulation.zone.dto.LayoutZoneDtos.StructureConstraintUpdateRequest;
import com.hwalro.simulation.zone.dto.LayoutZoneDtos.ZoneCreateRequest;
import com.hwalro.simulation.zone.dto.LayoutZoneDtos.ZoneMemberDto;
import com.hwalro.simulation.zone.dto.LayoutZoneDtos.ZoneResponse;
import java.math.BigDecimal;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LayoutZoneAuthorizationTest {
    private static final Long LAYOUT_ID = 802L;
    private static final Long VERSION_ID = 803L;
    private static final Long MINE_ZONE_ID = 30L;
    private static final Long OTHER_ZONE_ID = 31L;
    private static final Long MY_FABRIC_ID = 20L;
    private static final Long OTHER_FABRIC_ID = 21L;
    private static final Long COMMON_FABRIC_ID = 22L;
    private static final Long EMPLOYEE_ID = 9L;
    private static final String AUTH = "Bearer token";

    @Mock
    private LayoutZoneService layoutZoneService;

    @Mock
    private DrawingService drawingService;

    @Mock
    private EmployeeDirectoryClient employeeDirectoryClient;

    @Mock
    private EvacuationRouteWarmer evacuationRouteWarmer;

    private LayoutMetadataService service;

    private static JwtUser employee() {
        return new JwtUser(EMPLOYEE_ID, Set.of("GENERAL_EMPLOYEE"));
    }

    private static JwtUser operator() {
        return new JwtUser(7L, Set.of("OPERATOR"));
    }

    private static final Long EXCLUSION_ZONE_ID = 9301L;

    private static LayoutZone zone(Long id, Long assignedUserId) {
        LayoutZone zone = new LayoutZone();
        zone.setId(id);
        zone.setLayoutVersionId(VERSION_ID);
        zone.setName("구역 " + id);
        zone.setZoneType("WORK");
        zone.setX(BigDecimal.ZERO);
        zone.setY(BigDecimal.ZERO);
        zone.setWidth(BigDecimal.TEN);
        zone.setHeight(BigDecimal.TEN);
        zone.setAssignedUserId(assignedUserId);
        return zone;
    }

    private static Fabric fabric(Long id) {
        Fabric fabric = new Fabric();
        fabric.setId(id);
        fabric.setLayoutVersionId(VERSION_ID);
        fabric.setMovementPolicy("WITHIN_ZONE");
        return fabric;
    }

    @BeforeEach
    void setUp() {
        when(layoutZoneService.currentVersionId(LAYOUT_ID)).thenReturn(VERSION_ID);
        when(layoutZoneService.zones(VERSION_ID))
                .thenReturn(List.of(zone(MINE_ZONE_ID, EMPLOYEE_ID), zone(OTHER_ZONE_ID, 99L)));
        when(layoutZoneService.memberships(VERSION_ID))
                .thenReturn(List.of(
                        LayoutZoneMember.of(VERSION_ID, MINE_ZONE_ID, ZoneElementKind.FABRIC, MY_FABRIC_ID),
                        LayoutZoneMember.of(VERSION_ID, OTHER_ZONE_ID, ZoneElementKind.FABRIC, OTHER_FABRIC_ID)));
        when(layoutZoneService.fabrics(VERSION_ID))
                .thenReturn(List.of(fabric(MY_FABRIC_ID), fabric(OTHER_FABRIC_ID), fabric(COMMON_FABRIC_ID)));
        when(layoutZoneService.zoneIdOfFabric(VERSION_ID, MY_FABRIC_ID)).thenReturn(MINE_ZONE_ID);
        when(layoutZoneService.zoneIdOfFabric(VERSION_ID, OTHER_FABRIC_ID)).thenReturn(OTHER_ZONE_ID);
        when(layoutZoneService.zoneIdOfFabric(VERSION_ID, COMMON_FABRIC_ID)).thenReturn(null);
        when(layoutZoneService.zoneOrThrow(MINE_ZONE_ID)).thenReturn(zone(MINE_ZONE_ID, EMPLOYEE_ID));
        when(layoutZoneService.zoneOrThrow(OTHER_ZONE_ID)).thenReturn(zone(OTHER_ZONE_ID, 99L));

        service = new LayoutMetadataService(
                layoutZoneService, drawingService, employeeDirectoryClient, evacuationRouteWarmer);
    }

    @Test
    void myZonesStartsRouteWarmingOncePerLayout() {
        when(layoutZoneService.assignedZones(EMPLOYEE_ID))
                .thenReturn(List.of(
                        new AssignedZoneRow(30L, "A", "WORK", 100L, "1층", 1001L, 1L, "출구 A"),
                        new AssignedZoneRow(31L, "B", "WORK", 100L, "1층", 1001L, 1L, "출구 A"),
                        new AssignedZoneRow(32L, "C", "WORK", 200L, "2층", 2001L, 2L, "출구 B")));

        assertThat(service.myZones(employee())).hasSize(3);

        verify(evacuationRouteWarmer).warm(100L);
        verify(evacuationRouteWarmer).warm(200L);
    }

    @Test
    void employeeMetadataShowsOnlyTheirOwnZones() {
        LayoutMetadataResponse response = service.readMetadata(LAYOUT_ID, employee());

        assertThat(response.zones()).extracting(ZoneResponse::zoneId).containsExactly(MINE_ZONE_ID);
        assertThat(response.zones().get(0).members()).containsExactly(new ZoneMemberDto("FABRIC", MY_FABRIC_ID));
    }

    @Test
    void employeeMetadataHidesOtherZonesAndCommonStructureConstraints() {
        LayoutMetadataResponse response = service.readMetadata(LAYOUT_ID, employee());

        assertThat(response.structureConstraints())
                .extracting(StructureConstraintDto::fabricId)
                .containsExactly(MY_FABRIC_ID);
    }

    @Test
    void wallMembershipWithTheSameNumericIdNeverAttachesToStructureConstraints() {
        when(layoutZoneService.memberships(VERSION_ID))
                .thenReturn(List.of(
                        LayoutZoneMember.of(VERSION_ID, MINE_ZONE_ID, ZoneElementKind.FABRIC, MY_FABRIC_ID),
                        LayoutZoneMember.of(VERSION_ID, OTHER_ZONE_ID, ZoneElementKind.WALL, MY_FABRIC_ID)));

        LayoutMetadataResponse response = service.readMetadata(LAYOUT_ID, operator());

        assertThat(response.structureConstraints())
                .filteredOn(constraint -> constraint.fabricId().equals(MY_FABRIC_ID))
                .singleElement()
                .satisfies(constraint -> assertThat(constraint.zoneId()).isEqualTo(MINE_ZONE_ID));
    }

    @Test
    void employeeMetadataHidesExclusionZones() {
        // 배치 제외 영역은 EXCLUSION 유형 구역이다. 배정자가 없으므로 직원 시야에 들어오면 안 된다.
        LayoutZone exclusionZone = zone(EXCLUSION_ZONE_ID, null);
        exclusionZone.setZoneType("EXCLUSION");
        when(layoutZoneService.zones(VERSION_ID)).thenReturn(List.of(zone(MINE_ZONE_ID, EMPLOYEE_ID), exclusionZone));

        assertThat(service.readMetadata(LAYOUT_ID, employee()).zones())
                .extracting(ZoneResponse::zoneId)
                .containsExactly(MINE_ZONE_ID);
        assertThat(service.readMetadata(LAYOUT_ID, operator()).zones())
                .extracting(ZoneResponse::zoneId)
                .contains(EXCLUSION_ZONE_ID);
    }

    @Test
    void privilegedMetadataShowsEveryZoneAndStructure() {
        LayoutMetadataResponse response = service.readMetadata(LAYOUT_ID, operator());

        assertThat(response.zones()).hasSize(2);
        assertThat(response.structureConstraints()).hasSize(3);
    }

    @Test
    void metadataIsForbiddenWhenTheDrawingIsNotAccessible() {
        doThrow(new ForbiddenException("이 도면에 접근할 권한이 없습니다."))
                .when(drawingService)
                .requireAccessible(LAYOUT_ID, employee());

        assertThatThrownBy(() -> service.readMetadata(LAYOUT_ID, employee())).isInstanceOf(ForbiddenException.class);
    }

    @Test
    void employeeCannotEditStructureConstraints() {
        assertThatThrownBy(() -> service.updateStructureConstraints(LAYOUT_ID, MY_FABRIC_ID, patch(), employee()))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("기획/운영 담당자");
        verify(layoutZoneService, never()).updateStructureConstraints(anyLong(), anyLong(), any());
    }

    @Test
    void assignmentIsNotStoredWhenTheDirectoryLookupFails() {
        doThrow(new ResponseStatusException(HttpStatus.BAD_GATEWAY, "직원 정보를 확인할 수 없습니다."))
                .when(employeeDirectoryClient)
                .requireEmployee(EMPLOYEE_ID, AUTH);
        ZoneCreateRequest request = new ZoneCreateRequest(
                "구역",
                "WORK",
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.TEN,
                BigDecimal.TEN,
                EMPLOYEE_ID,
                null,
                null);

        assertThatThrownBy(() -> service.createZone(LAYOUT_ID, request, operator(), AUTH))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("502");
        verify(layoutZoneService, never()).createZone(anyLong(), any());
    }

    private static StructureConstraintUpdateRequest patch() {
        return new StructureConstraintUpdateRequest("FIXED");
    }
}
