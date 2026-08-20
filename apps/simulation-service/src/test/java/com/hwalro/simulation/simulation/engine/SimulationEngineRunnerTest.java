package com.hwalro.simulation.simulation.engine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hwalro.simulation.simulation.dto.SimulationDtos.DrawingGeometryDto;
import com.hwalro.simulation.simulation.dto.SimulationDtos.PointDto;
import com.hwalro.simulation.simulation.dto.SimulationDtos.SimulationSetupResponse;
import com.hwalro.simulation.simulation.engine.SimulationEngineRunner.EngineResult;
import com.hwalro.simulation.simulation.engine.SimulationEngineRunner.EngineRunException;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.core.io.ClassPathResource;

class SimulationEngineRunnerTest {
    @Test
    void rejectsMissingChunkCountsBeforeUnboxing() {
        EngineResult result =
                new EngineResult("1.4.2", "ALL_EVACUATED", 1.0, 1, 0, 1.0, 1.0, 1.0, null, 1, 0.0, null, null);

        assertThatThrownBy(() -> SimulationEngineRunner.validateChunkCounts(result))
                .isInstanceOf(EngineRunException.class)
                .hasMessageContaining("청크 개수");
    }

    @Test
    void capturesOnlyTheFirstThousandDiagnosticBytes() throws Exception {
        byte[] output = "x".repeat(5_000).getBytes(StandardCharsets.UTF_8);

        String diagnostic = SimulationEngineRunner.readDiagnostic(new ByteArrayInputStream(output));

        assertThat(diagnostic).hasSize(1_000);
    }

    @Test
    void logsOnePhaseSummaryWhenARunFails(@TempDir Path temporaryDirectory) throws Exception {
        Path blockedWorkRoot = temporaryDirectory.resolve("work-root");
        Files.writeString(blockedWorkRoot, "not a directory");
        SimulationEngineRunner runner = new SimulationEngineRunner(
                new ObjectMapper(),
                "python",
                temporaryDirectory.resolve("runner.py").toString(),
                blockedWorkRoot.toString(),
                Duration.ofSeconds(1),
                1,
                1,
                false);
        Logger logger = (Logger) LoggerFactory.getLogger(SimulationEngineRunner.class);
        Level originalLevel = logger.getLevel();
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.setLevel(Level.INFO);
        logger.addAppender(appender);
        try {
            assertThatThrownBy(() -> runner.run(21L, null)).isInstanceOf(EngineRunException.class);
        } finally {
            logger.detachAppender(appender);
            logger.setLevel(originalLevel);
            appender.stop();
        }

        var phaseLogs = appender.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .filter(message -> message.startsWith("simulation_engine_phase "))
                .toList();
        assertThat(phaseLogs).singleElement().satisfies(message -> assertThat(message)
                .contains(
                        "simulationId=21",
                        "outcome=ERROR",
                        "inputWriteMs=",
                        "pythonProcessMs=",
                        "resultReadMs=",
                        "timelineReadMs=",
                        "heatmapReadMs=",
                        "cleanupMs=",
                        "totalMs=",
                        "timelineChunks=0",
                        "timelineChars=0",
                        "heatmapChunks=0",
                        "heatmapChars=0"));
    }

    @Test
    void readsStrictRoutingFailureAndReconstructsCurrentPosition(@TempDir Path temporaryDirectory) throws Exception {
        Path output = temporaryDirectory.resolve("output");
        Files.createDirectories(output);
        Files.writeString(
                output.resolve("error.json"),
                """
                {"schemaVersion":1,"code":"AGENT_ROUTE_UNREACHABLE","agentId":2,
                 "recommendedPosition":{"x":4.5,"y":5.5}}
                """);

        var detail = runner(temporaryDirectory).readFailureDetail(output, setup());

        assertThat(detail).isNotNull();
        assertThat(detail.agentId()).isEqualTo(2L);
        assertThat(detail.currentPosition()).isEqualTo(new PointDto(BigDecimal.valueOf(2), BigDecimal.valueOf(3)));
        assertThat(detail.recommendedPosition())
                .isEqualTo(new PointDto(BigDecimal.valueOf(4.5), BigDecimal.valueOf(5.5)));

        Files.writeString(
                output.resolve("error.json"),
                """
                {"schemaVersion":1,"code":"AGENT_ROUTE_UNREACHABLE","agentId":1,
                 "recommendedPosition":null}
                """);
        assertThat(runner(temporaryDirectory).readFailureDetail(output, setup()).recommendedPosition())
                .isNull();
    }

