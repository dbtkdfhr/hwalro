package com.hwalro.simulation.simulation.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hwalro.simulation.common.jwt.ForbiddenException;
import com.hwalro.simulation.common.jwt.JwtUser;
import com.hwalro.simulation.drawing.domain.Fabric;
import com.hwalro.simulation.drawing.domain.LayoutExit;
import com.hwalro.simulation.drawing.domain.LayoutText;
import com.hwalro.simulation.drawing.domain.OutsideWall;
import com.hwalro.simulation.drawing.domain.Pillar;
import com.hwalro.simulation.drawing.domain.Wall;
import com.hwalro.simulation.drawing.mapper.DrawingMapper;
import com.hwalro.simulation.simulation.client.RegulationUsageClient;
import com.hwalro.simulation.simulation.domain.HazardZone;
import com.hwalro.simulation.simulation.domain.LayoutSimulationContext;
import com.hwalro.simulation.simulation.domain.Simulation;
import com.hwalro.simulation.simulation.domain.SimulationOption;
import com.hwalro.simulation.simulation.domain.SimulationResult;
import com.hwalro.simulation.simulation.dto.SimulationDtos.DraftCreateRequest;
import com.hwalro.simulation.simulation.dto.SimulationDtos.DrawingGeometryDto;
import com.hwalro.simulation.simulation.dto.SimulationDtos.ExitDto;
import com.hwalro.simulation.simulation.dto.SimulationDtos.FabricRectDto;
import com.hwalro.simulation.simulation.dto.SimulationDtos.HazardZoneDto;
import com.hwalro.simulation.simulation.dto.SimulationDtos.PlacementAdjustmentDraftRequest;
import com.hwalro.simulation.simulation.dto.SimulationDtos.PointDto;
import com.hwalro.simulation.simulation.dto.SimulationDtos.RectDto;
import com.hwalro.simulation.simulation.dto.SimulationDtos.SegmentDto;
import com.hwalro.simulation.simulation.dto.SimulationDtos.SetupUpdateRequest;
import com.hwalro.simulation.simulation.dto.SimulationDtos.SimulationFailureDetailResponse;
import com.hwalro.simulation.simulation.dto.SimulationDtos.SimulationOverviewPageResponse;
import com.hwalro.simulation.simulation.dto.SimulationDtos.SimulationOverviewResponse;
import com.hwalro.simulation.simulation.dto.SimulationDtos.SimulationSetupResponse;
import com.hwalro.simulation.simulation.dto.SimulationDtos.SimulationSummaryResponse;
import com.hwalro.simulation.simulation.dto.SimulationDtos.SimulationWorkSummaryResponse;
import com.hwalro.simulation.simulation.dto.SimulationDtos.TextDto;
import com.hwalro.simulation.simulation.engine.SimulationEngineRunner;
import com.hwalro.simulation.simulation.exception.InvalidSimulationGeometryException;
import com.hwalro.simulation.simulation.exception.SimulationConflictException;
import com.hwalro.simulation.simulation.exception.SimulationNotFoundException;
import com.hwalro.simulation.simulation.mapper.SimulationMapper;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

@Service
public class SimulationService {
    private static final String LAYOUT_STATUS_DRAFT = "초안";
    private static final String LAYOUT_STATUS_LOCKED = "잠금";
    private static final String SIMULATION_STATUS_DRAFT = "DRAFT";
    private static final String SIMULATION_STATUS_FAILED = "FAILED";
    private static final String ROUTING_ERROR_CODE = "AGENT_ROUTE_UNREACHABLE";
    private static final String MODEL_PROFILE = "SFM_DEFAULT_V2";
    private static final String ROUTING_PROFILE = "HAZARD_RADIAL_EXP_V3";
    private static final BigDecimal DEFAULT_WALKING_SPEED = BigDecimal.valueOf(1.25);
    private static final BigDecimal DEFAULT_REACTION_TIME = BigDecimal.valueOf(0.5);
    private static final BigDecimal DEFAULT_INITIAL_RESPONSE_TIME = BigDecimal.ZERO;
    private static final BigDecimal MAX_INITIAL_RESPONSE_TIME = BigDecimal.valueOf(600.0);
    private static final BigDecimal MAX_WALKING_SPEED = BigDecimal.valueOf(3.0);
    private static final BigDecimal MAX_COORDINATE = BigDecimal.valueOf(1_000_000);
    private static final String ROLE_ADMIN = "ADMIN";
    private static final String ROLE_OPERATOR = "OPERATOR";
    private static final String ROLE_REVIEWER = "SAFETY_REVIEWER";
    private static final int MAX_PAGE_SIZE = 100;
    private static final int MAX_PAGE = 100_000;
    private static final int MAX_TITLE_LENGTH = 200;

