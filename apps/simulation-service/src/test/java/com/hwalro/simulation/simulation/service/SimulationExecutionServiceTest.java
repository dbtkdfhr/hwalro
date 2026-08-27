package com.hwalro.simulation.simulation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hwalro.simulation.analysis.domain.DetectedBottleneck;
import com.hwalro.simulation.analysis.domain.DetectedBottleneck.RectangleGeometry;
import com.hwalro.simulation.analysis.service.BottleneckDetector;
import com.hwalro.simulation.analysis.service.DensityThresholdProvider;
import com.hwalro.simulation.analysis.service.DensityThresholdProvider.DensityThreshold;
import com.hwalro.simulation.common.jwt.JwtUser;
import com.hwalro.simulation.simulation.domain.Simulation;
import com.hwalro.simulation.simulation.domain.SimulationOption;
import com.hwalro.simulation.simulation.domain.SimulationResult;
import com.hwalro.simulation.simulation.dto.SimulationDtos.DrawingGeometryDto;
import com.hwalro.simulation.simulation.dto.SimulationDtos.ExitDto;
import com.hwalro.simulation.simulation.dto.SimulationDtos.PointDto;
import com.hwalro.simulation.simulation.dto.SimulationDtos.SimulationFailureDetailResponse;
import com.hwalro.simulation.simulation.dto.SimulationDtos.SimulationSetupResponse;
import com.hwalro.simulation.simulation.engine.SimulationEngineRunner;
import com.hwalro.simulation.simulation.engine.SimulationEngineRunner.EngineResult;
import com.hwalro.simulation.simulation.engine.SimulationEngineRunner.EngineRun;
import com.hwalro.simulation.simulation.engine.SimulationEngineRunner.EngineRunException;
import com.hwalro.simulation.simulation.engine.SimulationEngineRunner.HeatmapChunk;
import com.hwalro.simulation.simulation.engine.SimulationEngineRunner.TimelineChunk;
import com.hwalro.simulation.simulation.exception.InvalidSimulationGeometryException;
import com.hwalro.simulation.simulation.exception.SimulationConflictException;
import com.hwalro.simulation.simulation.exception.SimulationEngineUnavailableException;
import com.hwalro.simulation.simulation.mapper.SimulationMapper;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.SimpleTransactionStatus;
import org.springframework.transaction.support.TransactionTemplate;

@ExtendWith(MockitoExtension.class)
class SimulationExecutionServiceTest {
    @Mock
    private SimulationMapper simulationMapper;

    @Mock
    private SimulationService simulationService;

    @Mock
    private SimulationEngineRunner engineRunner;

    @Mock
    private ThreadPoolTaskExecutor executor;

    @Mock
    private DensityThresholdProvider densityThresholdProvider;

    @Mock
    private BottleneckDetector bottleneckDetector;

    private SimulationExecutionService service;
    private AtomicReference<Runnable> queued;
    private JwtUser user;

    @BeforeEach
    void setUp() {
        queued = new AtomicReference<>();
        service = new SimulationExecutionService(
                simulationMapper,
                simulationService,
                engineRunner,
                executor,
                new TransactionTemplate(new NoOpTransactionManager()),
                new ObjectMapper(),
                densityThresholdProvider,
                bottleneckDetector);
        user = new JwtUser(7L, Set.of("OPERATOR"));
    }

    @Test
    void validatesDraftRoutingWithoutChangingExecutionState() throws Exception {
        SimulationSetupResponse setup = validDraftSetup();
        when(simulationService.getAccessibleSimulation(21L, user)).thenReturn(simulation("DRAFT"));
        when(simulationService.getSetup(21L, user)).thenReturn(setup);
        Semaphore capacity = (Semaphore) ReflectionTestUtils.getField(service, "executionCapacity");
        int permitsBefore = capacity.availablePermits();

        var response = service.validateRouting(21L, user);

        assertThat(response.valid()).isTrue();
        assertThat(response.message()).isEqualTo("경로 검증에 성공했습니다. 시뮬레이션 실행을 요청합니다.");
        assertThat(response.failureDetail()).isNull();
        assertThat(capacity.availablePermits()).isEqualTo(permitsBefore);
        verify(engineRunner).validateRouting(21L, setup);
        verify(simulationMapper, never()).requestExecution(anyLong());
        verify(executor, never()).execute(any(Runnable.class));
    }

    @Test
    void returnsTypedRoutingFailureWithoutQueuingExecution() throws Exception {
        SimulationSetupResponse setup = validDraftSetup();
        SimulationFailureDetailResponse detail = new SimulationFailureDetailResponse(
                "NO_REACHABLE_SELECTED_EXIT",
                null,
                null,
                null,
                2L,
                List.of(1L, 2L),
                List.of(501L),
                "NO_EXIT_SEED_IN_OCCUPIED_COMPONENT");
        when(simulationService.getAccessibleSimulation(21L, user)).thenReturn(simulation("DRAFT"));
        when(simulationService.getSetup(21L, user)).thenReturn(setup);
        when(engineRunner.validateRouting(21L, setup)).thenReturn(detail);

        var response = service.validateRouting(21L, user);

        assertThat(response.valid()).isFalse();
        assertThat(response.message()).isEqualTo("선택한 출입구에 도달할 수 없는 구역이 있습니다. 도면과 출입구를 확인해 주세요.");
        assertThat(response.failureDetail()).isSameAs(detail);
        verify(simulationMapper, never()).requestExecution(anyLong());
        verify(executor, never()).execute(any(Runnable.class));
    }

    @Test
    void rejectsRoutingValidationOutsideDraftState() {
        when(simulationService.getAccessibleSimulation(21L, user)).thenReturn(simulation("FAILED"));

        assertThatThrownBy(() -> service.validateRouting(21L, user))
                .isInstanceOf(SimulationConflictException.class)
                .hasMessageContaining("DRAFT");

        verify(engineRunner, never()).assertAvailable();
    }

    @Test
    void exposesRoutingValidationTimeoutAsEngineUnavailable() throws Exception {
        SimulationSetupResponse setup = validDraftSetup();
        when(simulationService.getAccessibleSimulation(21L, user)).thenReturn(simulation("DRAFT"));
        when(simulationService.getSetup(21L, user)).thenReturn(setup);
        when(engineRunner.validateRouting(21L, setup)).thenThrow(new EngineRunException("timeout", true));

        assertThatThrownBy(() -> service.validateRouting(21L, user))
                .isInstanceOf(SimulationEngineUnavailableException.class)
                .hasMessageContaining("시간이 초과");
    }

