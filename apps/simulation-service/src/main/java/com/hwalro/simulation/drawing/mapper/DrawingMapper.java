package com.hwalro.simulation.drawing.mapper;

import com.hwalro.simulation.drawing.domain.Fabric;
import com.hwalro.simulation.drawing.domain.FloorPlan;
import com.hwalro.simulation.drawing.domain.Layout;
import com.hwalro.simulation.drawing.domain.LayoutExit;
import com.hwalro.simulation.drawing.domain.LayoutText;
import com.hwalro.simulation.drawing.domain.LayoutVersion;
import com.hwalro.simulation.drawing.domain.OutsideWall;
import com.hwalro.simulation.drawing.domain.Pillar;
import com.hwalro.simulation.drawing.domain.Wall;
import com.hwalro.simulation.drawing.dto.SimulationCountByLayout;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface DrawingMapper {

    int insertFloorPlan(FloorPlan floorPlan);

    int insertLayout(Layout layout);

    int insertLayoutVersion(LayoutVersion layoutVersion);

    int insertWalls(List<Wall> walls);

    int insertPillars(List<Pillar> pillars);

    int insertFabrics(List<Fabric> fabrics);

    int insertOutsideWalls(List<OutsideWall> outsideWalls);

    int insertLayoutTexts(List<LayoutText> layoutTexts);

    int insertLayoutExits(List<LayoutExit> layoutExits);

    int insertWall(Wall wall);

    int insertPillar(Pillar pillar);

    int insertLayoutExit(LayoutExit layoutExit);

    int insertFabric(Fabric fabric);

    List<Long> findFabricIdsByVersionId(@Param("layoutVersionId") Long layoutVersionId);

    List<Long> findLayoutExitIdsByVersionId(@Param("layoutVersionId") Long layoutVersionId);

    List<Long> findWallIdsByVersionId(@Param("layoutVersionId") Long layoutVersionId);

    List<Long> findPillarIdsByVersionId(@Param("layoutVersionId") Long layoutVersionId);

    int updateFabricGeometry(Fabric fabric);

    int updateLayoutExitGeometry(LayoutExit layoutExit);

    int updateWallGeometry(Wall wall);

    int updatePillarGeometry(Pillar pillar);

    int deleteFabricsByIds(@Param("layoutVersionId") Long layoutVersionId, @Param("ids") List<Long> ids);

    int deleteWallsByIds(@Param("layoutVersionId") Long layoutVersionId, @Param("ids") List<Long> ids);

    int deletePillarsByIds(@Param("layoutVersionId") Long layoutVersionId, @Param("ids") List<Long> ids);

    int deleteLayoutExitsByIds(@Param("layoutVersionId") Long layoutVersionId, @Param("ids") List<Long> ids);

    int nullifyZoneExitReferences(@Param("layoutVersionId") Long layoutVersionId, @Param("exitIds") List<Long> exitIds);

    int nullifyZoneExitReferencesByLayoutId(@Param("layoutId") Long layoutId);

    /** 배치 제약만 갱신한다. 기하 컬럼은 건드리지 않는다(도면 저장과 소유권이 다르다). */
    int updateFabricConstraints(Fabric fabric);

    Long lockLayout(@Param("layoutId") Long layoutId);

    int findNextLayoutVersionNumber(@Param("layoutId") Long layoutId);

    int copyWalls(@Param("sourceVersionId") Long sourceVersionId, @Param("targetVersionId") Long targetVersionId);

    int copyPillars(@Param("sourceVersionId") Long sourceVersionId, @Param("targetVersionId") Long targetVersionId);

    int copyOutsideWalls(
            @Param("sourceVersionId") Long sourceVersionId, @Param("targetVersionId") Long targetVersionId);

    int copyLayoutTexts(@Param("sourceVersionId") Long sourceVersionId, @Param("targetVersionId") Long targetVersionId);

    FloorPlan findFloorPlanById(@Param("id") Long id);

    Layout findLayoutById(@Param("id") Long id);

    List<Layout> findLayoutPage(
            @Param("offset") int offset,
            @Param("size") int size,
            @Param("createdBy") Long createdBy,
            @Param("query") String query);

    long countLayouts(@Param("createdBy") Long createdBy, @Param("query") String query);

    List<Layout> findLayoutPageAssignedToUser(
            @Param("offset") int offset,
            @Param("size") int size,
            @Param("userId") Long userId,
            @Param("query") String query);

    long countLayoutsAssignedToUser(@Param("userId") Long userId, @Param("query") String query);

    LayoutVersion findLayoutVersionById(@Param("id") Long id);

    List<LayoutVersion> findLayoutVersionsByLayoutId(@Param("layoutId") Long layoutId);

    List<Wall> findWallsByVersionId(@Param("layoutVersionId") Long layoutVersionId);

    List<Pillar> findPillarsByVersionId(@Param("layoutVersionId") Long layoutVersionId);

    List<Fabric> findFabricsByVersionId(@Param("layoutVersionId") Long layoutVersionId);

    List<OutsideWall> findOutsideWallsByVersionId(@Param("layoutVersionId") Long layoutVersionId);

    List<LayoutText> findLayoutTextsByVersionId(@Param("layoutVersionId") Long layoutVersionId);

    List<LayoutExit> findLayoutExitsByVersionId(@Param("layoutVersionId") Long layoutVersionId);

    int updateLayout(Layout layout);

    int updateLayoutCurrentVersion(Layout layout);

    int updateLayoutVersionLock(
            @Param("id") Long id, @Param("expectedLock") Integer expectedLock, @Param("nextLock") Integer nextLock);

    int deleteWallsByVersionId(@Param("layoutVersionId") Long layoutVersionId);

    int deletePillarsByVersionId(@Param("layoutVersionId") Long layoutVersionId);

    int deleteFabricsByVersionId(@Param("layoutVersionId") Long layoutVersionId);

    int deleteOutsideWallsByVersionId(@Param("layoutVersionId") Long layoutVersionId);

    int deleteLayoutTextsByVersionId(@Param("layoutVersionId") Long layoutVersionId);

    int deleteLayoutExitsByVersionId(@Param("layoutVersionId") Long layoutVersionId);

    int deleteLayoutById(@Param("id") Long id);

    int deleteFloorPlanById(@Param("id") Long id);

    int countSimulationsByLayoutId(@Param("layoutId") Long layoutId);

    List<SimulationCountByLayout> countSimulationsByLayoutIds(@Param("layoutIds") List<Long> layoutIds);
}