    private final SimulationMapper simulationMapper;
    private final DrawingMapper drawingMapper;
    private final ObjectMapper objectMapper;
    private final RegulationUsageClient regulationUsageClient;
    private final TransactionTemplate transactionTemplate;

    public SimulationService(
            SimulationMapper simulationMapper,
            DrawingMapper drawingMapper,
            ObjectMapper objectMapper,
            RegulationUsageClient regulationUsageClient,
            TransactionTemplate transactionTemplate) {
        this.simulationMapper = simulationMapper;
        this.drawingMapper = drawingMapper;
        this.objectMapper = objectMapper;
        this.regulationUsageClient = regulationUsageClient;
        this.transactionTemplate = transactionTemplate;
    }

    public List<SimulationSummaryResponse> list(Long layoutVersionId, JwtUser user) {
        LayoutSimulationContext context = findLayoutContext(layoutVersionId);
        requireAccessible(context.getCreatedBy(), user);
        Long createdBy = canSeeAll(user.roles()) ? null : user.userId();
        return simulationMapper.findSimulationsByLayoutVersion(layoutVersionId, createdBy).stream()
                .map(simulation -> new SimulationSummaryResponse(
                        simulation.getId(),
                        simulation.getLayoutVersionId(),
                        simulation.getParentSimulationId(),
                        simulation.getTitle(),
                        simulation.getStatus(),
                        simulation.getCreatedAt(),
                        simulation.getTotalPeople(),
                        simulation.getIsImprovement()))
                .toList();
    }

    public SimulationOverviewPageResponse listOverview(int page, int size, JwtUser user) {
        return listOverview(page, size, null, user);
    }

    public SimulationOverviewPageResponse listOverview(int page, int size, String query, JwtUser user) {
        if (page < 1 || page > MAX_PAGE || size < 1 || size > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("page는 1 이상, size는 1~100이어야 합니다.");
        }
        String normalizedQuery = StringUtils.hasText(query) ? query.trim() : null;
        Long createdBy = canSeeAll(user.roles()) ? null : user.userId();
        long totalCount = simulationMapper.countSimulationOverview(createdBy, normalizedQuery);
        List<SimulationOverviewResponse> items =
                simulationMapper
                        .findSimulationOverviewPage((page - 1) * size, size, createdBy, normalizedQuery)
                        .stream()
                        .map(SimulationService::toOverviewResponse)
                        .toList();
        return new SimulationOverviewPageResponse(
                Math.toIntExact(totalCount), page, size, page * size < totalCount, items);
    }

    public List<SimulationOverviewResponse> listMonitor(JwtUser user) {
        return simulationMapper.findSimulationMonitor(user.userId()).stream()
                .map(SimulationService::toOverviewResponse)
                .toList();
    }

    public SimulationOverviewResponse getOverview(Long id, JwtUser user) {
        Simulation simulation = simulationMapper.findSimulationOverviewById(id);
        if (simulation == null) {
            throw new SimulationNotFoundException("시뮬레이션을 찾을 수 없습니다: " + id);
        }
        requireAccessible(simulation.getCreatedBy(), user);
        return toOverviewResponse(simulation);
    }

    public SimulationWorkSummaryResponse getWorkSummary(JwtUser user) {
        LocalDateTime weekStartUtc = LocalDate.now(ZoneId.of("Asia/Seoul"))
                .with(DayOfWeek.MONDAY)
                .atStartOfDay(ZoneId.of("Asia/Seoul"))
                .toInstant()
                .atZone(ZoneOffset.UTC)
                .toLocalDateTime();
        return new SimulationWorkSummaryResponse(
                Math.toIntExact(simulationMapper.countInProgress(user.userId())),
                Math.toIntExact(simulationMapper.countCompletedThisWeek(user.userId(), weekStartUtc)));
    }

