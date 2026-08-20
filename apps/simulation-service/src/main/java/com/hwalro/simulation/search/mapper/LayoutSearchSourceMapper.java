package com.hwalro.simulation.search.mapper;

import com.hwalro.simulation.search.domain.BaselineMetric;
import com.hwalro.simulation.search.domain.HeatmapChunk;
import com.hwalro.simulation.search.domain.StoredBottleneck;
import com.hwalro.simulation.search.domain.TimelineChunk;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface LayoutSearchSourceMapper {
    Long findLayoutVersionIdBySimulationId(@Param("simulationId") long simulationId);

    List<StoredBottleneck> findBottlenecksBySimulationId(@Param("simulationId") long simulationId);

    List<HeatmapChunk> findHeatmapChunksBySimulationId(@Param("simulationId") long simulationId);

    List<TimelineChunk> findTimelineChunksBySimulationId(@Param("simulationId") long simulationId);

    List<BaselineMetric> findBaselineMetricsBySimulationId(@Param("simulationId") long simulationId);
}
