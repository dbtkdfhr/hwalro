package com.hwalro.simulation.result.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface SimulationReportContextMapper {
    List<SummaryRow> findSummaries(@Param("ids") List<Long> ids, @Param("createdBy") Long createdBy);

    List<MetricRow> findMetrics(@Param("ids") List<Long> ids);

    List<BottleneckRow> findBottlenecks(@Param("ids") List<Long> ids);

    record SummaryRow(Long simulationResultId, Long simulationId, Long createdBy, String layoutTitle, String title) {}

    record MetricRow(Long simulationResultId, String metricType, double metricValue, String unit) {}

    record BottleneckRow(
            Long simulationResultId,
            int bottleneckOrder,
            double startTimeSeconds,
            double endTimeSeconds,
            double peakDensity,
            double thresholdValue) {}
}