    public void delete(Long id, JwtUser user, String authorization) {
        Simulation simulation = findSimulation(id);
        requireAccessible(simulation.getCreatedBy(), user);

        if ("REQUESTED".equals(simulation.getStatus()) || "RUNNING".equals(simulation.getStatus())) {
            throw new SimulationConflictException("진행 중인 시뮬레이션은 삭제할 수 없습니다.");
        }

        SimulationResult result = simulationMapper.findSimulationResult(id);
        if (result != null) {
            RegulationUsageClient.RegulationUsageResponse usage =
                    regulationUsageClient.checkUsage(result.getId(), authorization);
            if (usage.usedInReports()) {
                throw new SimulationConflictException("보고서에 연결된 시뮬레이션은 삭제할 수 없습니다.");
            }
        }

        transactionTemplate.executeWithoutResult(status -> {
            Simulation lockedSimulation = findSimulationForUpdate(id);
            if ("REQUESTED".equals(lockedSimulation.getStatus()) || "RUNNING".equals(lockedSimulation.getStatus())) {
                throw new SimulationConflictException("진행 중인 시뮬레이션은 삭제할 수 없습니다.");
            }

            if (simulationMapper.countBlockingImprovementReferences(id) > 0) {
                throw new SimulationConflictException("개선안의 원본으로 사용 중인 시뮬레이션은 삭제할 수 없습니다.");
            }

            if (simulationMapper.countChildSimulations(id) > 0) {
                throw new SimulationConflictException("파생된 시뮬레이션이 있어 삭제할 수 없습니다.");
            }

            simulationMapper.deleteSimulation(id);
            simulationMapper.unlockLayoutVersionIfNoSimulations(lockedSimulation.getLayoutVersionId());
        });
    }

    @Transactional
    public SimulationSetupResponse createDraft(DraftCreateRequest request, JwtUser user) {
        if (request == null || request.layoutVersionId() == null) {
            throw new IllegalArgumentException("layoutVersionId가 필요합니다.");
        }

        LayoutSimulationContext context = simulationMapper.findLayoutContextForUpdate(request.layoutVersionId());
        if (context == null) {
            throw new SimulationNotFoundException("도면 버전을 찾을 수 없습니다: " + request.layoutVersionId());
        }
        requireAccessible(context.getCreatedBy(), user);
        if (!LAYOUT_STATUS_DRAFT.equals(context.getLayoutVersionStatus())
                && !LAYOUT_STATUS_LOCKED.equals(context.getLayoutVersionStatus())) {
            throw new SimulationConflictException("초안 또는 잠금 상태의 도면 버전만 시뮬레이션에 사용할 수 있습니다.");
        }

        DrawingSnapshot drawing = loadDrawing(context);
        List<PointDto> boundary =
                SimulationGeometry.assembleBoundary(drawing.outsideWalls(), context.getWidth(), context.getHeight());
        List<PointDto> agents = List.of();
        if (request.parentSimulationId() != null) {
            Simulation parent = findSimulation(request.parentSimulationId());
            requireAccessible(parent.getCreatedBy(), user);
            if (!request.layoutVersionId().equals(parent.getLayoutVersionId())) {
                throw new SimulationConflictException("같은 도면 버전의 시뮬레이션 배치만 복사할 수 있습니다.");
            }
            agents = readAgentPositions(parent.getId());
            SimulationGeometry.validateSetup(
                    agents,
                    List.of(),
                    boundary,
                    drawing.walls(),
                    drawing.pillars(),
                    drawing.fabrics(),
                    drawing.exits());
        }

        String title = truncateTitle(
                StringUtils.hasText(request.title()) ? request.title().trim() : context.getTitle());

        Simulation simulation = new Simulation();
        simulation.setLayoutVersionId(request.layoutVersionId());
        simulation.setParentSimulationId(request.parentSimulationId());
        simulation.setCreatedBy(user.userId());
        simulation.setTitle(title);
        simulation.setStatus(SIMULATION_STATUS_DRAFT);
        simulationMapper.insertSimulation(simulation);

        SimulationOption option = new SimulationOption();
        option.setSimulationId(simulation.getId());
        option.setRandomSeed(ThreadLocalRandom.current().nextInt());
        option.setModelProfile(MODEL_PROFILE);
        option.setRoutingProfile(ROUTING_PROFILE);
        option.setTotalPeople(agents.size());
        option.setWalkingSpeed(DEFAULT_WALKING_SPEED);
        option.setReactionTime(DEFAULT_REACTION_TIME);
        option.setInitialResponseTimeStdDev(DEFAULT_INITIAL_RESPONSE_TIME);
        simulationMapper.insertSimulationOption(option);
        simulationMapper.insertInitialState(simulation.getId(), writeAgentPositions(agents));

        if (LAYOUT_STATUS_DRAFT.equals(context.getLayoutVersionStatus())
                && simulationMapper.lockLayoutVersion(request.layoutVersionId()) != 1) {
            throw new SimulationConflictException("도면 버전을 잠그지 못했습니다. 다시 시도해 주세요.");
        }
        return getSetupInternal(simulation.getId(), user);
    }

