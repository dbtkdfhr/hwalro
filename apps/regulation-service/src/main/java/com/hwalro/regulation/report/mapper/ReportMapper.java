package com.hwalro.regulation.report.mapper;

import com.hwalro.regulation.report.dto.AiReportDraftMonitorItem;
import com.hwalro.regulation.report.dto.ReportDetailRow;
import com.hwalro.regulation.report.dto.ReportDraftInsert;
import com.hwalro.regulation.report.dto.ReportListItem;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ReportMapper {
    long countReports(@Param("query") String query, @Param("status") String status, @Param("authorId") Long authorId);

    List<ReportListItem> findReports(
            @Param("query") String query,
            @Param("status") String status,
            @Param("authorId") Long authorId,
            @Param("limit") int limit,
            @Param("offset") long offset);

    List<AiReportDraftMonitorItem> findAiDraftMonitorItems(@Param("authorId") Long authorId);

    ReportDetailRow findDetailById(@Param("id") Long id);

    List<Long> findSimulationResultIds(@Param("reportId") Long reportId);

    int countBySimulationResultId(@Param("simulationResultId") Long simulationResultId);

    String findAiGenerationRequest(@Param("reportId") Long reportId);

    int updateReport(
            @Param("id") Long id,
            @Param("title") String title,
            @Param("content") String content,
            @Param("status") String status);

    int startEditing(@Param("id") Long id);

    int completeAiGeneration(@Param("id") Long id, @Param("title") String title, @Param("content") String content);

    int failAiGeneration(@Param("id") Long id);

    int restartAiGeneration(@Param("id") Long id);

    int failStaleAiGenerations(@Param("cutoff") LocalDateTime cutoff);

    int deleteById(@Param("id") Long id);

    int insertDraft(ReportDraftInsert draft);

    int insertSimulationLinks(
            @Param("reportId") Long reportId, @Param("simulationResultIds") List<Long> simulationResultIds);
}
