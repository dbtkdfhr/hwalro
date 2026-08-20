package com.hwalro.simulation.result.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface SimulationResultDetailMapper {
    SummaryRow findSummary(@Param("simulationId") Long simulationId);

    List<MetricRow> findMetrics(@Param("simulationResultId") Long simulationResultId);

    List<SegmentRow> findWalls(@Param("layoutVersionId") Long layoutVersionId);

    List<SegmentRow> findExits(@Param("layoutVersionId") Long layoutVersionId);

    List<SegmentRow> findPillars(@Param("layoutVersionId") Long layoutVersionId);

    List<SegmentRow> findFabrics(@Param("layoutVersionId") Long layoutVersionId);

    List<HazardZoneRow> findHazardZones(@Param("simulationId") Long simulationId);

    List<BottleneckRow> findBottlenecks(@Param("simulationResultId") Long simulationResultId);

    long countComparableSimulations(@Param("simulationId") Long simulationId, @Param("createdBy") Long createdBy);

    List<ComparableRow> findComparableSimulationPage(
            @Param("simulationId") Long simulationId,
            @Param("createdBy") Long createdBy,
            @Param("offset") int offset,
            @Param("size") int size);

    record SummaryRow(
            Long simulationResultId,
            Long simulationId,
            Long createdBy,
            Long layoutVersionId,
            String layoutTitle,
            String title,
            String drawingName,
            double drawingWidth,
            double drawingHeight,
            int totalPeople) {}

    record MetricRow(String metricType, double metricValue) {}

    record SegmentRow(String name, double startX, double startY, double endX, double endY, double rotation) {}

    record HazardZoneRow(Long id, double centerX, double centerY, double radius) {}

    record BottleneckRow(
            Long id,
            int bottleneckOrder,
            double startTimeSeconds,
            double endTimeSeconds,
            double peakDensity,
            double thresholdValue,
            String geometry) {}

    record ComparableRow(Long simulationId, Long simulationResultId, String name, double totalEvacuationTime) {}
}