    @Transactional
    public SimulationSetupResponse createPlacementAdjustmentDraft(
            Long failedId, PlacementAdjustmentDraftRequest request, JwtUser user) {
        if (failedId == null || request == null || request.applyRecommendation() == null) {
            throw new IllegalArgumentException("failedId와 applyRecommendation이 필요합니다.");
        }

        Simulation source = findSimulationForUpdate(failedId);
        requireAccessible(source.getCreatedBy(), user);
        if (!SIMULATION_STATUS_FAILED.equals(source.getStatus())) {
            throw new SimulationConflictException("실패한 시뮬레이션에서만 배치 조정 초안을 만들 수 있습니다.");
        }

        List<PointDto> agents = readAgentPositions(source.getId());
        SimulationFailureDetailResponse failureDetail = readFailureDetail(source);
        if (!matchesFailureDetail(failureDetail, agents)) {
            throw new SimulationConflictException("배치 조정에 사용할 에이전트 오류 정보가 없습니다.");
        }

        SimulationOption sourceOption = simulationMapper.findSimulationOption(source.getId());
        if (sourceOption == null
                || sourceOption.getTotalPeople() == null
                || sourceOption.getTotalPeople() != agents.size()) {
            throw new IllegalStateException("원본 시뮬레이션 옵션과 에이전트 수가 일치하지 않습니다.");
        }
        List<HazardZone> sourceHazards = simulationMapper.findHazardZones(source.getId());
        List<Long> selectedExitIds = simulationMapper.findSelectedExitIds(source.getId());

        if (request.applyRecommendation()) {
            if (failureDetail.recommendedPosition() == null) {
                throw new InvalidSimulationGeometryException("적용할 수 있는 추천 좌표가 없습니다.");
            }
            agents = new ArrayList<>(agents);
            agents.set(Math.toIntExact(failureDetail.agentId() - 1), failureDetail.recommendedPosition());
        }
        LayoutSimulationContext context = findLayoutContext(source.getLayoutVersionId());
        DrawingSnapshot drawing = loadDrawing(context);
        List<PointDto> boundary =
                SimulationGeometry.assembleBoundary(drawing.outsideWalls(), context.getWidth(), context.getHeight());
        SimulationGeometry.validateSetup(
                agents,
                sourceHazards.stream()
                        .map(hazard -> new HazardZoneDto(
                                hazard.getId(), hazard.getCenterX(), hazard.getCenterY(), hazard.getRadius()))
                        .toList(),
                boundary,
                drawing.walls(),
                drawing.pillars(),
                drawing.fabrics(),
                drawing.exits());

        String baseTitle =
                truncateTitle(StringUtils.hasText(source.getTitle()) ? source.getTitle() : context.getTitle());

        Simulation draft = new Simulation();
        draft.setLayoutVersionId(source.getLayoutVersionId());
        draft.setParentSimulationId(source.getId());
        draft.setCreatedBy(user.userId());
        draft.setTitle(baseTitle);
        draft.setStatus(SIMULATION_STATUS_DRAFT);
        simulationMapper.insertSimulation(draft);

        SimulationOption copiedOption = new SimulationOption();
        copiedOption.setSimulationId(draft.getId());
        copiedOption.setRandomSeed(sourceOption.getRandomSeed());
        copiedOption.setModelProfile(sourceOption.getModelProfile());
        copiedOption.setRoutingProfile(sourceOption.getRoutingProfile());
        copiedOption.setTotalPeople(sourceOption.getTotalPeople());
        copiedOption.setWalkingSpeed(sourceOption.getWalkingSpeed());
        copiedOption.setReactionTime(DEFAULT_REACTION_TIME);
        copiedOption.setInitialResponseTimeStdDev(sourceOption.getInitialResponseTimeStdDev());
        simulationMapper.insertSimulationOption(copiedOption);
        simulationMapper.insertInitialState(draft.getId(), writeAgentPositions(agents));

        List<HazardZone> copiedHazards = sourceHazards.stream()
                .map(hazard -> new HazardZoneDto(null, hazard.getCenterX(), hazard.getCenterY(), hazard.getRadius()))
                .map(hazard -> toHazardZone(draft.getId(), hazard))
                .toList();
        if (!copiedHazards.isEmpty()) {
            simulationMapper.insertHazardZones(copiedHazards);
        }
        if (!selectedExitIds.isEmpty()) {
            simulationMapper.insertSimulationExits(draft.getId(), source.getLayoutVersionId(), selectedExitIds);
        }
        return getSetupInternal(draft.getId(), user);
    }

    public SimulationSetupResponse getSetup(Long id, JwtUser user) {
        return getSetupInternal(id, user);
    }

