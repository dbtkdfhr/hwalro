package com.hwalro.simulation.search.dto;

import java.math.BigDecimal;
import java.util.List;

public final class LayoutSearchDtos {
    private LayoutSearchDtos() {}

    /**
     * {@code verify}가 참이면 후보마다 실제 엔진으로 시행해 실측 개선을 확인한다. 생략하면 확인하지 않고 후보만
     * 생성한다 - 어떤 후보를 실제로 돌려볼지는 사용자가 고른다.
     */
    /**
     * 제약은 도면에 저장된 값을 쓴다. 요청으로 덮어쓰는 경로를 두면 진실 원천이 둘이 된다. 실행마다 달라지는 값은 {@code verify}뿐이다.
     */
    public record StartStudyRequest(boolean verify) {}

    public record StartStudyResponse(long searchId, String status) {}

    public record CancellationResponse(long searchId, String status) {}

    public record PreparedSimulationDto(Long simulationId, String status) {}

    public record MetricDto(String metricType, String unit, double metricValue) {}

    public record RegionDto(double startX, double startY, double endX, double endY) {}

    public record EvidenceDto(String metric, double value, String unit, String source) {}

    public record FindingDto(
            String type, double severity, RegionDto region, EvidenceDto evidence, String description) {}

    public record DiagnosisDto(List<FindingDto> findings) {}

    public record FabricTransformDto(
            BigDecimal startX, BigDecimal startY, BigDecimal endX, BigDecimal endY, BigDecimal rotation) {}

    public record ChangeOpDto(String type, Long fabricId, FabricTransformDto before, FabricTransformDto after) {}

    public record ChangeSetDto(int schemaVersion, String coordinateUnit, List<ChangeOpDto> ops) {}

    public record MetricDeltaDto(
            String metricType, double baseline, double measured, double difference, double ratio) {}

    public record RationaleDto(
            Integer findingIndex, String operatorType, String direction, Double distanceMeters, String description) {}

    public record ProgressDto(
            int verifiedCount,
            Integer plannedCount,
            int round,
            long baselineRunSeconds,
            Long estimatedRemainingSeconds,
            double trialCapSeconds) {}

    public record CandidateDto(
            Long candidateId,
            int round,
            String originFindingType,
            String operatorType,
            String status,
            RationaleDto rationale,
            ChangeSetDto changeSet,
            List<MetricDto> measuredMetrics,
            List<MetricDeltaDto> delta,
            String rejectReason,
            PreparedSimulationDto preparedSimulation) {}

    public record LayoutSearchResponse(
            long searchId,
            long baselineSimulationId,
            long baselineLayoutVersionId,
            String status,
            String plannerVersion,
            ProgressDto progress,
            List<MetricDto> baselineMetrics,
            DiagnosisDto diagnosis,
            List<CandidateDto> improvedCandidates,
            List<CandidateDto> rejectedCandidates,
            String failureCode,
            String failureMessage) {}
}
