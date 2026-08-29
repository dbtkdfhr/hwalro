package com.hwalro.simulation.search.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hwalro.simulation.search.config.LayoutSearchProperties;
import com.hwalro.simulation.search.domain.ChangeOp;
import com.hwalro.simulation.search.domain.SearchResult;
import com.hwalro.simulation.search.domain.SearchResult.SearchCandidate;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
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
public class LayoutSearchRunner {
    private static final Logger log = LoggerFactory.getLogger(LayoutSearchRunner.class);
    private static final int MAX_LOG_LINE_LENGTH = 1000;

    private final ObjectMapper objectMapper;
    private final String pythonCommand;
    private final Path scriptPath;
    private final Path workRoot;
    private final Duration timeout;
    private final boolean keepJobDirectory;

    public LayoutSearchRunner(
            ObjectMapper objectMapper,
            LayoutSearchProperties properties,
            @Value("${simulation.engine.python:python}") String pythonCommand) {
        this.objectMapper = objectMapper;
        this.pythonCommand = resolvePythonCommand(pythonCommand);
        this.scriptPath = resolveScript(properties.getScript());
        this.workRoot = properties.getWorkDirectory() == null
                        || properties.getWorkDirectory().isBlank()
                ? Path.of(System.getProperty("java.io.tmpdir"), "hwalro-layout-search")
                        .toAbsolutePath()
                        .normalize()
                : Path.of(properties.getWorkDirectory()).toAbsolutePath().normalize();
        this.timeout = properties.getSearchTimeout();
        this.keepJobDirectory = properties.isKeepJobDirectory();
    }

