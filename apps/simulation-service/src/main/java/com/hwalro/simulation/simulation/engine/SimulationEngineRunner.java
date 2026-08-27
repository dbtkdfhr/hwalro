package com.hwalro.simulation.simulation.engine;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hwalro.simulation.simulation.dto.SimulationDtos.PointDto;
import com.hwalro.simulation.simulation.dto.SimulationDtos.SimulationFailureDetailResponse;
import com.hwalro.simulation.simulation.dto.SimulationDtos.SimulationSetupResponse;
import com.hwalro.simulation.simulation.exception.SimulationEngineUnavailableException;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class SimulationEngineRunner {
    private static final Logger log = LoggerFactory.getLogger(SimulationEngineRunner.class);
    private static final Duration READINESS_TIMEOUT = Duration.ofSeconds(15);
    private static final int MAX_ENGINE_MESSAGE_LENGTH = 1000;
    private static final long MAX_FAILURE_DETAIL_BYTES = 4096;
    private static final int ROUTING_ERROR_EXIT_CODE = 3;
    public static final String ROUTING_ERROR_CODE = "AGENT_ROUTE_UNREACHABLE";
    public static final String NO_REACHABLE_EXIT_CODE = "NO_REACHABLE_SELECTED_EXIT";
    public static final String NO_WALKABLE_ORIGIN_CODE = "NO_WALKABLE_ORIGIN_IN_ZONE";
    private static final BigDecimal MAX_COORDINATE = BigDecimal.valueOf(1_000_000);

    private final ObjectMapper objectMapper;
    private final String pythonCommand;
    private final Path scriptPath;
    private final Path workRoot;
    private final Duration timeout;
    private final double maxSimulationTimeSeconds;
    private final double frameIntervalSeconds;
    private final boolean sharedTargetRecoveryEnabled;

    public SimulationEngineRunner(
            ObjectMapper objectMapper,
            @Value("${simulation.engine.python:python}") String pythonCommand,
            @Value("${simulation.engine.script:engine/runner.py}") String script,
            @Value("${simulation.engine.work-directory:}") String workDirectory,
            @Value("${simulation.engine.timeout:30m}") Duration timeout,
            @Value("${simulation.engine.max-simulation-time:600}") double maxSimulationTimeSeconds,
            @Value("${simulation.engine.frame-interval:1}") double frameIntervalSeconds,
            @Value("${simulation.engine.shared-target-recovery-enabled:false}") boolean sharedTargetRecoveryEnabled) {
        this.objectMapper = objectMapper;
        this.pythonCommand = resolvePythonCommand(pythonCommand);
        this.scriptPath = resolveScript(script);
        this.workRoot = workDirectory.isBlank()
                ? Path.of(System.getProperty("java.io.tmpdir"), "hwalro-simulations")
                        .toAbsolutePath()
                        .normalize()
                : Path.of(workDirectory).toAbsolutePath().normalize();
        this.timeout = timeout;
        this.maxSimulationTimeSeconds = maxSimulationTimeSeconds;
        this.frameIntervalSeconds = frameIntervalSeconds;
        this.sharedTargetRecoveryEnabled = sharedTargetRecoveryEnabled;
    }

    public void assertAvailable() {
        if (!Files.isRegularFile(scriptPath)) {
            log.warn("JuPedSim runner is missing at {}", scriptPath);
            throw new SimulationEngineUnavailableException("JuPedSim runner를 찾을 수 없습니다.");
        }
        Process process = null;
        ProcessOutputCapture output = null;
        try {
            Files.createDirectories(workRoot);
            process = new ProcessBuilder(pythonCommand, scriptPath.toString(), "--version")
                    .redirectErrorStream(true)
                    .start();
            output = new ProcessOutputCapture(process.getInputStream());
            if (!process.waitFor(READINESS_TIMEOUT.toMillis(), TimeUnit.MILLISECONDS)) {
                stop(process);
                throw new SimulationEngineUnavailableException("JuPedSim 설치 확인 시간이 초과되었습니다.");
            }
            String diagnostic = output.await();
            if (process.exitValue() != 0) {
                log.warn("JuPedSim readiness check failed: {}", diagnostic);
                throw new SimulationEngineUnavailableException("JuPedSim을 실행할 수 없습니다.");
            }
        } catch (IOException exception) {
            log.warn("Could not start the JuPedSim readiness check", exception);
            throw new SimulationEngineUnavailableException("Python 또는 JuPedSim runner를 실행할 수 없습니다.", exception);
        } catch (InterruptedException exception) {
            if (process != null) {
                stop(process);
            }
            Thread.currentThread().interrupt();
            throw new SimulationEngineUnavailableException("JuPedSim 설치 확인이 중단되었습니다.", exception);
        }
    }

    public SimulationFailureDetailResponse validateRouting(Long simulationId, SimulationSetupResponse setup)
            throws EngineRunException {
        Path jobDirectory = null;
        Process process = null;
        try {
            Files.createDirectories(workRoot);
            jobDirectory = Files.createTempDirectory(workRoot, "routing-validation-" + simulationId + "-")
                    .toAbsolutePath()
                    .normalize();
            Path inputPath = jobDirectory.resolve("input.json");
            Path outputDirectory = jobDirectory.resolve("output");
            objectMapper.writeValue(inputPath.toFile(), createInput(setup));

            process = new ProcessBuilder(
                            pythonCommand,
                            scriptPath.toString(),
                            "--validate-only",
                            inputPath.toString(),
                            outputDirectory.toString())
                    .redirectErrorStream(true)
                    .start();
            ProcessOutputCapture output = new ProcessOutputCapture(process.getInputStream());
            if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                stop(process);
                throw new EngineRunException("ENGINE_TIMEOUT: 경로 검증 시간 제한을 초과했습니다.", true);
            }
            String diagnostic = output.await();
            if (process.exitValue() == 0) {
                return null;
            }
            if (process.exitValue() == ROUTING_ERROR_EXIT_CODE) {
                SimulationFailureDetailResponse failureDetail = readFailureDetail(outputDirectory, setup);
                if (failureDetail != null) {
                    return failureDetail;
                }
                log.warn("Simulation {} engine returned an invalid routing validation detail", simulationId);
            } else {
                log.warn("Simulation {} engine routing validation failed: {}", simulationId, diagnostic);
            }
            throw new EngineRunException("ENGINE_ERROR: 경로 검증에 실패했습니다.", false);
        } catch (IOException exception) {
            log.warn("Simulation {} engine routing validation I/O failed", simulationId, exception);
            throw new EngineRunException("ENGINE_ERROR: 경로 검증 입출력 처리에 실패했습니다.", false, exception);
        } catch (InterruptedException exception) {
            if (process != null) {
                stop(process);
            }
            Thread.currentThread().interrupt();
            throw new EngineRunException("경로 검증이 중단되었습니다.", false, exception);
        } finally {
            deleteJobDirectory(jobDirectory);
        }
    }

    /**
     * 시뮬레이션을 돌리지 않고 계획된 경로만 받아온다. {@link #validateRouting}과 같은 실패 계약(종료 코드 3, {@code error.json})을 쓰며,
     * 다른 점은 {@code --route-preview} 인자와 {@code routes.json}을 읽는다는 것뿐이다.
     *
     * @return 에이전트별 경로. 도달 불가 등 엔진이 거부한 경우 {@link EngineRunException}을 던진다.
     */
    public List<PreviewedRoute> previewRoutes(String jobLabel, SimulationSetupResponse setup)
            throws EngineRunException {
        return previewRoutes(jobLabel, setup, null);
    }

    public List<PreviewedRoute> previewRoutes(
            String jobLabel, SimulationSetupResponse setup, RouteOriginBounds routeOriginBounds)
            throws EngineRunException {
        return previewRouteResult(jobLabel, setup, routeOriginBounds).routes();
    }

    public RoutePreviewResult previewRouteResult(
            String jobLabel, SimulationSetupResponse setup, RouteOriginBounds routeOriginBounds)
            throws EngineRunException {
        return previewRouteResult(jobLabel, setup, routeOriginBounds, List.of());
    }

    public RoutePreviewResult previewZoneRoutes(
            String jobLabel, SimulationSetupResponse setup, List<RoutePreviewZone> zones) throws EngineRunException {
        return previewRouteResult(jobLabel, setup, null, zones);
    }

    private RoutePreviewResult previewRouteResult(
            String jobLabel,
            SimulationSetupResponse setup,
            RouteOriginBounds routeOriginBounds,
            List<RoutePreviewZone> zones)
            throws EngineRunException {
        Path jobDirectory = null;
        Process process = null;
        try {
            Files.createDirectories(workRoot);
            jobDirectory = Files.createTempDirectory(workRoot, "route-preview-" + jobLabel + "-")
                    .toAbsolutePath()
                    .normalize();
            Path inputPath = jobDirectory.resolve("input.json");
            Path outputDirectory = jobDirectory.resolve("output");
            Map<String, Object> input = createInput(setup);
            if (routeOriginBounds != null) {
                input.put("routeOriginBounds", routeOriginBounds);
            }
            if (!zones.isEmpty()) {
                input.put("routePreviewZones", zones);
            }
            objectMapper.writeValue(inputPath.toFile(), input);

            process = new ProcessBuilder(
                            pythonCommand,
                            scriptPath.toString(),
                            "--route-preview",
                            inputPath.toString(),
                            outputDirectory.toString())
                    .redirectErrorStream(true)
                    .start();
            ProcessOutputCapture output = new ProcessOutputCapture(process.getInputStream());
            if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                stop(process);
                throw new EngineRunException("ENGINE_TIMEOUT: 대피 경로 계산 시간 제한을 초과했습니다.", true);
            }
            String diagnostic = output.await();
            if (process.exitValue() != 0) {
                if (process.exitValue() == ROUTING_ERROR_EXIT_CODE) {
                    SimulationFailureDetailResponse failureDetail = readFailureDetail(outputDirectory, setup);
                    if (failureDetail != null) {
                        throw new EngineRunException("ENGINE_ROUTING_ERROR: 대피 경로를 계산하지 못했습니다.", false, failureDetail);
                    }
                }
                log.warn("Route preview {} failed with exit code {}: {}", jobLabel, process.exitValue(), diagnostic);
                throw new EngineRunException("ENGINE_ERROR: 대피 경로를 계산하지 못했습니다.", false);
            }
            return readRoutePreviewResult(outputDirectory);
        } catch (IOException exception) {
            log.warn("Route preview {} I/O failed", jobLabel, exception);
            throw new EngineRunException("ENGINE_ERROR: 대피 경로 입출력 처리에 실패했습니다.", false, exception);
        } catch (InterruptedException exception) {
            if (process != null) {
                stop(process);
            }
            Thread.currentThread().interrupt();
            throw new EngineRunException("대피 경로 계산이 중단되었습니다.", false, exception);
        } finally {
            deleteJobDirectory(jobDirectory);
        }
    }

    RoutePreviewResult readRoutePreviewResult(Path outputDirectory) throws IOException {
        Path routesPath = outputDirectory.resolve("routes.json");
        if (!Files.exists(routesPath)) {
            throw new IOException("엔진이 대피 경로를 내놓지 않았습니다.");
        }
        JsonNode root = objectMapper.readTree(routesPath.toFile());
        JsonNode routes = root.get("routes");
        if (routes == null || !routes.isArray()) {
            throw new IOException("대피 경로 응답 형식이 올바르지 않습니다.");
        }
        List<PreviewedRoute> parsed = readPreviewRoutes(routes, false);
        JsonNode zoneRoutes = root.get("zoneRoutes");
        List<PreviewedRoute> parsedZoneRoutes = zoneRoutes == null ? List.of() : readPreviewRoutes(zoneRoutes, true);
        return new RoutePreviewResult(
                List.copyOf(parsed), readRouteCoverage(root.get("coverage")), List.copyOf(parsedZoneRoutes));
    }

    private List<PreviewedRoute> readPreviewRoutes(JsonNode routes, boolean zoneRoute) throws IOException {
        if (!routes.isArray()) {
            throw new IOException("대피 경로 응답 형식이 올바르지 않습니다.");
        }
        List<PreviewedRoute> parsed = new ArrayList<>();
        for (JsonNode route : routes) {
            List<PointDto> waypoints = new ArrayList<>();
            JsonNode waypointNodes = route.get("waypoints");
            if (waypointNodes != null) {
                for (JsonNode waypoint : waypointNodes) {
                    waypoints.add(new PointDto(
                            waypoint.get("x").decimalValue(), waypoint.get("y").decimalValue()));
                }
            }
            JsonNode exitId = route.get("exitId");
            PointDto routeOrigin = readPoint(route.get("routeOrigin"));
            JsonNode adjusted = route.get("originAdjusted");
            JsonNode distance = route.get("distanceMeters");
            JsonNode zoneId = route.get("zoneId");
            if (routeOrigin == null
                    || adjusted == null
                    || !adjusted.isBoolean()
                    || distance == null
                    || !distance.isNumber()
                    || !Double.isFinite(distance.doubleValue())
                    || distance.doubleValue() < 0
                    || (zoneRoute && (zoneId == null || !zoneId.isIntegralNumber() || !zoneId.canConvertToLong()))) {
                throw new IOException("대피 경로 응답 값이 올바르지 않습니다.");
            }
            parsed.add(new PreviewedRoute(
                    zoneRoute ? zoneId.longValue() : null,
                    exitId == null || exitId.isNull() ? null : exitId.asLong(),
                    routeOrigin,
                    adjusted.booleanValue(),
                    distance.doubleValue(),
                    waypoints));
        }
        return parsed;
    }

    private RouteCoverage readRouteCoverage(JsonNode coverage) throws IOException {
        if (coverage == null || !coverage.isObject()) {
            throw new IOException("대피 경로 커버리지 응답 형식이 올바르지 않습니다.");
        }
        JsonNode originX = coverage.get("originX");
        JsonNode originY = coverage.get("originY");
        JsonNode step = coverage.get("step");
        JsonNode columns = coverage.get("columns");
        JsonNode rows = coverage.get("rows");
        JsonNode labels = coverage.get("labels");
        JsonNode exitIds = coverage.get("exitIds");
        if (originX == null
                || !originX.isNumber()
                || !Double.isFinite(originX.doubleValue())
                || originY == null
                || !originY.isNumber()
                || !Double.isFinite(originY.doubleValue())
                || step == null
                || !step.isNumber()
                || !Double.isFinite(step.doubleValue())
                || step.doubleValue() <= 0
                || columns == null
                || !columns.isIntegralNumber()
                || !columns.canConvertToInt()
                || columns.intValue() <= 0
                || rows == null
                || !rows.isIntegralNumber()
                || !rows.canConvertToInt()
                || rows.intValue() <= 0
                || labels == null
                || !labels.isArray()
                || exitIds == null
                || !exitIds.isArray()
                || exitIds.isEmpty()
                || (long) columns.intValue() * rows.intValue() != labels.size()) {
            throw new IOException("대피 경로 커버리지 응답 값이 올바르지 않습니다.");
        }
        List<Long> parsedExitIds = new ArrayList<>();
        for (JsonNode exitId : exitIds) {
            if (!exitId.isIntegralNumber() || !exitId.canConvertToLong()) {
                throw new IOException("대피 경로 커버리지 비상구 값이 올바르지 않습니다.");
            }
            parsedExitIds.add(exitId.longValue());
        }
        List<Integer> parsedLabels = new ArrayList<>();
        for (JsonNode label : labels) {
            if (!label.isIntegralNumber()
                    || !label.canConvertToInt()
                    || label.intValue() < -1
                    || label.intValue() >= parsedExitIds.size()) {
                throw new IOException("대피 경로 커버리지 라벨 값이 올바르지 않습니다.");
            }
            parsedLabels.add(label.intValue());
        }
        return new RouteCoverage(
                originX.decimalValue(),
                originY.decimalValue(),
                step.decimalValue(),
                columns.intValue(),
                rows.intValue(),
                List.copyOf(parsedLabels),
                List.copyOf(parsedExitIds));
    }

    /** 대피 경로 미리보기 결과 한 건. 시뮬레이션 식별자나 지표는 담지 않는다. */
    public record PreviewedRoute(
            Long zoneId,
            Long exitId,
            PointDto routeOrigin,
            boolean originAdjusted,
            double distanceMeters,
            List<PointDto> waypoints) {
        public PreviewedRoute(
                Long exitId,
                PointDto routeOrigin,
                boolean originAdjusted,
                double distanceMeters,
                List<PointDto> waypoints) {
            this(null, exitId, routeOrigin, originAdjusted, distanceMeters, waypoints);
        }
    }

    public record RoutePreviewResult(
            List<PreviewedRoute> routes, RouteCoverage coverage, List<PreviewedRoute> zoneRoutes) {
        public RoutePreviewResult(List<PreviewedRoute> routes, RouteCoverage coverage) {
            this(routes, coverage, List.of());
        }
    }

    public record RouteCoverage(
            BigDecimal originX,
            BigDecimal originY,
            BigDecimal step,
            int columns,
            int rows,
            List<Integer> labels,
            List<Long> exitIds) {}

    public record RouteOriginBounds(BigDecimal x, BigDecimal y, BigDecimal width, BigDecimal height) {}

    public record RoutePreviewZone(
            Long zoneId, BigDecimal x, BigDecimal y, BigDecimal width, BigDecimal height, Long defaultExitId) {}

    public EngineRun run(Long simulationId, SimulationSetupResponse setup) throws EngineRunException {
        return run(simulationId, setup, maxSimulationTimeSeconds);
    }

    public EngineRun run(Long simulationId, SimulationSetupResponse setup, double simulationTimeCapOverride)
            throws EngineRunException {
        double simulationTimeCap = simulationTimeCapOverride > 0 ? simulationTimeCapOverride : maxSimulationTimeSeconds;
        long totalStarted = System.nanoTime();
        long inputWriteMs = 0;
        long pythonProcessMs = 0;
        long resultReadMs = 0;
        long timelineReadMs = 0;
        long heatmapReadMs = 0;
        long cleanupMs;
        int timelineChunkCount = 0;
        int heatmapChunkCount = 0;
        long timelineChars = 0;
        long heatmapChars = 0;
        String outcome = "ERROR";
        Path jobDirectory = null;
        Process process = null;
        ProcessOutputCapture output = null;
        try {
            Files.createDirectories(workRoot);
            jobDirectory = Files.createTempDirectory(workRoot, "simulation-" + simulationId + "-")
                    .toAbsolutePath()
                    .normalize();
            Path inputPath = jobDirectory.resolve("input.json");
            Path outputDirectory = jobDirectory.resolve("output");
            long inputWriteStarted = System.nanoTime();
            try {
                objectMapper.writeValue(inputPath.toFile(), createInput(setup, simulationTimeCap));
            } finally {
                inputWriteMs = elapsedMillis(inputWriteStarted);
            }

            long pythonProcessStarted = System.nanoTime();
            try {
                process = new ProcessBuilder(
                                pythonCommand, scriptPath.toString(), inputPath.toString(), outputDirectory.toString())
                        .redirectErrorStream(true)
                        .start();
                output = new ProcessOutputCapture(process.getInputStream());
                if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                    stop(process);
                    throw new EngineRunException("ENGINE_TIMEOUT: 실제 실행시간 제한을 초과했습니다.", true);
                }
                String diagnostic = output.await();
                if (process.exitValue() != 0) {
                    SimulationFailureDetailResponse failureDetail = null;
                    if (process.exitValue() == ROUTING_ERROR_EXIT_CODE) {
                        failureDetail = readFailureDetail(outputDirectory, setup);
                        if (failureDetail == null) {
                            log.warn("Simulation {} engine returned an invalid routing failure detail", simulationId);
                        } else if (ROUTING_ERROR_CODE.equals(failureDetail.code())) {
                            log.warn(
                                    "Simulation {} engine routing failure code={} agentId={} recommendationPresent={}",
                                    simulationId,
                                    failureDetail.code(),
                                    failureDetail.agentId(),
                                    failureDetail.recommendedPosition() != null);
                        } else {
                            log.warn(
                                    "Simulation {} engine setup failure code={} affectedAgentCount={}",
                                    simulationId,
                                    failureDetail.code(),
                                    failureDetail.affectedAgentCount());
                        }
                    } else {
                        log.warn("Simulation {} engine process failed: {}", simulationId, diagnostic);
                    }
                    throw new EngineRunException("ENGINE_ERROR: 시뮬레이션 엔진 실행에 실패했습니다.", false, failureDetail);
                }
            } finally {
                pythonProcessMs = elapsedMillis(pythonProcessStarted);
            }

            EngineResult result;
            long resultReadStarted = System.nanoTime();
            try {
                result = objectMapper.readValue(
                        outputDirectory.resolve("result.json").toFile(), EngineResult.class);
                validateChunkCounts(result);
            } finally {
                resultReadMs = elapsedMillis(resultReadStarted);
            }
            List<TimelineChunk> timeline;
            long timelineReadStarted = System.nanoTime();
            try {
                timeline = readTimeline(outputDirectory, result.timelineChunkCount());
            } finally {
                timelineReadMs = elapsedMillis(timelineReadStarted);
            }
            timelineChunkCount = timeline.size();
            timelineChars = timeline.stream()
                    .mapToLong(chunk -> chunk.frameData().length())
                    .sum();
            List<HeatmapChunk> heatmaps;
            long heatmapReadStarted = System.nanoTime();
            try {
                heatmaps = readHeatmaps(outputDirectory, result.heatmapChunkCount());
            } finally {
                heatmapReadMs = elapsedMillis(heatmapReadStarted);
            }
            heatmapChunkCount = heatmaps.size();
            heatmapChars = heatmaps.stream()
                    .mapToLong(chunk -> chunk.densityData().length())
                    .sum();
            outcome = "COMPLETED";
            return new EngineRun(result, timeline, heatmaps, simulationTimeCap);
        } catch (EngineRunException exception) {
            outcome = exception.isTimeout() ? "TIMEOUT" : "ERROR";
            throw exception;
        } catch (IOException exception) {
            log.warn("Simulation {} engine I/O failed", simulationId, exception);
            throw new EngineRunException("ENGINE_ERROR: 시뮬레이션 엔진 입출력 처리에 실패했습니다.", false, exception);
        } catch (InterruptedException exception) {
            if (process != null) {
                stop(process);
            }
            Thread.currentThread().interrupt();
            throw new EngineRunException("엔진 실행이 중단되었습니다.", false, exception);
        } finally {
            long cleanupStarted = System.nanoTime();
            try {
                deleteJobDirectory(jobDirectory);
            } finally {
                cleanupMs = elapsedMillis(cleanupStarted);
            }
            log.info(
                    "simulation_engine_phase simulationId={} outcome={} inputWriteMs={} pythonProcessMs={} resultReadMs={} timelineReadMs={} heatmapReadMs={} cleanupMs={} totalMs={} timelineChunks={} timelineChars={} heatmapChunks={} heatmapChars={}",
                    simulationId,
                    outcome,
                    inputWriteMs,
                    pythonProcessMs,
                    resultReadMs,
                    timelineReadMs,
                    heatmapReadMs,
                    cleanupMs,
                    elapsedMillis(totalStarted),
                    timelineChunkCount,
                    timelineChars,
                    heatmapChunkCount,
                    heatmapChars);
        }
    }

    private static long elapsedMillis(long startedNanos) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedNanos);
    }

    Map<String, Object> createInput(SimulationSetupResponse setup) {
        return createInput(setup, maxSimulationTimeSeconds);
    }

    private Map<String, Object> createInput(SimulationSetupResponse setup, double simulationTimeCap) {
        Map<String, Object> model = new LinkedHashMap<>();
        model.put("modelProfile", setup.modelProfile());
        model.put("routingProfile", setup.routingProfile());
        model.put("walkingSpeed", setup.walkingSpeed());
        model.put("initialResponseTimeStdDev", setup.initialResponseTimeStdDev());

        Map<String, Object> input = new LinkedHashMap<>();
        input.put("model", model);
        input.put("randomSeed", setup.randomSeed());
        input.put("drawing", setup.drawing());
        input.put("agents", setup.agentPositions());
        input.put("hazards", setup.hazardZones());
        input.put("selectedExitIds", setup.selectedExitIds());
        input.put("maxSimulationTimeSeconds", simulationTimeCap);
        input.put("frameIntervalSeconds", frameIntervalSeconds);
        input.put("recoveryDetectorEnabled", sharedTargetRecoveryEnabled);
        return input;
    }

    private List<TimelineChunk> readTimeline(Path outputDirectory, int count) throws IOException {
        if (count < 1) {
            throw new IOException("엔진이 타임라인을 생성하지 않았습니다.");
        }
        java.util.ArrayList<TimelineChunk> chunks = new java.util.ArrayList<>(count);
        for (int sequence = 0; sequence < count; sequence++) {
            Path path = outputDirectory.resolve("timeline").resolve(String.format("%06d.json", sequence));
            String json = Files.readString(path, StandardCharsets.UTF_8);
            chunks.add(new TimelineChunk(sequence, json));
        }
        return List.copyOf(chunks);
    }

    static void validateChunkCounts(EngineResult result) throws EngineRunException {
        if (result.timelineChunkCount() == null || result.heatmapChunkCount() == null) {
            throw new EngineRunException("ENGINE_ERROR: 엔진 결과의 청크 개수가 누락되었습니다.", false);
        }
    }

    SimulationFailureDetailResponse readFailureDetail(Path outputDirectory, SimulationSetupResponse setup) {
        try {
            Path detailPath = outputDirectory.resolve("error.json");
            if (!Files.isRegularFile(detailPath)
                    || Files.size(detailPath) < 1
                    || Files.size(detailPath) > MAX_FAILURE_DETAIL_BYTES) {
                return null;
            }
            JsonNode root = objectMapper.readTree(detailPath.toFile());
            if (root == null || !root.isObject() || !root.has("schemaVersion") || !root.has("code")) {
                return null;
            }
            JsonNode schemaVersion = root.get("schemaVersion");
            JsonNode code = root.get("code");
            if (!schemaVersion.isIntegralNumber()
                    || !schemaVersion.canConvertToInt()
                    || schemaVersion.intValue() != 1
                    || !code.isTextual()) {
                return null;
            }
            String codeValue = code.textValue();
            if (ROUTING_ERROR_CODE.equals(codeValue)) {
                return readAgentRouteUnreachableDetail(root, setup);
            }
            if (NO_REACHABLE_EXIT_CODE.equals(codeValue)) {
                return readNoReachableExitDetail(root, setup);
            }
            if (NO_WALKABLE_ORIGIN_CODE.equals(codeValue)) {
                return readNoWalkableOriginDetail(root, setup);
            }
            return null;
        } catch (IOException | RuntimeException exception) {
            return null;
        }
    }

    private SimulationFailureDetailResponse readNoWalkableOriginDetail(JsonNode root, SimulationSetupResponse setup) {
        if (root.size() != 3
                || !root.has("agentId")
                || !NO_WALKABLE_ORIGIN_CODE.equals(root.get("code").textValue())) {
            return null;
        }
        JsonNode agentId = root.get("agentId");
        if (!agentId.isIntegralNumber()
                || !agentId.canConvertToInt()
                || agentId.intValue() < 1
                || agentId.intValue() > setup.agentPositions().size()) {
            return null;
        }
        return new SimulationFailureDetailResponse(
                NO_WALKABLE_ORIGIN_CODE,
                agentId.longValue(),
                setup.agentPositions().get(agentId.intValue() - 1),
                null,
                null,
                null,
                setup.selectedExitIds(),
                null);
    }

    private SimulationFailureDetailResponse readAgentRouteUnreachableDetail(
            JsonNode root, SimulationSetupResponse setup) {
        if (root.size() != 4
                || !root.has("agentId")
                || !root.has("recommendedPosition")
                || !ROUTING_ERROR_CODE.equals(root.get("code").textValue())) {
            return null;
        }
        JsonNode agentIdNode = root.get("agentId");
        if (!agentIdNode.isIntegralNumber() || !agentIdNode.canConvertToInt()) {
            return null;
        }
        int agentId = agentIdNode.intValue();
        if (agentId < 1 || agentId > setup.agentPositions().size()) {
            return null;
        }
        JsonNode recommendedPosition = root.get("recommendedPosition");
        PointDto recommendation = recommendedPosition.isNull() ? null : readPoint(recommendedPosition);
        if (!recommendedPosition.isNull()
                && (recommendation == null
                        || recommendation.x().signum() < 0
                        || recommendation.y().signum() < 0
                        || recommendation.x().compareTo(setup.drawing().width()) > 0
                        || recommendation.y().compareTo(setup.drawing().height()) > 0)) {
            return null;
        }
        return new SimulationFailureDetailResponse(
                ROUTING_ERROR_CODE,
                (long) agentId,
                setup.agentPositions().get(agentId - 1),
                recommendation,
                null,
                null,
                null,
                null);
    }

    private SimulationFailureDetailResponse readNoReachableExitDetail(JsonNode root, SimulationSetupResponse setup) {
        if (root.size() != 7
                || !root.has("affectedAgentCount")
                || !root.has("representativeAgentIds")
                || !root.has("componentCount")
                || !root.has("selectedExitIds")
                || !root.has("reason")
                || !NO_REACHABLE_EXIT_CODE.equals(root.get("code").textValue())) {
            return null;
        }
        JsonNode affectedNode = root.get("affectedAgentCount");
        JsonNode componentNode = root.get("componentCount");
        if (!affectedNode.isIntegralNumber()
                || !affectedNode.canConvertToLong()
                || affectedNode.longValue() < 1
                || affectedNode.longValue() > setup.agentPositions().size()
                || !componentNode.isIntegralNumber()
                || !componentNode.canConvertToLong()
                || componentNode.longValue() < 1
                || componentNode.longValue() > setup.agentPositions().size()) {
            return null;
        }
        List<Long> representativeIds = readBoundedIdList(
                root.get("representativeAgentIds"), setup.agentPositions().size());
        List<Long> exitIds = readIdListInSet(root.get("selectedExitIds"), setup.selectedExitIds());
        if (representativeIds == null || representativeIds.isEmpty() || exitIds == null || exitIds.isEmpty()) {
            return null;
        }
        JsonNode reasonNode = root.get("reason");
        if (!reasonNode.isTextual() || !isSupportedNoReachableExitReason(reasonNode.textValue())) {
            return null;
        }
        return new SimulationFailureDetailResponse(
                NO_REACHABLE_EXIT_CODE,
                null,
                null,
                null,
                affectedNode.longValue(),
                representativeIds,
                exitIds,
                reasonNode.textValue());
    }

    private static boolean isSupportedNoReachableExitReason(String reason) {
        return "NO_EXIT_SEED_IN_OCCUPIED_COMPONENT".equals(reason);
    }

    private static List<Long> readBoundedIdList(JsonNode node, int maximumValue) {
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

    private static List<Long> readIdListInSet(JsonNode node, List<Long> allowedValues) {
        if (node == null || !node.isArray() || node.size() < 1 || node.size() > allowedValues.size()) {
            return null;
        }
        java.util.ArrayList<Long> values = new java.util.ArrayList<>(node.size());
        for (JsonNode item : node) {
            if (!item.isIntegralNumber() || !item.canConvertToLong()) {
                return null;
            }
            long value = item.longValue();
            if (!allowedValues.contains(value)) {
                return null;
            }
            values.add(value);
        }
        return List.copyOf(values);
    }

    private static PointDto readPoint(JsonNode node) {
        if (node == null || !node.isObject() || node.size() != 2 || !node.has("x") || !node.has("y")) {
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

    private List<HeatmapChunk> readHeatmaps(Path outputDirectory, int count) throws IOException {
        if (count < 1) {
            throw new IOException("엔진이 히트맵을 생성하지 않았습니다.");
        }
        java.util.ArrayList<HeatmapChunk> chunks = new java.util.ArrayList<>(count);
        for (int sequence = 0; sequence < count; sequence++) {
            Path path = outputDirectory.resolve("heatmap").resolve(String.format("%06d.json", sequence));
            String json = Files.readString(path, StandardCharsets.UTF_8);
            chunks.add(new HeatmapChunk(sequence, json));
        }
        return List.copyOf(chunks);
    }

    private static Path resolveScript(String configured) {
        Path configuredPath = Path.of(configured);
        if (configuredPath.isAbsolute()) {
            return configuredPath.normalize();
        }
        Path current = configuredPath.toAbsolutePath().normalize();
        if (Files.isRegularFile(current)) {
            return current;
        }
        return Path.of("apps", "simulation-service")
                .resolve(configuredPath)
                .toAbsolutePath()
                .normalize();
    }

    private static String resolvePythonCommand(String configured) {
        if (!"python".equals(configured)) {
            return configured;
        }
        for (Path candidate : List.of(
                Path.of("engine", ".venv", "Scripts", "python.exe"),
                Path.of("engine", ".venv", "bin", "python"),
                Path.of("apps", "simulation-service", "engine", ".venv", "Scripts", "python.exe"),
                Path.of("apps", "simulation-service", "engine", ".venv", "bin", "python"))) {
            Path resolved = candidate.toAbsolutePath().normalize();
            if (Files.isRegularFile(resolved)) {
                return resolved.toString();
            }
        }
        return configured;
    }

    private static void stop(Process process) {
        process.destroy();
        boolean interrupted = false;
        try {
            if (!process.waitFor(5, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                process.waitFor(5, TimeUnit.SECONDS);
            }
        } catch (InterruptedException exception) {
            interrupted = true;
            process.destroyForcibly();
            try {
                process.waitFor(5, TimeUnit.SECONDS);
            } catch (InterruptedException secondInterruption) {
                interrupted = true;
            }
        }
        if (interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    static String readDiagnostic(InputStream input) throws IOException {
        try (input;
                var captured = new ByteArrayOutputStream(MAX_ENGINE_MESSAGE_LENGTH)) {
            byte[] buffer = new byte[4096];
            int length;
            while ((length = input.read(buffer)) != -1) {
                int remaining = MAX_ENGINE_MESSAGE_LENGTH - captured.size();
                if (remaining > 0) {
                    captured.write(buffer, 0, Math.min(length, remaining));
                }
            }
            String message = captured.toString(StandardCharsets.UTF_8)
                    .replaceAll("\\p{Cntrl}", " ")
                    .replaceAll("\\s+", " ")
                    .trim();
            return message.isEmpty() ? "no diagnostic output" : message;
        }
    }

    private static final class ProcessOutputCapture {
        private String diagnostic = "diagnostic output unavailable";
        private final Thread reader;

        private ProcessOutputCapture(InputStream input) {
            reader = new Thread(() -> {
                try {
                    diagnostic = readDiagnostic(input);
                } catch (IOException ignored) {
                    // The public error remains generic when diagnostic capture fails.
                }
            });
            reader.setName("simulation-engine-output");
            reader.setDaemon(true);
            reader.start();
        }

        private String await() throws InterruptedException {
            reader.join();
            return diagnostic;
        }
    }

    private void deleteJobDirectory(Path jobDirectory) {
        if (jobDirectory == null || !jobDirectory.startsWith(workRoot) || jobDirectory.equals(workRoot)) {
            return;
        }
        try (var paths = Files.walk(jobDirectory)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // Temporary engine files are removed on a best-effort basis.
                }
            });
        } catch (IOException | UncheckedIOException | SecurityException ignored) {
            // Temporary engine files are removed on a best-effort basis.
        }
    }

    public record EngineRun(
            EngineResult result,
            List<TimelineChunk> timelineChunks,
            List<HeatmapChunk> heatmapChunks,
            double maxSimulationTimeSeconds) {}

    public record TimelineChunk(int sequence, String frameData) {}

    public record HeatmapChunk(int sequence, String densityData) {}

    public record EngineResult(
            String engineVersion,
            String terminationReason,
            Double simulationDurationSeconds,
            Integer evacuatedPeople,
            Integer remainingPeople,
            Double totalEvacuationTimeSeconds,
            Double averageEvacuationTimeSeconds,
            Double frameIntervalSeconds,
            Integer timelineChunkCount,
            Integer heatmapChunkCount,
            Double maxDensity,
            com.fasterxml.jackson.databind.JsonNode terminationDetail,
            com.fasterxml.jackson.databind.JsonNode recoverySummary) {}

    public static class EngineRunException extends Exception {
        private final boolean timeout;
        private final SimulationFailureDetailResponse failureDetail;

        public EngineRunException(String message, boolean timeout) {
            this(message, timeout, null, null);
        }

        public EngineRunException(String message, boolean timeout, Throwable cause) {
            this(message, timeout, null, cause);
        }

        public EngineRunException(String message, boolean timeout, SimulationFailureDetailResponse failureDetail) {
            this(message, timeout, failureDetail, null);
        }

        private EngineRunException(
                String message, boolean timeout, SimulationFailureDetailResponse failureDetail, Throwable cause) {
            super(message, cause);
            this.timeout = timeout;
            this.failureDetail = failureDetail;
        }

        public boolean isTimeout() {
            return timeout;
        }

        public SimulationFailureDetailResponse failureDetail() {
            return failureDetail;
        }
    }
}