    Simulation getAccessibleSimulation(Long id, JwtUser user) {
        Simulation simulation = findSimulation(id);
        requireAccessible(simulation.getCreatedBy(), user);
        return simulation;
    }

    SimulationFailureDetailResponse readFailureDetail(Simulation simulation) {
        if (simulation.getFailureDetail() == null
                || simulation.getFailureDetail().isBlank()) {
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(simulation.getFailureDetail());
            if (root == null
                    || !root.isObject()
                    || !root.has("code")
                    || !root.get("code").isTextual()) {
                return null;
            }
            String code = root.get("code").textValue();
            if (ROUTING_ERROR_CODE.equals(code)) {
                return readAgentRouteUnreachableFailureDetail(root);
            }
            if (SimulationEngineRunner.NO_REACHABLE_EXIT_CODE.equals(code)) {
                return readNoReachableExitFailureDetail(root);
            }
            return null;
        } catch (IOException | RuntimeException exception) {
            return null;
        }
    }

    private static SimulationFailureDetailResponse readAgentRouteUnreachableFailureDetail(JsonNode root) {
        if (root.size() != 4
                || !root.has("agentId")
                || !root.has("currentPosition")
                || !root.has("recommendedPosition")
                || !root.get("agentId").isIntegralNumber()
                || !root.get("agentId").canConvertToLong()) {
            return null;
        }
        long agentId = root.get("agentId").longValue();
        PointDto currentPosition = readFailurePoint(root.get("currentPosition"));
        JsonNode recommendationNode = root.get("recommendedPosition");
        PointDto recommendation = recommendationNode.isNull() ? null : readFailurePoint(recommendationNode);
        if (agentId < 1
                || agentId > SimulationGeometry.MAX_AGENTS
                || currentPosition == null
                || (!recommendationNode.isNull() && recommendation == null)) {
            return null;
        }
        return new SimulationFailureDetailResponse(
                ROUTING_ERROR_CODE, agentId, currentPosition, recommendation, null, null, null, null);
    }

    private static SimulationFailureDetailResponse readNoReachableExitFailureDetail(JsonNode root) {
        if (root.size() != 7
                || !root.has("affectedAgentCount")
                || !root.has("representativeAgentIds")
                || !root.has("componentCount")
                || !root.has("selectedExitIds")
                || !root.has("reason")
                || !root.get("affectedAgentCount").isIntegralNumber()
                || !root.get("affectedAgentCount").canConvertToLong()
                || !root.get("componentCount").isIntegralNumber()
                || !root.get("componentCount").canConvertToLong()) {
            return null;
        }
        long affected = root.get("affectedAgentCount").longValue();
        long componentCount = root.get("componentCount").longValue();
        List<Long> representativeIds =
                readFailureIdList(root.get("representativeAgentIds"), SimulationGeometry.MAX_AGENTS);
        List<Long> selectedExitIds = readFailureIdList(root.get("selectedExitIds"), SimulationGeometry.MAX_AGENTS);
        if (affected < 1
                || affected > SimulationGeometry.MAX_AGENTS
                || componentCount < 1
                || componentCount > SimulationGeometry.MAX_AGENTS
                || representativeIds == null
                || representativeIds.isEmpty()
                || selectedExitIds == null
                || selectedExitIds.isEmpty()
                || !root.get("reason").isTextual()
                || !isSupportedNoReachableExitReason(root.get("reason").textValue())) {
            return null;
        }
        return new SimulationFailureDetailResponse(
                SimulationEngineRunner.NO_REACHABLE_EXIT_CODE,
                null,
                null,
                null,
                affected,
                representativeIds,
                selectedExitIds,
                root.get("reason").textValue());
    }

    private static boolean isSupportedNoReachableExitReason(String reason) {
        return "NO_EXIT_SEED_IN_OCCUPIED_COMPONENT".equals(reason);
    }

    private static List<Long> readFailureIdList(JsonNode node, int maximumValue) {
        if (node == null || !node.isArray() || node.size() < 1 || node.size() > maximumValue) {
            return null;
        }
        java.util.ArrayList<Long> values = new java.util.ArrayList<>(node.size());
        for (JsonNode item : node) {
            if (!item.isIntegralNumber() || !item.canConvertToLong()) {
                return null;
            }
            long value = item.longValue();
            if (value < 1 || value > maximumValue) {
                return null;
            }
            values.add(value);
        }
        return List.copyOf(values);
    }

