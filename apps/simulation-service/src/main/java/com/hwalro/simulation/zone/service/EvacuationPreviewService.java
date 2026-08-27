package com.hwalro.simulation.zone.service;

import com.hwalro.simulation.common.jwt.ForbiddenException;
import com.hwalro.simulation.common.jwt.JwtUser;
import com.hwalro.simulation.drawing.service.DrawingService;
import com.hwalro.simulation.simulation.dto.SimulationDtos.DrawingGeometryDto;
import com.hwalro.simulation.simulation.dto.SimulationDtos.ExitDto;
import com.hwalro.simulation.simulation.dto.SimulationDtos.FabricRectDto;
import com.hwalro.simulation.simulation.dto.SimulationDtos.PointDto;
import com.hwalro.simulation.simulation.dto.SimulationDtos.RectDto;
import com.hwalro.simulation.simulation.dto.SimulationDtos.SimulationSetupResponse;
import com.hwalro.simulation.simulation.engine.SimulationEngineRunner;
import com.hwalro.simulation.simulation.engine.SimulationEngineRunner.EngineRunException;
import com.hwalro.simulation.simulation.engine.SimulationEngineRunner.PreviewedRoute;
import com.hwalro.simulation.simulation.engine.SimulationEngineRunner.RouteOriginBounds;
import com.hwalro.simulation.simulation.engine.SimulationEngineRunner.RoutePreviewResult;
import com.hwalro.simulation.simulation.engine.SimulationEngineRunner.RoutePreviewZone;
import com.hwalro.simulation.simulation.exception.SimulationEngineUnavailableException;
import com.hwalro.simulation.simulation.service.SimulationService;
import com.hwalro.simulation.zone.domain.LayoutZone;
import com.hwalro.simulation.zone.dto.EvacuationRouteResponse;
import com.hwalro.simulation.zone.dto.ZoneExitPartitionDto;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

/**
 * 구역에서 비상구까지의 정적 대피 경로를 계산한다.
 *
 * <p>평상시 기준 안내다. 실제 화재·통로 차단 같은 현재 상황은 반영하지 않는다 — 그것을 판단할 권위 있는 실시간 상태
 * 소스가 시스템에 없기 때문이다. 최신 시뮬레이션 결과를 실시간 진실로 쓰지 않는다.
 *
 * <p>직원 단건 안내와 안전 담당자의 전체 구역 검토 모두 시뮬레이션 엔진의 역방향 다익스트라 경로 미리보기를 재사용한다. 시뮬레이션 레코드와 시간 진행은
 * 만들지 않는다.
 */
@Service
public class EvacuationPreviewService {
    private static final String MODEL_PROFILE = "SFM_DEFAULT_V2";
    private static final String ROUTING_PROFILE = "HAZARD_RADIAL_EXP_V3";
    private static final BigDecimal WALKING_SPEED = BigDecimal.valueOf(1.25);

    public static final String STATUS_AVAILABLE = "AVAILABLE";
    public static final String STATUS_UNREACHABLE = "UNREACHABLE";
    public static final String STATUS_NOT_CONFIGURED = "NOT_CONFIGURED";

    public static final String CHOICE_ASSIGNED = "ASSIGNED";
    public static final String CHOICE_NEAREST = "NEAREST";

    public static final String REASON_NO_EXIT = "NO_EXIT";
    public static final String REASON_ASSIGNED_EXIT_NOT_FOUND = "ASSIGNED_EXIT_NOT_FOUND";
    public static final String REASON_NO_WALKABLE_ORIGIN = "NO_WALKABLE_ORIGIN_IN_ZONE";
    public static final String REASON_NO_REACHABLE_EXIT = "NO_REACHABLE_EXIT";

    private final LayoutZoneService layoutZoneService;
    private final DrawingService drawingService;
    private final SimulationService simulationService;
    private final SimulationEngineRunner engineRunner;
    private final EvacuationRouteCache routeCache;
    private final EvacuationRouteStore routeStore;
    private final Map<Long, CompletableFuture<List<EvacuationRouteResponse>>> inFlightComputes =
            new ConcurrentHashMap<>();
    private final ExecutorService computeExecutor = Executors.newFixedThreadPool(2, runnable -> {
        Thread thread = new Thread(runnable, "evacuation-route-compute");
        thread.setDaemon(true);
        return thread;
    });

