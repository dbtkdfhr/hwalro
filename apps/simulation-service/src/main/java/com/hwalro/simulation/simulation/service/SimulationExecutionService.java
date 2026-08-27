package com.hwalro.simulation.simulation.service;

import static com.hwalro.simulation.simulation.config.SimulationExecutionConfig.EXECUTION_QUEUE_CAPACITY;
import static com.hwalro.simulation.simulation.config.SimulationExecutionConfig.MAX_CONCURRENT_EXECUTIONS;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hwalro.simulation.analysis.domain.DetectedBottleneck;
import com.hwalro.simulation.analysis.service.BottleneckDetector;
import com.hwalro.simulation.analysis.service.DensityThresholdProvider;
import com.hwalro.simulation.common.jwt.JwtUser;
import com.hwalro.simulation.simulation.domain.Simulation;
import com.hwalro.simulation.simulation.domain.SimulationMetric;
import com.hwalro.simulation.simulation.domain.SimulationResult;
import com.hwalro.simulation.simulation.dto.SimulationDtos.ExitEventResponse;
import com.hwalro.simulation.simulation.dto.SimulationDtos.HeatmapChunkResponse;
import com.hwalro.simulation.simulation.dto.SimulationDtos.SimulationExecutionResponse;
import com.hwalro.simulation.simulation.dto.SimulationDtos.SimulationFailureDetailResponse;
import com.hwalro.simulation.simulation.dto.SimulationDtos.SimulationMetricResponse;
import com.hwalro.simulation.simulation.dto.SimulationDtos.SimulationResultResponse;
import com.hwalro.simulation.simulation.dto.SimulationDtos.SimulationRoutingValidationResponse;
import com.hwalro.simulation.simulation.dto.SimulationDtos.SimulationSetupResponse;
import com.hwalro.simulation.simulation.dto.SimulationDtos.TimelineAgentResponse;
import com.hwalro.simulation.simulation.dto.SimulationDtos.TimelineChunkResponse;
import com.hwalro.simulation.simulation.dto.SimulationDtos.TimelineFrameResponse;
import com.hwalro.simulation.simulation.engine.SimulationEngineRunner;
import com.hwalro.simulation.simulation.engine.SimulationEngineRunner.EngineResult;
import com.hwalro.simulation.simulation.engine.SimulationEngineRunner.EngineRun;
import com.hwalro.simulation.simulation.engine.SimulationEngineRunner.EngineRunException;
import com.hwalro.simulation.simulation.exception.InvalidSimulationGeometryException;
import com.hwalro.simulation.simulation.exception.SimulationConflictException;
import com.hwalro.simulation.simulation.exception.SimulationEngineUnavailableException;
import com.hwalro.simulation.simulation.exception.SimulationNotFoundException;
import com.hwalro.simulation.simulation.mapper.SimulationMapper;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.FutureTask;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class SimulationExecutionService {
    private static final Logger log = LoggerFactory.getLogger(SimulationExecutionService.class);
    private static final String STATUS_COMPLETED = "COMPLETED";
    private static final String MODEL_PROFILE = "SFM_DEFAULT_V2";
    private static final String ROUTING_PROFILE = "HAZARD_RADIAL_EXP_V3";
    private static final String ROUTING_VALIDATION_SUCCESS_MESSAGE = "경로 검증에 성공했습니다. 시뮬레이션 실행을 요청합니다.";
    private static final String NO_REACHABLE_EXIT_MESSAGE = "선택한 출입구에 도달할 수 없는 구역이 있습니다. 도면과 출입구를 확인해 주세요.";
    private static final int MAX_FAILURE_MESSAGE_LENGTH = 1000;
    private static final int RECOVERY_GROUP_SIZE = 3;
    private static final Set<String> RECOVERY_SUMMARY_FIELDS = Set.of(
            "schemaVersion",
            "scanCount",
            "eligibleGroupCount",
            "skippedEligibleGroupCount",
            "infeasibleScanCount",
            "recoveredGroupCount",
            "recoveredAgentCount",
            "recoveredMidRouteAgentCount",
            "recoveryTimeSeconds",
            "recoveredExitLabels",
            "recoveredExitIds",
            "attemptedGroupSignatures",
            "events");
    private static final Set<String> RECOVERY_COUNTER_FIELDS = Set.of(
            "scanCount",
            "eligibleGroupCount",
            "skippedEligibleGroupCount",
            "infeasibleScanCount",
            "recoveredGroupCount",
            "recoveredAgentCount",
            "recoveredMidRouteAgentCount",
            "attemptedGroupSignatures");
    private static final Set<String> RECOVERY_EVENT_FIELDS = Set.of(
            "timeSeconds",
            "iteration",
            "contextIndex",
            "exitId",
            "exitLabel",
            "target",
            "stableIds",
            "oldTargets",
            "newTargets",
            "newApproaches",
            "seedNodeIds",
            "status",
            "reasonCode",
            "exceptionClass",
            "postRecoveryInvalidMoves",
            "postRecoveryFullRollbacks");
    private static final Set<String> RECOVERY_EVENT_STATUSES =
            Set.of("RECOVERED", "RECOVERY_INFEASIBLE", "RECOVERY_MUTATION_FAILED");
    private static final Set<String> RECOVERY_REASON_CODES = Set.of(
            "NO_SEEDS",
            "EDGE_SEED",
            "NON_DISTINCT_TARGETS",
            "CONNECT_FAILED",
            "REACH_FAILED",
            "EXIT_ID_CHANGED",
            "REROUTE_UNREACHABLE",
            "REROUTE_UNCHANGED",
            "MUTATION_APPLY_FAILED");
    private static final Set<String> RECOVERY_EVENT_POINT_LIST_FIELDS =
            Set.of("oldTargets", "newTargets", "newApproaches");
    private static final Pattern RECOVERY_EXCEPTION_CLASS_PATTERN =
            Pattern.compile("([A-Za-z_$][A-Za-z0-9_$]*\\.)*[A-Za-z_$][A-Za-z0-9_$]*");
    private static final int LEGACY_TIMELINE_FRAMES_PER_CHUNK = 10;
    private static final int TIMELINE_FRAMES_PER_CHUNK = 20;

    private final SimulationMapper simulationMapper;
    private final SimulationService simulationService;
    private final SimulationEngineRunner engineRunner;
    private final ThreadPoolTaskExecutor executor;
    private final TransactionTemplate transactionTemplate;
    private final ObjectMapper objectMapper;
    private final DensityThresholdProvider densityThresholdProvider;
    private final BottleneckDetector bottleneckDetector;
    private final Semaphore executionCapacity =
            new Semaphore(MAX_CONCURRENT_EXECUTIONS + EXECUTION_QUEUE_CAPACITY, true);
    private final Map<Long, SimulationTask> activeTasks = new ConcurrentHashMap<>();
    private final Object engineReadinessMonitor = new Object();
    private volatile boolean engineReady;

    public SimulationExecutionService(
            SimulationMapper simulationMapper,
            SimulationService simulationService,
            SimulationEngineRunner engineRunner,
            @Qualifier("simulationExecutionExecutor") ThreadPoolTaskExecutor executor,
            TransactionTemplate transactionTemplate,
            ObjectMapper objectMapper,
            DensityThresholdProvider densityThresholdProvider,
            BottleneckDetector bottleneckDetector) {
        this.simulationMapper = simulationMapper;
        this.simulationService = simulationService;
        this.engineRunner = engineRunner;
        this.executor = executor;
        this.transactionTemplate = transactionTemplate;
        this.objectMapper = objectMapper;
        this.densityThresholdProvider = densityThresholdProvider;
        this.bottleneckDetector = bottleneckDetector;
    }

    public SimulationExecutionResponse execute(Long simulationId, JwtUser user) {
        long setupNanos = 0;
        long setupStarted = System.nanoTime();
        Simulation current = simulationService.getAccessibleSimulation(simulationId, user);
        setupNanos += System.nanoTime() - setupStarted;
        if (!"DRAFT".equals(current.getStatus())
                && !"FAILED".equals(current.getStatus())
                && !"CANCELLED".equals(current.getStatus())) {
            throw new SimulationConflictException("DRAFT, FAILED 또는 CANCELLED 상태에서만 실행할 수 있습니다.");
        }
        long readinessStarted = System.nanoTime();
        boolean readinessCheckPerformed;
        try {
            readinessCheckPerformed = ensureEngineReady();
        } catch (RuntimeException exception) {
            log.info(
                    "simulation_execution_phase simulationId={} outcome=READINESS_FAILED readinessChecked=true readinessMs={} setupMs={} queueMs=0 engineMs=0 persistMs=0 workerMs=0",
                    simulationId,
                    elapsedMillis(readinessStarted),
                    TimeUnit.NANOSECONDS.toMillis(setupNanos));
            throw exception;
        }
        long readinessMs = elapsedMillis(readinessStarted);
        if (!executionCapacity.tryAcquire()) {
            throw new SimulationEngineUnavailableException("시뮬레이션 대기열이 가득 찼습니다. 잠시 후 다시 시도해 주세요.");
        }

        boolean taskOwnsCapacity = false;
        try {
            setupStarted = System.nanoTime();
            SimulationSetupResponse setup = transactionTemplate.execute(status -> {
                if (simulationMapper.updateExecutionProfiles(simulationId, MODEL_PROFILE, ROUTING_PROFILE) != 1) {
                    throw new IllegalStateException("시뮬레이션 실행 프로필을 갱신하지 못했습니다.");
                }
                if (simulationMapper.requestExecution(simulationId) != 1) {
                    throw new SimulationConflictException("DRAFT, FAILED 또는 CANCELLED 상태에서만 실행할 수 있습니다.");
                }
                SimulationSetupResponse requestedSetup = simulationService.getSetup(simulationId, user);
                validateExecutionSetup(requestedSetup);
                return requestedSetup;
            });
            setupNanos += System.nanoTime() - setupStarted;
            long setupMs = TimeUnit.NANOSECONDS.toMillis(setupNanos);
            long queuedAtNanos = System.nanoTime();
            SimulationTask task = new SimulationTask(
                    simulationId, setup, queuedAtNanos, readinessCheckPerformed, readinessMs, setupMs);
            activeTasks.put(simulationId, task);
            taskOwnsCapacity = true;
            try {
                executor.execute(task);
                if (task.isCancelled() && executor.getThreadPoolExecutor() != null) {
                    executor.getThreadPoolExecutor().remove(task);
                }
            } catch (RuntimeException exception) {
                task.cancel(false);
                log.info(
                        "simulation_execution_phase simulationId={} outcome=SUBMISSION_FAILED readinessChecked={} readinessMs={} setupMs={} queueMs=0 engineMs=0 persistMs=0 workerMs=0",
                        simulationId,
                        readinessCheckPerformed,
                        readinessMs,
                        setupMs);
                markFailed(simulationId, "SERVICE_UNAVAILABLE: 실행 작업을 대기열에 등록하지 못했습니다.");
                throw new SimulationEngineUnavailableException("시뮬레이션 실행 서비스를 사용할 수 없습니다. 잠시 후 다시 시도해 주세요.", exception);
            }
        } finally {
            if (!taskOwnsCapacity) {
                executionCapacity.release();
            }
        }
        return getExecution(simulationId, user);
    }

    public SimulationRoutingValidationResponse validateRouting(Long simulationId, JwtUser user) {
        Simulation current = simulationService.getAccessibleSimulation(simulationId, user);
        if (!"DRAFT".equals(current.getStatus())) {
            throw new SimulationConflictException("DRAFT 상태에서만 경로를 검증할 수 있습니다.");
        }
        ensureEngineReady();
        if (!executionCapacity.tryAcquire()) {
            throw new SimulationEngineUnavailableException("시뮬레이션 대기열이 가득 찼습니다. 잠시 후 다시 시도해 주세요.");
        }
        try {
            SimulationSetupResponse setup = simulationService.getSetup(simulationId, user);
            validateRoutingSetup(setup);
            SimulationFailureDetailResponse failureDetail = engineRunner.validateRouting(simulationId, setup);
            if (failureDetail == null) {
                return new SimulationRoutingValidationResponse(true, ROUTING_VALIDATION_SUCCESS_MESSAGE, null);
            }
            return new SimulationRoutingValidationResponse(false, routingFailureMessage(failureDetail), failureDetail);
        } catch (EngineRunException exception) {
            String message =
                    exception.isTimeout() ? "경로 검증 시간이 초과되었습니다. 잠시 후 다시 시도해 주세요." : "시뮬레이션 엔진에서 경로를 검증할 수 없습니다.";
            throw new SimulationEngineUnavailableException(message, exception);
        } finally {
            executionCapacity.release();
        }
    }

    private boolean ensureEngineReady() {
        if (engineReady) {
            return false;
        }
        synchronized (engineReadinessMonitor) {
            if (engineReady) {
                return false;
            }
            engineRunner.assertAvailable();
            engineReady = true;
            return true;
        }
    }

    public SimulationExecutionResponse cancel(Long simulationId, JwtUser user) {
        Simulation current = simulationService.getAccessibleSimulation(simulationId, user);
        if ("CANCELLED".equals(current.getStatus())) {
            return getExecution(simulationId, user);
        }
        if (!"REQUESTED".equals(current.getStatus()) && !"RUNNING".equals(current.getStatus())) {
            throw new SimulationConflictException("REQUESTED 또는 RUNNING 상태에서만 취소할 수 있습니다.");
        }

        Integer cancelled = transactionTemplate.execute(status -> simulationMapper.cancelExecution(simulationId));
        if (cancelled == null || cancelled != 1) {
            Simulation latest = simulationService.getAccessibleSimulation(simulationId, user);
            if (!"CANCELLED".equals(latest.getStatus())) {
                throw new SimulationConflictException("이미 종료된 시뮬레이션은 취소할 수 없습니다.");
            }
        }

        SimulationTask task = activeTasks.get(simulationId);
        if (task != null) {
            task.cancel(true);
            var threadPool = executor.getThreadPoolExecutor();
            if (threadPool != null) {
                threadPool.remove(task);
            }
        }
        return getExecution(simulationId, user);
    }

    public SimulationExecutionResponse getExecution(Long simulationId, JwtUser user) {
        Simulation simulation = simulationService.getAccessibleSimulation(simulationId, user);
        SimulationResult result = simulationMapper.findSimulationResult(simulationId);
        SimulationResultResponse resultResponse = null;
        if (result != null) {
            List<SimulationMetric> metrics = simulationMapper.findSimulationMetrics(result.getId());
            BigDecimal duration = metrics.stream()
                    .filter(metric -> "SIMULATION_DURATION_SECONDS".equals(metric.getMetricType()))
                    .findFirst()
                    .map(metric -> BigDecimal.valueOf(metric.getMetricValue()))
                    .orElse(BigDecimal.ZERO);
            resultResponse = new SimulationResultResponse(
                    result.getId(),
                    result.getEngineVersion(),
                    result.getTerminationReason(),
                    duration,
                    result.getFrameIntervalSeconds(),
                    result.getTimelineChunkCount(),
                    result.getFrameIntervalSeconds()
                            .multiply(BigDecimal.valueOf(
                                    result.getTimelineSchemaVersion() != null && result.getTimelineSchemaVersion() >= 1
                                            ? TIMELINE_FRAMES_PER_CHUNK
                                            : LEGACY_TIMELINE_FRAMES_PER_CHUNK)),
                    result.getHeatmapChunkCount(),
                    result.getTerminationDetail(),
                    metrics.stream()
                            .map(metric -> new SimulationMetricResponse(
                                    metric.getMetricType(), metric.getUnit(), metric.getMetricValue()))
                            .toList());
        }
        return new SimulationExecutionResponse(
                simulation.getId(),
                simulation.getStatus(),
                simulation.getRequestedAt(),
                simulation.getStartedAt(),
                simulation.getFinishedAt(),
                simulation.getFailureMessage(),
                simulationService.readFailureDetail(simulation),
                resultResponse);
    }

    public TimelineChunkResponse getTimeline(Long simulationId, int chunkSequence, JwtUser user) {
        if (chunkSequence < 0) {
            throw new IllegalArgumentException("chunkSequence는 0 이상이어야 합니다.");
        }
        Simulation simulation = simulationService.getAccessibleSimulation(simulationId, user);
        if (!STATUS_COMPLETED.equals(simulation.getStatus())) {
            throw new SimulationConflictException("완료된 시뮬레이션의 타임라인만 조회할 수 있습니다.");
        }
        String json = simulationMapper.findTimelineJson(simulationId, chunkSequence);
        if (json == null) {
            throw new SimulationNotFoundException("타임라인 청크를 찾을 수 없습니다: " + chunkSequence);
        }
        try {
            JsonNode root = objectMapper.readTree(json);
            if (root.path("schemaVersion").asInt(0) >= 1) {
                return objectMapper.treeToValue(root, TimelineChunkResponse.class);
            }
            return normalizeLegacyTimeline(root, chunkSequence, simulationId);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("저장된 타임라인을 읽을 수 없습니다.", exception);
        }
    }

    public HeatmapChunkResponse getHeatmap(Long simulationId, int chunkSequence, JwtUser user) {
        if (chunkSequence < 0) {
            throw new IllegalArgumentException("chunkSequence는 0 이상이어야 합니다.");
        }
        Simulation simulation = simulationService.getAccessibleSimulation(simulationId, user);
        if (!STATUS_COMPLETED.equals(simulation.getStatus())) {
            throw new SimulationConflictException("완료된 시뮬레이션의 히트맵만 조회할 수 있습니다.");
        }
        String json = simulationMapper.findHeatmapJson(simulationId, chunkSequence);
        if (json == null) {
            throw new SimulationNotFoundException("히트맵 청크를 찾을 수 없습니다: " + chunkSequence);
        }
        try {
            return objectMapper.readValue(json, HeatmapChunkResponse.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("저장된 히트맵을 읽을 수 없습니다.", exception);
        }
    }

    @EventListener(ApplicationReadyEvent.class)
    public void failInterruptedExecutions() {
        Integer count = transactionTemplate.execute(status ->
                simulationMapper.markInterruptedExecutionsFailed("SERVICE_RESTARTED: 서비스 재시작으로 실행이 중단되었습니다."));
        if (count != null && count > 0) {
            log.warn("Marked {} interrupted simulation executions as FAILED", count);
        }
    }

    private void runJob(
            Long simulationId,
            SimulationSetupResponse setup,
            long queuedAtNanos,
            boolean readinessCheckPerformed,
            long readinessMs,
            long setupMs) {
        long workerStarted = System.nanoTime();
        long queueMs = elapsedMillis(queuedAtNanos);
        long engineMs = 0;
        long persistMs = 0;
        String outcome = "NOT_STARTED";
        try {
            Integer started =
                    transactionTemplate.execute(status -> simulationMapper.markExecutionRunning(simulationId));
            if (started == null || started != 1) {
                return;
            }
            long engineStarted = System.nanoTime();
            EngineRun run;
            try {
                run = engineRunner.run(simulationId, setup);
            } finally {
                engineMs = elapsedMillis(engineStarted);
            }
            long persistStarted = System.nanoTime();
            try {
                transactionTemplate.executeWithoutResult(status -> persistResult(simulationId, setup, run));
            } finally {
                persistMs = elapsedMillis(persistStarted);
            }
            outcome = "COMPLETED";
        } catch (EngineRunException exception) {
            outcome = exception.isTimeout() ? "ENGINE_TIMEOUT" : "ENGINE_ERROR";
            SimulationFailureDetailResponse failureDetail = exception.isTimeout() ? null : exception.failureDetail();
            if (failureDetail == null) {
                log.warn("Simulation {} engine execution failed (timeout={})", simulationId, exception.isTimeout());
            }
            markFailed(
                    simulationId,
                    exception.isTimeout()
                            ? "ENGINE_TIMEOUT: 실제 실행시간 제한을 초과했습니다."
                            : failureDetail == null
                                    ? "ENGINE_ERROR: 시뮬레이션 엔진 실행에 실패했습니다."
                                    : routingFailureMessage(failureDetail),
                    failureDetail);
        } catch (RuntimeException exception) {
            outcome = "SERVICE_ERROR";
            log.error("Simulation {} execution failed", simulationId, exception);
            markFailed(simulationId, "ENGINE_ERROR: 시뮬레이션 실행 또는 결과 저장에 실패했습니다.");
        } finally {
            log.info(
                    "simulation_execution_phase simulationId={} outcome={} readinessChecked={} readinessMs={} setupMs={} queueMs={} engineMs={} persistMs={} workerMs={}",
                    simulationId,
                    outcome,
                    readinessCheckPerformed,
                    readinessMs,
                    setupMs,
                    queueMs,
                    engineMs,
                    persistMs,
                    elapsedMillis(workerStarted));
        }
    }

    private static long elapsedMillis(long startedNanos) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedNanos);
    }

    public void persistEngineRun(Long simulationId, SimulationSetupResponse setup, EngineRun run) {
        persistResult(simulationId, setup, run);
    }

    private void persistResult(Long simulationId, SimulationSetupResponse setup, EngineRun run) {
        EngineResult output = run.result();
        validateEngineResult(output, setup, run.maxSimulationTimeSeconds());
        if (output.timelineChunkCount() != run.timelineChunks().size()) {
            throw new IllegalStateException("타임라인 청크 수가 결과 요약과 일치하지 않습니다.");
        }
        if (output.heatmapChunkCount() != run.heatmapChunks().size()
                || output.heatmapChunkCount() != output.timelineChunkCount()) {
            throw new IllegalStateException("히트맵 청크 수가 타임라인과 일치하지 않습니다.");
        }

        SimulationResult result = new SimulationResult();
        result.setSimulationId(simulationId);
        result.setEngineVersion(output.engineVersion());
        result.setTerminationReason(output.terminationReason());
        result.setFrameIntervalSeconds(BigDecimal.valueOf(output.frameIntervalSeconds()));
        if (output.terminationDetail() != null) {
            result.setTerminationDetail(output.terminationDetail().toString());
        }
        JsonNode recoverySummary = validateRecoverySummary(output.recoverySummary());
        if (recoverySummary != null) {
            logRecoverySummary(simulationId, recoverySummary);
            result.setRecoveryDetail(recoverySummary.toString());
        }
        simulationMapper.insertSimulationResult(result);

        List<SimulationMetric> metrics = new ArrayList<>();
        metrics.add(
                metric(result.getId(), "SIMULATION_DURATION_SECONDS", "seconds", output.simulationDurationSeconds()));
        if (output.totalEvacuationTimeSeconds() != null) {
            metrics.add(metric(
                    result.getId(), "TOTAL_EVACUATION_TIME_SECONDS", "seconds", output.totalEvacuationTimeSeconds()));
        }
        if (output.averageEvacuationTimeSeconds() != null) {
            metrics.add(metric(
                    result.getId(),
                    "AVERAGE_EVACUATION_TIME_SECONDS",
                    "seconds",
                    output.averageEvacuationTimeSeconds()));
        }
        metrics.add(metric(result.getId(), "EVACUATED_PEOPLE", "people", output.evacuatedPeople()));
        metrics.add(metric(result.getId(), "REMAINING_PEOPLE", "people", output.remainingPeople()));
        metrics.add(metric(result.getId(), "MAX_DENSITY", "PERSON_PER_M2", output.maxDensity()));
        simulationMapper.insertSimulationMetrics(metrics);
        for (var chunk : run.timelineChunks()) {
            simulationMapper.insertTimeline(result.getId(), chunk.sequence(), chunk.frameData());
        }
        for (var chunk : run.heatmapChunks()) {
            simulationMapper.insertHeatmap(result.getId(), chunk.sequence(), chunk.densityData());
        }
        List<DetectedBottleneck> bottlenecks =
                bottleneckDetector.detect(run.heatmapChunks(), densityThresholdProvider.getCurrent());
        if (!bottlenecks.isEmpty()) {
            simulationMapper.insertDetectedBottlenecks(result.getId(), bottlenecks);
        }
        if (simulationMapper.markExecutionCompleted(simulationId) != 1) {
            throw new IllegalStateException("시뮬레이션 완료 상태를 저장하지 못했습니다.");
        }
    }

    private void markFailed(Long simulationId, String message) {
        markFailed(simulationId, message, null);
    }

    private void markFailed(Long simulationId, String message, SimulationFailureDetailResponse failureDetail) {
        String safeMessage = message == null || message.isBlank() ? "ENGINE_ERROR: 실행에 실패했습니다." : message;
        safeMessage = safeMessage.substring(0, Math.min(safeMessage.length(), MAX_FAILURE_MESSAGE_LENGTH));
        String finalMessage = safeMessage;
        String detailJson = null;
        if (failureDetail != null) {
            try {
                detailJson = objectMapper.writeValueAsString(failureDetail);
            } catch (JsonProcessingException exception) {
                log.warn("Could not serialize simulation {} failure detail", simulationId);
            }
        }
        String finalDetailJson = detailJson;
        transactionTemplate.executeWithoutResult(
                status -> simulationMapper.markExecutionFailed(simulationId, finalMessage, finalDetailJson));
    }

    private final class SimulationTask extends FutureTask<Void> {
        private final Long simulationId;
        private final AtomicBoolean started = new AtomicBoolean();
        private final AtomicBoolean capacityReleased = new AtomicBoolean();

        private SimulationTask(
                Long simulationId,
                SimulationSetupResponse setup,
                long queuedAtNanos,
                boolean readinessCheckPerformed,
                long readinessMs,
                long setupMs) {
            super(() -> {
                SimulationExecutionService.this.runJob(
                        simulationId, setup, queuedAtNanos, readinessCheckPerformed, readinessMs, setupMs);
                return null;
            });
            this.simulationId = simulationId;
        }

        @Override
        public void run() {
            started.set(true);
            try {
                super.run();
            } finally {
                releaseCapacity();
            }
        }

        @Override
        protected void done() {
            activeTasks.remove(simulationId, this);
            if (!started.get()) {
                releaseCapacity();
            }
        }

        private void releaseCapacity() {
            if (capacityReleased.compareAndSet(false, true)) {
                executionCapacity.release();
            }
        }
    }

    private static SimulationMetric metric(Long resultId, String type, String unit, double value) {
        SimulationMetric metric = new SimulationMetric();
        metric.setSimulationResultId(resultId);
        metric.setMetricType(type);
        metric.setUnit(unit);
        metric.setMetricValue(value);
        return metric;
    }

    static void validateEngineResult(
            EngineResult output, SimulationSetupResponse setup, double maxSimulationTimeSeconds) {
        if (output.engineVersion() == null || output.engineVersion().isBlank()) {
            throw new IllegalStateException("엔진 버전이 누락되었습니다.");
        }
        requireFiniteRange(output.simulationDurationSeconds(), 0, maxSimulationTimeSeconds, "모의시간");
        requireFiniteRange(output.frameIntervalSeconds(), 0.001, maxSimulationTimeSeconds, "프레임 간격");
        if (output.evacuatedPeople() == null
                || output.remainingPeople() == null
                || output.evacuatedPeople() < 0
                || output.remainingPeople() < 0
                || output.evacuatedPeople() + output.remainingPeople()
                        != setup.agentPositions().size()) {
            throw new IllegalStateException("대피·잔류 인원 합계가 초기 인원과 일치하지 않습니다.");
        }
        if (output.timelineChunkCount() == null || output.timelineChunkCount() < 1) {
            throw new IllegalStateException("타임라인 청크가 누락되었습니다.");
        }
        if (output.heatmapChunkCount() == null || output.heatmapChunkCount() < 1) {
            throw new IllegalStateException("히트맵 청크가 누락되었습니다.");
        }
        requireFiniteRange(output.maxDensity(), 0, setup.agentPositions().size(), "최대 밀도");
        if (output.averageEvacuationTimeSeconds() != null) {
            requireFiniteRange(output.averageEvacuationTimeSeconds(), 0, output.simulationDurationSeconds(), "평균 대피시간");
        } else if (output.evacuatedPeople() > 0) {
            throw new IllegalStateException("탈출자가 있지만 평균 대피시간이 누락되었습니다.");
        }

        if ("ALL_EVACUATED".equals(output.terminationReason())) {
            if (output.remainingPeople() != 0 || output.totalEvacuationTimeSeconds() == null) {
                throw new IllegalStateException("전원 대피 종료 결과가 인원 또는 대피시간과 일치하지 않습니다.");
            }
            requireFiniteRange(output.totalEvacuationTimeSeconds(), 0, output.simulationDurationSeconds(), "전원 대피시간");
        } else if ("MAX_DURATION".equals(output.terminationReason())) {
            if (output.remainingPeople() < 1
                    || output.totalEvacuationTimeSeconds() != null
                    || output.simulationDurationSeconds() < maxSimulationTimeSeconds - 0.02) {
                throw new IllegalStateException("최대 모의시간 종료 결과가 잔류 인원 또는 시간과 일치하지 않습니다.");
            }
        } else if ("STALLED".equals(output.terminationReason())) {
            if (output.remainingPeople() < 1
                    || output.totalEvacuationTimeSeconds() != null
                    || output.simulationDurationSeconds() <= 0) {
                throw new IllegalStateException("정체 종료 결과가 잔류 인원, 대피시간 또는 모의시간과 일치하지 않습니다.");
            }
        } else {
            throw new IllegalStateException("지원하지 않는 종료 사유입니다: " + output.terminationReason());
        }
        validateTerminationDetail(output, setup);
    }

    private static void validateTerminationDetail(EngineResult output, SimulationSetupResponse setup) {
        if (output.terminationDetail() == null) {
            if ("STALLED".equals(output.terminationReason())) {
                throw new IllegalStateException("정체 종료 결과에 종료 상세 정보가 누락되었습니다.");
            }
            return;
        }
        JsonNode detail = output.terminationDetail();
        if (!detail.isObject()
                || detail.path("schemaVersion").asInt(0) != 1
                || !detail.path("globalReason").isTextual()
                || !("GLOBAL_STALLED".equals(detail.path("globalReason").asText())
                        || "PARTIAL_STALLED".equals(detail.path("globalReason").asText()))
                || !detail.path("remainingPeople").isIntegralNumber()
                || detail.path("remainingPeople").longValue() != output.remainingPeople()
                || !detail.path("reasonCounts").isObject()
                || !detail.path("representativeAgents").isArray()) {
            throw new IllegalStateException("종료 상세 정보 형식이 올바르지 않습니다.");
        }
        JsonNode reasonCounts = detail.path("reasonCounts");
        long reasonTotal = 0L;
        var countFields = reasonCounts.fields();
        while (countFields.hasNext()) {
            var entry = countFields.next();
            if (!"EXIT_PORTAL_STUCK".equals(entry.getKey()) && !"ROUTE_FOLLOWING_STUCK".equals(entry.getKey())) {
                throw new IllegalStateException("종료 상세 정보의 사유 종류가 지원되지 않습니다.");
            }
            JsonNode count = entry.getValue();
            if (!count.isIntegralNumber() || !count.canConvertToLong() || count.longValue() < 1) {
                throw new IllegalStateException("종료 상세 정보의 사유 집계가 올바르지 않습니다.");
            }
            reasonTotal += count.longValue();
        }
        if (reasonTotal != output.remainingPeople()) {
            throw new IllegalStateException("종료 상세 정보의 사유 집계 합계가 잔류 인원과 일치하지 않습니다.");
        }
        JsonNode representatives = detail.path("representativeAgents");
        int maximumAgents = setup.agentPositions().size();
        if (representatives.size() < 1
                || representatives.size() > 5
                || representatives.size() > output.remainingPeople()) {
            throw new IllegalStateException("종료 상세 정보의 대표 에이전트 수가 올바르지 않습니다.");
        }
        java.util.Set<Long> seen = new java.util.HashSet<>();
        for (JsonNode agent : representatives) {
            if (!agent.isIntegralNumber() || !agent.canConvertToLong()) {
                throw new IllegalStateException("종료 상세 정보의 대표 에이전트가 올바르지 않습니다.");
            }
            long agentId = agent.longValue();
            if (agentId < 1 || agentId > maximumAgents || !seen.add(agentId)) {
                throw new IllegalStateException("종료 상세 정보의 대표 에이전트가 올바르지 않습니다.");
            }
        }
    }

    static JsonNode validateRecoverySummary(JsonNode recoverySummary) {
        if (recoverySummary == null) {
            return null;
        }
        if (!recoverySummary.isObject()) {
            return invalidRecoverySummary();
        }
        for (Iterator<String> names = recoverySummary.fieldNames(); names.hasNext(); ) {
            if (!RECOVERY_SUMMARY_FIELDS.contains(names.next())) {
                return invalidRecoverySummary();
            }
        }
        JsonNode schemaVersion = recoverySummary.path("schemaVersion");
        if (!schemaVersion.isIntegralNumber() || !schemaVersion.canConvertToInt() || schemaVersion.intValue() != 1) {
            return invalidRecoverySummary();
        }
        for (String counter : RECOVERY_COUNTER_FIELDS) {
            if (!isNonNegativeIntegral(recoverySummary.path(counter))) {
                return invalidRecoverySummary();
            }
        }
        if (!isFiniteNonNegativeNumber(recoverySummary.path("recoveryTimeSeconds"))) {
            return invalidRecoverySummary();
        }
        JsonNode exitLabels = recoverySummary.path("recoveredExitLabels");
        JsonNode exitIds = recoverySummary.path("recoveredExitIds");
        if (!exitLabels.isArray() || !exitIds.isArray()) {
            return invalidRecoverySummary();
        }
        for (JsonNode label : exitLabels) {
            if (!label.isIntegralNumber() || !label.canConvertToInt() || label.intValue() < 0) {
                return invalidRecoverySummary();
            }
        }
        for (JsonNode exitId : exitIds) {
            if (!isExitId(exitId)) {
                return invalidRecoverySummary();
            }
        }
        long recoveredGroups = recoverySummary.path("recoveredGroupCount").asLong();
        long recoveredAgents = recoverySummary.path("recoveredAgentCount").asLong();
        long recoveredMidRouteAgents =
                recoverySummary.path("recoveredMidRouteAgentCount").asLong();
        if (recoveredAgents != RECOVERY_GROUP_SIZE * recoveredGroups) {
            return invalidRecoverySummary();
        }
        if ((recoveredGroups + recoveredMidRouteAgents == 0) != (exitLabels.size() == 0 && exitIds.size() == 0)) {
            return invalidRecoverySummary();
        }
        JsonNode events = recoverySummary.path("events");
        if (!events.isArray()) {
            return invalidRecoverySummary();
        }
        Set<String> recoveredLabelKeys = new HashSet<>();
        for (JsonNode label : exitLabels) {
            recoveredLabelKeys.add(label.asText());
        }
        Set<String> recoveredIdKeys = new HashSet<>();
        for (JsonNode exitId : exitIds) {
            recoveredIdKeys.add(exitId.asText());
        }
        long recoveredGroupEventCount = 0;
        long recoveredMidRouteEventCount = 0;
        for (JsonNode event : events) {
            if (!isValidRecoveryEvent(event, recoveredLabelKeys, recoveredIdKeys)) {
                return invalidRecoverySummary();
            }
            if ("RECOVERED".equals(event.path("status").asText())) {
                int recoveredEventAgentCount = event.path("stableIds").size();
                if (recoveredEventAgentCount == 1) {
                    recoveredMidRouteEventCount += 1;
                } else {
                    recoveredGroupEventCount += 1;
                }
            }
        }
        if (recoveredGroupEventCount != recoveredGroups || recoveredMidRouteEventCount != recoveredMidRouteAgents) {
            return invalidRecoverySummary();
        }
        return recoverySummary;
    }

    private static boolean isValidRecoveryEvent(
            JsonNode event, Set<String> recoveredLabelKeys, Set<String> recoveredIdKeys) {
        if (event == null || !event.isObject()) {
            return false;
        }
        for (Iterator<String> names = event.fieldNames(); names.hasNext(); ) {
            if (!RECOVERY_EVENT_FIELDS.contains(names.next())) {
                return false;
            }
        }
        String status = event.path("status").asText("");
        if (!RECOVERY_EVENT_STATUSES.contains(status)) {
            return false;
        }
        boolean hasReasonCode = !event.path("reasonCode").isMissingNode();
        boolean hasExceptionClass = !event.path("exceptionClass").isMissingNode();
        if ("RECOVERED".equals(status)) {
            if (hasReasonCode || hasExceptionClass) {
                return false;
            }
        } else if ("RECOVERY_MUTATION_FAILED".equals(status)) {
            if (!hasReasonCode
                    || !"MUTATION_APPLY_FAILED".equals(event.path("reasonCode").asText())) {
                return false;
            }
            if (!hasExceptionClass
                    || !event.path("exceptionClass").isTextual()
                    || !RECOVERY_EXCEPTION_CLASS_PATTERN
                            .matcher(event.path("exceptionClass").asText())
                            .matches()) {
                return false;
            }
        } else {
            if (!hasReasonCode
                    || !RECOVERY_REASON_CODES.contains(event.path("reasonCode").asText())) {
                return false;
            }
            if (hasExceptionClass) {
                return false;
            }
        }
        if (!isFiniteNonNegativeNumber(event.path("timeSeconds"))
                || !isNonNegativeIntegral(event.path("iteration"))
                || !isNonNegativeIntegral(event.path("contextIndex"))
                || !isNonNegativeIntegral(event.path("exitLabel"))) {
            return false;
        }
        if (!isExitId(event.path("exitId")) || !isPoint(event.path("target"))) {
            return false;
        }
        JsonNode stableIds = event.path("stableIds");
        if (!stableIds.isArray() || !(stableIds.size() == 1 || stableIds.size() == RECOVERY_GROUP_SIZE)) {
            return false;
        }
        int eventAgentCount = stableIds.size();
        Set<Long> distinctStableIds = new HashSet<>();
        for (JsonNode stableId : stableIds) {
            if (!stableId.isIntegralNumber()
                    || !stableId.canConvertToLong()
                    || stableId.longValue() < 1
                    || !distinctStableIds.add(stableId.longValue())) {
                return false;
            }
        }
        for (String listField : RECOVERY_EVENT_POINT_LIST_FIELDS) {
            JsonNode list = event.path(listField);
            if (!list.isArray() || !(list.size() == 0 || list.size() == eventAgentCount)) {
                return false;
            }
            for (JsonNode item : list) {
                if ("oldTargets".equals(listField) && item.isNull()) {
                    continue;
                }
                if (!isPoint(item)) {
                    return false;
                }
            }
        }
        JsonNode seedNodeIds = event.path("seedNodeIds");
        if (!seedNodeIds.isArray() || !(seedNodeIds.size() == 0 || seedNodeIds.size() == eventAgentCount)) {
            return false;
        }
        for (JsonNode nodeId : seedNodeIds) {
            if (!isNonNegativeIntegral(nodeId)) {
                return false;
            }
        }
        if (!isNonNegativeIntegral(event.path("postRecoveryInvalidMoves"))
                || !isNonNegativeIntegral(event.path("postRecoveryFullRollbacks"))) {
            return false;
        }
        if ("RECOVERED".equals(status)) {
            return recoveredLabelKeys.contains(event.path("exitLabel").asText())
                    && recoveredIdKeys.contains(event.path("exitId").asText());
        }
        return true;
    }

    private static boolean isExitId(JsonNode node) {
        return node.isIntegralNumber() || (node.isTextual() && !node.asText().isBlank());
    }

    private static boolean isNonNegativeIntegral(JsonNode node) {
        return node.isIntegralNumber() && node.canConvertToLong() && node.longValue() >= 0;
    }

    private static boolean isFiniteNonNegativeNumber(JsonNode node) {
        return node.isNumber() && Double.isFinite(node.doubleValue()) && node.doubleValue() >= 0;
    }

    private static boolean isPoint(JsonNode node) {
        return node != null
                && node.isArray()
                && node.size() == 2
                && node.get(0).isNumber()
                && Double.isFinite(node.get(0).doubleValue())
                && node.get(1).isNumber()
                && Double.isFinite(node.get(1).doubleValue());
    }

    private static JsonNode invalidRecoverySummary() {
        log.warn("Simulation recovery summary has an invalid schema and will not be persisted");
        return null;
    }

    private static void logRecoverySummary(Long simulationId, JsonNode recoverySummary) {
        log.info(
                "simulation_recovery_phase simulationId={} recoveredAgentCount={} recoveredMidRouteAgentCount={} recoveryTimeSeconds={} recoveredExitLabels={} recoveredExitIds={}",
                simulationId,
                recoverySummary.path("recoveredAgentCount").asLong(),
                recoverySummary.path("recoveredMidRouteAgentCount").asLong(),
                recoverySummary.path("recoveryTimeSeconds").decimalValue(),
                recoverySummary.path("recoveredExitLabels").toString(),
                recoverySummary.path("recoveredExitIds").toString());
        for (JsonNode event : recoverySummary.path("events")) {
            if ("RECOVERY_MUTATION_FAILED".equals(event.path("status").asText())) {
                log.warn(
                        "Simulation {} recovery mutation failed exceptionClass={}",
                        simulationId,
                        event.path("exceptionClass").asText());
            }
        }
    }

    private static void requireFiniteRange(Double value, double minimum, double maximum, String label) {
        if (value == null || !Double.isFinite(value) || value < minimum || value > maximum) {
            throw new IllegalStateException(label + " 값이 유효하지 않습니다.");
        }
    }

    private static void validateExecutionSetup(SimulationSetupResponse setup) {
        if (!"REQUESTED".equals(setup.status())) {
            throw new SimulationConflictException("실행 요청 상태를 만들지 못했습니다.");
        }
        validateRoutingInputs(setup);
    }

    private static void validateRoutingSetup(SimulationSetupResponse setup) {
        if (!"DRAFT".equals(setup.status())) {
            throw new SimulationConflictException("DRAFT 상태에서만 경로를 검증할 수 있습니다.");
        }
        validateRoutingInputs(setup);
    }

    private static void validateRoutingInputs(SimulationSetupResponse setup) {
        if (setup.agentPositions().isEmpty()) {
            throw new InvalidSimulationGeometryException("에이전트를 한 명 이상 배치해야 합니다.");
        }
        if (setup.selectedExitIds().isEmpty()) {
            throw new InvalidSimulationGeometryException("출입구를 한 개 이상 선택해야 합니다.");
        }
        if (setup.drawing().outsideBoundary().size() < 3) {
            throw new InvalidSimulationGeometryException("유효한 외곽 영역이 필요합니다.");
        }
    }

    private static String routingFailureMessage(SimulationFailureDetailResponse failureDetail) {
        if (SimulationEngineRunner.ROUTING_ERROR_CODE.equals(failureDetail.code())) {
            return "Agent #%d의 시작 위치를 대피 경로에 연결할 수 없습니다.".formatted(failureDetail.agentId());
        }
        return NO_REACHABLE_EXIT_MESSAGE;
    }

    private TimelineChunkResponse normalizeLegacyTimeline(JsonNode root, int chunkSequence, Long simulationId) {
        JsonNode storedFrames = root.path("frames");
        if (!storedFrames.isArray() || storedFrames.isEmpty()) {
            throw new IllegalStateException("저장된 타임라인 프레임 형식이 올바르지 않습니다.");
        }
        var option = simulationMapper.findSimulationOption(simulationId);
        SimulationResult result = simulationMapper.findSimulationResult(simulationId);
        if (option == null || result == null || result.getFrameIntervalSeconds() == null) {
            throw new IllegalStateException("레거시 타임라인 메타데이터가 없습니다.");
        }
        int firstFrame = chunkSequence * LEGACY_TIMELINE_FRAMES_PER_CHUNK;
        List<TimelineFrameResponse> frames = new ArrayList<>();
        int localIndex = 0;
        for (JsonNode storedFrame : storedFrames) {
            List<TimelineAgentResponse> agents = new ArrayList<>();
            JsonNode storedAgents = storedFrame.path("agents");
            if (!storedAgents.isArray()) {
                throw new IllegalStateException("저장된 레거시 에이전트 형식이 올바르지 않습니다.");
            }
            for (JsonNode storedAgent : storedAgents) {
                if (!storedAgent.isArray() || storedAgent.size() != 3) {
                    throw new IllegalStateException("저장된 레거시 에이전트 좌표가 올바르지 않습니다.");
                }
                agents.add(new TimelineAgentResponse(
                        storedAgent.get(0).longValue() + 1,
                        storedAgent.get(1).decimalValue(),
                        storedAgent.get(2).decimalValue()));
            }
            int active = agents.size();
            frames.add(new TimelineFrameResponse(
                    firstFrame + localIndex,
                    storedFrame.path("timeSeconds").decimalValue(),
                    active,
                    option.getTotalPeople() - active,
                    List.copyOf(agents)));
            localIndex++;
        }
        return new TimelineChunkResponse(
                1,
                "FLOOR_PLAN",
                "METER",
                BigDecimal.valueOf(1.0 / result.getFrameIntervalSeconds().doubleValue()),
                chunkSequence,
                firstFrame,
                firstFrame + frames.size() - 1,
                List.copyOf(frames),
                Collections.<ExitEventResponse>emptyList());
    }
}