    @Transactional
    public SimulationSetupResponse updateSetup(Long id, SetupUpdateRequest request, JwtUser user) {
        if (request == null
                || request.agentPositions() == null
                || request.hazardZones() == null
                || request.selectedExitIds() == null
                || request.walkingSpeed() == null
                || request.initialResponseTimeStdDev() == null) {
            throw new IllegalArgumentException("에이전트, 위험 구역, 출입구와 시뮬레이션 옵션이 모두 필요합니다.");
        }
        validateOptions(request.walkingSpeed(), request.initialResponseTimeStdDev());

        Simulation simulation = findSimulationForUpdate(id);
        requireAccessible(simulation.getCreatedBy(), user);
        if (!SIMULATION_STATUS_DRAFT.equals(simulation.getStatus())) {
            throw new SimulationConflictException("DRAFT 상태의 시뮬레이션만 수정할 수 있습니다.");
        }

        LayoutSimulationContext context = findLayoutContext(simulation.getLayoutVersionId());
        DrawingSnapshot drawing = loadDrawing(context);
        List<PointDto> boundary =
                SimulationGeometry.assembleBoundary(drawing.outsideWalls(), context.getWidth(), context.getHeight());
        validateSelectedExits(request.selectedExitIds(), drawing.exits());
        SimulationGeometry.validateSetup(
                request.agentPositions(),
                request.hazardZones(),
                boundary,
                drawing.walls(),
                drawing.pillars(),
                drawing.fabrics(),
                drawing.exits());

        if (request.title() != null) {
            String nextTitle = truncateTitle(
                    StringUtils.hasText(request.title()) ? request.title().trim() : context.getTitle());
            simulationMapper.updateSimulationTitle(id, nextTitle);
        }

        simulationMapper.updateSimulationOption(
                id, request.agentPositions().size(), request.walkingSpeed(), request.initialResponseTimeStdDev());
        simulationMapper.updateInitialState(id, writeAgentPositions(request.agentPositions()));
        simulationMapper.deleteHazardZones(id);
        simulationMapper.deleteSimulationExits(id);

        List<HazardZone> hazards = request.hazardZones().stream()
                .map(hazard -> toHazardZone(id, hazard))
                .toList();
        if (!hazards.isEmpty()) {
            simulationMapper.insertHazardZones(hazards);
        }
        if (!request.selectedExitIds().isEmpty()) {
            simulationMapper.insertSimulationExits(id, simulation.getLayoutVersionId(), request.selectedExitIds());
        }
        return getSetupInternal(id, user);
    }

    private SimulationSetupResponse getSetupInternal(Long id, JwtUser user) {
        Simulation simulation = findSimulation(id);
        requireAccessible(simulation.getCreatedBy(), user);
        return buildSetup(id, simulation);
    }

    /**
     * 시뮬레이션 없이 도면 버전의 기하만 조립한다. 대피 경로 미리보기가 합성 엔진 입력을 만들 때 쓴다.
     *
     * <p>{@code buildSetup}과 같은 조립 경로를 공유하므로 엔진이 보는 도면이 시뮬레이션과 어긋나지 않는다.
     */
    public DrawingGeometryDto layoutGeometry(Long layoutVersionId) {
        LayoutSimulationContext context = findLayoutContext(layoutVersionId);
        DrawingSnapshot snapshot = loadDrawing(context);
        return new DrawingGeometryDto(
                context.getLayoutId(),
                context.getTitle(),
                context.getWidth(),
                context.getHeight(),
                SimulationGeometry.assembleBoundary(snapshot.outsideWalls(), context.getWidth(), context.getHeight()),
                snapshot.walls().stream().map(SimulationService::toSegment).toList(),
                snapshot.pillars().stream().map(SimulationService::toRect).toList(),
                snapshot.fabrics().stream().map(SimulationService::toFabricRect).toList(),
                snapshot.layoutTexts().stream().map(SimulationService::toText).toList(),
                snapshot.exits().stream().map(SimulationService::toExit).toList());
    }

