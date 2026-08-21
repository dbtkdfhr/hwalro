package com.hwalro.simulation.simulation.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public final class SimulationDtos {
    private SimulationDtos() {}

    public record DraftCreateRequest(Long layoutVersionId, Long parentSimulationId, String title) {
        public DraftCreateRequest(Long layoutVersionId, Long parentSimulationId) {
            this(layoutVersionId, parentSimulationId, null);
        }
    }

    public record PlacementAdjustmentDraftRequest(Boolean applyRecommendation) {}

    public record SetupUpdateRequest(
            String title,
            List<PointDto> agentPositions,
            List<HazardZoneDto> hazardZones,
            List<Long> selectedExitIds,
            BigDecimal walkingSpeed,
            BigDecimal initialResponseTimeStdDev) {
        public SetupUpdateRequest(
                List<PointDto> agentPositions,
                List<HazardZoneDto> hazardZones,
                List<Long> selectedExitIds,
                BigDecimal walkingSpeed,
                BigDecimal initialResponseTimeStdDev) {
            this(null, agentPositions, hazardZones, selectedExitIds, walkingSpeed, initialResponseTimeStdDev);
        }
    }

    public record PointDto(BigDecimal x, BigDecimal y) {}

    public record HazardZoneDto(Long id, BigDecimal centerX, BigDecimal centerY, BigDecimal radius) {}

    public record SegmentDto(String name, BigDecimal startX, BigDecimal startY, BigDecimal endX, BigDecimal endY) {}

    public record RectDto(
            String name, BigDecimal startX, BigDecimal startY, BigDecimal endX, BigDecimal endY, BigDecimal rotation) {}

    public record FabricRectDto(
            Long id,
            String name,
            BigDecimal startX,
            BigDecimal startY,
            BigDecimal endX,
            BigDecimal endY,
            BigDecimal rotation) {}

    public record TextDto(String text, BigDecimal x, BigDecimal y) {}

    public record ExitDto(
            Long id, String name, BigDecimal startX, BigDecimal startY, BigDecimal endX, BigDecimal endY) {}

    public record DrawingGeometryDto(
            Long layoutId,
            String title,
            BigDecimal width,
            BigDecimal height,
            List<PointDto> outsideBoundary,
            List<SegmentDto> walls,
            List<RectDto> pillars,
            List<FabricRectDto> fabrics,
            List<TextDto> layoutTexts,
            List<ExitDto> exits) {}

    public record SimulationSummaryResponse(
            Long id,
            Long layoutVersionId,
            Long parentSimulationId,
            String title,
            String status,
            LocalDateTime createdAt,
            Integer totalPeople) {}

    public record SimulationOverviewResponse(
            Long id,
            Long layoutVersionId,
            Long layoutId,
            String layoutTitle,
            Integer layoutVersionNumber,
            Long createdBy,
            String title,
            String status,
            LocalDateTime createdAt,
            LocalDateTime requestedAt,
            LocalDateTime startedAt,
            LocalDateTime finishedAt,
            Integer totalPeople,
            String terminationReason) {}

    public record SimulationOverviewPageResponse(
            int totalCount, int page, int size, boolean hasNext, List<SimulationOverviewResponse> items) {}

    public record SimulationWorkSummaryResponse(int inProgressCount, int completedThisWeekCount) {}

    public record SimulationSetupResponse(
            Long simulationId,
            Long layoutVersionId,
            Long parentSimulationId,
            String title,
            String status,
            LocalDateTime createdAt,
            Integer randomSeed,
            String modelProfile,
            String routingProfile,
            Integer totalPeople,
            BigDecimal walkingSpeed,
            BigDecimal initialResponseTimeStdDev,
            List<PointDto> agentPositions,
            List<HazardZoneDto> hazardZones,
            List<Long> selectedExitIds,
            DrawingGeometryDto drawing) {}

    public record SimulationMetricResponse(String metricType, String unit, double metricValue) {}

    public record SimulationResultResponse(
            Long id,
            String engineVersion,
            String terminationReason,
            BigDecimal simulationDurationSeconds,
            BigDecimal frameIntervalSeconds,
            Integer timelineChunkCount,
            BigDecimal timelineChunkDurationSeconds,
            Integer heatmapChunkCount,
            String terminationDetail,
            List<SimulationMetricResponse> metrics) {}

    public record SimulationFailureDetailResponse(
            String code,
            Long agentId,
            PointDto currentPosition,
            PointDto recommendedPosition,
            Long affectedAgentCount,
            List<Long> representativeAgentIds,
            List<Long> selectedExitIds,
            String reason) {}

    public record SimulationRoutingValidationResponse(
            boolean valid, String message, SimulationFailureDetailResponse failureDetail) {}

    public record SimulationExecutionResponse(
            Long simulationId,
            String status,
            LocalDateTime requestedAt,
            LocalDateTime startedAt,
            LocalDateTime finishedAt,
            String failureMessage,
            SimulationFailureDetailResponse failureDetail,
            SimulationResultResponse result) {}

    public record TimelineAgentResponse(Long agentId, BigDecimal x, BigDecimal y) {}

    public record TimelineFrameResponse(
            Integer frameIndex,
            BigDecimal timeSeconds,
            Integer activeAgentCount,
            Integer evacuatedCount,
            List<TimelineAgentResponse> agents) {}

    public record ExitEventResponse(Integer frameIndex, BigDecimal timeSeconds, Long agentId, Long exitId) {}

    public record TimelineChunkResponse(
            Integer schemaVersion,
            String coordinateSystem,
            String coordinateUnit,
            BigDecimal frameRate,
            Integer chunkSequence,
            Integer startFrame,
            Integer endFrame,
            List<TimelineFrameResponse> frames,
            List<ExitEventResponse> exitEvents) {}

    public record HeatmapGridResponse(
            BigDecimal originX,
            BigDecimal originY,
            BigDecimal cellSize,
            Integer rows,
            Integer columns,
            String cellOrder) {}

    public record HeatmapFrameResponse(Integer frameIndex, BigDecimal timeSeconds, List<List<BigDecimal>> cells) {}

    public record HeatmapChunkResponse(
            Integer schemaVersion,
            String analysisVersion,
            String coordinateSystem,
            String coordinateUnit,
            String densityMethod,
            String densityUnit,
            BigDecimal frameRate,
            Integer chunkSequence,
            Integer startFrame,
            Integer endFrame,
            HeatmapGridResponse grid,
            List<HeatmapFrameResponse> frames) {}
}