    @Test
    void rejectsInvalidRoutingFailureSidecars(@TempDir Path temporaryDirectory) throws Exception {
        Path output = temporaryDirectory.resolve("output");
        Files.createDirectories(output);
        SimulationEngineRunner runner = runner(temporaryDirectory);

        Files.writeString(
                output.resolve("error.json"),
                """
                {"schemaVersion":1,"code":"AGENT_ROUTE_UNREACHABLE","agentId":3,
                 "recommendedPosition":null}
                """);
        assertThat(runner.readFailureDetail(output, setup())).isNull();

        Files.writeString(
                output.resolve("error.json"),
                """
                {"schemaVersion":1,"code":"AGENT_ROUTE_UNREACHABLE","agentId":1,
                 "recommendedPosition":{"x":11,"y":5}}
                """);
        assertThat(runner.readFailureDetail(output, setup())).isNull();

        Files.writeString(
                output.resolve("error.json"),
                """
                {"schemaVersion":1,"code":"AGENT_ROUTE_UNREACHABLE","agentId":1,
                 "recommendedPosition":null,"extra":true}
                """);
        assertThat(runner.readFailureDetail(output, setup())).isNull();
    }

    @Test
    void readsStrictTypedNoReachableExitFailure(@TempDir Path temporaryDirectory) throws Exception {
        Path output = temporaryDirectory.resolve("output");
        Files.createDirectories(output);
        Files.writeString(
                output.resolve("error.json"),
                """
                {"schemaVersion":1,"code":"NO_REACHABLE_SELECTED_EXIT","affectedAgentCount":2,
                 "representativeAgentIds":[1,2],"componentCount":1,"selectedExitIds":[501],
                 "reason":"NO_EXIT_SEED_IN_OCCUPIED_COMPONENT"}
                """);

        var detail = runner(temporaryDirectory).readFailureDetail(output, setup());

        assertThat(detail).isNotNull();
        assertThat(detail.code()).isEqualTo("NO_REACHABLE_SELECTED_EXIT");
        assertThat(detail.affectedAgentCount()).isEqualTo(2L);
        assertThat(detail.representativeAgentIds()).containsExactly(1L, 2L);
        assertThat(detail.selectedExitIds()).containsExactly(501L);
        assertThat(detail.reason()).isEqualTo("NO_EXIT_SEED_IN_OCCUPIED_COMPONENT");
        assertThat(detail.agentId()).isNull();
        assertThat(detail.currentPosition()).isNull();
        assertThat(detail.recommendedPosition()).isNull();
    }

    @Test
    void rejectsInvalidNoReachableExitFailureSidecars(@TempDir Path temporaryDirectory) throws Exception {
        Path output = temporaryDirectory.resolve("output");
        Files.createDirectories(output);
        SimulationEngineRunner runner = runner(temporaryDirectory);

        Files.writeString(
                output.resolve("error.json"),
                """
                {"schemaVersion":1,"code":"NO_REACHABLE_SELECTED_EXIT","affectedAgentCount":3,
                 "representativeAgentIds":[1],"componentCount":1,"selectedExitIds":[501],
                 "reason":"NO_EXIT_SEED_IN_OCCUPIED_COMPONENT"}
                """);
        assertThat(runner.readFailureDetail(output, setup())).isNull();

        Files.writeString(
                output.resolve("error.json"),
                """
                {"schemaVersion":1,"code":"NO_REACHABLE_SELECTED_EXIT","affectedAgentCount":1,
                 "representativeAgentIds":[2],"componentCount":1,"selectedExitIds":[999],
                 "reason":"NO_EXIT_SEED_IN_OCCUPIED_COMPONENT"}
                """);
        assertThat(runner.readFailureDetail(output, setup())).isNull();

        Files.writeString(
                output.resolve("error.json"),
                """
                {"schemaVersion":1,"code":"NO_REACHABLE_SELECTED_EXIT","affectedAgentCount":1,
                 "representativeAgentIds":[],"componentCount":1,"selectedExitIds":[501],
                 "reason":"NO_EXIT_SEED_IN_OCCUPIED_COMPONENT"}
                """);
        assertThat(runner.readFailureDetail(output, setup())).isNull();

        Files.writeString(
                output.resolve("error.json"),
                """
                {"schemaVersion":1,"code":"NO_REACHABLE_SELECTED_EXIT","affectedAgentCount":1,
                 "representativeAgentIds":[1],"componentCount":1,"selectedExitIds":[501],
                 "reason":"UNKNOWN_REASON"}
                """);
        assertThat(runner.readFailureDetail(output, setup())).isNull();

        Files.writeString(
                output.resolve("error.json"),
                """
                {"schemaVersion":1,"code":"NO_REACHABLE_SELECTED_EXIT","affectedAgentCount":1,
                 "representativeAgentIds":[1],"componentCount":1,"selectedExitIds":[501],
                 "reason":"NO_EXIT_SEED_IN_OCCUPIED_COMPONENT","extra":true}
                """);
        assertThat(runner.readFailureDetail(output, setup())).isNull();
    }

