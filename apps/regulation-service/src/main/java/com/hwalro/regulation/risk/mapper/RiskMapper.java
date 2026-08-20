package com.hwalro.regulation.risk.mapper;

import com.hwalro.regulation.risk.domain.Risk;
import com.hwalro.regulation.risk.dto.AttachedLawRef;
import com.hwalro.regulation.risk.dto.RiskAttachedLaw;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface RiskMapper {
    Risk findById(@Param("id") Long id);

    List<Risk> findPage(
            @Param("offset") int offset,
            @Param("size") int size,
            @Param("assigneeId") Long assigneeId,
            @Param("query") String query);

    long count(@Param("assigneeId") Long assigneeId, @Param("query") String query);

    List<Risk> findBySimulationResultId(
            @Param("simulationResultId") Long simulationResultId, @Param("assigneeId") Long assigneeId);

    List<Risk> findBySimulationResultIds(@Param("simulationResultIds") List<Long> simulationResultIds);

    int countBySimulationResultId(@Param("simulationResultId") Long simulationResultId);

    int insert(Risk risk);

    int update(Risk risk);

    int deleteById(@Param("id") Long id);

    int insertAttachedLaws(@Param("riskId") Long riskId, @Param("attachedLaws") List<AttachedLawRef> attachedLaws);

    int deleteAttachedLawsByRiskId(@Param("riskId") Long riskId);

    List<RiskAttachedLaw> findAttachedLawsByRiskIds(@Param("riskIds") List<Long> riskIds);
}