    @Test
    void executesAndPersistsMetricsAndTimeline() throws Exception {
        stubDraftAndRequestedStatus();
        captureWorker();
        when(simulationMapper.requestExecution(21L)).thenReturn(1);
        when(simulationService.getSetup(21L, user)).thenReturn(validSetup());
        when(simulationMapper.markExecutionRunning(21L)).thenReturn(1);
        when(engineRunner.run(eq(21L), any())).thenReturn(successfulRun());
        when(simulationMapper.insertSimulationResult(any())).thenAnswer(invocation -> {
            SimulationResult result = invocation.getArgument(0);
            result.setId(31L);
            return 1;
        });
        DensityThreshold threshold = new DensityThreshold(BigDecimal.valueOf(3.5), "PERSON_PER_M2");
        DetectedBottleneck bottleneck = new DetectedBottleneck(
                1, 3.0, 9.0, 4.2, 3.5, new RectangleGeometry("RECTANGLE", "병목 1", 1.0, 2.0, 3.0, 4.0), "GRID_COUNT_V1");
        when(densityThresholdProvider.getCurrent()).thenReturn(threshold);
        when(bottleneckDetector.detect(any(), eq(threshold))).thenReturn(List.of(bottleneck));
        when(simulationMapper.markExecutionCompleted(21L)).thenReturn(1);

        var response = service.execute(21L, user);
        queued.get().run();

        assertThat(response.status()).isEqualTo("REQUESTED");
        verify(simulationMapper).insertSimulationMetrics(any());
        verify(simulationMapper).insertTimeline(31L, 0, "{\"chunkSequence\":0,\"frames\":[]}");
        verify(simulationMapper).insertHeatmap(31L, 0, "{\"chunkSequence\":0,\"frames\":[]}");
        verify(bottleneckDetector).detect(any(), eq(threshold));
        InOrder persistenceOrder = inOrder(simulationMapper);
        persistenceOrder.verify(simulationMapper).insertDetectedBottlenecks(31L, List.of(bottleneck));
        persistenceOrder.verify(simulationMapper).markExecutionCompleted(21L);
    }

    @Test
    void completesWithoutBottleneckInsertWhenDetectorFindsNone() throws Exception {
        stubDraftAndRequestedStatus();
        captureWorker();
        when(simulationMapper.requestExecution(21L)).thenReturn(1);
        when(simulationService.getSetup(21L, user)).thenReturn(validSetup());
        when(simulationMapper.markExecutionRunning(21L)).thenReturn(1);
        when(engineRunner.run(eq(21L), any())).thenReturn(successfulRun());
        when(simulationMapper.insertSimulationResult(any())).thenAnswer(invocation -> {
            SimulationResult result = invocation.getArgument(0);
            result.setId(31L);
            return 1;
        });
        DensityThreshold threshold = new DensityThreshold(BigDecimal.valueOf(3.5), "PERSON_PER_M2");
        when(densityThresholdProvider.getCurrent()).thenReturn(threshold);
        when(bottleneckDetector.detect(any(), eq(threshold))).thenReturn(List.of());
        when(simulationMapper.markExecutionCompleted(21L)).thenReturn(1);

        service.execute(21L, user);
        queued.get().run();

        verify(simulationMapper, never()).insertDetectedBottlenecks(anyLong(), any());
        verify(simulationMapper).markExecutionCompleted(21L);
    }

    @Test
    void acceptsMaxDurationMatchingTheConfiguredRunLimit() {
        SimulationExecutionService.validateEngineResult(
                new EngineResult("1.4.2", "MAX_DURATION", 400.0, 0, 1, null, null, 1.0, 1, 1, 1.0, null, null),
                validSetup(),
                400.0);
    }

    @Test
    void rejectsStalledResultWithoutTerminationDetail() {
        assertThatThrownBy(() -> SimulationExecutionService.validateEngineResult(
                        new EngineResult("1.4.2", "STALLED", 10.0, 0, 1, null, null, 1.0, 1, 1, 1.0, null, null),
                        validSetup(),
                        400.0))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("종료 상세 정보가 누락");
    }

    @Test
    void rejectsTerminationDetailWhoseRemainingPeopleDisagreesWithTheResult() throws JsonProcessingException {
        JsonNode detail = new ObjectMapper()
                .readTree(
                        """
                        {"schemaVersion":1,"globalReason":"GLOBAL_STALLED","remainingPeople":3,
                         "reasonCounts":{"ROUTE_FOLLOWING_STUCK":3},"representativeAgents":[1,2]}
                        """);
        assertThatThrownBy(() -> SimulationExecutionService.validateEngineResult(
                        new EngineResult("1.4.2", "STALLED", 10.0, 0, 2, null, null, 1.0, 1, 1, 1.0, detail, null),
                        twoAgentSetup(),
                        400.0))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("종료 상세 정보 형식");
    }

    @Test
    void rejectsTerminationDetailWhoseReasonCountsDisagreeWithRemainingPeople() throws JsonProcessingException {
        JsonNode detail = new ObjectMapper()
                .readTree(
                        """
                        {"schemaVersion":1,"globalReason":"GLOBAL_STALLED","remainingPeople":2,
                         "reasonCounts":{"ROUTE_FOLLOWING_STUCK":1},"representativeAgents":[1,2]}
                        """);
        assertThatThrownBy(() -> SimulationExecutionService.validateEngineResult(
                        new EngineResult("1.4.2", "STALLED", 10.0, 0, 2, null, null, 1.0, 1, 1, 1.0, detail, null),
                        twoAgentSetup(),
                        400.0))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("사유 집계 합계");
    }

    @Test
    void rejectsTerminationDetailWithInvalidRepresentativeAgents() throws JsonProcessingException {
        JsonNode duplicated = new ObjectMapper()
                .readTree(
                        """
                        {"schemaVersion":1,"globalReason":"GLOBAL_STALLED","remainingPeople":2,
                         "reasonCounts":{"ROUTE_FOLLOWING_STUCK":2},"representativeAgents":[1,1]}
                        """);
        assertThatThrownBy(() -> SimulationExecutionService.validateEngineResult(
                        new EngineResult("1.4.2", "STALLED", 10.0, 0, 2, null, null, 1.0, 1, 1, 1.0, duplicated, null),
                        twoAgentSetup(),
                        400.0))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("대표 에이전트");

        JsonNode outOfRange = new ObjectMapper()
                .readTree(
                        """
                        {"schemaVersion":1,"globalReason":"GLOBAL_STALLED","remainingPeople":2,
                         "reasonCounts":{"ROUTE_FOLLOWING_STUCK":2},"representativeAgents":[3]}
                        """);
        assertThatThrownBy(() -> SimulationExecutionService.validateEngineResult(
                        new EngineResult("1.4.2", "STALLED", 10.0, 0, 2, null, null, 1.0, 1, 1, 1.0, outOfRange, null),
                        twoAgentSetup(),
                        400.0))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("대표 에이전트");
    }

