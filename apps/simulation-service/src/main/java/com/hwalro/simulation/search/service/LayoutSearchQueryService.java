package com.hwalro.simulation.search.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hwalro.simulation.common.jwt.JwtUser;
import com.hwalro.simulation.search.config.LayoutSearchProperties;
import com.hwalro.simulation.search.domain.CandidateStatus;
import com.hwalro.simulation.search.domain.ChangeSet;
import com.hwalro.simulation.search.domain.Diagnosis;
import com.hwalro.simulation.search.domain.Finding;
import com.hwalro.simulation.search.domain.LayoutSearchCandidateEntity;
import com.hwalro.simulation.search.domain.LayoutSearchEntity;
import com.hwalro.simulation.search.domain.LayoutSearchTrialEntity;
import com.hwalro.simulation.search.domain.Metric;
import com.hwalro.simulation.search.domain.MetricDelta;
import com.hwalro.simulation.search.domain.SearchBudget;
import com.hwalro.simulation.search.dto.LayoutSearchDtos.CandidateDto;
import com.hwalro.simulation.search.dto.LayoutSearchDtos.ChangeOpDto;
import com.hwalro.simulation.search.dto.LayoutSearchDtos.ChangeSetDto;
import com.hwalro.simulation.search.dto.LayoutSearchDtos.DiagnosisDto;
import com.hwalro.simulation.search.dto.LayoutSearchDtos.EvidenceDto;
import com.hwalro.simulation.search.dto.LayoutSearchDtos.FabricTransformDto;
import com.hwalro.simulation.search.dto.LayoutSearchDtos.FindingDto;
import com.hwalro.simulation.search.dto.LayoutSearchDtos.LayoutSearchResponse;
import com.hwalro.simulation.search.dto.LayoutSearchDtos.MetricDeltaDto;
import com.hwalro.simulation.search.dto.LayoutSearchDtos.MetricDto;
import com.hwalro.simulation.search.dto.LayoutSearchDtos.PreparedSimulationDto;
import com.hwalro.simulation.search.dto.LayoutSearchDtos.ProgressDto;
import com.hwalro.simulation.search.dto.LayoutSearchDtos.RationaleDto;
import com.hwalro.simulation.search.dto.LayoutSearchDtos.RegionDto;
import com.hwalro.simulation.search.dto.LayoutSearchMonitorItem;
import com.hwalro.simulation.search.mapper.LayoutSearchMapper;
import com.hwalro.simulation.simulation.domain.Simulation;
import com.hwalro.simulation.simulation.domain.SimulationMetric;
import com.hwalro.simulation.simulation.domain.SimulationResult;
import com.hwalro.simulation.simulation.exception.SimulationNotFoundException;
import com.hwalro.simulation.simulation.mapper.SimulationMapper;
import com.hwalro.simulation.simulation.service.SimulationService;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class LayoutSearchQueryService {
    private static final List<String> TERMINAL_STATUSES = List.of("COMPLETED", "NO_IMPROVEMENT", "FAILED", "CANCELLED");

    private final LayoutSearchMapper layoutSearchMapper;
    private final SimulationService simulationService;
    private final SimulationMapper simulationMapper;
    private final LayoutSearchProperties properties;
    private final ObjectMapper objectMapper;

    public LayoutSearchQueryService(
            LayoutSearchMapper layoutSearchMapper,
            SimulationService simulationService,
            SimulationMapper simulationMapper,
            LayoutSearchProperties properties,
            ObjectMapper objectMapper) {
        this.layoutSearchMapper = layoutSearchMapper;
        this.simulationService = simulationService;
        this.simulationMapper = simulationMapper;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public LayoutSearchResponse getLatest(long simulationId, JwtUser user) {
        simulationService.getSetup(simulationId, user);
        LayoutSearchEntity search = layoutSearchMapper.findLatestSearchByBaselineSimulationId(simulationId);
        if (search == null) {
            throw new SimulationNotFoundException("배치 개선안 탐색이 없습니다.");
        }
        return toResponse(search, user);
    }

    public LayoutSearchResponse getSearch(long searchId, JwtUser user) {
        LayoutSearchEntity search = requireSearch(searchId);
        simulationService.getSetup(search.getBaselineSimulationId(), user);
        return toResponse(search, user);
    }

    public List<LayoutSearchMonitorItem> getMonitor(JwtUser user) {
        return layoutSearchMapper.findMonitorItems(user.userId());
    }

    private LayoutSearchResponse toResponse(LayoutSearchEntity search, JwtUser user) {
        List<LayoutSearchCandidateEntity> candidates = layoutSearchMapper.findCandidatesBySearchId(search.getId());
        Map<Long, LayoutSearchTrialEntity> trials = layoutSearchMapper.findTrialsBySearchId(search.getId()).stream()
                .collect(Collectors.toMap(LayoutSearchTrialEntity::getCandidateId, trial -> trial));
        List<Metric> baselineMetrics = readMetrics(search.getBaselineMetrics());
        SearchBudget budget = readBudget(search.getBudget());
        long baselineRunSeconds = baselineRunSeconds(search.getBaselineSimulationId());

        // 확인한 탐색은 실측 개선이 확인된 후보만 제안하고, 실측 델타로 순위를 매긴다. 확인하지 않은
        // 탐색은 아직 QUEUED인 후보를 그대로 제안하고, 엔진이 이미 매겨 둔 순서(candidate_order)를 쓴다 -
        // 잴 것이 없으니 다시 매길 것도 없다.
        boolean verified = budget.verifies();
        String proposedStatus = verified ? CandidateStatus.EVALUATED.name() : CandidateStatus.QUEUED.name();
        List<CandidateDto> improved = new ArrayList<>();
        List<CandidateDto> rejected = new ArrayList<>();
        List<CandidateSelector.RankableCandidate> rankable = new ArrayList<>();
        for (LayoutSearchCandidateEntity candidate : candidates) {
            CandidateDto dto = toCandidate(candidate, trials.get(candidate.getId()), baselineMetrics);
            if (proposedStatus.equals(candidate.getStatus())) {
                improved.add(dto);
                rankable.add(new CandidateSelector.RankableCandidate(
                        candidate.getId(), List.of(), readDeltas(candidate.getMetricDelta()), opsCount(candidate)));
            } else {
                rejected.add(dto);
            }
        }
        if (verified) {
            List<Long> rankedIds = rankable.stream()
                    .sorted(CandidateSelector.rankingComparator(baselineMetrics))
                    .map(CandidateSelector.RankableCandidate::candidateId)
                    .toList();
            Map<Long, CandidateDto> improvedById =
                    improved.stream().collect(Collectors.toMap(CandidateDto::candidateId, dto -> dto));
            improved = rankedIds.stream().map(improvedById::get).toList();
        }

        boolean terminal = TERMINAL_STATUSES.contains(search.getStatus());
        int verifiedCount = (int) candidates.stream()
                .filter(candidate ->
                        List.of("EVALUATED", "NOT_IMPROVED", "FAILED").contains(candidate.getStatus()))
                .count();
        int generatedCount = (int) candidates.stream()
                .filter(candidate -> !CandidateStatus.REJECTED_CONSTRAINT.name().equals(candidate.getStatus()))
                .count();
        // 확인하지 않는 탐색은 시행을 돌리지 않으므로 남은 시행도 없다. 시행 예산을 그대로 두면 진행률이
        // 영원히 0/N으로 멈춰 있는 것처럼 보인다.
        Integer plannedCount = verified ? plannedCount(budget, terminal, candidates, generatedCount) : 0;
        int remaining = plannedCount == null ? 0 : Math.max(0, plannedCount - verifiedCount);
        Long estimatedRemaining = terminal
                ? 0L
                : plannedCount == null
                        ? null
                        : TrialBudgetCalculator.estimatedStudySeconds(
                                remaining,
                                properties.getTrialConcurrency(),
                                baselineRunSeconds,
                                properties.getAbortMargin());
        int round = candidates.stream()
                .mapToInt(LayoutSearchCandidateEntity::getRoundIndex)
                .max()
                .orElse(0);

        ProgressDto progress = new ProgressDto(
                verifiedCount, plannedCount, round, baselineRunSeconds, estimatedRemaining, budget.trialCapSeconds());

        return new LayoutSearchResponse(
                search.getId(),
                search.getBaselineSimulationId(),
                search.getBaselineLayoutVersionId(),
                search.getStatus(),
                search.getPlannerVersion(),
                progress,
                baselineMetrics.stream()
                        .map(LayoutSearchQueryService::toMetricDto)
                        .toList(),
                toDiagnosis(search.getDiagnosis()),
                improved,
                rejected,
                search.getFailureCode(),
                search.getFailureMessage());
    }

    static Integer plannedCount(
            SearchBudget budget, boolean terminal, List<LayoutSearchCandidateEntity> candidates, int generatedCount) {
        if (!"THOROUGH".equals(budget.preset())) {
            return budget.maxTrials();
        }
        int generatedRounds = candidates.stream()
                .mapToInt(LayoutSearchCandidateEntity::getRoundIndex)
                .max()
                .orElse(0);
        return !terminal && generatedRounds < budget.maxRounds() ? null : generatedCount;
    }

    private CandidateDto toCandidate(
            LayoutSearchCandidateEntity candidate, LayoutSearchTrialEntity trial, List<Metric> baselineMetrics) {
        List<Metric> measuredMetrics =
                trial == null || trial.getMetrics() == null ? null : readMetrics(trial.getMetrics());
        List<MetricDelta> deltas = readDeltas(candidate.getMetricDelta());

        if ((measuredMetrics == null || deltas.isEmpty()) && candidate.getPreparedSimulationId() != null) {
            SimulationResult preparedResult =
                    simulationMapper.findSimulationResult(candidate.getPreparedSimulationId());
            if (preparedResult != null) {
                List<SimulationMetric> simMetrics = simulationMapper.findSimulationMetrics(preparedResult.getId());
                if (simMetrics != null && !simMetrics.isEmpty()) {
                    List<Metric> preparedMetrics = simMetrics.stream()
                            .map(m -> new Metric(m.getMetricType(), m.getUnit(), m.getMetricValue()))
                            .toList();
                    if (measuredMetrics == null) {
                        measuredMetrics = preparedMetrics;
                    }
                    if (deltas.isEmpty()) {
                        deltas = CandidateSelector.deltas(preparedMetrics, baselineMetrics);
                    }
                }
            }
        }

        return new CandidateDto(
                candidate.getId(),
                candidate.getRoundIndex(),
                candidate.getOriginFindingType(),
                candidate.getOperatorType(),
                candidate.getStatus(),
                toRationale(candidate.getRationale()),
                toChangeSet(candidate.getChangeSet()),
                measuredMetrics == null
                        ? null
                        : measuredMetrics.stream()
                                .map(LayoutSearchQueryService::toMetricDto)
                                .toList(),
                deltas.stream().map(LayoutSearchQueryService::toDeltaDto).toList(),
                candidate.getRejectReason(),
                candidate.getPreparedSimulationId() == null
                        ? null
                        : new PreparedSimulationDto(
                                candidate.getPreparedSimulationId(), candidate.getPreparedSimulationStatus()));
    }

    private long baselineRunSeconds(long simulationId) {
        Simulation simulation = simulationMapper.findSimulationById(simulationId);
        if (simulation == null || simulation.getStartedAt() == null || simulation.getFinishedAt() == null) {
            return 0;
        }
        return Math.max(
                0,
                Duration.between(simulation.getStartedAt(), simulation.getFinishedAt())
                        .getSeconds());
    }

    private LayoutSearchEntity requireSearch(Long searchId) {
        LayoutSearchEntity search = layoutSearchMapper.findSearchById(searchId);
        if (search == null) {
            throw new SimulationNotFoundException("배치 개선안 탐색을 찾을 수 없습니다: " + searchId);
        }
        return search;
    }

    private RationaleDto toRationale(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, RationaleDto.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("저장된 후보 근거를 읽지 못했습니다.", exception);
        }
    }

    private ChangeSetDto toChangeSet(String json) {
        ChangeSet changeSet = readChangeSet(json);
        List<ChangeOpDto> ops = changeSet.ops().stream()
                .map(op -> new ChangeOpDto(op.type(), op.fabricId(), toTransform(op.before()), toTransform(op.after())))
                .toList();
        return new ChangeSetDto(changeSet.schemaVersion(), changeSet.coordinateUnit(), ops);
    }

    private FabricTransformDto toTransform(com.hwalro.simulation.search.domain.ChangeOp.FabricTransform transform) {
        if (transform == null) {
            return null;
        }
        return new FabricTransformDto(
                transform.startX(), transform.startY(), transform.endX(), transform.endY(), transform.rotation());
    }

    private DiagnosisDto toDiagnosis(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            Diagnosis diagnosis = objectMapper.readValue(json, Diagnosis.class);
            List<FindingDto> findings = diagnosis.findings().stream()
                    .map(LayoutSearchQueryService::toFindingDto)
                    .toList();
            return new DiagnosisDto(findings);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("저장된 진단을 읽지 못했습니다.", exception);
        }
    }

    private static FindingDto toFindingDto(Finding finding) {
        RegionDto region = finding.region() == null
                ? null
                : new RegionDto(
                        finding.region().startX(),
                        finding.region().startY(),
                        finding.region().endX(),
                        finding.region().endY());
        EvidenceDto evidence = finding.evidence() == null
                ? null
                : new EvidenceDto(
                        finding.evidence().metric(),
                        finding.evidence().value(),
                        finding.evidence().unit(),
                        finding.evidence().source());
        return new FindingDto(finding.type().name(), finding.severity(), region, evidence, finding.description());
    }

    private static MetricDto toMetricDto(Metric metric) {
        return new MetricDto(metric.metricType(), metric.unit(), metric.metricValue());
    }

    private static MetricDeltaDto toDeltaDto(MetricDelta delta) {
        return new MetricDeltaDto(
                delta.metricType(), delta.baseline(), delta.measured(), delta.difference(), delta.ratio());
    }

    private int opsCount(LayoutSearchCandidateEntity candidate) {
        return readChangeSet(candidate.getChangeSet()).ops().size();
    }

    private List<Metric> readMetrics(String json) {
        try {
            return objectMapper.readValue(json, new TypeReference<List<Metric>>() {});
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("저장된 지표를 읽지 못했습니다.", exception);
        }
    }

    private List<MetricDelta> readDeltas(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<List<MetricDelta>>() {});
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("저장된 지표 변화를 읽지 못했습니다.", exception);
        }
    }

    private ChangeSet readChangeSet(String json) {
        try {
            return objectMapper.readValue(json, ChangeSet.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("저장된 변경 집합을 읽지 못했습니다.", exception);
        }
    }

    private SearchBudget readBudget(String json) {
        try {
            return objectMapper.readValue(json, SearchBudget.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("저장된 검색 예산을 읽지 못했습니다.", exception);
        }
    }
}
