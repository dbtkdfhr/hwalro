package com.hwalro.simulation.improvement.mapper;

import com.hwalro.simulation.improvement.domain.HeatmapChunk;
import com.hwalro.simulation.improvement.domain.StoredBottleneck;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 개선안 탐색이 원본 시뮬레이션 결과를 읽기 위한 전용 조회 Mapper입니다. */
@Mapper
public interface ImprovementSourceMapper {

    /** 원본 simulation이 사용한 도면 버전을 찾습니다. */
    Long findLayoutVersionIdBySimulationId(@Param("simulationId") Long simulationId);

    /** 원본 simulation의 결과에 저장된 병목을 화면 순서대로 읽습니다. */
    List<StoredBottleneck> findBottlenecksBySimulationId(@Param("simulationId") Long simulationId);

    /** 점수 계산을 위해 원본 히트맵 JSON 청크를 순서대로 읽습니다. */
    List<HeatmapChunk> findHeatmapChunksBySimulationId(@Param("simulationId") Long simulationId);
}