    @Test
    void rejectsTerminationDetailWithUnsupportedReasonKey() throws JsonProcessingException {
        JsonNode detail = new ObjectMapper()
                .readTree(
                        """
                        {"schemaVersion":1,"globalReason":"GLOBAL_STALLED","remainingPeople":2,
                         "reasonCounts":{"PHYSICAL_CONGESTION":2},"representativeAgents":[1,2]}
                        """);
        assertThatThrownBy(() -> SimulationExecutionService.validateEngineResult(
                        new EngineResult("1.4.2", "STALLED", 10.0, 0, 2, null, null, 1.0, 1, 1, 1.0, detail, null),
                        twoAgentSetup(),
                        400.0))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("사유 종류");
    }

    @Test
    void acceptsValidRecoverySummary() throws JsonProcessingException {
        JsonNode valid = new ObjectMapper().readTree(VALID_RECOVERY_SUMMARY_JSON);

        assertThat(SimulationExecutionService.validateRecoverySummary(valid)).isSameAs(valid);
        assertThat(SimulationExecutionService.validateRecoverySummary(null)).isNull();
    }

    @Test
    void acceptsGroupAndSingleAgentMidRouteRecoverySummary() throws JsonProcessingException {
        JsonNode valid = new ObjectMapper()
                .readTree(
                        """
                        {"schemaVersion":1,"scanCount":2,"eligibleGroupCount":1,"skippedEligibleGroupCount":0,
                         "infeasibleScanCount":1,"recoveredGroupCount":1,"recoveredAgentCount":3,
                         "recoveredMidRouteAgentCount":1,"recoveryTimeSeconds":5.5,
                         "recoveredExitLabels":[0,1],"recoveredExitIds":[501,502],
                         "attemptedGroupSignatures":1,
                         "events":[
                           {"timeSeconds":5.5,"iteration":550,"contextIndex":0,"exitId":501,"exitLabel":0,
                            "target":[9.7,4.0],"stableIds":[1,2,3],
                            "oldTargets":[[9.7,4.0],[9.7,4.0],[9.7,4.0]],
                            "newTargets":[[10.0,3.75],[10.0,4.0],[10.0,4.25]],
                            "newApproaches":[[9.7,3.75],[9.7,4.0],[9.7,4.25]],"seedNodeIds":[11,12,13],
                            "status":"RECOVERED","postRecoveryInvalidMoves":0,"postRecoveryFullRollbacks":0},
                           {"timeSeconds":6.0,"iteration":600,"contextIndex":0,"exitId":502,"exitLabel":1,
                            "target":[3.0,3.0],"stableIds":[4],"oldTargets":[[3.0,3.0]],
                            "newTargets":[[4.0,4.0]],"newApproaches":[],"seedNodeIds":[],
                            "status":"RECOVERED","postRecoveryInvalidMoves":0,"postRecoveryFullRollbacks":0},
                           {"timeSeconds":6.5,"iteration":650,"contextIndex":0,"exitId":502,"exitLabel":1,
                            "target":[4.0,4.0],"stableIds":[5],"oldTargets":[[4.0,4.0]],
                            "newTargets":[],"newApproaches":[],"seedNodeIds":[],
                            "status":"RECOVERY_INFEASIBLE","reasonCode":"REROUTE_UNCHANGED",
                            "postRecoveryInvalidMoves":0,"postRecoveryFullRollbacks":0}]}
                        """);

        assertThat(SimulationExecutionService.validateRecoverySummary(valid)).isSameAs(valid);
    }

    @Test
    void rejectsRecoverySummaryWithMalformedEvents() throws JsonProcessingException {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode unknownField = mapper.readTree(VALID_RECOVERY_SUMMARY_JSON.replace(
                "\"seedNodeIds\":[11,12,13]", "\"seedNodeIds\":[11,12,13],\"message\":\"secret failure detail\""));
        JsonNode rawStack = mapper.readTree(VALID_RECOVERY_SUMMARY_JSON.replace(
                "\"seedNodeIds\":[11,12,13]", "\"seedNodeIds\":[11,12,13],\"stackTrace\":\"boom\""));
        JsonNode badStatus = mapper.readTree(
                VALID_RECOVERY_SUMMARY_JSON.replace("\"status\":\"RECOVERED\"", "\"status\":\"PANIC\""));
        JsonNode badReasonCode = mapper.readTree(VALID_RECOVERY_SUMMARY_JSON.replace(
                "\"status\":\"RECOVERED\"", "\"status\":\"RECOVERY_INFEASIBLE\",\"reasonCode\":\"BOGUS\""));
        JsonNode oversizedStableIds = mapper.readTree(
                VALID_RECOVERY_SUMMARY_JSON.replace("\"stableIds\":[1,2,3]", "\"stableIds\":[1,2,3,4]"));
        JsonNode mismatchedCounters = mapper.readTree(
                VALID_RECOVERY_SUMMARY_JSON.replace("\"recoveredAgentCount\":3", "\"recoveredAgentCount\":5"));
        JsonNode unknownTopLevel = mapper.readTree(VALID_RECOVERY_SUMMARY_JSON.replace(
                "\"attemptedGroupSignatures\":1", "\"attemptedGroupSignatures\":1,\"debugDump\":{}"));

        assertThat(SimulationExecutionService.validateRecoverySummary(unknownField))
                .isNull();
        assertThat(SimulationExecutionService.validateRecoverySummary(rawStack)).isNull();
        assertThat(SimulationExecutionService.validateRecoverySummary(badStatus))
                .isNull();
        assertThat(SimulationExecutionService.validateRecoverySummary(badReasonCode))
                .isNull();
        assertThat(SimulationExecutionService.validateRecoverySummary(oversizedStableIds))
                .isNull();
        assertThat(SimulationExecutionService.validateRecoverySummary(mismatchedCounters))
                .isNull();
        assertThat(SimulationExecutionService.validateRecoverySummary(unknownTopLevel))
                .isNull();
    }