    @Test
    void includesRecoveryDetectorEnabledInEngineInput(@TempDir Path temporaryDirectory) {
        assertThat(runner(temporaryDirectory, true).createInput(setup()))
                .containsEntry("recoveryDetectorEnabled", true);
        assertThat(runner(temporaryDirectory, false).createInput(setup()))
                .containsEntry("recoveryDetectorEnabled", false);
    }

    @Test
    void passesInitialResponseDistributionAndSeedWithoutConfigurableSfmReactionTime(@TempDir Path temporaryDirectory) {
        Map<String, Object> input = runner(temporaryDirectory, false).createInput(setup());

        assertThat(input).containsEntry("randomSeed", 1);
        assertThat(input.get("model"))
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
                .containsEntry("initialResponseTimeMean", BigDecimal.ZERO)
                .containsEntry("initialResponseTimeStdDev", BigDecimal.ZERO)
                .doesNotContainKey("reactionTime");
    }

    @Test
    void bindsSharedTargetRecoveryFlagDefaultingToFalse() throws Exception {
        YamlPropertiesFactoryBean factory = new YamlPropertiesFactoryBean();
        factory.setResources(new ClassPathResource("application.yml"));
        Properties properties = factory.getObject();
        assertThat(properties).isNotNull();
        assertThat(properties.getProperty("simulation.engine.shared-target-recovery-enabled"))
                .isEqualTo("${SIMULATION_ENGINE_SHARED_TARGET_RECOVERY_ENABLED:false}");
    }

    @Test
    void deserializesEngineResultWithoutRecoverySummary() throws Exception {
        String resultJson =
                """
                {"engineVersion":"1.4.2","terminationReason":"ALL_EVACUATED","simulationDurationSeconds":1.0,
                 "evacuatedPeople":1,"remainingPeople":0,"totalEvacuationTimeSeconds":1.0,
                 "averageEvacuationTimeSeconds":1.0,"frameIntervalSeconds":1.0,"timelineChunkCount":1,
                 "heatmapChunkCount":1,"maxDensity":0.0}
                """;
        EngineResult withoutSummary = new ObjectMapper().readValue(resultJson, EngineResult.class);

        assertThat(withoutSummary.recoverySummary()).isNull();
        assertThat(withoutSummary.terminationDetail()).isNull();
    }

    @Test
    void deserializesEngineResultWithRecoverySummary() throws Exception {
        String resultJson =
                """
                {"engineVersion":"1.4.2","terminationReason":"ALL_EVACUATED","simulationDurationSeconds":1.0,
                 "evacuatedPeople":1,"remainingPeople":0,"totalEvacuationTimeSeconds":1.0,
                 "averageEvacuationTimeSeconds":1.0,"frameIntervalSeconds":1.0,"timelineChunkCount":1,
                 "heatmapChunkCount":1,"maxDensity":0.0,
                 "recoverySummary":{"schemaVersion":1,"recoveredAgentCount":3,"recoveryTimeSeconds":0.5,
                                    "recoveredExitLabels":["1"],"recoveredExitIds":[501],"events":[]}}
                """;
        EngineResult withSummary = new ObjectMapper().readValue(resultJson, EngineResult.class);

        assertThat(withSummary.recoverySummary()).isNotNull();
        assertThat(withSummary.recoverySummary().path("recoveredAgentCount").asInt())
                .isEqualTo(3);
        assertThat(withSummary.recoverySummary().path("recoveryTimeSeconds").asDouble())
                .isEqualTo(0.5);
    }

    private static SimulationEngineRunner runner(Path temporaryDirectory) {
        return runner(temporaryDirectory, false);
    }

    private static SimulationEngineRunner runner(Path temporaryDirectory, boolean sharedTargetRecoveryEnabled) {
        return new SimulationEngineRunner(
                new ObjectMapper(),
                "python",
                temporaryDirectory.resolve("runner.py").toString(),
                temporaryDirectory.resolve("work").toString(),
                Duration.ofSeconds(1),
                1,
                1,
                sharedTargetRecoveryEnabled);
    }

    private static SimulationSetupResponse setup() {
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
                List.of());
        return new SimulationSetupResponse(
                21L,
                11L,
                null,
                "test simulation",
                "REQUESTED",
                LocalDateTime.now(),
                1,
                "SFM_DEFAULT_V2",
                "HAZARD_RADIAL_EXP_V3",
                2,
                BigDecimal.valueOf(1.25),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                List.of(
                        new PointDto(BigDecimal.ONE, BigDecimal.ONE),
                        new PointDto(BigDecimal.valueOf(2), BigDecimal.valueOf(3))),
                List.of(),
                List.of(501L),
                drawing);
    }
}