    private SimulationSetupResponse buildSetup(Long id, Simulation simulation) {
        LayoutSimulationContext context = findLayoutContext(simulation.getLayoutVersionId());
        DrawingSnapshot snapshot = loadDrawing(context);
        List<PointDto> boundary =
                SimulationGeometry.assembleBoundary(snapshot.outsideWalls(), context.getWidth(), context.getHeight());
        SimulationOption option = simulationMapper.findSimulationOption(id);
        if (option == null) {
            throw new IllegalStateException("시뮬레이션 옵션이 없습니다: " + id);
        }

        DrawingGeometryDto drawing = new DrawingGeometryDto(
                context.getLayoutId(),
                context.getTitle(),
                context.getWidth(),
                context.getHeight(),
                boundary,
                snapshot.walls().stream().map(SimulationService::toSegment).toList(),
                snapshot.pillars().stream().map(SimulationService::toRect).toList(),
                snapshot.fabrics().stream().map(SimulationService::toFabricRect).toList(),
                snapshot.layoutTexts().stream().map(SimulationService::toText).toList(),
                snapshot.exits().stream().map(SimulationService::toExit).toList());

        return new SimulationSetupResponse(
                simulation.getId(),
                simulation.getLayoutVersionId(),
                simulation.getParentSimulationId(),
                simulation.getTitle(),
                simulation.getStatus(),
                simulation.getCreatedAt(),
                option.getRandomSeed(),
                option.getModelProfile(),
                option.getRoutingProfile(),
                option.getTotalPeople(),
                option.getWalkingSpeed(),
                option.getInitialResponseTimeStdDev(),
                readAgentPositions(id),
                simulationMapper.findHazardZones(id).stream()
                        .map(hazard -> new HazardZoneDto(
                                hazard.getId(), hazard.getCenterX(), hazard.getCenterY(), hazard.getRadius()))
                        .toList(),
                simulationMapper.findSelectedExitIds(id),
                drawing,
                simulation.getIsImprovement());
    }

    private DrawingSnapshot loadDrawing(LayoutSimulationContext context) {
        Long versionId = context.getLayoutVersionId();
        return new DrawingSnapshot(
                drawingMapper.findWallsByVersionId(versionId),
                drawingMapper.findOutsideWallsByVersionId(versionId),
                drawingMapper.findPillarsByVersionId(versionId),
                drawingMapper.findFabricsByVersionId(versionId),
                drawingMapper.findLayoutTextsByVersionId(versionId),
                drawingMapper.findLayoutExitsByVersionId(versionId));
    }

    private void validateSelectedExits(List<Long> selectedExitIds, List<LayoutExit> exits) {
        if (selectedExitIds.stream().anyMatch(id -> id == null)) {
            throw new IllegalArgumentException("선택한 출입구 ID가 올바르지 않습니다.");
        }
        Set<Long> unique = new HashSet<>(selectedExitIds);
        if (unique.size() != selectedExitIds.size()) {
            throw new IllegalArgumentException("같은 출입구를 중복 선택할 수 없습니다.");
        }
        Set<Long> available = exits.stream().map(LayoutExit::getId).collect(java.util.stream.Collectors.toSet());
        if (!available.containsAll(unique)) {
            throw new SimulationConflictException("현재 도면 버전에 속하지 않은 출입구가 선택되었습니다.");
        }
    }

    private void validateOptions(BigDecimal walkingSpeed, BigDecimal initialResponseTimeStdDev) {
        if (walkingSpeed.signum() <= 0 || walkingSpeed.compareTo(MAX_WALKING_SPEED) > 0) {
            throw new IllegalArgumentException("보행 속도는 0보다 크고 3m/s 이하여야 합니다.");
        }
        if (initialResponseTimeStdDev.signum() < 0
                || initialResponseTimeStdDev.compareTo(MAX_INITIAL_RESPONSE_TIME) > 0) {
            throw new IllegalArgumentException("출발시간 표준편차는 0초 이상 600초 이하여야 합니다.");
        }
    }

    private LayoutSimulationContext findLayoutContext(Long layoutVersionId) {
        if (layoutVersionId == null) {
            throw new IllegalArgumentException("layoutVersionId가 필요합니다.");
        }
        LayoutSimulationContext context = simulationMapper.findLayoutContext(layoutVersionId);
        if (context == null) {
            throw new SimulationNotFoundException("도면 버전을 찾을 수 없습니다: " + layoutVersionId);
        }
        return context;
    }

    private Simulation findSimulation(Long id) {
        Simulation simulation = simulationMapper.findSimulationById(id);
        if (simulation == null) {
            throw new SimulationNotFoundException("시뮬레이션을 찾을 수 없습니다: " + id);
        }
        return simulation;
    }

    private Simulation findSimulationForUpdate(Long id) {
        Simulation simulation = simulationMapper.findSimulationByIdForUpdate(id);
        if (simulation == null) {
            throw new SimulationNotFoundException("시뮬레이션을 찾을 수 없습니다: " + id);
        }
        return simulation;
    }

    private boolean canSeeAll(Set<String> roles) {
        return roles.contains(ROLE_ADMIN) || roles.contains(ROLE_REVIEWER);
    }

    private void requireAccessible(Long createdBy, JwtUser user) {
        if (canSeeAll(user.roles())) {
            return;
        }
        if (user.roles().contains(ROLE_OPERATOR) && user.userId().equals(createdBy)) {
            return;
        }
        throw new ForbiddenException("시뮬레이션에 접근할 권한이 없습니다.");
    }