    @Test
    void rejectsNonCanonicalSchemaVersionAndExceptionClass() throws JsonProcessingException {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode stringSchemaVersion =
                mapper.readTree(VALID_RECOVERY_SUMMARY_JSON.replace("\"schemaVersion\":1", "\"schemaVersion\":\"1\""));
        JsonNode floatSchemaVersion =
                mapper.readTree(VALID_RECOVERY_SUMMARY_JSON.replace("\"schemaVersion\":1", "\"schemaVersion\":1.0"));
        String mutationBase = VALID_RECOVERY_SUMMARY_JSON.replace(
                "\"status\":\"RECOVERED\"",
                "\"status\":\"RECOVERY_MUTATION_FAILED\","
                        + "\"reasonCode\":\"MUTATION_APPLY_FAILED\","
                        + "\"exceptionClass\":\"RuntimeError\"");
        JsonNode suffixedExceptionClass =
                mapper.readTree(mutationBase.replace("RuntimeError", "RuntimeError+restoreFailed=1"));
        JsonNode colonInExceptionClass =
                mapper.readTree(mutationBase.replace("RuntimeError", "RuntimeError: secret detail"));
        JsonNode spaceInExceptionClass =
                mapper.readTree(mutationBase.replace("RuntimeError", "java.lang.Runtime Error"));

        assertThat(SimulationExecutionService.validateRecoverySummary(stringSchemaVersion))
                .isNull();
        assertThat(SimulationExecutionService.validateRecoverySummary(floatSchemaVersion))
                .isNull();
        assertThat(SimulationExecutionService.validateRecoverySummary(suffixedExceptionClass))
                .isNull();
        assertThat(SimulationExecutionService.validateRecoverySummary(colonInExceptionClass))
                .isNull();
        assertThat(SimulationExecutionService.validateRecoverySummary(spaceInExceptionClass))
                .isNull();

        JsonNode qualifiedException = mapper.readTree(
                """
                {"schemaVersion":1,"scanCount":1,"eligibleGroupCount":1,"skippedEligibleGroupCount":0,
                 "infeasibleScanCount":0,"recoveredGroupCount":0,"recoveredAgentCount":0,
                 "recoveredMidRouteAgentCount":0,
                 "recoveryTimeSeconds":0.5,"recoveredExitLabels":[],"recoveredExitIds":[],
                 "attemptedGroupSignatures":1,
                 "events":[{"timeSeconds":0.5,"iteration":50,"contextIndex":0,"exitId":501,
                            "exitLabel":1,"target":[1.0,1.0],"stableIds":[1,2,3],
                            "oldTargets":[],"newTargets":[],"newApproaches":[],"seedNodeIds":[],
                            "status":"RECOVERY_MUTATION_FAILED","reasonCode":"MUTATION_APPLY_FAILED",
                            "exceptionClass":"com.hwalro.simulation.EngineException",
                            "postRecoveryInvalidMoves":0,"postRecoveryFullRollbacks":1}]}
                """);
        assertThat(SimulationExecutionService.validateRecoverySummary(qualifiedException))
                .isSameAs(qualifiedException);
    }

    @Test
    void rejectsInvalidRecoverySummaryWithoutFailingTheResult() throws JsonProcessingException {
        ObjectMapper mapper = new ObjectMapper();
        JsonNode invalidSchema = mapper.readTree(
                """
                {"schemaVersion":2,"recoveredAgentCount":3,"recoveryTimeSeconds":0.5,"recoveredExitLabels":["1"],"recoveredExitIds":[501],"events":[]}
                """);
        JsonNode wrongType = mapper.readTree(
                """
                {"schemaVersion":1,"recoveredAgentCount":"three","recoveryTimeSeconds":0.5,"recoveredExitLabels":["1"],"recoveredExitIds":[501],"events":[]}
                """);
        JsonNode negativeTime = mapper.readTree(
                """
                {"schemaVersion":1,"recoveredAgentCount":3,"recoveryTimeSeconds":-0.5,"recoveredExitLabels":["1"],"recoveredExitIds":[501],"events":[]}
                """);
        JsonNode missingArrays = mapper.readTree(
                """
                {"schemaVersion":1,"recoveredAgentCount":3,"recoveryTimeSeconds":0.5}
                """);

        assertThat(SimulationExecutionService.validateRecoverySummary(invalidSchema))
                .isNull();
        assertThat(SimulationExecutionService.validateRecoverySummary(wrongType))
                .isNull();
        assertThat(SimulationExecutionService.validateRecoverySummary(negativeTime))
                .isNull();
        assertThat(SimulationExecutionService.validateRecoverySummary(missingArrays))
                .isNull();
    }

    @Test
    void warnsOnInvalidRecoverySummaryAndStaysSilentWhenAbsent() throws JsonProcessingException {
        JsonNode invalid = new ObjectMapper()
                .readTree(
                        """
                        {"schemaVersion":1,"recoveredAgentCount":-1,"recoveryTimeSeconds":0.5,
                         "recoveredExitLabels":["1"],"recoveredExitIds":[501],"events":[]}
                        """);
        Logger logger = (Logger) LoggerFactory.getLogger(SimulationExecutionService.class);
        Level originalLevel = logger.getLevel();
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.setLevel(Level.WARN);
        logger.addAppender(appender);
        try {
            assertThat(SimulationExecutionService.validateRecoverySummary(invalid))
                    .isNull();
            assertThat(SimulationExecutionService.validateRecoverySummary(null)).isNull();
        } finally {
            logger.detachAppender(appender);
            logger.setLevel(originalLevel);
            appender.stop();
        }

        var messages =
                appender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
        assertThat(messages).singleElement().satisfies(message -> assertThat(message)
                .contains("recovery summary"));
    }

    @Test
    void persistsRecoveryDetailWhenEngineReturnsValidRecoverySummary() throws Exception {
        stubRecoveryExecution(runWithRecoverySummary());

        service.execute(21L, user);
        queued.get().run();

        ArgumentCaptor<SimulationResult> persisted = ArgumentCaptor.forClass(SimulationResult.class);
        verify(simulationMapper).insertSimulationResult(persisted.capture());
        assertThat(persisted.getValue().getRecoveryDetail()).contains("\"recoveredAgentCount\":3");
        assertThat(persisted.getValue().getTerminationDetail()).isNull();
    }

    @Test
    void doesNotPersistInvalidRecoverySummary() throws Exception {
        JsonNode invalid = new ObjectMapper()
                .readTree(
                        """
                        {"schemaVersion":1,"recoveredAgentCount":3,"recoveryTimeSeconds":-1.0,
                         "recoveredExitLabels":["1"],"recoveredExitIds":[501],"events":[]}
                        """);
        stubRecoveryExecution(new EngineRun(
                new EngineResult("1.4.2", "ALL_EVACUATED", 12.5, 1, 0, 12.5, 8.0, 1.0, 1, 1, 1.0, null, invalid),
                List.of(new TimelineChunk(0, "{\"chunkSequence\":0,\"frames\":[]}")),
                List.of(new HeatmapChunk(0, "{\"chunkSequence\":0,\"frames\":[]}")),
                400.0));

        service.execute(21L, user);
        queued.get().run();

        ArgumentCaptor<SimulationResult> persisted = ArgumentCaptor.forClass(SimulationResult.class);
        verify(simulationMapper).insertSimulationResult(persisted.capture());
        assertThat(persisted.getValue().getRecoveryDetail()).isNull();
    }

    @Test
    void doesNotPersistSummaryWithMalformedEventCarryingRawFailureDetails() throws Exception {
        JsonNode malformed = new ObjectMapper()
                .readTree(VALID_RECOVERY_SUMMARY_JSON.replace(
                        "\"seedNodeIds\":[11,12,13]",
                        "\"seedNodeIds\":[11,12,13],"
                                + "\"message\":\"RuntimeError: secret failure detail\","
                                + "\"stackTrace\":[\"at java.lang.Thread.run\"]"));
        stubRecoveryExecution(new EngineRun(
                new EngineResult("1.4.2", "ALL_EVACUATED", 12.5, 1, 0, 12.5, 8.0, 1.0, 1, 1, 1.0, null, malformed),
                List.of(new TimelineChunk(0, "{\"chunkSequence\":0,\"frames\":[]}")),
                List.of(new HeatmapChunk(0, "{\"chunkSequence\":0,\"frames\":[]}")),
                400.0));

        service.execute(21L, user);
        queued.get().run();

        ArgumentCaptor<SimulationResult> persisted = ArgumentCaptor.forClass(SimulationResult.class);
        verify(simulationMapper).insertSimulationResult(persisted.capture());
        assertThat(persisted.getValue().getRecoveryDetail()).isNull();
        assertThat(persisted.getValue().getTerminationDetail()).isNull();
    }