    public SearchResult run(SearchInput input) {
        if (!Files.isRegularFile(scriptPath)) {
            throw new SearchRunException("배치 탐색 엔진을 찾을 수 없습니다.");
        }
        Path jobDirectory = null;
        Process process = null;
        SearchLogRelay relay = null;
        try {
            Files.createDirectories(workRoot);
            jobDirectory = Files.createTempDirectory(workRoot, "search-" + input.studyId() + "-")
                    .toAbsolutePath()
                    .normalize();
            Path inputPath = jobDirectory.resolve("input.json");
            Path outputPath = jobDirectory.resolve("output.json");
            objectMapper.writeValue(inputPath.toFile(), createInput(input));

            process = new ProcessBuilder(
                            pythonCommand, scriptPath.toString(), inputPath.toString(), outputPath.toString())
                    .redirectErrorStream(true)
                    .start();
            relay = new SearchLogRelay(process.getInputStream(), input.studyId());
            relay.start();
            if (!process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS)) {
                stop(process);
                relay.await();
                throw new SearchRunException("배치 탐색 엔진 실행 시간이 %d초를 초과했습니다.".formatted(timeout.toSeconds()));
            }
            relay.await();
            if (process.exitValue() != 0) {
                log.warn(
                        "Layout search failed for {} with exit code {}: {}",
                        input.studyId(),
                        process.exitValue(),
                        relay.lastLine());
                throw new SearchRunException("배치 탐색 엔진 실행에 실패했습니다.");
            }
            if (!Files.isRegularFile(outputPath)) {
                throw new SearchRunException("배치 탐색 엔진이 결과 파일을 생성하지 않았습니다.");
            }
            SearchResult result = objectMapper.readValue(outputPath.toFile(), SearchResult.class);
            validate(result, input.maxCandidates(), input.exhaustive());
            return result;
        } catch (SearchRunException exception) {
            throw exception;
        } catch (IOException exception) {
            throw new SearchRunException("배치 탐색 엔진 입출력 처리에 실패했습니다.", exception);
        } catch (InterruptedException exception) {
            if (process != null) {
                stop(process);
            }
            Thread.currentThread().interrupt();
            throw new SearchRunException("배치 탐색 엔진 실행이 중단되었습니다.", exception);
        } finally {
            if (keepJobDirectory) {
                log.info("Layout search {} job directory kept for inspection: {}", input.studyId(), jobDirectory);
            } else {
                deleteJobDirectory(jobDirectory);
            }
        }
    }

    Map<String, Object> createInput(SearchInput input) {
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("searchId", input.studyId());
        value.put("round", input.round());
        value.put("drawing", input.drawing());
        value.put("agents", input.agents());
        value.put("hazards", input.hazards());
        value.put("selectedExitIds", input.selectedExitIds());
        value.put("plannerMode", "IDEAL_ROUTE_DOCKING");
        value.put("densityThreshold", input.densityThreshold());
        value.put("findings", input.findings());
        value.put("parents", input.parents());
        if (!input.exhaustive()) {
            value.put("maxCandidates", input.maxCandidates());
        }
        value.put("exhaustive", input.exhaustive());
        value.put("generationMode", input.exhaustive() ? "EXHAUSTIVE" : "BOUNDED");
        value.put("surrogateMode", input.surrogateMode());
        value.put("surrogateBundle", input.surrogateBundle());
        if (input.constraints() != null) {
            value.put("constraints", parseConstraintsInput(input.constraints()));
        }
        return value;
    }

    private Object parseConstraintsInput(Object raw) {
        if (raw instanceof String json && !json.isBlank()) {
            try {
                return objectMapper.readValue(json, Object.class);
            } catch (IOException exception) {
                throw new IllegalStateException("제약 조건 JSON을 파싱하지 못했습니다.", exception);
            }
        }
        return raw;
    }

    static void validate(SearchResult result, int maxCandidates, boolean exhaustive) {
        if (result == null
                || result.plannerVersion() == null
                || result.plannerVersion().isBlank()
                || result.candidates() == null
                || result.rejected() == null
                || !exhaustive && result.candidates().size() > maxCandidates) {
            throw new SearchRunException("배치 탐색 엔진 결과 계약이 올바르지 않습니다.");
        }
        result.candidates().forEach(LayoutSearchRunner::validateCandidate);
        result.rejected().forEach(rejected -> {
            if (rejected.operatorType() == null
                    || rejected.operatorType().isBlank()
                    || rejected.fabricId() == null
                    || rejected.reason() == null
                    || rejected.reason().isBlank()) {
                throw new SearchRunException("배치 탐색 엔진의 거부 후보 형식이 올바르지 않습니다.");
            }
        });
    }

    static void validate(SearchResult result, int maxCandidates) {
        validate(result, maxCandidates, false);
    }

    private static void validateCandidate(SearchCandidate candidate) {
        if (candidate == null
                || candidate.originFindingType() == null
                || candidate.operatorType() == null
                || candidate.proxyScore() == null
                || !Double.isFinite(candidate.proxyScore())
                || candidate.proxyScore() < 0
                || candidate.ops() == null
                || candidate.ops().isEmpty()
                || candidate.rationale() == null) {
            throw new SearchRunException("배치 탐색 엔진의 후보 형식이 올바르지 않습니다.");
        }
        candidate.ops().forEach(LayoutSearchRunner::validateOperation);
    }

    private static void validateOperation(ChangeOp operation) {
        if (operation == null
                || !"MOVE_FABRIC".equals(operation.type())
                || operation.fabricId() == null
                || operation.fabricId() <= 0
                || operation.before() == null
                || operation.after() == null) {
            throw new SearchRunException("배치 탐색 엔진의 변경 작업 형식이 올바르지 않습니다.");
        }
        ChangeOp.FabricTransform before = operation.before();
        ChangeOp.FabricTransform after = operation.after();
        if (!isFiniteRectangle(before) || !isFiniteRectangle(after)) {
            throw new SearchRunException("배치 탐색 엔진의 변경 작업 좌표가 유효하지 않습니다.");
        }
        if (!LayoutSearchPrecision.sameSpan(before.startX(), before.endX(), after.startX(), after.endX())
                || !LayoutSearchPrecision.sameSpan(before.startY(), before.endY(), after.startY(), after.endY())) {
            throw new SearchRunException("배치 탐색 엔진의 변경 작업이 fabric 크기를 변경합니다.");
        }
        if (sameRectangle(before, after)) {
            throw new SearchRunException("배치 탐색 엔진의 변경 작업 전후가 같습니다.");
        }
    }

    private static boolean isFiniteRectangle(ChangeOp.FabricTransform rectangle) {
        return rectangle.startX() != null
                && rectangle.startY() != null
                && rectangle.endX() != null
                && rectangle.endY() != null
                && rectangle.rotation() != null
                && width(rectangle).compareTo(BigDecimal.ZERO) > 0
                && height(rectangle).compareTo(BigDecimal.ZERO) > 0
                && rectangle.startX().abs().compareTo(BigDecimal.valueOf(1_000_000)) <= 0
                && rectangle.startY().abs().compareTo(BigDecimal.valueOf(1_000_000)) <= 0
                && rectangle.endX().abs().compareTo(BigDecimal.valueOf(1_000_000)) <= 0
                && rectangle.endY().abs().compareTo(BigDecimal.valueOf(1_000_000)) <= 0;
    }

    private static BigDecimal width(ChangeOp.FabricTransform rectangle) {
        return rectangle.endX().subtract(rectangle.startX()).abs();
    }

    private static BigDecimal height(ChangeOp.FabricTransform rectangle) {
        return rectangle.endY().subtract(rectangle.startY()).abs();
    }

    private static boolean sameRectangle(ChangeOp.FabricTransform before, ChangeOp.FabricTransform after) {
        return LayoutSearchPrecision.sameTransform(before, after);
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
        try {
            if (!process.waitFor(5, TimeUnit.SECONDS)) {
                process.destroyForcibly();
                process.waitFor(5, TimeUnit.SECONDS);
            }
        } catch (InterruptedException exception) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();
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
                    // 임시 파일 정리는 best effort입니다.
                }
            });
        } catch (IOException ignored) {
            // 임시 파일 정리는 best effort입니다.
        }
    }

    public record SearchInput(
            long studyId,
            int round,
            Object drawing,
            Object agents,
            Object hazards,
            Object selectedExitIds,
            Object densityThreshold,
            Object findings,
            List<Map<String, Object>> parents,
            int maxCandidates,
            boolean exhaustive,
            String surrogateMode,
            String surrogateBundle,
            Object constraints) {
        public SearchInput(
                long studyId,
                int round,
                Object drawing,
                Object agents,
                Object hazards,
                Object selectedExitIds,
                Object densityThreshold,
                Object findings,
                List<Map<String, Object>> parents,
                int maxCandidates,
                boolean exhaustive,
                String surrogateMode,
                String surrogateBundle) {
            this(
                    studyId,
                    round,
                    drawing,
                    agents,
                    hazards,
                    selectedExitIds,
                    densityThreshold,
                    findings,
                    parents,
                    maxCandidates,
                    exhaustive,
                    surrogateMode,
                    surrogateBundle,
                    null);
        }

        public SearchInput(
                long studyId,
                int round,
                Object drawing,
                Object agents,
                Object hazards,
                Object selectedExitIds,
                Object densityThreshold,
                Object findings,
                List<Map<String, Object>> parents,
                int maxCandidates) {
            this(
                    studyId,
                    round,
                    drawing,
                    agents,
                    hazards,
                    selectedExitIds,
                    densityThreshold,
                    findings,
                    parents,
                    maxCandidates,
                    false,
                    "SHADOW",
                    "");
        }
    }

    private static final class SearchLogRelay {
        private final InputStream input;
        private final long studyId;
        private final Thread reader;
        private volatile String lastLine = "no diagnostic output";

        private SearchLogRelay(InputStream input, long studyId) {
            this.input = input;
            this.studyId = studyId;
            this.reader = new Thread(this::read);
            this.reader.setName("layout-search-output");
            this.reader.setDaemon(true);
        }

        private void start() {
            reader.start();
        }

        private void read() {
            try (input;
                    BufferedReader lines = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
                String line;
                while ((line = lines.readLine()) != null) {
                    String safe = line.replaceAll("\\p{Cntrl}", " ")
                            .replaceAll("\\s+", " ")
                            .trim();
                    if (safe.length() > MAX_LOG_LINE_LENGTH) {
                        safe = safe.substring(0, MAX_LOG_LINE_LENGTH);
                    }
                    if (!safe.isEmpty()) {
                        lastLine = safe;
                        log.info("Layout search {}: {}", studyId, safe);
                    }
                }
            } catch (IOException exception) {
                log.debug("Could not relay layout search output for search {}", studyId, exception);
            }
        }

        private void await() throws InterruptedException {
            reader.join();
        }

        private String lastLine() {
            return lastLine;
        }
    }

    public static class SearchRunException extends RuntimeException {
        public SearchRunException(String message) {
            super(message);
        }

        public SearchRunException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