    public EvacuationPreviewService(
            LayoutZoneService layoutZoneService,
            DrawingService drawingService,
            SimulationService simulationService,
            SimulationEngineRunner engineRunner,
            EvacuationRouteCache routeCache,
            EvacuationRouteStore routeStore) {
        this.layoutZoneService = layoutZoneService;
        this.drawingService = drawingService;
        this.simulationService = simulationService;
        this.engineRunner = engineRunner;
        this.routeCache = routeCache;
        this.routeStore = routeStore;
    }

    public EvacuationRouteResponse preview(Long zoneId, JwtUser user) {
        LayoutZone zone = layoutZoneService.zoneOrThrow(zoneId);
        requireAccessible(zone, user);
        if (DrawingService.isPrivileged(user)) {
            Long layoutId = layoutZoneService.layoutIdOfVersion(zone.getLayoutVersionId());
            return previewAll(layoutId, user).stream()
                    .filter(route -> zoneId.equals(route.zoneId()))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("선택한 구역의 대피 경로 계산 결과가 없습니다."));
        }
        DrawingGeometryDto drawing = simulationService.layoutGeometry(zone.getLayoutVersionId());
        return employeeRoute(zone, drawing);
    }

    /**
     * 한 도면의 모든 구역에 대한 대피 경로. 안전 담당자가 도면 전체의 대피 계획을 한 번에 검토할 때 쓴다.
     *
     * <p>엔진 프로세스는 도면마다 한 번만 실행하고 모든 구역과 비상구 갈래가 결과를 나눠 쓴다.
     */
    public List<EvacuationRouteResponse> previewAll(Long layoutId, JwtUser user) {
        drawingService.requireAccessible(layoutId, user);
        Long versionId = layoutZoneService.currentVersionId(layoutId);
        List<LayoutZone> zones = layoutZoneService.zones(versionId);
        // 보이는 구역이 하나도 없는 직원에게는 엔진을 돌릴 이유가 없다.
        Set<Long> visibleZoneIds = LayoutZoneService.visibleZones(zones, user).stream()
                .map(LayoutZone::getId)
                .collect(Collectors.toSet());
        if (zones.isEmpty() || visibleZoneIds.isEmpty()) {
            return List.of();
        }
        return filterVisible(routesOfLayout(layoutId, versionId, zones), visibleZoneIds);
    }

    public void warmLayout(Long layoutId) {
        Long versionId = layoutZoneService.currentVersionId(layoutId);
        List<LayoutZone> zones = layoutZoneService.zones(versionId);
        if (zones.isEmpty()) {
            return;
        }
        String cacheKey = EvacuationRouteCache.keyOf(versionId, zones);
        if (routeCache.find(cacheKey) != null || routeStore.find(versionId, cacheKey) != null) {
            return;
        }
        startCompute(layoutId, versionId, zones, cacheKey);
    }

    private List<EvacuationRouteResponse> routesOfLayout(Long layoutId, Long versionId, List<LayoutZone> zones) {
        String cacheKey = EvacuationRouteCache.keyOf(versionId, zones);
        List<EvacuationRouteResponse> all = routeCache.find(cacheKey);
        if (all == null) {
            all = routeStore.find(versionId, cacheKey);
        }
        if (all != null) {
            routeCache.put(cacheKey, all);
            return all;
        }

        List<EvacuationRouteResponse> stale = routeStore.findLatest(versionId);
        CompletableFuture<List<EvacuationRouteResponse>> inFlight = inFlightComputes.get(layoutId);
        if (inFlight == null) {
            inFlight = startCompute(layoutId, versionId, zones, cacheKey);
        }
        if (stale != null) {
            return stale;
        }
        try {
            return inFlight.join();
        } catch (CompletionException exception) {
            Throwable cause = exception.getCause() != null ? exception.getCause() : exception;
            if (cause instanceof RuntimeException runtimeException) {
                throw runtimeException;
            }
            throw exception;
        }
    }

    private CompletableFuture<List<EvacuationRouteResponse>> startCompute(
            Long layoutId, Long versionId, List<LayoutZone> zones, String cacheKey) {
        boolean[] createdByMe = new boolean[1];
        CompletableFuture<List<EvacuationRouteResponse>> future = inFlightComputes.computeIfAbsent(layoutId, key -> {
            createdByMe[0] = true;
            return new CompletableFuture<>();
        });
        if (createdByMe[0]) {
            CompletableFuture.supplyAsync(
                            () -> {
                                List<EvacuationRouteResponse> fresh = computeAll(layoutId, versionId, zones);
                                routeStore.save(layoutId, versionId, cacheKey, fresh);
                                routeCache.put(cacheKey, fresh);
                                return fresh;
                            },
                            computeExecutor)
                    .whenComplete((fresh, error) -> {
                        if (error != null) {
                            future.completeExceptionally(error);
                        } else {
                            future.complete(fresh);
                        }
                        inFlightComputes.remove(layoutId, future);
                    });
        }
        return future;
    }

    /**
     * 캐시에서 꺼낸 전체 결과에서 그 사용자가 볼 구역만 남긴다.
     *
     * <p>필터는 반드시 캐시 <b>뒤에</b> 온다. 걸러낸 결과를 캐시에 담으면 다음 사람이 남의 구역을 보게 된다.
     */
    private static List<EvacuationRouteResponse> filterVisible(
            List<EvacuationRouteResponse> routes, Set<Long> visibleZoneIds) {
        return routes.stream()
                .filter(route -> visibleZoneIds.contains(route.zoneId()))
                .toList();
    }

    /**
     * 도면 전체 대피 경로를 실제로 계산한다.
     *
     * <p>엔진 실행 실패로 예외가 나가는 경로에서는 호출자가 캐시에 담지 않는다. 일시적인 장애를 오래 붙들고 있으면
     * 엔진이 복구된 뒤에도 계속 실패를 돌려주기 때문이다.
     */
    private List<EvacuationRouteResponse> computeAll(Long layoutId, Long versionId, List<LayoutZone> zones) {
        DrawingGeometryDto drawing = simulationService.layoutGeometry(versionId);
        if (drawing.exits().isEmpty()) {
            return zones.stream()
                    .map(zone -> notConfigured(
                            zone, new PointDto(zone.centerX(), zone.centerY()), null, REASON_NO_EXIT, null))
                    .toList();
        }

        List<LayoutZone> routableZones = zones.stream()
                .filter(zone -> zone.getDefaultExitId() == null || findExit(drawing, zone.getDefaultExitId()) != null)
                .toList();
        if (routableZones.isEmpty()) {
            return zones.stream()
                    .map(zone -> notConfigured(
                            zone,
                            new PointDto(zone.centerX(), zone.centerY()),
                            null,
                            REASON_ASSIGNED_EXIT_NOT_FOUND,
                            CHOICE_ASSIGNED))
                    .toList();
        }

        try {
            RoutePreviewResult result = engineRunner.previewZoneRoutes(
                    "layout-" + layoutId,
                    syntheticBatchSetup(versionId, drawing, routableZones),
                    routableZones.stream()
                            .map(zone -> new RoutePreviewZone(
                                    zone.getId(),
                                    zone.getX(),
                                    zone.getY(),
                                    zone.getWidth(),
                                    zone.getHeight(),
                                    zone.getDefaultExitId()))
                            .toList());
            Map<Long, List<PreviewedRoute>> routesByZone =
                    result.zoneRoutes().stream().collect(Collectors.groupingBy(PreviewedRoute::zoneId));
            return zones.stream()
                    .map(zone -> batchRoute(zone, drawing, result, routesByZone.getOrDefault(zone.getId(), List.of())))
                    .toList();
        } catch (EngineRunException exception) {
            if (exception.failureDetail() != null
                    && (SimulationEngineRunner.NO_REACHABLE_EXIT_CODE.equals(
                                    exception.failureDetail().code())
                            || SimulationEngineRunner.ROUTING_ERROR_CODE.equals(
                                    exception.failureDetail().code()))) {
                return zones.stream()
                        .map(zone -> unavailableBatchZone(zone, drawing))
                        .toList();
            }
            throw new SimulationEngineUnavailableException("대피 경로 엔진을 실행할 수 없습니다.", exception);
        }
    }

    private EvacuationRouteResponse employeeRoute(LayoutZone zone, DrawingGeometryDto drawing) {
        PointDto origin = new PointDto(zone.centerX(), zone.centerY());
        ExitDto assignedExit = findExit(drawing, zone.getDefaultExitId());
        if (zone.getDefaultExitId() != null && assignedExit == null) {
            return notConfigured(zone, origin, null, REASON_ASSIGNED_EXIT_NOT_FOUND, CHOICE_ASSIGNED);
        }
        List<ExitDto> candidates = assignedExit == null ? drawing.exits() : List.of(assignedExit);
        if (candidates.isEmpty()) {
            return notConfigured(zone, origin, null, REASON_NO_EXIT, null);
        }

        try {
            List<PreviewedRoute> routes = engineRunner.previewRoutes(
                    "zone-" + zone.getId(),
                    syntheticSetup(
                            zone,
                            drawing,
                            origin,
                            candidates.stream().map(ExitDto::id).toList()),
                    new RouteOriginBounds(zone.getX(), zone.getY(), zone.getWidth(), zone.getHeight()));
            if (routes.isEmpty()) {
                return unreachable(zone, origin, assignedExit, REASON_NO_REACHABLE_EXIT);
            }
            PreviewedRoute route = routes.get(0);
            ExitDto reached = findExit(drawing, route.exitId());
            if (reached == null) {
                throw new SimulationEngineUnavailableException("대피 경로 엔진이 현재 도면에 없는 비상구를 반환했습니다.");
            }
            PreviewedRoute trimmed = trim(zone, reached, route, drawing);
            return new EvacuationRouteResponse(
                    zone.getId(),
                    zone.getName(),
                    origin,
                    route.routeOrigin(),
                    route.originAdjusted(),
                    STATUS_AVAILABLE,
                    null,
                    assignedExit,
                    route.exitId(),
                    reached.name(),
                    assignedExit != null ? CHOICE_ASSIGNED : CHOICE_NEAREST,
                    route.distanceMeters(),
                    null,
                    trimmed != null ? trimmed.waypoints() : route.waypoints(),
                    // 직원 안내는 구역 하나에 경로 하나다. 색으로 나눌 것이 없다.
                    List.of());
        } catch (EngineRunException exception) {
            if (exception.failureDetail() != null) {
                String code = exception.failureDetail().code();
                if (SimulationEngineRunner.NO_WALKABLE_ORIGIN_CODE.equals(code)) {
                    return unreachable(zone, origin, assignedExit, REASON_NO_WALKABLE_ORIGIN);
                }
                if (SimulationEngineRunner.ROUTING_ERROR_CODE.equals(code)
                        || SimulationEngineRunner.NO_REACHABLE_EXIT_CODE.equals(code)) {
                    return unreachable(zone, origin, assignedExit, REASON_NO_REACHABLE_EXIT);
                }
            }
            throw new SimulationEngineUnavailableException("대피 경로 엔진을 실행할 수 없습니다.", exception);
        }
    }

    private EvacuationRouteResponse batchRoute(
            LayoutZone zone, DrawingGeometryDto drawing, RoutePreviewResult result, List<PreviewedRoute> zoneRoutes) {
        ExitDto assignedExit = findExit(drawing, zone.getDefaultExitId());
        PointDto origin = new PointDto(zone.centerX(), zone.centerY());
        if (zone.getDefaultExitId() != null && assignedExit == null) {
            return notConfigured(zone, origin, null, REASON_ASSIGNED_EXIT_NOT_FOUND, CHOICE_ASSIGNED);
        }
        if (assignedExit != null) {
            PreviewedRoute route = zoneRoutes.stream()
                    .filter(candidate -> assignedExit.id().equals(candidate.exitId()))
                    .findFirst()
                    .orElse(null);
            if (route == null) {
                return unreachable(zone, origin, assignedExit, REASON_NO_REACHABLE_EXIT);
            }
            PreviewedRoute trimmed = trim(zone, assignedExit, route, drawing);
            return available(
                    zone,
                    origin,
                    assignedExit,
                    assignedExit,
                    CHOICE_ASSIGNED,
                    trimmed != null ? trimmed : route,
                    List.of());
        }

        ZoneCoverageBranchExtractor.Result extracted = ZoneCoverageBranchExtractor.extract(
                result.coverage(),
                new ZoneCoverageBranchExtractor.ZoneBounds(
                        zone.getX(), zone.getY(), zone.getWidth(), zone.getHeight()));
        if (extracted.isolated() || extracted.branches().isEmpty()) {
            return unreachable(zone, origin, null, REASON_NO_REACHABLE_EXIT);
        }

        Map<Long, PreviewedRoute> routesByExit = zoneRoutes.stream()
                .collect(Collectors.toMap(PreviewedRoute::exitId, Function.identity(), (first, ignored) -> first));
        PreviewedRoute primaryRoute = zoneRoutes.stream()
                .filter(candidate -> candidate.routeOrigin() != null)
                .min(Comparator.comparingDouble(
                        candidate -> squaredDistanceToZoneCenter(origin, candidate.routeOrigin())))
                .orElse(null);
        if (primaryRoute == null) {
            return unreachable(zone, origin, null, REASON_NO_REACHABLE_EXIT);
        }
        List<ZoneExitPartitionDto> partitions = extracted.branches().stream()
                .filter(branch -> !primaryRoute.exitId().equals(branch.exitId()))
                .sorted(Comparator.comparingInt(ZoneCoverageBranchExtractor.Branch::sampleCount)
                        .reversed())
                .map(branch -> partition(zone, drawing, branch, routesByExit.get(branch.exitId())))
                .filter(Objects::nonNull)
                .toList();
        ExitDto primaryExit = findExit(drawing, primaryRoute.exitId());
        if (primaryExit == null) {
            return unreachable(zone, origin, null, REASON_NO_REACHABLE_EXIT);
        }
        PreviewedRoute primaryTrimmed = trim(zone, primaryExit, primaryRoute, drawing);
        return available(
                zone,
                origin,
                null,
                primaryExit,
                CHOICE_NEAREST,
                primaryTrimmed != null ? primaryTrimmed : primaryRoute,
                partitions);
    }

    private static double squaredDistanceToZoneCenter(PointDto zoneCenter, PointDto routeOrigin) {
        double deltaX = routeOrigin.x().doubleValue() - zoneCenter.x().doubleValue();
        double deltaY = routeOrigin.y().doubleValue() - zoneCenter.y().doubleValue();
        return deltaX * deltaX + deltaY * deltaY;
    }

    private ZoneExitPartitionDto partition(
            LayoutZone zone,
            DrawingGeometryDto drawing,
            ZoneCoverageBranchExtractor.Branch branch,
            PreviewedRoute route) {
        ExitDto exit = findExit(drawing, branch.exitId());
        if (exit == null || route == null) {
            return null;
        }
        PreviewedRoute trimmed = trim(zone, exit, route, drawing);
        if (trimmed == null) {
            trimmed = route;
        }
        List<PointDto> waypoints = trimmed.waypoints();
        return new ZoneExitPartitionDto(
                branch.exitId(),
                exit.name(),
                waypoints.isEmpty() ? null : waypoints.get(0),
                waypoints,
                route.distanceMeters(),
                null);
    }

    private EvacuationRouteResponse available(
            LayoutZone zone,
            PointDto origin,
            ExitDto assignedExit,
            ExitDto reached,
            String choice,
            PreviewedRoute route,
            List<ZoneExitPartitionDto> partitions) {
        return new EvacuationRouteResponse(
                zone.getId(),
                zone.getName(),
                origin,
                route.routeOrigin(),
                route.originAdjusted(),
                STATUS_AVAILABLE,
                null,
                assignedExit,
                route.exitId(),
                reached == null ? null : reached.name(),
                choice,
                route.distanceMeters(),
                null,
                route.waypoints(),
                partitions);
    }

    private PreviewedRoute trim(LayoutZone zone, ExitDto exit, PreviewedRoute route, DrawingGeometryDto drawing) {
        if (route == null) {
            return null;
        }
        List<PointDto> waypoints = ZoneBoundaryTrimmer.trim(
                route.waypoints(),
                new ZoneBoundaryTrimmer.ZoneBounds(zone.getX(), zone.getY(), zone.getWidth(), zone.getHeight()),
                exit,
                drawing.walls(),
                obstaclesOf(drawing));
        return new PreviewedRoute(
                route.zoneId(),
                route.exitId(),
                route.routeOrigin(),
                route.originAdjusted(),
                route.distanceMeters(),
                waypoints);
    }

    private static List<ZoneBoundaryTrimmer.Obstacle> obstaclesOf(DrawingGeometryDto drawing) {
        List<ZoneBoundaryTrimmer.Obstacle> obstacles = new ArrayList<>();
        for (RectDto pillar : drawing.pillars()) {
            if (pillar == null) {
                continue;
            }
            obstacles.add(new ZoneBoundaryTrimmer.Obstacle(
                    pillar.startX().doubleValue(),
                    pillar.startY().doubleValue(),
                    pillar.endX().doubleValue(),
                    pillar.endY().doubleValue(),
                    pillar.rotation() == null ? 0 : pillar.rotation().doubleValue()));
        }
        for (FabricRectDto fabric : drawing.fabrics()) {
            if (fabric == null) {
                continue;
            }
            obstacles.add(new ZoneBoundaryTrimmer.Obstacle(
                    fabric.startX().doubleValue(),
                    fabric.startY().doubleValue(),
                    fabric.endX().doubleValue(),
                    fabric.endY().doubleValue(),
                    fabric.rotation() == null ? 0 : fabric.rotation().doubleValue()));
        }
        return List.copyOf(obstacles);
    }

    private SimulationSetupResponse syntheticSetup(
            LayoutZone zone, DrawingGeometryDto drawing, PointDto origin, List<Long> selectedExitIds) {
        return new SimulationSetupResponse(
                null,
                zone.getLayoutVersionId(),
                null,
                zone.getName(),
                null,
                null,
                1,
                MODEL_PROFILE,
                ROUTING_PROFILE,
                1,
                WALKING_SPEED,
                BigDecimal.ZERO,
                List.of(origin),
                List.of(),
                selectedExitIds,
                drawing,
                false);
    }

    private SimulationSetupResponse syntheticBatchSetup(
            Long versionId, DrawingGeometryDto drawing, List<LayoutZone> zones) {
        return new SimulationSetupResponse(
                null,
                versionId,
                null,
                drawing.title(),
                null,
                null,
                1,
                MODEL_PROFILE,
                ROUTING_PROFILE,
                zones.size(),
                WALKING_SPEED,
                BigDecimal.ZERO,
                zones.stream()
                        .map(zone -> new PointDto(zone.centerX(), zone.centerY()))
                        .toList(),
                List.of(),
                drawing.exits().stream().map(ExitDto::id).toList(),
                drawing,
                false);
    }

    private EvacuationRouteResponse notConfigured(
            LayoutZone zone, PointDto origin, ExitDto assignedExit, String reason, String exitChoice) {
        return new EvacuationRouteResponse(
                zone.getId(),
                zone.getName(),
                origin,
                origin,
                false,
                STATUS_NOT_CONFIGURED,
                reason,
                assignedExit,
                null,
                null,
                exitChoice,
                0,
                null,
                List.of(),
                List.of());
    }

    private EvacuationRouteResponse unreachable(LayoutZone zone, PointDto origin, ExitDto assignedExit, String reason) {
        return new EvacuationRouteResponse(
                zone.getId(),
                zone.getName(),
                origin,
                origin,
                false,
                STATUS_UNREACHABLE,
                reason,
                assignedExit,
                null,
                null,
                assignedExit != null ? CHOICE_ASSIGNED : CHOICE_NEAREST,
                0,
                null,
                List.of(),
                List.of());
    }

    private EvacuationRouteResponse unavailableBatchZone(LayoutZone zone, DrawingGeometryDto drawing) {
        PointDto origin = new PointDto(zone.centerX(), zone.centerY());
        ExitDto assignedExit = findExit(drawing, zone.getDefaultExitId());
        if (zone.getDefaultExitId() != null && assignedExit == null) {
            return notConfigured(zone, origin, null, REASON_ASSIGNED_EXIT_NOT_FOUND, CHOICE_ASSIGNED);
        }
        return unreachable(zone, origin, assignedExit, REASON_NO_REACHABLE_EXIT);
    }

    private void requireAccessible(LayoutZone zone, JwtUser user) {
        if (DrawingService.isPrivileged(user)) {
            drawingService.requireAccessible(layoutZoneService.layoutIdOfVersion(zone.getLayoutVersionId()), user);
            return;
        }
        if (!Objects.equals(zone.getAssignedUserId(), user.userId())) {
            throw new ForbiddenException("담당 구역의 대피 경로만 조회할 수 있습니다.");
        }
    }

    private static ExitDto findExit(DrawingGeometryDto drawing, Long exitId) {
        if (exitId == null) {
            return null;
        }
        return drawing.exits().stream()
                .filter(exit -> exitId.equals(exit.id()))
                .findFirst()
                .orElse(null);
    }
}