    @Test
    void warnsOnlyExceptionClassWhenRecoveryMutationFails() throws Exception {
        JsonNode summary = new ObjectMapper()
                .readTree(
                        """
                        {"schemaVersion":1,"scanCount":1,"eligibleGroupCount":1,"skippedEligibleGroupCount":0,
                         "infeasibleScanCount":0,"recoveredGroupCount":0,"recoveredAgentCount":0,
                         "recoveredMidRouteAgentCount":0,
                         "recoveryTimeSeconds":0.5,"recoveredExitLabels":[],"recoveredExitIds":[],
                         "attemptedGroupSignatures":1,
                         "events":[{"timeSeconds":0.5,"iteration":50,"contextIndex":0,"exitId":501,
                                    "exitLabel":1,"target":[1.0,1.0],"stableIds":[1,2,3],
                                    "oldTargets":[],"newTargets":[],"newApproaches":[],"seedNodeIds":[],
                                    "status":"RECOVERY_MUTATION_FAILED","reasonCode":"MUTATION_APPLY_FAILED",
                                    "exceptionClass":"RuntimeException","postRecoveryInvalidMoves":0,
                                    "postRecoveryFullRollbacks":1}]}
                        """);
        stubRecoveryExecution(new EngineRun(
                new EngineResult("1.4.2", "ALL_EVACUATED", 12.5, 1, 0, 12.5, 8.0, 1.0, 1, 1, 1.0, null, summary),
                List.of(new TimelineChunk(0, "{\"chunkSequence\":0,\"frames\":[]}")),
                List.of(new HeatmapChunk(0, "{\"chunkSequence\":0,\"frames\":[]}")),
                400.0));
        Logger logger = (Logger) LoggerFactory.getLogger(SimulationExecutionService.class);
        Level originalLevel = logger.getLevel();
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.setLevel(Level.WARN);
        logger.addAppender(appender);
        try {
            service.execute(21L, user);
            queued.get().run();
        } finally {
            logger.detachAppender(appender);
            logger.setLevel(originalLevel);
            appender.stop();
        }

        var messages =
                appender.list.stream().map(ILoggingEvent::getFormattedMessage).toList();
        assertThat(messages).singleElement().satisfies(message -> assertThat(message)
                .contains("exceptionClass=RuntimeException"));
    }

    @Test
    void cachesSuccessfulEngineReadinessForTheServiceLifetime() {
        stubTwoExecutionRequests();

        service.execute(21L, user);
        service.execute(21L, user);

        verify(engineRunner, times(1)).assertAvailable();
        verify(executor, times(2)).execute(any(Runnable.class));
    }

    @Test
    void retriesEngineReadinessAfterAFailedCheck() {
        when(simulationMapper.updateExecutionProfiles(21L, "SFM_DEFAULT_V2", "HAZARD_RADIAL_EXP_V3"))
                .thenReturn(1);
        when(simulationMapper.requestExecution(21L)).thenReturn(1);
        when(simulationService.getSetup(21L, user)).thenReturn(validSetup());
        when(simulationService.getAccessibleSimulation(21L, user))
                .thenReturn(simulation("DRAFT"), simulation("DRAFT"), simulation("REQUESTED"));
        doThrow(new SimulationEngineUnavailableException("not ready"))
                .doNothing()
                .when(engineRunner)
                .assertAvailable();

        assertThatThrownBy(() -> service.execute(21L, user)).isInstanceOf(SimulationEngineUnavailableException.class);
        var response = service.execute(21L, user);

        assertThat(response.status()).isEqualTo("REQUESTED");
        verify(engineRunner, times(2)).assertAvailable();
        verify(simulationMapper, times(1)).requestExecution(21L);
    }

    @Test
    void sharesOneReadinessCheckAcrossConcurrentFirstRequests() throws Exception {
        CountDownLatch readinessEntered = new CountDownLatch(1);
        CountDownLatch bothRequestsStarted = new CountDownLatch(2);
        CountDownLatch releaseReadiness = new CountDownLatch(1);
        ThreadLocal<Integer> accessCount = ThreadLocal.withInitial(() -> 0);
        when(simulationMapper.updateExecutionProfiles(21L, "SFM_DEFAULT_V2", "HAZARD_RADIAL_EXP_V3"))
                .thenReturn(1);
        when(simulationMapper.requestExecution(21L)).thenReturn(1);
        when(simulationService.getSetup(21L, user)).thenReturn(validSetup());
        when(simulationService.getAccessibleSimulation(21L, user)).thenAnswer(invocation -> {
            int count = accessCount.get();
            accessCount.set(count + 1);
            if (count == 0) {
                bothRequestsStarted.countDown();
            }
            return simulation(count == 0 ? "DRAFT" : "REQUESTED");
        });
        doAnswer(invocation -> {
                    readinessEntered.countDown();
                    if (!releaseReadiness.await(5, TimeUnit.SECONDS)) {
                        throw new AssertionError("readiness check was not released");
                    }
                    return null;
                })
                .when(engineRunner)
                .assertAvailable();

        ExecutorService callers = Executors.newFixedThreadPool(2);
        try {
            var first = callers.submit(() -> service.execute(21L, user));
            assertThat(readinessEntered.await(5, TimeUnit.SECONDS)).isTrue();
            var second = callers.submit(() -> service.execute(21L, user));
            assertThat(bothRequestsStarted.await(5, TimeUnit.SECONDS)).isTrue();
            releaseReadiness.countDown();

            assertThat(first.get(5, TimeUnit.SECONDS).status()).isEqualTo("REQUESTED");
            assertThat(second.get(5, TimeUnit.SECONDS).status()).isEqualTo("REQUESTED");
        } finally {
            releaseReadiness.countDown();
            callers.shutdownNow();
        }

        verify(engineRunner, times(1)).assertAvailable();
        verify(executor, times(2)).execute(any(Runnable.class));
    }

    @Test
    void rejectsExecutionWithoutAgentsBeforeWorkerStarts() throws Exception {
        stubDraftAndRequestedStatus();
        when(simulationMapper.requestExecution(21L)).thenReturn(1);
        SimulationSetupResponse invalid = setup(List.of(), List.of(501L));
        when(simulationService.getSetup(21L, user)).thenReturn(invalid);

        assertThatThrownBy(() -> service.execute(21L, user)).isInstanceOf(InvalidSimulationGeometryException.class);

        verify(engineRunner, never()).run(anyLong(), any());
    }

