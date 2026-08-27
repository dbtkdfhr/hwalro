package com.hwalro.simulation.zone.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hwalro.simulation.common.jwt.ForbiddenException;
import com.hwalro.simulation.common.jwt.JwtUser;
import com.hwalro.simulation.drawing.service.DrawingService;
import com.hwalro.simulation.simulation.dto.SimulationDtos.DrawingGeometryDto;
import com.hwalro.simulation.simulation.dto.SimulationDtos.ExitDto;
import com.hwalro.simulation.simulation.dto.SimulationDtos.PointDto;
import com.hwalro.simulation.simulation.dto.SimulationDtos.SegmentDto;
import com.hwalro.simulation.simulation.dto.SimulationDtos.SimulationFailureDetailResponse;
import com.hwalro.simulation.simulation.dto.SimulationDtos.SimulationSetupResponse;
import com.hwalro.simulation.simulation.engine.SimulationEngineRunner;
import com.hwalro.simulation.simulation.engine.SimulationEngineRunner.EngineRunException;
import com.hwalro.simulation.simulation.engine.SimulationEngineRunner.PreviewedRoute;
import com.hwalro.simulation.simulation.engine.SimulationEngineRunner.RouteCoverage;
import com.hwalro.simulation.simulation.engine.SimulationEngineRunner.RouteOriginBounds;
import com.hwalro.simulation.simulation.engine.SimulationEngineRunner.RoutePreviewResult;
import com.hwalro.simulation.simulation.engine.SimulationEngineRunner.RoutePreviewZone;
import com.hwalro.simulation.simulation.exception.SimulationEngineUnavailableException;
import com.hwalro.simulation.simulation.service.SimulationService;
import com.hwalro.simulation.zone.domain.LayoutZone;
import com.hwalro.simulation.zone.dto.EvacuationRouteResponse;
import com.hwalro.simulation.zone.mapper.EvacuationRouteStoreMapper;
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

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class EvacuationPreviewServiceTest {
    private static final Long ZONE_ID = 30L;
    private static final Long LAYOUT_ID = 802L;
    private static final Long VERSION_ID = 803L;
    private static final Long EMPLOYEE_ID = 9L;
    private static final Long NEAR_EXIT_ID = 910L;
    private static final Long FAR_EXIT_ID = 911L;

    @Mock
    private LayoutZoneService layoutZoneService;

    @Mock
    private DrawingService drawingService;

    @Mock
    private SimulationService simulationService;

    @Mock
    private SimulationEngineRunner engineRunner;

    private EvacuationPreviewService service;

    private static JwtUser employee() {
        return new JwtUser(EMPLOYEE_ID, Set.of("GENERAL_EMPLOYEE"));
    }

    private static JwtUser reviewer() {
        return new JwtUser(1L, Set.of("SAFETY_REVIEWER"));
    }

    private static BigDecimal m(double value) {
        return BigDecimal.valueOf(value);
    }

    /** 구역은 (10,20)에서 20x10이므로 중심점은 (20,25)다. */
    private static LayoutZone zone(Long defaultExitId, Long assignedUserId) {
        return zone(ZONE_ID, defaultExitId, assignedUserId);
    }

    private static LayoutZone zone(Long zoneId, Long defaultExitId, Long assignedUserId) {
        LayoutZone zone = new LayoutZone();
        zone.setId(zoneId);
        zone.setLayoutVersionId(VERSION_ID);
        zone.setName("작업 구역");
        zone.setX(m(10));
        zone.setY(m(20));
        zone.setWidth(m(20));
        zone.setHeight(m(10));
        zone.setAssignedUserId(assignedUserId);
        zone.setDefaultExitId(defaultExitId);
        return zone;
    }

    /** 비상구 910은 구역 바로 옆, 911은 도면 반대편이다. 사이를 막는 벽은 없다. */
    private static DrawingGeometryDto drawing(List<ExitDto> exits) {
        return new DrawingGeometryDto(
                LAYOUT_ID, "도면", m(60), m(40), List.of(), List.of(), List.of(), List.of(), List.of(), exits);
    }

    private static DrawingGeometryDto drawing() {
        return drawing(List.of(
                new ExitDto(NEAR_EXIT_ID, "가까운 비상구", m(34), m(24), m(34), m(26)),
                new ExitDto(FAR_EXIT_ID, "먼 비상구", m(2), m(2), m(2), m(4))));
    }

    @BeforeEach
    void setUp() throws Exception {
        when(simulationService.layoutGeometry(VERSION_ID)).thenReturn(drawing());
        when(engineRunner.previewRoutes(anyString(), any(SimulationSetupResponse.class), any(RouteOriginBounds.class)))
                .thenAnswer(invocation -> {
                    SimulationSetupResponse setup = invocation.getArgument(1);
                    Long exitId = setup.selectedExitIds().contains(FAR_EXIT_ID)
                                    && setup.selectedExitIds().size() == 1
                            ? FAR_EXIT_ID
                            : NEAR_EXIT_ID;
                    PointDto origin = setup.agentPositions().get(0);
                    return List.of(new PreviewedRoute(
                            exitId, origin, false, 12.5, List.of(origin, new PointDto(m(34), m(25)))));
                });
        RouteCoverage coverage = new RouteCoverage(
                m(10), m(20), m(10), 3, 2, List.of(0, 0, 1, 0, 0, 1), List.of(NEAR_EXIT_ID, FAR_EXIT_ID));
        when(engineRunner.previewZoneRoutes(anyString(), any(SimulationSetupResponse.class), any()))
                .thenAnswer(invocation -> {
                    List<RoutePreviewZone> zones = invocation.getArgument(2);
                    List<PreviewedRoute> zoneRoutes = zones.stream()
                            .flatMap(zone -> {
                                if (zone.defaultExitId() != null) {
                                    return java.util.stream.Stream.of(new PreviewedRoute(
                                            zone.zoneId(),
                                            zone.defaultExitId(),
                                            new PointDto(m(20), m(25)),
                                            false,
                                            14.0,
                                            List.of(new PointDto(m(20), m(25)), new PointDto(m(34), m(25)))));
                                }
                                return java.util.stream.Stream.of(
                                        new PreviewedRoute(
                                                zone.zoneId(),
                                                NEAR_EXIT_ID,
                                                new PointDto(m(10), m(20)),
                                                false,
                                                24.0,
                                                List.of(new PointDto(m(10), m(20)), new PointDto(m(34), m(25)))),
                                        new PreviewedRoute(
                                                zone.zoneId(),
                                                FAR_EXIT_ID,
                                                new PointDto(m(30), m(20)),
                                                false,
                                                30.0,
                                                List.of(new PointDto(m(30), m(20)), new PointDto(m(2), m(3)))));
                            })
                            .toList();
                    return new RoutePreviewResult(List.of(), coverage, zoneRoutes);
                });
        service = new EvacuationPreviewService(
                layoutZoneService,
                drawingService,
                simulationService,
                engineRunner,
                new EvacuationRouteCache(),
                new EvacuationRouteStore(mock(EvacuationRouteStoreMapper.class), new ObjectMapper()));
    }

    @Test
    void 배정된_비상구가_있으면_그곳으로_안내한다() {
        when(layoutZoneService.zoneOrThrow(ZONE_ID)).thenReturn(zone(FAR_EXIT_ID, EMPLOYEE_ID));

        EvacuationRouteResponse response = service.preview(ZONE_ID, employee());

        assertThat(response.status()).isEqualTo(EvacuationPreviewService.STATUS_AVAILABLE);
        assertThat(response.exitChoice()).isEqualTo(EvacuationPreviewService.CHOICE_ASSIGNED);
        // 더 가까운 비상구가 있어도 배정된 곳을 지킨다.
        assertThat(response.recommendedExitId()).isEqualTo(FAR_EXIT_ID);
        assertThat(response.defaultExit().id()).isEqualTo(FAR_EXIT_ID);
        assertThat(response.waypoints()).isNotEmpty();
        assertThat(response.distanceMeters()).isGreaterThan(0.0);
    }

    @Test
    void 배정된_비상구가_없으면_걸어서_가장_가까운_곳으로_안내한다() {
        when(layoutZoneService.zoneOrThrow(ZONE_ID)).thenReturn(zone(null, EMPLOYEE_ID));

        EvacuationRouteResponse response = service.preview(ZONE_ID, employee());

        assertThat(response.status()).isEqualTo(EvacuationPreviewService.STATUS_AVAILABLE);
        assertThat(response.exitChoice()).isEqualTo(EvacuationPreviewService.CHOICE_NEAREST);
        assertThat(response.recommendedExitId()).isEqualTo(NEAR_EXIT_ID);
        assertThat(response.defaultExit()).isNull();
        assertThat(response.recommendedExitName()).isEqualTo("가까운 비상구");
    }

    @Test
    void 도면에_비상구가_하나도_없으면_안내할_것이_없다() {
        when(simulationService.layoutGeometry(VERSION_ID)).thenReturn(drawing(List.of()));
        when(layoutZoneService.zoneOrThrow(ZONE_ID)).thenReturn(zone(null, EMPLOYEE_ID));

        EvacuationRouteResponse response = service.preview(ZONE_ID, employee());

        assertThat(response.status()).isEqualTo(EvacuationPreviewService.STATUS_NOT_CONFIGURED);
        assertThat(response.unavailableReason()).isEqualTo(EvacuationPreviewService.REASON_NO_EXIT);
        assertThat(response.waypoints()).isEmpty();
        assertThat(response.recommendedExitId()).isNull();
    }

    @Test
    void 지정한_비상구가_현재_도면에_없으면_다른_출구로_전환하지_않는다() {
        when(layoutZoneService.zoneOrThrow(ZONE_ID)).thenReturn(zone(999L, EMPLOYEE_ID));

        EvacuationRouteResponse response = service.preview(ZONE_ID, employee());

        assertThat(response.status()).isEqualTo(EvacuationPreviewService.STATUS_NOT_CONFIGURED);
        assertThat(response.unavailableReason()).isEqualTo(EvacuationPreviewService.REASON_ASSIGNED_EXIT_NOT_FOUND);
        assertThat(response.recommendedExitId()).isNull();
    }

    @Test
    void 벽으로_완전히_갇힌_구역은_도달_불가로_알린다() throws Exception {
        DrawingGeometryDto boxed = new DrawingGeometryDto(
                LAYOUT_ID,
                "도면",
                m(60),
                m(40),
                List.of(),
                List.of(
                        new SegmentDto("벽", m(8), m(18), m(32), m(18)),
                        new SegmentDto("벽", m(32), m(18), m(32), m(32)),
                        new SegmentDto("벽", m(32), m(32), m(8), m(32)),
                        new SegmentDto("벽", m(8), m(32), m(8), m(18))),
                List.of(),
                List.of(),
                List.of(),
                List.of(new ExitDto(NEAR_EXIT_ID, "가까운 비상구", m(50), m(24), m(50), m(26))));
        when(simulationService.layoutGeometry(VERSION_ID)).thenReturn(boxed);
        when(layoutZoneService.zoneOrThrow(ZONE_ID)).thenReturn(zone(null, EMPLOYEE_ID));
        when(engineRunner.previewRoutes(anyString(), any(SimulationSetupResponse.class), any(RouteOriginBounds.class)))
                .thenThrow(new EngineRunException(
                        "route unavailable",
                        false,
                        new SimulationFailureDetailResponse(
                                SimulationEngineRunner.NO_REACHABLE_EXIT_CODE,
                                null,
                                null,
                                null,
                                1L,
                                List.of(1L),
                                List.of(NEAR_EXIT_ID),
                                "NO_EXIT_SEED_IN_OCCUPIED_COMPONENT")));

        EvacuationRouteResponse response = service.preview(ZONE_ID, employee());

        assertThat(response.status()).isEqualTo(EvacuationPreviewService.STATUS_UNREACHABLE);
        assertThat(response.unavailableReason()).isEqualTo(EvacuationPreviewService.REASON_NO_REACHABLE_EXIT);
        assertThat(response.waypoints()).isEmpty();
    }

    @Test
    void 출발점은_요청한_구역_중심점_그대로_돌려준다() {
        when(layoutZoneService.zoneOrThrow(ZONE_ID)).thenReturn(zone(NEAR_EXIT_ID, EMPLOYEE_ID));

        EvacuationRouteResponse response = service.preview(ZONE_ID, employee());

        assertThat(response.origin().x()).isEqualByComparingTo(m(20));
        assertThat(response.origin().y()).isEqualByComparingTo(m(25));
    }

    @Test
    void 엔진이_구역_내_출발점을_보정하면_직원_응답에_그대로_표시한다() throws Exception {
        PointDto adjusted = new PointDto(m(21), m(25));
        when(layoutZoneService.zoneOrThrow(ZONE_ID)).thenReturn(zone(NEAR_EXIT_ID, EMPLOYEE_ID));
        when(engineRunner.previewRoutes(anyString(), any(SimulationSetupResponse.class), any(RouteOriginBounds.class)))
                .thenReturn(List.of(new PreviewedRoute(
                        NEAR_EXIT_ID, adjusted, true, 8.0, List.of(adjusted, new PointDto(m(34), m(25))))));

        EvacuationRouteResponse response = service.preview(ZONE_ID, employee());

        assertThat(response.origin()).isEqualTo(new PointDto(m(20), m(25)));
        assertThat(response.routeOrigin()).isEqualTo(adjusted);
        assertThat(response.originAdjusted()).isTrue();
        assertThat(response.narrowestMeters()).isNull();
    }

    @Test
    void 엔진_실행_장애는_경로_없음으로_위장하지_않는다() throws Exception {
        when(layoutZoneService.zoneOrThrow(ZONE_ID)).thenReturn(zone(NEAR_EXIT_ID, EMPLOYEE_ID));
        when(engineRunner.previewRoutes(anyString(), any(SimulationSetupResponse.class), any(RouteOriginBounds.class)))
                .thenThrow(new EngineRunException("timeout", true));

        assertThatThrownBy(() -> service.preview(ZONE_ID, employee()))
                .isInstanceOf(SimulationEngineUnavailableException.class)
                .hasMessageContaining("엔진");
    }

    @Test
    void 직원은_남의_구역_경로를_볼_수_없다() {
        when(layoutZoneService.zoneOrThrow(ZONE_ID)).thenReturn(zone(NEAR_EXIT_ID, 99L));

        assertThatThrownBy(() -> service.preview(ZONE_ID, employee()))
                .isInstanceOf(ForbiddenException.class)
                .hasMessageContaining("담당 구역");
    }

    @Test
    void 안전_담당자는_도면_전체의_대피_경로를_받는다() throws Exception {
        when(layoutZoneService.currentVersionId(LAYOUT_ID)).thenReturn(VERSION_ID);
        when(layoutZoneService.zones(VERSION_ID))
                .thenReturn(List.of(zone(ZONE_ID, null, EMPLOYEE_ID), zone(31L, NEAR_EXIT_ID, 99L)));

        List<EvacuationRouteResponse> routes = service.previewAll(LAYOUT_ID, reviewer());

        assertThat(routes).hasSize(2);
        assertThat(routes)
                .allSatisfy(route -> assertThat(route.status()).isEqualTo(EvacuationPreviewService.STATUS_AVAILABLE));
        assertThat(routes.get(0).partitions()).hasSize(1);
        assertThat(routes.get(0).partitions()).allSatisfy(partition -> {
            assertThat(partition.waypoints()).isNotEmpty();
            assertThat(partition.entryPoint()).isEqualTo(partition.waypoints().get(0));
        });
        verify(engineRunner, times(1)).previewZoneRoutes(anyString(), any(SimulationSetupResponse.class), any());
        verify(engineRunner, never()).previewRoutes(anyString(), any(SimulationSetupResponse.class), any());
    }

    @Test
    void 안전_담당자의_선택_구역은_커버리지_결과와_캐시를_사용한다() throws Exception {
        LayoutZone selected = zone(ZONE_ID, null, EMPLOYEE_ID);
        when(layoutZoneService.zoneOrThrow(ZONE_ID)).thenReturn(selected);
        when(layoutZoneService.layoutIdOfVersion(VERSION_ID)).thenReturn(LAYOUT_ID);
        when(layoutZoneService.currentVersionId(LAYOUT_ID)).thenReturn(VERSION_ID);
        when(layoutZoneService.zones(VERSION_ID)).thenReturn(List.of(selected));

        EvacuationRouteResponse first = service.preview(ZONE_ID, reviewer());
        EvacuationRouteResponse second = service.preview(ZONE_ID, reviewer());

        assertThat(first.partitions()).hasSize(1);
        assertThat(second).isEqualTo(first);
        verify(engineRunner, times(1)).previewZoneRoutes(anyString(), any(SimulationSetupResponse.class), any());
        verify(engineRunner, never()).previewRoutes(anyString(), any(SimulationSetupResponse.class), any());
    }

    @Test
    void 일반_직원은_자기_구역의_대피_경로만_받는다() throws Exception {
        when(layoutZoneService.currentVersionId(LAYOUT_ID)).thenReturn(VERSION_ID);
        when(layoutZoneService.zones(VERSION_ID))
                .thenReturn(List.of(zone(ZONE_ID, null, EMPLOYEE_ID), zone(31L, NEAR_EXIT_ID, 99L)));

        List<EvacuationRouteResponse> routes = service.previewAll(LAYOUT_ID, employee());

        assertThat(routes).singleElement().satisfies(route -> assertThat(route.zoneId())
                .isEqualTo(ZONE_ID));
    }

    @Test
    void 배정된_구역이_없는_직원에게는_엔진을_돌리지_않는다() throws Exception {
        when(layoutZoneService.currentVersionId(LAYOUT_ID)).thenReturn(VERSION_ID);
        when(layoutZoneService.zones(VERSION_ID)).thenReturn(List.of(zone(31L, NEAR_EXIT_ID, 99L)));

        assertThat(service.previewAll(LAYOUT_ID, employee())).isEmpty();
        verify(engineRunner, never()).previewZoneRoutes(anyString(), any(SimulationSetupResponse.class), any());
    }

    @Test
    void 같은_도면을_다시_조회하면_엔진을_다시_실행하지_않는다() throws Exception {
        when(layoutZoneService.currentVersionId(LAYOUT_ID)).thenReturn(VERSION_ID);
        when(layoutZoneService.zones(VERSION_ID))
                .thenReturn(List.of(zone(ZONE_ID, null, EMPLOYEE_ID), zone(31L, NEAR_EXIT_ID, 99L)));

        List<EvacuationRouteResponse> first = service.previewAll(LAYOUT_ID, reviewer());
        List<EvacuationRouteResponse> second = service.previewAll(LAYOUT_ID, reviewer());

        assertThat(second).isEqualTo(first);
        verify(engineRunner, times(1)).previewZoneRoutes(anyString(), any(SimulationSetupResponse.class), any());
    }

    @Test
    void 구역을_옮기면_버전이_그대로여도_다시_계산한다() throws Exception {
        // 구역 편집은 새 버전을 만들지 않고 현재 버전을 제자리에서 고친다. 버전 ID만 키로 쓰면 낡은 경로가 남는다.
        when(layoutZoneService.currentVersionId(LAYOUT_ID)).thenReturn(VERSION_ID);
        when(layoutZoneService.zones(VERSION_ID)).thenReturn(List.of(zone(ZONE_ID, null, EMPLOYEE_ID)));
        service.previewAll(LAYOUT_ID, reviewer());

        LayoutZone moved = zone(ZONE_ID, null, EMPLOYEE_ID);
        moved.setX(m(12));
        when(layoutZoneService.zones(VERSION_ID)).thenReturn(List.of(moved));
        service.previewAll(LAYOUT_ID, reviewer());

        verify(engineRunner, times(2)).previewZoneRoutes(anyString(), any(SimulationSetupResponse.class), any());
    }

    @Test
    void 엔진_장애는_캐시하지_않는다() throws Exception {
        when(layoutZoneService.currentVersionId(LAYOUT_ID)).thenReturn(VERSION_ID);
        when(layoutZoneService.zones(VERSION_ID)).thenReturn(List.of(zone(ZONE_ID, null, EMPLOYEE_ID)));
        when(engineRunner.previewZoneRoutes(anyString(), any(SimulationSetupResponse.class), any()))
                .thenThrow(new EngineRunException("엔진 실행 실패", false));

        assertThatThrownBy(() -> service.previewAll(LAYOUT_ID, reviewer()))
                .isInstanceOf(SimulationEngineUnavailableException.class);
        assertThatThrownBy(() -> service.previewAll(LAYOUT_ID, reviewer()))
                .isInstanceOf(SimulationEngineUnavailableException.class);

        // 장애를 캐시하면 엔진이 살아난 뒤에도 계속 실패를 돌려준다.
        verify(engineRunner, times(2)).previewZoneRoutes(anyString(), any(SimulationSetupResponse.class), any());
    }
}
