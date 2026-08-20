package com.hwalro.simulation.improvement.mapper;

import com.hwalro.simulation.improvement.domain.ImprovementProposal;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/** 개선안 생성 결과를 조회하고 저장하는 MyBatis Mapper입니다. */
@Mapper
public interface ImprovementProposalMapper {

    /** 원본 시뮬레이션의 개선안을 순위순으로 조회합니다. */
    List<ImprovementProposal> findBySourceSimulationId(@Param("sourceSimulationId") Long sourceSimulationId);

    /** 개선안 한 건을 조회합니다. */
    ImprovementProposal findById(@Param("id") Long id);

    Long findCreatedByBySimulationId(@Param("simulationId") Long simulationId);

    Long lockProposalId(@Param("proposalId") Long proposalId);

    Long findSimulationIdByProposalId(@Param("proposalId") Long proposalId);

    int insertSimulationLink(
            @Param("proposalId") Long proposalId,
            @Param("simulationId") Long simulationId,
            @Param("sourceSimulationId") Long sourceSimulationId);

    /** 저장된 개선안이 있으면 재생성으로 기존 확정안을 덮어쓰지 않습니다. */
    boolean existsSavedBySourceSimulationId(@Param("sourceSimulationId") Long sourceSimulationId);

    /** 같은 원본 시뮬레이션의 개선안 재생성을 직렬화합니다. */
    Long lockSourceSimulationId(@Param("sourceSimulationId") Long sourceSimulationId);

    /** 아직 배치 버전으로 저장되지 않은 개선안을 삭제합니다. */
    int deleteUnsavedBySourceSimulationId(@Param("sourceSimulationId") Long sourceSimulationId);

    /** 생성된 개선안을 저장하고 DB 생성 ID를 채웁니다. */
    int insert(ImprovementProposal proposal);
}