    @Test
    void rejectsDuplicateExecution() throws Exception {
        stubDraftAndRequestedStatus();
        when(simulationMapper.requestExecution(21L)).thenReturn(0);

        assertThatThrownBy(() -> service.execute(21L, user)).isInstanceOf(SimulationConflictException.class);

        verify(simulationService, never()).getSetup(21L, user);
        verify(engineRunner, never()).run(anyLong(), any());
    }

    @Test
    void marksEngineTimeoutAsFailed() throws Exception {
        stubDraftAndRequestedStatus();
        captureWorker();
        when(simulationMapper.requestExecution(21L)).thenReturn(1);
        when(simulationService.getSetup(21L, user)).thenReturn(validSetup());
        when(simulationMapper.markExecutionRunning(21L)).thenReturn(1);
        when(engineRunner.run(eq(21L), any())).thenThrow(new EngineRunException("ENGINE_TIMEOUT: limit", true));

        service.execute(21L, user);
        queued.get().run();

        verify(simulationMapper).markExecutionFailed(eq(21L), contains("ENGINE_TIMEOUT"), isNull());
    }

    @Test
    void compensatesRejectedWorkerSubmissionAsFailed() {
        stubDraftAndRequestedStatus();
        when(simulationMapper.requestExecution(21L)).thenReturn(1);
        when(simulationService.getSetup(21L, user)).thenReturn(validSetup());
        doAnswer(invocation -> {
                    throw new org.springframework.core.task.TaskRejectedException("stopped");
                })
                .when(executor)
                .execute(any(Runnable.class));

        assertThatThrownBy(() -> service.execute(21L, user)).isInstanceOf(SimulationEngineUnavailableException.class);

        verify(simulationMapper).markExecutionFailed(eq(21L), contains("SERVICE_UNAVAILABLE"), isNull());
    }

    @Test
    void releasesCapacityWhenExecutorThrowsAnotherRuntimeException() {
        stubDraftAndRequestedStatus();
        when(simulationMapper.requestExecution(21L)).thenReturn(1);
        when(simulationService.getSetup(21L, user)).thenReturn(validSetup());
        doAnswer(invocation -> {
                    throw new IllegalStateException("executor stopped");
                })
                .when(executor)
                .execute(any(Runnable.class));
        Semaphore capacity = (Semaphore) ReflectionTestUtils.getField(service, "executionCapacity");
        int availableBefore = capacity.availablePermits();

        assertThatThrownBy(() -> service.execute(21L, user)).isInstanceOf(SimulationEngineUnavailableException.class);

        assertThat(capacity.availablePermits()).isEqualTo(availableBefore);
        verify(simulationMapper).markExecutionFailed(eq(21L), contains("SERVICE_UNAVAILABLE"), isNull());
    }

    @Test
    void cancelsQueuedExecutionAndReleasesCapacity() {
        stubDraftAndRequestedStatus();
        captureWorker();
        when(simulationMapper.requestExecution(21L)).thenReturn(1);
        when(simulationService.getSetup(21L, user)).thenReturn(validSetup());
        when(simulationMapper.cancelExecution(21L)).thenReturn(1);
        ThreadPoolExecutor threadPool = mock(ThreadPoolExecutor.class);
        when(executor.getThreadPoolExecutor()).thenReturn(threadPool);

        service.execute(21L, user);
        Semaphore capacity = (Semaphore) ReflectionTestUtils.getField(service, "executionCapacity");
        assertThat(capacity.availablePermits()).isEqualTo(23);
        when(simulationService.getAccessibleSimulation(21L, user))
                .thenReturn(simulation("REQUESTED"), simulation("CANCELLED"));

        var response = service.cancel(21L, user);

        assertThat(response.status()).isEqualTo("CANCELLED");
        assertThat(((Future<?>) queued.get()).isCancelled()).isTrue();
        assertThat(capacity.availablePermits()).isEqualTo(24);
        verify(threadPool).remove(queued.get());
    }

    @Test
    void interruptsRunningExecutionWhenCancelled() throws Exception {
        stubDraftAndRequestedStatus();
        captureWorker();
        when(simulationMapper.requestExecution(21L)).thenReturn(1);
        when(simulationService.getSetup(21L, user)).thenReturn(validSetup());
        when(simulationMapper.markExecutionRunning(21L)).thenReturn(1);
        when(simulationMapper.cancelExecution(21L)).thenReturn(1);
        CountDownLatch engineStarted = new CountDownLatch(1);
        AtomicBoolean interrupted = new AtomicBoolean();
        when(engineRunner.run(eq(21L), any())).thenAnswer(invocation -> {
            engineStarted.countDown();
            try {
                new CountDownLatch(1).await();
                throw new AssertionError("취소되지 않은 엔진 실행");
            } catch (InterruptedException exception) {
                interrupted.set(true);
                throw new EngineRunException("cancelled", false, exception);
            }
        });

        service.execute(21L, user);
        Thread worker = new Thread(queued.get());
        worker.start();
        assertThat(engineStarted.await(1, TimeUnit.SECONDS)).isTrue();
        when(simulationService.getAccessibleSimulation(21L, user))
                .thenReturn(simulation("RUNNING"), simulation("CANCELLED"));

        service.cancel(21L, user);
        worker.join(1000);

        assertThat(interrupted).isTrue();
        assertThat(worker.isAlive()).isFalse();
    }

    @Test
    void cancellationIsIdempotent() {
        when(simulationService.getAccessibleSimulation(21L, user))
                .thenReturn(simulation("CANCELLED"), simulation("CANCELLED"));

        var response = service.cancel(21L, user);

        assertThat(response.status()).isEqualTo("CANCELLED");
        verify(simulationMapper, never()).cancelExecution(anyLong());
    }

    @Test
    void rejectsCancellationAfterCompletionWinsRace() {
        when(simulationService.getAccessibleSimulation(21L, user))
                .thenReturn(simulation("RUNNING"), simulation("COMPLETED"));
        when(simulationMapper.cancelExecution(21L)).thenReturn(0);

        assertThatThrownBy(() -> service.cancel(21L, user)).isInstanceOf(SimulationConflictException.class);
    }

    @Test
    void rejectsCancellationBeforeExecutionStarts() {
        when(simulationService.getAccessibleSimulation(21L, user)).thenReturn(simulation("DRAFT"));

        assertThatThrownBy(() -> service.cancel(21L, user)).isInstanceOf(SimulationConflictException.class);

        verify(simulationMapper, never()).cancelExecution(anyLong());
    }

    @Test
    void executesCancelledSimulationAgain() {
        captureWorker();
        when(simulationMapper.updateExecutionProfiles(21L, "SFM_DEFAULT_V2", "HAZARD_RADIAL_EXP_V3"))
                .thenReturn(1);
        when(simulationMapper.requestExecution(21L)).thenReturn(1);
        when(simulationService.getSetup(21L, user)).thenReturn(validSetup());
        when(simulationService.getAccessibleSimulation(21L, user))
                .thenReturn(simulation("CANCELLED"), simulation("REQUESTED"));

        var response = service.execute(21L, user);

        assertThat(response.status()).isEqualTo("REQUESTED");
    }

