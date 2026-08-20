package com.hwalro.simulation.simulation.mapper;

import com.hwalro.simulation.analysis.domain.DetectedBottleneck;
import com.hwalro.simulation.simulation.domain.HazardZone;
import com.hwalro.simulation.simulation.domain.LayoutSimulationContext;
import com.hwalro.simulation.simulation.domain.Simulation;
import com.hwalro.simulation.simulation.domain.SimulationMetric;
import com.hwalro.simulation.simulation.domain.SimulationOption;
import com.hwalro.simulation.simulation.domain.SimulationResult;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface SimulationMapper {
    LayoutSimulationContext findLayoutContext(@Param("layoutVersionId") Long layoutVersionId);

    LayoutSimulationContext findLayoutContextForUpdate(@Param("layoutVersionId") Long layoutVersionId);

    Simulation findSimulationById(@Param("id") Long id);

    Simulation findSimulationByIdForUpdate(@Param("id") Long id);

    List<Simulation> findSimulationsByLayoutVersion(
            @Param("layoutVersionId") Long layoutVersionId, @Param("createdBy") Long createdBy);

    List<Simulation> findSimulationOverviewPage(
            @Param("offset") int offset,
            @Param("size") int size,
            @Param("createdBy") Long createdBy,
            @Param("query") String query);

    List<Simulation> findSimulationMonitor(@Param("createdBy") Long createdBy);

    Simulation findSimulationOverviewById(@Param("id") Long id);

    long countSimulationOverview(@Param("createdBy") Long createdBy, @Param("query") String query);

    long countInProgress(@Param("createdBy") Long createdBy);

    long countCompletedThisWeek(@Param("createdBy") Long createdBy, @Param("weekStart") LocalDateTime weekStart);

    SimulationOption findSimulationOption(@Param("simulationId") Long simulationId);

    String findInitialStateJson(@Param("simulationId") Long simulationId);

    List<HazardZone> findHazardZones(@Param("simulationId") Long simulationId);

    List<Long> findSelectedExitIds(@Param("simulationId") Long simulationId);

    SimulationResult findSimulationResult(@Param("simulationId") Long simulationId);

    List<SimulationMetric> findSimulationMetrics(@Param("simulationResultId") Long simulationResultId);

    String findTimelineJson(@Param("simulationId") Long simulationId, @Param("chunkSequence") int chunkSequence);

    String findHeatmapJson(@Param("simulationId") Long simulationId, @Param("chunkSequence") int chunkSequence);

    int insertSimulation(Simulation simulation);

    int insertSimulationOption(SimulationOption option);

    int insertInitialState(@Param("simulationId") Long simulationId, @Param("agentPositions") String agentPositions);

    int insertHazardZones(List<HazardZone> hazardZones);

    int insertSimulationExits(
            @Param("simulationId") Long simulationId,
            @Param("layoutVersionId") Long layoutVersionId,
            @Param("layoutExitIds") List<Long> layoutExitIds);

    int updateSimulationOption(
            @Param("simulationId") Long simulationId,
            @Param("totalPeople") int totalPeople,
            @Param("walkingSpeed") BigDecimal walkingSpeed,
            @Param("initialResponseTimeMean") BigDecimal initialResponseTimeMean,
            @Param("initialResponseTimeStdDev") BigDecimal initialResponseTimeStdDev);

    int updateSimulationTitle(@Param("simulationId") Long simulationId, @Param("title") String title);

    int updateExecutionProfiles(
            @Param("simulationId") Long simulationId,
            @Param("modelProfile") String modelProfile,
            @Param("routingProfile") String routingProfile);

    int updateInitialState(@Param("simulationId") Long simulationId, @Param("agentPositions") String agentPositions);

    int deleteHazardZones(@Param("simulationId") Long simulationId);

    int deleteSimulationExits(@Param("simulationId") Long simulationId);

    int lockLayoutVersion(@Param("layoutVersionId") Long layoutVersionId);

    int unlockLayoutVersionIfNoSimulations(@Param("layoutVersionId") Long layoutVersionId);

    int requestExecution(@Param("simulationId") Long simulationId);

    int markExecutionRunning(@Param("simulationId") Long simulationId);

    int markExecutionCompleted(@Param("simulationId") Long simulationId);

    int markExecutionFailed(
            @Param("simulationId") Long simulationId, @Param("message") String message, @Param("detail") String detail);

    int cancelExecution(@Param("simulationId") Long simulationId);

    int markInterruptedExecutionsFailed(@Param("message") String message);

    int insertSimulationResult(SimulationResult result);

    int insertSimulationMetrics(List<SimulationMetric> metrics);

    int insertTimeline(
            @Param("simulationResultId") Long simulationResultId,
            @Param("chunkSequence") int chunkSequence,
            @Param("frameData") String frameData);

    int insertHeatmap(
            @Param("simulationResultId") Long simulationResultId,
            @Param("chunkSequence") int chunkSequence,
            @Param("densityData") String densityData);

    int insertDetectedBottlenecks(
            @Param("simulationResultId") Long simulationResultId,
            @Param("bottlenecks") List<DetectedBottleneck> bottlenecks);

    int countImprovementReferences(@Param("simulationId") Long simulationId);

    int countChildSimulations(@Param("simulationId") Long simulationId);

    int deleteSimulation(@Param("simulationId") Long simulationId);
}
