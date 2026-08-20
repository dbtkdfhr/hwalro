package com.hwalro.simulation.search.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hwalro.simulation.common.jwt.JwtUser;
import com.hwalro.simulation.drawing.domain.Fabric;
import com.hwalro.simulation.drawing.domain.LayoutExit;
import com.hwalro.simulation.drawing.domain.LayoutVersion;
import com.hwalro.simulation.drawing.domain.OutsideWall;
import com.hwalro.simulation.drawing.domain.Pillar;
import com.hwalro.simulation.drawing.domain.Wall;
import com.hwalro.simulation.drawing.mapper.DrawingMapper;
import com.hwalro.simulation.search.domain.CandidateStatus;
import com.hwalro.simulation.search.domain.ChangeOp;
import com.hwalro.simulation.search.domain.ChangeSet;
import com.hwalro.simulation.search.domain.LayoutSearchCandidateEntity;
import com.hwalro.simulation.search.domain.LayoutSearchEntity;
import com.hwalro.simulation.search.dto.LayoutSearchDtos.PreparedSimulationDto;
import com.hwalro.simulation.search.mapper.LayoutSearchMapper;
import com.hwalro.simulation.simulation.domain.HazardZone;
import com.hwalro.simulation.simulation.domain.LayoutSimulationContext;
import com.hwalro.simulation.simulation.domain.Simulation;
import com.hwalro.simulation.simulation.domain.SimulationOption;
import com.hwalro.simulation.simulation.dto.SimulationDtos.HazardZoneDto;
import com.hwalro.simulation.simulation.dto.SimulationDtos.PointDto;
import com.hwalro.simulation.simulation.exception.SimulationConflictException;
import com.hwalro.simulation.simulation.exception.SimulationNotFoundException;
import com.hwalro.simulation.simulation.mapper.SimulationMapper;
import com.hwalro.simulation.simulation.service.AgentPositions;
import com.hwalro.simulation.simulation.service.SimulationGeometry;
import com.hwalro.simulation.simulation.service.SimulationService;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class CandidateAdoptionService {
    private static final String LOCKED_LAYOUT_STATUS = "잠금";
    private static final String MOVE_FABRIC = "MOVE_FABRIC";
    private static final Set<String> PREPARABLE_STATUSES =
            Set.of(CandidateStatus.EVALUATED.name(), CandidateStatus.QUEUED.name());

    private final LayoutSearchMapper layoutStudyMapper;
    private final DrawingMapper drawingMapper;
    private final SimulationMapper simulationMapper;
    private final SimulationService simulationService;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;

    public CandidateAdoptionService(
            LayoutSearchMapper layoutStudyMapper,
            DrawingMapper drawingMapper,
            SimulationMapper simulationMapper,
            SimulationService simulationService,
            ObjectMapper objectMapper,
            TransactionTemplate transactionTemplate) {
        this.layoutStudyMapper = layoutStudyMapper;
        this.drawingMapper = drawingMapper;
        this.simulationMapper = simulationMapper;
        this.simulationService = simulationService;
        this.objectMapper = objectMapper;
        this.transactionTemplate = transactionTemplate;
    }

    public PreparedSimulationDto prepare(long studyId, long candidateId, JwtUser user) {
        LayoutSearchEntity study = requireStudy(studyId);
        simulationService.getSetup(study.getBaselineSimulationId(), user);

        return transactionTemplate.execute(status -> {
            LayoutSearchCandidateEntity candidate = requireCandidateForUpdate(candidateId);
            if (!candidate.getStudyId().equals(studyId)) {
                throw new SimulationNotFoundException("후보가 해당 탐색에 속하지 않습니다.");
            }
            if (candidate.getPreparedSimulationId() != null) {
                return preparedSimulation(candidate.getPreparedSimulationId());
            }
            // QUEUED는 확인하지 않는 탐색이 내놓은 후보다. 그런 탐색에서는 사용자가 직접 고른 후보를
            // 돌려보는 것이 흐름 자체이므로, 실측을 준비의 전제로 둘 수 없다.
            if (!PREPARABLE_STATUSES.contains(candidate.getStatus())) {
                throw new SimulationConflictException("탐색이 제안한 후보만 시뮬레이션으로 준비할 수 있습니다.");
            }
            Long targetVersionId = candidate.getAdoptedLayoutVersionId() == null
                    ? createAdoptedLayout(study.getBaselineLayoutVersionId(), candidate)
                    : candidate.getAdoptedLayoutVersionId();
            Long draftSimulationId =
                    createDraftSimulation(study.getBaselineSimulationId(), targetVersionId, user.userId());
            layoutStudyMapper.markCandidatePrepared(
                    candidateId, targetVersionId, draftSimulationId, LocalDateTime.now());
            return new PreparedSimulationDto(draftSimulationId, "DRAFT");
        });
    }

    private PreparedSimulationDto preparedSimulation(Long simulationId) {
        Simulation simulation = simulationMapper.findSimulationById(simulationId);
        if (simulation == null) {
            throw new IllegalStateException("준비된 시뮬레이션을 찾을 수 없습니다: " + simulationId);
        }
        return new PreparedSimulationDto(simulationId, simulation.getStatus());
    }

    private Long createAdoptedLayout(Long sourceVersionId, LayoutSearchCandidateEntity candidate) {
        LayoutVersion sourceVersion = drawingMapper.findLayoutVersionById(sourceVersionId);
        if (sourceVersion == null || drawingMapper.lockLayout(sourceVersion.getLayoutId()) == null) {
            throw new IllegalArgumentException("원본 배치 버전을 찾을 수 없습니다.");
        }

        LayoutVersion targetVersion = new LayoutVersion();
        targetVersion.setLayoutId(sourceVersion.getLayoutId());
        targetVersion.setVersion(drawingMapper.findNextLayoutVersionNumber(sourceVersion.getLayoutId()));
        targetVersion.setStatus(LOCKED_LAYOUT_STATUS);
        targetVersion.setOptimisticLock(0);
        drawingMapper.insertLayoutVersion(targetVersion);

        drawingMapper.copyWalls(sourceVersionId, targetVersion.getId());
        drawingMapper.copyPillars(sourceVersionId, targetVersion.getId());
        drawingMapper.copyOutsideWalls(sourceVersionId, targetVersion.getId());
        drawingMapper.copyLayoutTexts(sourceVersionId, targetVersion.getId());
        drawingMapper.insertFabrics(changedFabrics(candidate, sourceVersionId, targetVersion.getId()));
        copyExits(sourceVersionId, targetVersion.getId());
        return targetVersion.getId();
    }

    List<Fabric> changedFabrics(LayoutSearchCandidateEntity candidate, Long sourceVersionId, Long targetVersionId) {
        Map<Long, Fabric> copiedBySourceId = new LinkedHashMap<>();
        List<Fabric> copied = drawingMapper.findFabricsByVersionId(sourceVersionId).stream()
                .map(source -> {
                    Fabric target = copyFabric(source, targetVersionId);
                    copiedBySourceId.put(source.getId(), target);
                    return target;
                })
                .toList();
        ChangeSet changeSet = readChangeSet(candidate.getChangeSet());
        for (ChangeOp op : changeSet.ops()) {
            if (!MOVE_FABRIC.equals(op.type())) {
                throw new IllegalArgumentException("지원하지 않는 변경 연산입니다: " + op.type());
            }
            if (op.fabricId() == null
                    || op.before() == null
                    || op.after() == null
                    || !isValidTransform(op.before())
                    || !isValidTransform(op.after())) {
                throw new IllegalArgumentException("MOVE_FABRIC에는 fabricId, before, after가 모두 필요합니다.");
            }
            Fabric target = copiedBySourceId.get(op.fabricId());
            if (target == null) {
                throw new IllegalArgumentException("변경 대상 fabric을 찾을 수 없습니다: fabricId=" + op.fabricId());
            }
            if (!sameTransform(target, op.before())) {
                throw new IllegalArgumentException("변경 연산의 before 연결이 올바르지 않습니다: fabricId=" + op.fabricId());
            }
            apply(target, op.after());
        }
        return copied;
    }

    private void apply(Fabric fabric, ChangeOp.FabricTransform transform) {
        if (!isValidTransform(transform)) {
            throw new IllegalArgumentException("채택 후보의 구조물 좌표가 올바르지 않습니다.");
        }
        fabric.setStartX(transform.startX());
        fabric.setStartY(transform.startY());
        fabric.setEndX(transform.endX());
        fabric.setEndY(transform.endY());
        fabric.setRotation(transform.rotation());
    }

    private boolean isValidTransform(ChangeOp.FabricTransform transform) {
        return transform.startX() != null
                && transform.startY() != null
                && transform.endX() != null
                && transform.endY() != null
                && transform.rotation() != null
                && transform.startX().compareTo(transform.endX()) < 0
                && transform.startY().compareTo(transform.endY()) < 0;
    }

    private boolean sameTransform(Fabric fabric, ChangeOp.FabricTransform transform) {
        return fabric.getStartX().compareTo(transform.startX()) == 0
                && fabric.getStartY().compareTo(transform.startY()) == 0
                && fabric.getEndX().compareTo(transform.endX()) == 0
                && fabric.getEndY().compareTo(transform.endY()) == 0
                && fabric.getRotation().compareTo(transform.rotation()) == 0;
    }

    private void copyExits(Long sourceVersionId, Long targetVersionId) {
        for (LayoutExit source : drawingMapper.findLayoutExitsByVersionId(sourceVersionId)) {
            drawingMapper.insertLayoutExit(copyExit(source, targetVersionId));
        }
    }

    private Long createDraftSimulation(Long sourceSimulationId, Long targetVersionId, long requestedBy) {
        Simulation source = simulationMapper.findSimulationById(sourceSimulationId);
        if (source == null) {
            throw new IllegalArgumentException("기준 시뮬레이션을 찾을 수 없습니다.");
        }
        SimulationOption sourceOption = simulationMapper.findSimulationOption(source.getId());
        String initialState = simulationMapper.findInitialStateJson(source.getId());
        if (sourceOption == null || initialState == null) {
            throw new IllegalArgumentException("기준 시뮬레이션 설정을 찾을 수 없습니다.");
        }

        List<HazardZone> sourceHazards = simulationMapper.findHazardZones(source.getId());

        Simulation derived = new Simulation();
        derived.setLayoutVersionId(targetVersionId);
        derived.setParentSimulationId(source.getId());
        derived.setCreatedBy(requestedBy);
        derived.setTitle(source.getTitle() != null && !source.getTitle().isBlank() ? source.getTitle() : "개선안 시뮬레이션");
        derived.setStatus("DRAFT");
        simulationMapper.insertSimulation(derived);

        SimulationOption option = copyOption(sourceOption, derived.getId());
        simulationMapper.insertSimulationOption(option);
        simulationMapper.insertInitialState(
                derived.getId(), relocateAgents(targetVersionId, initialState, sourceHazards));

        List<HazardZone> hazards = sourceHazards.stream()
                .map(sourceHazard -> copyHazard(sourceHazard, derived.getId()))
                .toList();
        if (!hazards.isEmpty()) {
            simulationMapper.insertHazardZones(hazards);
        }

        List<Long> selectedExits = mapSelectedExits(
                source.getLayoutVersionId(),
                targetVersionId,
                new HashSet<>(simulationMapper.findSelectedExitIds(source.getId())));
        if (!selectedExits.isEmpty()) {
            simulationMapper.insertSimulationExits(derived.getId(), targetVersionId, selectedExits);
        }
        return derived.getId();
    }

    /**
     * 채택한 배치에서는 구조물이 옮겨졌으므로, 원본 시뮬레이션의 에이전트 좌표를 그대로 쓰면
     * 구조물 안에 사람이 박힌 초안이 만들어진다. 새 도면 기준으로 밀어낸 좌표를 저장한다.
     *
     * <p>재배치는 여기서 한 번만 일어나고, 결과를 저장 직전에 {@code validateSetup}으로 다시 확인한다.
     * 어느 쪽이든 실패하면 {@code prepare}의 트랜잭션이 통째로 롤백되어 잘못된 초안이 남지 않는다.
     */
    private String relocateAgents(Long targetVersionId, String initialState, List<HazardZone> hazards) {
        LayoutSimulationContext context = simulationMapper.findLayoutContext(targetVersionId);
        if (context == null) {
            throw new IllegalStateException("채택한 배치 버전을 찾을 수 없습니다: " + targetVersionId);
        }
        List<Wall> walls = drawingMapper.findWallsByVersionId(targetVersionId);
        List<OutsideWall> outsideWalls = drawingMapper.findOutsideWallsByVersionId(targetVersionId);
        List<Pillar> pillars = drawingMapper.findPillarsByVersionId(targetVersionId);
        List<Fabric> fabrics = drawingMapper.findFabricsByVersionId(targetVersionId);
        List<LayoutExit> exits = drawingMapper.findLayoutExitsByVersionId(targetVersionId);
        List<PointDto> boundary =
                SimulationGeometry.assembleBoundary(outsideWalls, context.getWidth(), context.getHeight());

        List<PointDto> agents = AgentPositions.read(objectMapper, initialState);
        List<PointDto> relaxed = SimulationGeometry.relaxAgents(agents, boundary, walls, pillars, fabrics, exits);

        List<HazardZoneDto> hazardDtos = hazards.stream()
                .map(hazard ->
                        new HazardZoneDto(hazard.getId(), hazard.getCenterX(), hazard.getCenterY(), hazard.getRadius()))
                .toList();
        SimulationGeometry.validateSetup(relaxed, hazardDtos, boundary, walls, pillars, fabrics, exits);

        return AgentPositions.write(objectMapper, relaxed);
    }

    private List<Long> mapSelectedExits(Long sourceVersionId, Long targetVersionId, Set<Long> selectedSourceIds) {
        if (selectedSourceIds.isEmpty()) {
            return List.of();
        }
        List<LayoutExit> targets = drawingMapper.findLayoutExitsByVersionId(targetVersionId);
        List<Long> selectedTargets = new ArrayList<>();
        for (LayoutExit source : drawingMapper.findLayoutExitsByVersionId(sourceVersionId)) {
            if (!selectedSourceIds.contains(source.getId())) {
                continue;
            }
            LayoutExit target = targets.stream()
                    .filter(candidate -> sameExit(source, candidate))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("복제된 출구를 찾을 수 없습니다."));
            selectedTargets.add(target.getId());
        }
        if (selectedTargets.size() != selectedSourceIds.size()) {
            throw new IllegalStateException("선택 출구를 새 배치 버전에 연결하지 못했습니다.");
        }
        return List.copyOf(selectedTargets);
    }

    private boolean sameExit(LayoutExit left, LayoutExit right) {
        return left.getName().equals(right.getName())
                && left.getStartX().compareTo(right.getStartX()) == 0
                && left.getStartY().compareTo(right.getStartY()) == 0
                && left.getEndX().compareTo(right.getEndX()) == 0
                && left.getEndY().compareTo(right.getEndY()) == 0;
    }

    private Fabric copyFabric(Fabric source, Long targetVersionId) {
        Fabric target = new Fabric();
        target.setLayoutVersionId(targetVersionId);
        target.setName(source.getName());
        target.setStartX(source.getStartX());
        target.setStartY(source.getStartY());
        target.setEndX(source.getEndX());
        target.setEndY(source.getEndY());
        target.setRotation(source.getRotation());
        return target;
    }

    private LayoutExit copyExit(LayoutExit source, Long targetVersionId) {
        LayoutExit target = new LayoutExit();
        target.setLayoutVersionId(targetVersionId);
        target.setName(source.getName());
        target.setStartX(source.getStartX());
        target.setStartY(source.getStartY());
        target.setEndX(source.getEndX());
        target.setEndY(source.getEndY());
        return target;
    }

    private SimulationOption copyOption(SimulationOption source, Long simulationId) {
        SimulationOption target = new SimulationOption();
        target.setSimulationId(simulationId);
        target.setRandomSeed(source.getRandomSeed());
        target.setModelProfile(source.getModelProfile());
        target.setRoutingProfile(source.getRoutingProfile());
        target.setTotalPeople(source.getTotalPeople());
        target.setWalkingSpeed(source.getWalkingSpeed());
        target.setReactionTime(source.getReactionTime());
        return target;
    }

    private HazardZone copyHazard(HazardZone source, Long simulationId) {
        HazardZone target = new HazardZone();
        target.setSimulationId(simulationId);
        target.setCenterX(source.getCenterX());
        target.setCenterY(source.getCenterY());
        target.setRadius(source.getRadius());
        return target;
    }

    private ChangeSet readChangeSet(String json) {
        try {
            return objectMapper.readValue(json, ChangeSet.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("저장된 변경 집합을 읽지 못했습니다.", exception);
        }
    }

    private LayoutSearchCandidateEntity requireCandidateForUpdate(Long candidateId) {
        LayoutSearchCandidateEntity candidate = layoutStudyMapper.findCandidateByIdForUpdate(candidateId);
        if (candidate == null) {
            throw new SimulationNotFoundException("후보를 찾을 수 없습니다: " + candidateId);
        }
        return candidate;
    }

    private LayoutSearchEntity requireStudy(Long studyId) {
        LayoutSearchEntity study = layoutStudyMapper.findSearchById(studyId);
        if (study == null) {
            throw new SimulationNotFoundException("배치 개선안 탐색을 찾을 수 없습니다: " + studyId);
        }
        return study;
    }
}