    @Test
    void doesNotPersistRawEngineDiagnostics() throws Exception {
        stubDraftAndRequestedStatus();
        captureWorker();
        when(simulationMapper.requestExecution(21L)).thenReturn(1);
        when(simulationService.getSetup(21L, user)).thenReturn(validSetup());
        when(simulationMapper.markExecutionRunning(21L)).thenReturn(1);
        when(engineRunner.run(eq(21L), any()))
                .thenThrow(new EngineRunException("runner error: C:\\private\\input.json", false));

        service.execute(21L, user);
        queued.get().run();

        verify(simulationMapper).markExecutionFailed(21L, "ENGINE_ERROR: 시뮬레이션 엔진 실행에 실패했습니다.", null);
    }

    @Test
    void persistsOnlyStructuredRoutingFailureFields() throws Exception {
        stubDraftAndRequestedStatus();
        captureWorker();
        when(simulationMapper.requestExecution(21L)).thenReturn(1);
        when(simulationService.getSetup(21L, user)).thenReturn(validSetup());
        when(simulationMapper.markExecutionRunning(21L)).thenReturn(1);
        SimulationFailureDetailResponse detail = new SimulationFailureDetailResponse(
                "AGENT_ROUTE_UNREACHABLE",
                1L,
                new PointDto(BigDecimal.ONE, BigDecimal.ONE),
                null,
                null,
                null,
                null,
                null);
        when(engineRunner.run(eq(21L), any())).thenThrow(new EngineRunException("private diagnostic", false, detail));

        service.execute(21L, user);
        queued.get().run();

        verify(simulationMapper)
                .markExecutionFailed(
                        eq(21L), eq("Agent #1의 시작 위치를 대피 경로에 연결할 수 없습니다."), contains("\"recommendedPosition\":null"));
    }

    @Test
    void persistsTypedNoReachableExitFailureFields() throws Exception {
        stubDraftAndRequestedStatus();
        captureWorker();
        when(simulationMapper.requestExecution(21L)).thenReturn(1);
        when(simulationService.getSetup(21L, user)).thenReturn(validSetup());
        when(simulationMapper.markExecutionRunning(21L)).thenReturn(1);
        SimulationFailureDetailResponse detail = new SimulationFailureDetailResponse(
                "NO_REACHABLE_SELECTED_EXIT",
                null,
                null,
                null,
                3L,
                List.of(1L, 2L),
                List.of(501L),
                "NO_EXIT_SEED_IN_OCCUPIED_COMPONENT");
        when(engineRunner.run(eq(21L), any())).thenThrow(new EngineRunException("private diagnostic", false, detail));

        service.execute(21L, user);
        queued.get().run();

        verify(simulationMapper)
                .markExecutionFailed(
                        eq(21L),
                        eq("선택한 출입구에 도달할 수 없는 구역이 있습니다. 도면과 출입구를 확인해 주세요."),
                        contains("\"affectedAgentCount\":3"));
    }

    @Test
    void marksInterruptedExecutionsFailedAtStartup() {
        when(simulationMapper.markInterruptedExecutionsFailed(any())).thenReturn(2);

        service.failInterruptedExecutions();

        verify(simulationMapper).markInterruptedExecutionsFailed(contains("SERVICE_RESTARTED"));
    }

    @Test
    void returnsPersistedFailureDetailWithExecutionStatus() {
        Simulation failed = simulation("FAILED");
        failed.setFailureMessage("route failure");
        SimulationFailureDetailResponse detail = new SimulationFailureDetailResponse(
                "AGENT_ROUTE_UNREACHABLE",
                1L,
                new PointDto(BigDecimal.ONE, BigDecimal.ONE),
                null,
                null,
                null,
                null,
                null);
        when(simulationService.getAccessibleSimulation(21L, user)).thenReturn(failed);
        when(simulationService.readFailureDetail(failed)).thenReturn(detail);

        var response = service.getExecution(21L, user);

        assertThat(response.failureDetail()).isEqualTo(detail);
    }

    @Test
    void readsCompletedTimelineChunk() {
        when(simulationService.getAccessibleSimulation(21L, user)).thenReturn(simulation("COMPLETED"));
        when(simulationMapper.findTimelineJson(21L, 0))
                .thenReturn("{\"sequence\":0,\"frames\":[{\"timeSeconds\":0,\"agents\":[[0,1,2]]}]}");
        SimulationOption option = new SimulationOption();
        option.setTotalPeople(1);
        when(simulationMapper.findSimulationOption(21L)).thenReturn(option);
        SimulationResult result = new SimulationResult();
        result.setFrameIntervalSeconds(BigDecimal.ONE);
        when(simulationMapper.findSimulationResult(21L)).thenReturn(result);

        var chunk = service.getTimeline(21L, 0, user);

        assertThat(chunk.schemaVersion()).isEqualTo(1);
        assertThat(chunk.chunkSequence()).isZero();
        assertThat(chunk.frames()).hasSize(1);
        assertThat(chunk.frames().get(0).agents().get(0).agentId()).isEqualTo(1L);
        assertThat(chunk.frames().get(0).agents().get(0).x()).isEqualByComparingTo(BigDecimal.ONE);
    }

    @Test
    void readsSparseGridHeatmapChunk() {
        when(simulationService.getAccessibleSimulation(21L, user)).thenReturn(simulation("COMPLETED"));
        when(simulationMapper.findHeatmapJson(21L, 0))
                .thenReturn(
                        """
                        {"schemaVersion":1,"analysisVersion":"GRID_COUNT_V1",\
                        "coordinateSystem":"FLOOR_PLAN","coordinateUnit":"METER",\
                        "densityMethod":"GRID_COUNT","densityUnit":"PERSON_PER_M2",\
                        "frameRate":1,"chunkSequence":0,"startFrame":0,"endFrame":0,\
                        "grid":{"originX":0,"originY":0,"cellSize":1,"rows":10,"columns":10,\
                        "cellOrder":"ROW_COLUMN_VALUE"},\
                        "frames":[{"frameIndex":0,"timeSeconds":0,"cells":[[1,2,3.0]]}]}
                        """);

        var chunk = service.getHeatmap(21L, 0, user);

        assertThat(chunk.densityMethod()).isEqualTo("GRID_COUNT");
        assertThat(chunk.frames().get(0).cells().get(0))
                .containsExactly(BigDecimal.ONE, BigDecimal.valueOf(2), BigDecimal.valueOf(3.0));
    }

    private static SimulationSetupResponse validSetup() {
        return setup(List.of(new PointDto(BigDecimal.ONE, BigDecimal.ONE)), List.of(501L));
    }