    private static boolean matchesFailureDetail(SimulationFailureDetailResponse failureDetail, List<PointDto> agents) {
        if (failureDetail == null
                || failureDetail.agentId() == null
                || failureDetail.agentId() < 1
                || failureDetail.agentId() > agents.size()) {
            return false;
        }
        PointDto stored = agents.get(Math.toIntExact(failureDetail.agentId() - 1));
        return stored.x().compareTo(failureDetail.currentPosition().x()) == 0
                && stored.y().compareTo(failureDetail.currentPosition().y()) == 0;
    }

    private static PointDto readFailurePoint(JsonNode node) {
        if (!node.isObject() || node.size() != 2 || !node.has("x") || !node.has("y")) {
            return null;
        }
        JsonNode xNode = node.get("x");
        JsonNode yNode = node.get("y");
        if (!xNode.isNumber()
                || !yNode.isNumber()
                || !Double.isFinite(xNode.doubleValue())
                || !Double.isFinite(yNode.doubleValue())) {
            return null;
        }
        BigDecimal x = xNode.decimalValue();
        BigDecimal y = yNode.decimalValue();
        if (x.abs().compareTo(MAX_COORDINATE) > 0 || y.abs().compareTo(MAX_COORDINATE) > 0) {
            return null;
        }
        return new PointDto(x, y);
    }

    private String writeAgentPositions(List<PointDto> positions) {
        return AgentPositions.write(objectMapper, positions);
    }

    private List<PointDto> readAgentPositions(Long simulationId) {
        String json = simulationMapper.findInitialStateJson(simulationId);
        if (json == null) {
            throw new IllegalStateException("시뮬레이션 초기 좌표가 없습니다: " + simulationId);
        }
        return AgentPositions.read(objectMapper, json);
    }

    private static HazardZone toHazardZone(Long simulationId, HazardZoneDto dto) {
        HazardZone hazard = new HazardZone();
        hazard.setSimulationId(simulationId);
        hazard.setCenterX(dto.centerX());
        hazard.setCenterY(dto.centerY());
        hazard.setRadius(dto.radius());
        return hazard;
    }

    private static String truncateTitle(String title) {
        if (title == null || title.length() <= MAX_TITLE_LENGTH) {
            return title;
        }
        String truncated = title.substring(0, MAX_TITLE_LENGTH);
        if (Character.isHighSurrogate(truncated.charAt(truncated.length() - 1))) {
            truncated = truncated.substring(0, truncated.length() - 1);
        }
        return truncated;
    }

    private static SegmentDto toSegment(Wall wall) {
        return new SegmentDto(wall.getName(), wall.getStartX(), wall.getStartY(), wall.getEndX(), wall.getEndY());
    }

    private static RectDto toRect(Pillar pillar) {
        return new RectDto(
                pillar.getName(),
                pillar.getStartX(),
                pillar.getStartY(),
                pillar.getEndX(),
                pillar.getEndY(),
                pillar.getRotation());
    }

    private static FabricRectDto toFabricRect(Fabric fabric) {
        return new FabricRectDto(
                fabric.getId(),
                fabric.getName(),
                fabric.getStartX(),
                fabric.getStartY(),
                fabric.getEndX(),
                fabric.getEndY(),
                fabric.getRotation());
    }

    private static TextDto toText(LayoutText text) {
        return new TextDto(text.getText(), text.getX(), text.getY());
    }

    private static ExitDto toExit(LayoutExit exit) {
        return new ExitDto(
                exit.getId(), exit.getName(), exit.getStartX(), exit.getStartY(), exit.getEndX(), exit.getEndY());
    }

    private static SimulationOverviewResponse toOverviewResponse(Simulation simulation) {
        return new SimulationOverviewResponse(
                simulation.getId(),
                simulation.getLayoutVersionId(),
                simulation.getLayoutId(),
                simulation.getLayoutTitle(),
                simulation.getLayoutVersionNumber(),
                simulation.getCreatedBy(),
                simulation.getTitle(),
                simulation.getStatus(),
                simulation.getCreatedAt(),
                simulation.getRequestedAt(),
                simulation.getStartedAt(),
                simulation.getFinishedAt(),
                simulation.getTotalPeople(),
                simulation.getTerminationReason(),
                simulation.getIsImprovement(),
                simulation.getHasLayoutSearch());
    }

    private record DrawingSnapshot(
            List<Wall> walls,
            List<OutsideWall> outsideWalls,
            List<Pillar> pillars,
            List<Fabric> fabrics,
            List<LayoutText> layoutTexts,
            List<LayoutExit> exits) {}
}
