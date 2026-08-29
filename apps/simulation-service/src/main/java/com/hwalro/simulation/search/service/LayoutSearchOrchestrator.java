package com.hwalro.simulation.search.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.hwalro.simulation.common.jwt.JwtUser;
import com.hwalro.simulation.drawing.domain.LayoutExit;
import com.hwalro.simulation.drawing.mapper.DrawingMapper;
import com.hwalro.simulation.search.config.LayoutSearchProperties;
import com.hwalro.simulation.search.diagnosis.BottleneckFindingExtractor;
import com.hwalro.simulation.search.diagnosis.CongestionFindingExtractor;
import com.hwalro.simulation.search.diagnosis.DiagnosisAssembler;
import com.hwalro.simulation.search.diagnosis.EvacuationTailFindingExtractor;
import com.hwalro.simulation.search.diagnosis.ExitBalanceFindingExtractor;
import com.hwalro.simulation.search.domain.BaselineMetric;
import com.hwalro.simulation.search.domain.CandidateStatus;
import com.hwalro.simulation.search.domain.ChangeOp;
import com.hwalro.simulation.search.domain.ChangeSet;
import com.hwalro.simulation.search.domain.Diagnosis;
import com.hwalro.simulation.search.domain.Finding;
import com.hwalro.simulation.search.domain.HeatmapChunk;
import com.hwalro.simulation.search.domain.LayoutSearchCandidateEntity;
import com.hwalro.simulation.search.domain.LayoutSearchEntity;
import com.hwalro.simulation.search.domain.Metric;
import com.hwalro.simulation.search.domain.MetricDelta;
import com.hwalro.simulation.search.domain.SearchBudget;
import com.hwalro.simulation.search.domain.SearchConstraints;
import com.hwalro.simulation.search.domain.SearchResult;
import com.hwalro.simulation.search.domain.SearchResult.RejectedCandidate;
import com.hwalro.simulation.search.domain.SearchResult.SearchCandidate;
import com.hwalro.simulation.search.domain.SearchSource;
import com.hwalro.simulation.search.domain.SearchStatus;
import com.hwalro.simulation.search.domain.StoredBottleneck;
import com.hwalro.simulation.search.domain.TimelineChunk;
import com.hwalro.simulation.search.mapper.LayoutSearchMapper;
import com.hwalro.simulation.search.mapper.LayoutSearchSourceMapper;
import com.hwalro.simulation.search.service.CandidateTrialService.TrialOutcome;
import com.hwalro.simulation.search.service.LayoutSearchRunner.SearchInput;
import com.hwalro.simulation.simulation.dto.SimulationDtos.SimulationSetupResponse;
import com.hwalro.simulation.simulation.exception.SimulationConflictException;
import com.hwalro.simulation.simulation.service.SimulationService;
import com.hwalro.simulation.zone.service.SearchConstraintProjector;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Future;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class LayoutSearchOrchestrator {
    private static final Logger log = LoggerFactory.getLogger(LayoutSearchOrchestrator.class);
    private static final String PLANNER_VERSION = "DIAGNOSTIC_BEAM_V2";
    private static final int MAX_FINDINGS = 4;
    private static final int MAX_FAILURE_MESSAGE_LENGTH = 1000;
    private static final String COMPLETED_STATUS = "COMPLETED";

    private final LayoutSearchMapper layoutSearchMapper;
    private final LayoutSearchSourceMapper layoutSearchSourceMapper;
    private final LayoutSearchSourceLoader layoutSearchSourceLoader;
    private final LayoutSearchRunner layoutSearchRunner;
    private final CandidateTrialService candidateTrialService;
    private final SimulationService simulationService;
    private final DrawingMapper drawingMapper;
    private final SearchConstraintProjector searchConstraintProjector;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;
    private final LayoutSearchProperties properties;
    private final ThreadPoolTaskExecutor coordinatorExecutor;
    private final ThreadPoolTaskExecutor trialExecutor;
    private final BottleneckFindingExtractor bottleneckExtractor;
    private final CongestionFindingExtractor congestionExtractor;
    private final ExitBalanceFindingExtractor exitBalanceExtractor;
    private final EvacuationTailFindingExtractor evacuationTailExtractor;
    private final DiagnosisAssembler diagnosisAssembler = new DiagnosisAssembler();

    public LayoutSearchOrchestrator(
            LayoutSearchMapper layoutSearchMapper,
            LayoutSearchSourceMapper layoutSearchSourceMapper,
            LayoutSearchSourceLoader layoutSearchSourceLoader,
            LayoutSearchRunner layoutSearchRunner,
            CandidateTrialService candidateTrialService,
            SimulationService simulationService,
            DrawingMapper drawingMapper,
            SearchConstraintProjector searchConstraintProjector,
            ObjectMapper objectMapper,
            TransactionTemplate transactionTemplate,
            LayoutSearchProperties properties,
            @Qualifier("layoutSearchCoordinatorExecutor") ThreadPoolTaskExecutor coordinatorExecutor,
            @Qualifier("layoutSearchTrialExecutor") ThreadPoolTaskExecutor trialExecutor) {
        this.layoutSearchMapper = layoutSearchMapper;
        this.layoutSearchSourceMapper = layoutSearchSourceMapper;
        this.layoutSearchSourceLoader = layoutSearchSourceLoader;
        this.layoutSearchRunner = layoutSearchRunner;
        this.candidateTrialService = candidateTrialService;
        this.simulationService = simulationService;
        this.drawingMapper = drawingMapper;
        this.searchConstraintProjector = searchConstraintProjector;
        this.objectMapper = objectMapper;
        this.transactionTemplate = transactionTemplate;
        this.properties = properties;
        this.coordinatorExecutor = coordinatorExecutor;
        this.trialExecutor = trialExecutor;
        this.bottleneckExtractor = new BottleneckFindingExtractor(objectMapper);
        this.congestionExtractor = new CongestionFindingExtractor(objectMapper);
        this.exitBalanceExtractor = new ExitBalanceFindingExtractor(objectMapper);
        this.evacuationTailExtractor = new EvacuationTailFindingExtractor(objectMapper);
    }

    public LayoutSearchEntity start(long simulationId, JwtUser user, String budgetPreset, boolean verify) {
        SimulationSetupResponse setup = simulationService.getSetup(simulationId, user);
        if (!COMPLETED_STATUS.equals(setup.status())) {
            throw new SimulationConflictException("완료된 시뮬레이션에서만 배치 개선안을 탐색할 수 있습니다.");
        }
        List<Metric> baselineMetrics = readBaselineMetrics(simulationId, setup.layoutVersionId());
        LayoutSearchProperties.Budget budget = properties.budget(budgetPreset);
        double trialCap = TrialBudgetCalculator.trialCapSeconds(baselineMetrics, properties.getAbortMargin());

        LayoutSearchEntity search = new LayoutSearchEntity();
        search.setBaselineSimulationId(simulationId);
        search.setBaselineLayoutVersionId(setup.layoutVersionId());
        search.setPlannerVersion(PLANNER_VERSION);
        search.setStatus(SearchStatus.PENDING.name());
        search.setBaselineMetrics(writeJson(baselineMetrics));
        search.setBudget(writeJson(new SearchBudget(budgetPreset, budget.trials(), budget.rounds(), trialCap, verify)));
        search.setRequestedBy(user.userId());
        transactionTemplate.executeWithoutResult(status -> {
            // 제약 스냅샷은 insertSearch와 같은 트랜잭션에서 잡는다. 밖에서 투영하면 탐색 시작과
            // 제약 수정이 경쟁할 때 기록된 스냅샷과 실제 실행 제약이 어긋난다.
            search.setConstraints(
                    searchConstraintProjector.project(setup.layoutVersionId()).toJson(objectMapper));
            if (layoutSearchMapper.lockBaselineSimulation(simulationId) == null) {
                throw new IllegalStateException("기준 시뮬레이션을 찾을 수 없습니다: " + simulationId);
            }
            if (layoutSearchMapper.findActiveSearchByBaselineSimulationId(simulationId) != null) {
                throw new SimulationConflictException("이미 진행 중인 배치 개선안 탐색이 있습니다.");
            }
            layoutSearchMapper.insertSearch(search);
        });
        schedule(search.getId(), setup, baselineMetrics);
        return search;
    }

    public void resume(Long searchId) {
        LayoutSearchEntity search = requireSearch(searchId);
        JwtUser internalUser = new JwtUser(search.getRequestedBy(), Set.of("ADMIN"));
        SimulationSetupResponse setup = simulationService.getSetup(search.getBaselineSimulationId(), internalUser);
        schedule(searchId, setup, readMetrics(search.getBaselineMetrics()));
    }

    private void schedule(Long searchId, SimulationSetupResponse setup, List<Metric> baselineMetrics) {
        try {
            coordinatorExecutor.execute(() -> runSearch(searchId, setup, baselineMetrics));
        } catch (RuntimeException exception) {
            log.error("Layout search {} could not be scheduled", searchId, exception);
            failSearch(searchId, "SERVICE_UNAVAILABLE: 탐색 실행 대기열에 등록하지 못했습니다.");
        }
    }

    public LayoutSearchEntity cancel(long searchId, JwtUser user) {
        LayoutSearchEntity search = requireSearch(searchId);
        simulationService.getSetup(search.getBaselineSimulationId(), user);
        if (!isActive(search.getStatus())) {
            throw new SimulationConflictException("진행 중인 배치 개선안 탐색만 취소할 수 있습니다.");
        }
        LocalDateTime cancelledAt = LocalDateTime.now();
        Boolean cancelled = transactionTemplate.execute(status -> {
            if (layoutSearchMapper.finishSearch(searchId, SearchStatus.CANCELLED.name(), cancelledAt, null, null)
                    != 1) {
                return false;
            }
            layoutSearchMapper.cancelOutstandingTrials(searchId, cancelledAt, "SEARCH_CANCELLED: 배치 개선안 탐색이 취소되었습니다.");
            layoutSearchMapper.cancelOutstandingCandidates(searchId, "SEARCH_CANCELLED");
            return true;
        });
        if (!Boolean.TRUE.equals(cancelled)) {
            throw new SimulationConflictException("진행 중인 배치 개선안 탐색만 취소할 수 있습니다.");
        }
        return requireSearch(searchId);
    }

    private void runSearch(Long searchId, SimulationSetupResponse baselineSetup, List<Metric> baselineMetrics) {
        try {
            LayoutSearchEntity search = requireSearch(searchId);
            SearchBudget budget = readBudget(search.getBudget());
            SearchConstraints constraints = SearchConstraints.fromJson(objectMapper, search.getConstraints());
            SearchSource source = layoutSearchSourceLoader.load(search.getBaselineSimulationId());
            List<LayoutExit> exits = drawingMapper.findLayoutExitsByVersionId(search.getBaselineLayoutVersionId());
            List<LayoutSearchCandidateEntity> existing =
                    SearchStatus.PENDING.name().equals(search.getStatus())
                            ? List.of()
                            : layoutSearchMapper.findCandidatesBySearchId(searchId);
            Diagnosis diagnosis;
            if (search.getDiagnosis() == null || search.getDiagnosis().isBlank()) {
                transactionTemplate.executeWithoutResult(
                        status -> layoutSearchMapper.updateStudyStarted(searchId, LocalDateTime.now()));
                diagnosis = diagnose(source, search.getBaselineLayoutVersionId());
                transactionTemplate.executeWithoutResult(status -> {
                    layoutSearchMapper.updateStudyDiagnosis(searchId, writeJson(diagnosis));
                    layoutSearchMapper.updateSearchStatus(searchId, SearchStatus.GENERATING.name());
                });
            } else {
                diagnosis = readDiagnosis(search.getDiagnosis());
            }
            if (isCancelled(searchId)) {
                return;
            }
            double trialCap = TrialBudgetCalculator.trialCapSeconds(baselineMetrics, properties.getAbortMargin());
            boolean verify = budget.verifies();
            // 2라운드는 실측으로 개선된 부모를 확장하는 단계다. 확인하지 않는 탐색에는 그 부모가 없으므로
            // 1라운드만 돈다.
            int rounds = 1;
            boolean exhaustive = false;
            if (existing.isEmpty()) {
                int round1Cap = budget.maxTrials();
                if (!runRound(
                        searchId,
                        1,
                        round1Cap,
                        exhaustive,
                        verify,
                        source,
                        diagnosis,
                        baselineSetup,
                        baselineMetrics,
                        trialCap,
                        List.of(),
                        constraints,
                        exits)) {
                    return;
                }
            } else if (verify) {
                if (!verifyCandidates(
                        searchId,
                        existing.stream()
                                .filter(candidate ->
                                        CandidateStatus.QUEUED.name().equals(candidate.getStatus()))
                                .toList(),
                        baselineSetup,
                        baselineMetrics,
                        trialCap,
                        exits)) {
                    return;
                }
            }
            if (isCancelled(searchId)) {
                return;
            }
            List<LayoutSearchCandidateEntity> candidatesAfterVerification =
                    layoutSearchMapper.findCandidatesBySearchId(searchId);
            if (hasExhaustedRecovery(candidatesAfterVerification)) {
                failSearch(searchId, "SERVICE_RESTARTED: 후보 검증 재시도 한도를 초과했습니다.");
                return;
            }

            boolean roundTwoExists =
                    candidatesAfterVerification.stream().anyMatch(candidate -> candidate.getRoundIndex() == 2);
            if (rounds >= 2 && !roundTwoExists) {
                List<LayoutSearchCandidateEntity> improved =
                        improvedCandidates(candidatesAfterVerification, baselineMetrics);
                if (improved.isEmpty()) {
                    // 2라운드는 개선된 부모 후보를 확장하는 단계라 여기서 돌 수 없다. 그렇다고 종료하면
                    // 어려운 배치일수록 예산 6회 중 2회만 쓰고 포기하게 된다 - 정확히 개선안이 가장
                    // 필요한 배치가 가장 적게 탐색된다. 남은 예산으로 1라운드를 더 깊게 뽑는다.
                    // 이미 시행한 변경은 persistCandidates가 걸러낸다.
                    if (!runRound(
                            searchId,
                            1,
                            budget.maxTrials(),
                            exhaustive,
                            verify,
                            source,
                            diagnosis,
                            baselineSetup,
                            baselineMetrics,
                            trialCap,
                            List.of(),
                            constraints,
                            exits)) {
                        return;
                    }
                    finish(searchId, hasUsableCandidate(searchId, verify));
                    return;
                }
                List<LayoutSearchCandidateEntity> parents = improved;
                List<Map<String, Object>> parentInputs =
                        parents.stream().map(this::toParentInput).toList();
                if (!runRound(
                        searchId,
                        2,
                        budget.maxTrials(),
                        exhaustive,
                        verify,
                        source,
                        diagnosis,
                        baselineSetup,
                        baselineMetrics,
                        trialCap,
                        parentInputs,
                        constraints,
                        exits)) {
                    return;
                }
                if (isCancelled(searchId)) {
                    return;
                }
            }
            finish(searchId, hasUsableCandidate(searchId, verify));
        } catch (RuntimeException exception) {
            log.error("Layout search {} failed", searchId, exception);
            failSearch(searchId, "STUDY_FAILED: " + safeMessage(exception.getMessage()));
        }
    }

    private Diagnosis diagnose(SearchSource source, Long layoutVersionId) {
        List<StoredBottleneck> bottlenecks =
                layoutSearchSourceMapper.findBottlenecksBySimulationId(source.simulationId());
        List<HeatmapChunk> heatmapChunks =
                layoutSearchSourceMapper.findHeatmapChunksBySimulationId(source.simulationId());
        List<TimelineChunk> timelineChunks =
                layoutSearchSourceMapper.findTimelineChunksBySimulationId(source.simulationId());
        List<LayoutExit> exits = drawingMapper.findLayoutExitsByVersionId(layoutVersionId);
        List<List<Finding>> extracted = List.of(
                bottleneckExtractor.extract(bottlenecks),
                congestionExtractor.extract(heatmapChunks, source.densityThreshold()),
                exitBalanceExtractor.extract(timelineChunks, exits),
                evacuationTailExtractor.extract(timelineChunks));
        return diagnosisAssembler.assemble(extracted, MAX_FINDINGS);
    }

    private boolean runRound(
            Long searchId,
            int round,
            int maxCandidates,
            boolean exhaustive,
            boolean verify,
            SearchSource source,
            Diagnosis diagnosis,
            SimulationSetupResponse baselineSetup,
            List<Metric> baselineMetrics,
            double trialCap,
            List<Map<String, Object>> parents,
            SearchConstraints constraints,
            List<LayoutExit> exits) {
        transactionTemplate.executeWithoutResult(
                status -> layoutSearchMapper.updateSearchStatus(searchId, SearchStatus.GENERATING.name()));
        SearchInput input = new SearchInput(
                searchId,
                round,
                source.drawing(),
                source.agents(),
                source.hazards(),
                source.selectedExitIds(),
                source.densityThreshold(),
                diagnosis.findings(),
                parents,
                maxCandidates,
                exhaustive,
                properties.getSurrogateMode(),
                properties.getSurrogateBundle(),
                constraints == null ? null : constraints.toJson(objectMapper));
        SearchResult search = layoutSearchRunner.run(input);
        List<LayoutSearchCandidateEntity> queued = new ArrayList<>();
        transactionTemplate.executeWithoutResult(status -> {
            layoutSearchMapper.updatePlannerVersion(searchId, search.plannerVersion());
            persistCandidates(searchId, round, search, queued, constraints);
        });
        if (queued.isEmpty() || !verify) {
            // 확인하지 않는 탐색에서는 후보가 QUEUED로 남는다. 어떤 후보를 실제로 돌려볼지는 사용자가 고른다.
            return true;
        }
        transactionTemplate.executeWithoutResult(
                status -> layoutSearchMapper.updateSearchStatus(searchId, SearchStatus.VERIFYING.name()));
        return verifyCandidates(searchId, queued, baselineSetup, baselineMetrics, trialCap, exits);
    }

    private void persistCandidates(
            Long searchId,
            int round,
            SearchResult search,
            List<LayoutSearchCandidateEntity> queued,
            SearchConstraints constraints) {
        String constraintsSnapshot = constraints == null ? null : constraints.toJson(objectMapper);
        // 같은 라운드를 두 번 돌릴 수 있으므로(1라운드 전멸 시 심화 재탐색) 이미 시행한 변경은
        // 다시 큐에 넣지 않고, candidate_order도 이어서 매긴다 - (study_id, round_index, order)가 유니크다.
        List<LayoutSearchCandidateEntity> alreadyPersisted = layoutSearchMapper.findCandidatesBySearchId(searchId);
        Set<String> seenOps = alreadyPersisted.stream()
                .filter(entity -> entity.getChangeSet() != null)
                .map(entity -> opsKey(readOps(entity.getChangeSet())))
                .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
        int order = alreadyPersisted.stream()
                        .filter(entity -> entity.getRoundIndex() != null && entity.getRoundIndex() == round)
                        .mapToInt(entity -> entity.getCandidateOrder() == null ? 0 : entity.getCandidateOrder())
                        .max()
                        .orElse(0)
                + 1;
        for (SearchCandidate candidate : search.candidates()) {
            if (!seenOps.add(opsKey(candidate.ops()))) {
                continue;
            }
            LayoutSearchCandidateEntity entity = new LayoutSearchCandidateEntity();
            entity.setStudyId(searchId);
            entity.setParentCandidateId(candidate.parentCandidateId());
            entity.setRoundIndex(round);
            entity.setCandidateOrder(order++);
            entity.setOriginFindingType(candidate.originFindingType());
            entity.setOperatorType(candidate.operatorType());
            entity.setStatus(CandidateStatus.QUEUED.name());
            entity.setChangeSet(writeJson(new ChangeSet(1, "METER", candidate.ops())));
            entity.setRationale(writeJson(enrichRationale(candidate)));
            entity.setProxyScore(candidate.proxyScore());
            entity.setConstraintsSnapshot(constraintsSnapshot);
            layoutSearchMapper.insertCandidate(entity);
            queued.add(entity);
        }
        // 거부된 변경은 제안이 아니라 "그 자리에 넣을 수 없다"는 사실일 뿐이라 사용자에게 보여줄
        // 것이 없다. 후보로 저장하지 않고 사유별 집계만 로그로 남긴다 - 후보가 갑자기 줄어드는
        // 원인을 추적할 때 이 분포가 결정적이므로 완전히 버리지는 않는다.
        Map<String, Integer> rejectedByReason = new LinkedHashMap<>();
        for (RejectedCandidate rejected : search.rejected()) {
            rejectedByReason.merge(rejected.reason(), 1, Integer::sum);
        }
        if (!rejectedByReason.isEmpty()) {
            log.info("Layout search {} round {} rejected candidates by reason: {}", searchId, round, rejectedByReason);
        }
    }

    boolean verifyCandidates(
            Long searchId,
            List<LayoutSearchCandidateEntity> queued,
            SimulationSetupResponse baselineSetup,
            List<Metric> baselineMetrics,
            double trialCap,
            List<LayoutExit> exits) {
        for (int start = 0; start < queued.size(); start += properties.getTrialConcurrency()) {
            if (isCancelled(searchId)) {
                return false;
            }
            List<Future<TrialOutcome>> futures = new ArrayList<>();
            for (LayoutSearchCandidateEntity candidate :
                    queued.subList(start, Math.min(queued.size(), start + properties.getTrialConcurrency()))) {
                if (!isCancelled(searchId)) {
                    futures.add(trialExecutor.submit(() -> candidateTrialService.run(
                            candidate,
                            baselineSetup,
                            baselineMetrics,
                            trialCap,
                            properties.getImprovementMargin(),
                            exits)));
                }
            }
            for (Future<TrialOutcome> future : futures) {
                try {
                    TrialOutcome outcome = future.get();
                    if (outcome.status() == null) {
                        return false;
                    }
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    return false;
                } catch (java.util.concurrent.ExecutionException exception) {
                    log.warn("Layout search {} trial task failed", searchId, exception.getCause());
                    return false;
                }
            }
        }
        return true;
    }

    private void finish(Long searchId, boolean anyImproved) {
        transactionTemplate.executeWithoutResult(status -> layoutSearchMapper.finishSearch(
                searchId,
                anyImproved ? SearchStatus.COMPLETED.name() : SearchStatus.NO_IMPROVEMENT.name(),
                LocalDateTime.now(),
                null,
                null));
    }

    private void failSearch(Long searchId, String message) {
        transactionTemplate.executeWithoutResult(status -> layoutSearchMapper.finishSearch(
                searchId, SearchStatus.FAILED.name(), LocalDateTime.now(), "STUDY_FAILED", safeMessage(message)));
    }

    private List<LayoutSearchCandidateEntity> improvedCandidates(
            List<LayoutSearchCandidateEntity> allCandidates, List<Metric> baselineMetrics) {
        List<LayoutSearchCandidateEntity> candidates = allCandidates.stream()
                .filter(candidate -> CandidateStatus.EVALUATED.name().equals(candidate.getStatus()))
                .toList();
        List<CandidateSelector.RankableCandidate> rankable = candidates.stream()
                .map(candidate -> new CandidateSelector.RankableCandidate(
                        candidate.getId(),
                        List.of(),
                        readDeltas(candidate.getMetricDelta()),
                        readOps(candidate.getChangeSet()).size()))
                .toList();
        List<Long> rankedIds = rankable.stream()
                .sorted(CandidateSelector.rankingComparator(baselineMetrics))
                .map(CandidateSelector.RankableCandidate::candidateId)
                .toList();
        Map<Long, LayoutSearchCandidateEntity> byId = new LinkedHashMap<>();
        candidates.forEach(candidate -> byId.put(candidate.getId(), candidate));
        return rankedIds.stream().map(byId::get).toList();
    }

    /**
     * 확인한 탐색은 실측 개선이 있어야 결과가 있는 것이고, 확인하지 않은 탐색은 제안할 후보가 하나라도
     * 남았으면 결과가 있는 것이다 - 후보를 뽑아 놓고 NO_IMPROVEMENT로 끝내면 화면에 아무것도 남지 않는다.
     */
    private boolean hasUsableCandidate(Long searchId, boolean verify) {
        String expected = verify ? CandidateStatus.EVALUATED.name() : CandidateStatus.QUEUED.name();
        return layoutSearchMapper.findCandidatesBySearchId(searchId).stream()
                .anyMatch(candidate -> expected.equals(candidate.getStatus()));
    }

    private Map<String, Object> toParentInput(LayoutSearchCandidateEntity candidate) {
        Map<String, Object> parent = new LinkedHashMap<>();
        parent.put("candidateId", candidate.getId());
        parent.put("originFindingType", candidate.getOriginFindingType());
        parent.put("ops", readOps(candidate.getChangeSet()));
        return parent;
    }

    private JsonNode enrichRationale(SearchCandidate candidate) {
        ObjectNode enriched = candidate.rationale().deepCopy();
        enriched.put("operatorType", candidate.operatorType());
        enriched.put("description", rationaleDescription(candidate.operatorType()));
        return enriched;
    }

    private String rationaleDescription(String operatorType) {
        return switch (operatorType) {
            case "CLEAR_CORRIDOR" -> "병목 구역의 보행 폭을 확보하기 위해 집기를 이동했습니다.";
            case "RELIEVE_HOTSPOT" -> "혼잡 구역에서 멀어지도록 집기를 이동했습니다.";
            case "REBALANCE_EXIT" -> "출구별 수요 편중을 줄이도록 통로를 확보했습니다.";
            case "ROTATE_TO_OPEN" -> "집기 장축이 통로를 가로지르지 않도록 회전했습니다.";
            case "OPEN_DUAL_GAP" -> "병목 구역 양쪽의 집기를 벌려 통로 폭을 확보했습니다.";
            case "RELIEVE_DIAGONAL" -> "혼잡 구역에서 대각 방향으로 멀어지도록 집기를 이동했습니다.";
            case "EXIT_OPENING" -> "한산한 출구 접근로의 집기를 정리해 출구 수요를 분산했습니다.";
            case "BOUNDARY_DOCKING" -> "이상 경로를 가로막는 구조물을 경계에 평행하게 정리했습니다.";
            default -> "배치 변경으로 대피 흐름을 개선합니다.";
        };
    }

    private List<Metric> readBaselineMetrics(long simulationId, Long layoutVersionId) {
        List<BaselineMetric> rows = layoutSearchSourceMapper.findBaselineMetricsBySimulationId(simulationId);
        if (rows.isEmpty()) {
            throw new IllegalStateException("기준 시뮬레이션의 저장된 지표가 없습니다.");
        }
        List<Metric> metrics = new ArrayList<>();
        for (BaselineMetric row : rows) {
            metrics.add(new Metric(
                    row.getMetricType(), row.getUnit(), row.getMetricValue() == null ? 0.0 : row.getMetricValue()));
        }
        appendExitImbalance(metrics, simulationId, layoutVersionId);
        return List.copyOf(metrics);
    }

    private void appendExitImbalance(List<Metric> metrics, long simulationId, Long layoutVersionId) {
        if (layoutVersionId == null
                || metrics.stream().anyMatch(metric -> CandidateSelector.EXIT_IMBALANCE.equals(metric.metricType()))) {
            return;
        }
        List<LayoutExit> exits = drawingMapper.findLayoutExitsByVersionId(layoutVersionId);
        if (exits.isEmpty()) {
            return;
        }
        Double severity = exitBalanceExtractor.worstExitSeverity(
                layoutSearchSourceMapper.findTimelineChunksBySimulationId(simulationId).stream()
                        .map(TimelineChunk::getFrameData)
                        .toList(),
                exits);
        if (severity != null) {
            metrics.add(new Metric(CandidateSelector.EXIT_IMBALANCE, "RATIO", severity));
        }
    }

    private SearchBudget readBudget(String json) {
        try {
            return objectMapper.readValue(json, SearchBudget.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("저장된 검색 예산을 읽지 못했습니다.", exception);
        }
    }

    private boolean hasExhaustedRecovery(List<LayoutSearchCandidateEntity> candidates) {
        return candidates.stream()
                .anyMatch(candidate -> CandidateStatus.FAILED.name().equals(candidate.getStatus())
                        && "SERVICE_RESTARTED".equals(candidate.getRejectReason()));
    }

    private Diagnosis readDiagnosis(String json) {
        try {
            return objectMapper.readValue(json, Diagnosis.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("저장된 진단을 읽지 못했습니다.", exception);
        }
    }

    private List<Metric> readMetrics(String json) {
        try {
            return objectMapper.readValue(json, new com.fasterxml.jackson.core.type.TypeReference<List<Metric>>() {});
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("저장된 기준 지표를 읽지 못했습니다.", exception);
        }
    }

    private List<MetricDelta> readDeltas(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(
                    json, new com.fasterxml.jackson.core.type.TypeReference<List<MetricDelta>>() {});
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

    private List<ChangeOp> readOps(String json) {
        return readChangeSet(json).ops();
    }

    private String opsKey(List<ChangeOp> ops) {
        return ops.stream().map(this::opKey).sorted().collect(java.util.stream.Collectors.joining("|"));
    }

    private String opKey(ChangeOp op) {
        ChangeOp.FabricTransform before = op.before();
        ChangeOp.FabricTransform after = op.after();
        return String.join(":", op.type(), String.valueOf(op.fabricId()), transformKey(before), transformKey(after));
    }

    private String transformKey(ChangeOp.FabricTransform transform) {
        return String.join(
                ",",
                LayoutSearchPrecision.key(transform.startX()),
                LayoutSearchPrecision.key(transform.startY()),
                LayoutSearchPrecision.key(transform.endX()),
                LayoutSearchPrecision.key(transform.endY()),
                LayoutSearchPrecision.key(transform.rotation()));
    }

    private LayoutSearchEntity requireSearch(Long searchId) {
        LayoutSearchEntity search = layoutSearchMapper.findSearchById(searchId);
        if (search == null) {
            throw new com.hwalro.simulation.simulation.exception.SimulationNotFoundException(
                    "배치 개선안 탐색을 찾을 수 없습니다: " + searchId);
        }
        return search;
    }

    private boolean isCancelled(Long searchId) {
        LayoutSearchEntity search = layoutSearchMapper.findSearchById(searchId);
        return search != null && SearchStatus.CANCELLED.name().equals(search.getStatus());
    }

    private boolean isActive(String status) {
        return List.of("PENDING", "DIAGNOSING", "GENERATING", "VERIFYING").contains(status);
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("탐색 데이터를 직렬화하지 못했습니다.", exception);
        }
    }

    private String safeMessage(String message) {
        if (message == null || message.isBlank()) {
            return "탐색 실행에 실패했습니다.";
        }
        return message.substring(0, Math.min(message.length(), MAX_FAILURE_MESSAGE_LENGTH));
    }
}