    private static SimulationSetupResponse validDraftSetup() {
        return setup(List.of(new PointDto(BigDecimal.ONE, BigDecimal.ONE)), List.of(501L), "DRAFT");
    }

    private static SimulationSetupResponse twoAgentSetup() {
        return setup(
                List.of(
                        new PointDto(BigDecimal.ONE, BigDecimal.ONE),
                        new PointDto(BigDecimal.valueOf(2), BigDecimal.ONE)),
                List.of(501L));
    }

    private static EngineRun successfulRun() {
        return new EngineRun(
                new EngineResult("1.4.2", "ALL_EVACUATED", 12.5, 1, 0, 12.5, 8.0, 1.0, 1, 1, 1.0, null, null),
                List.of(new TimelineChunk(0, "{\"chunkSequence\":0,\"frames\":[]}")),
                List.of(new HeatmapChunk(0, "{\"chunkSequence\":0,\"frames\":[]}")),
                400.0);
    }

    private static EngineRun runWithRecoverySummary() throws JsonProcessingException {
        return new EngineRun(
                new EngineResult(
                        "1.4.2", "ALL_EVACUATED", 12.5, 1, 0, 12.5, 8.0, 1.0, 1, 1, 1.0, null, recoverySummary()),
                List.of(new TimelineChunk(0, "{\"chunkSequence\":0,\"frames\":[]}")),
                List.of(new HeatmapChunk(0, "{\"chunkSequence\":0,\"frames\":[]}")),
                400.0);
    }

    private static JsonNode recoverySummary() throws JsonProcessingException {
        return new ObjectMapper().readTree(VALID_RECOVERY_SUMMARY_JSON);
    }

    private static final String VALID_RECOVERY_SUMMARY_JSON =
            """
            {"schemaVersion":1,"scanCount":2,"eligibleGroupCount":1,"skippedEligibleGroupCount":0,
             "infeasibleScanCount":0,"recoveredGroupCount":1,"recoveredAgentCount":3,
             "recoveredMidRouteAgentCount":0,
             "recoveryTimeSeconds":5.5,"recoveredExitLabels":[0],"recoveredExitIds":[501],
             "attemptedGroupSignatures":1,
             "events":[{"timeSeconds":5.5,"iteration":550,"contextIndex":0,"exitId":501,"exitLabel":0,
                        "target":[9.7,4.0],"stableIds":[1,2,3],
                        "oldTargets":[[9.7,4.0],[9.7,4.0],[9.7,4.0]],
                        "newTargets":[[10.0,3.75],[10.0,4.0],[10.0,4.25]],
                        "newApproaches":[[9.7,3.75],[9.7,4.0],[9.7,4.25]],
                        "seedNodeIds":[11,12,13],"status":"RECOVERED",
                        "postRecoveryInvalidMoves":0,"postRecoveryFullRollbacks":0}]}
            """;

    private void stubRecoveryExecution(EngineRun run) throws Exception {
        stubDraftAndRequestedStatus();
        captureWorker();
        when(simulationMapper.requestExecution(21L)).thenReturn(1);
        when(simulationService.getSetup(21L, user)).thenReturn(validSetup());
        when(simulationMapper.markExecutionRunning(21L)).thenReturn(1);
        when(engineRunner.run(eq(21L), any())).thenReturn(run);
        when(simulationMapper.insertSimulationResult(any())).thenAnswer(invocation -> {
            SimulationResult result = invocation.getArgument(0);
            result.setId(31L);
            return 1;
        });
        DensityThreshold threshold = new DensityThreshold(BigDecimal.valueOf(3.5), "PERSON_PER_M2");
        when(densityThresholdProvider.getCurrent()).thenReturn(threshold);
        when(bottleneckDetector.detect(any(), eq(threshold))).thenReturn(List.of());
        when(simulationMapper.markExecutionCompleted(21L)).thenReturn(1);
    }

    private void captureWorker() {
        doAnswer(invocation -> {
                    queued.set(invocation.getArgument(0));
                    return null;
                })
                .when(executor)
                .execute(any(Runnable.class));
    }

    private void stubTwoExecutionRequests() {
        when(simulationMapper.updateExecutionProfiles(21L, "SFM_DEFAULT_V2", "HAZARD_RADIAL_EXP_V3"))
                .thenReturn(1);
        when(simulationMapper.requestExecution(21L)).thenReturn(1);
        when(simulationService.getSetup(21L, user)).thenReturn(validSetup());
        when(simulationService.getAccessibleSimulation(21L, user))
                .thenReturn(simulation("DRAFT"), simulation("REQUESTED"), simulation("DRAFT"), simulation("REQUESTED"));
    }

    private void stubDraftAndRequestedStatus() {
        when(simulationMapper.updateExecutionProfiles(21L, "SFM_DEFAULT_V2", "HAZARD_RADIAL_EXP_V3"))
                .thenReturn(1);
        when(simulationService.getAccessibleSimulation(21L, user))
                .thenReturn(simulation("DRAFT"), simulation("REQUESTED"));
    }

    private static SimulationSetupResponse setup(List<PointDto> agents, List<Long> exits) {
        return setup(agents, exits, "REQUESTED");
    }

    private static SimulationSetupResponse setup(List<PointDto> agents, List<Long> exits, String status) {
        DrawingGeometryDto drawing = new DrawingGeometryDto(
                3L,
                "test",
                BigDecimal.TEN,
                BigDecimal.TEN,
                List.of(
                        new PointDto(BigDecimal.ZERO, BigDecimal.ZERO),
                        new PointDto(BigDecimal.TEN, BigDecimal.ZERO),
                        new PointDto(BigDecimal.TEN, BigDecimal.TEN),
                        new PointDto(BigDecimal.ZERO, BigDecimal.TEN)),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(new ExitDto(
                        501L, "exit", BigDecimal.TEN, BigDecimal.valueOf(4), BigDecimal.TEN, BigDecimal.valueOf(6))));
        return new SimulationSetupResponse(
                21L,
                11L,
                null,
                "test simulation",
                status,
                LocalDateTime.now(),
                1,
                "SFM_DEFAULT_V2",
                "HAZARD_RADIAL_EXP_V3",
                agents.size(),
                BigDecimal.valueOf(1.25),
                BigDecimal.ZERO,
                agents,
                List.of(),
                exits,
                drawing,
                false);
    }

    private static Simulation simulation(String status) {
        Simulation simulation = new Simulation();
        simulation.setId(21L);
        simulation.setLayoutVersionId(11L);
        simulation.setCreatedBy(7L);
        simulation.setTitle("test simulation");
        simulation.setStatus(status);
        return simulation;
    }

    private static final class NoOpTransactionManager implements PlatformTransactionManager {
        @Override
        public TransactionStatus getTransaction(TransactionDefinition definition) {
            return new SimpleTransactionStatus();
        }

        @Override
        public void commit(TransactionStatus status) {}

        @Override
        public void rollback(TransactionStatus status) {}
    }
}
