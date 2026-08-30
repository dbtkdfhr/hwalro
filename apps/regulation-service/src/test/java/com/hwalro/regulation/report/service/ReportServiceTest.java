package com.hwalro.regulation.report.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hwalro.regulation.common.jwt.ForbiddenException;
import com.hwalro.regulation.common.jwt.JwtUser;
import com.hwalro.regulation.report.client.AuthorDirectoryClient;
import com.hwalro.regulation.report.client.SimulationReportVisualContextClient;
import com.hwalro.regulation.report.dto.AiReportDraftCreateRequest;
import com.hwalro.regulation.report.dto.AiReportDraftMonitorItem;
import com.hwalro.regulation.report.dto.ReportContent;
import com.hwalro.regulation.report.dto.ReportDetailRow;
import com.hwalro.regulation.report.dto.ReportDraftInsert;
import com.hwalro.regulation.report.dto.ReportListItem;
import com.hwalro.regulation.report.dto.ReportListResponse;
import com.hwalro.regulation.report.dto.ReportUpdateRequest;
import com.hwalro.regulation.report.dto.ReportVisualContextResponse;
import com.hwalro.regulation.report.mapper.ReportMapper;
import com.hwalro.regulation.risk.domain.Risk;
import com.hwalro.regulation.risk.mapper.RiskMapper;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReportServiceTest {
    @Mock
    private ReportMapper reportMapper;

    @Mock
    private AuthorDirectoryClient authorDirectoryClient;

    @Mock
    private SimulationReportVisualContextClient visualContextClient;

    @Mock
    private RiskMapper riskMapper;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void returnsSecondPageWithStatusFilter() {
        ReportService reportService = reportService();
        JwtUser operator = new JwtUser(1L, Set.of("OPERATOR"));
        List<ReportListItem> reports = List.of(new ReportListItem(
                6L, 1L, null, "야외 휴게 공간 비상 유도선 점검 보고서", "완료", LocalDateTime.of(2026, 7, 28, 17, 20)));
        when(reportMapper.countReports(null, "완료", 1L)).thenReturn(6L);
        when(reportMapper.findReports(null, "완료", 1L, 5, 5L)).thenReturn(reports);
        when(authorDirectoryClient.findByIds(List.of(1L), "Bearer token"))
                .thenReturn(List.of(new AuthorDirectoryClient.AuthorSummary(1L, "김운영")));

        ReportListResponse response = reportService.getReports(operator, "Bearer token", null, "완료", 2, 5);

        assertThat(response.totalCount()).isEqualTo(6);
        assertThat(response.page()).isEqualTo(2);
        assertThat(response.hasNext()).isFalse();
        assertThat(response.items().get(0).authorName()).isEqualTo("김운영");
        verify(reportMapper).findReports(eq(null), eq("완료"), eq(1L), eq(5), eq(5L));
    }

    @Test
    void rejectsUnsupportedStatus() {
        ReportService reportService = reportService();
        JwtUser operator = new JwtUser(1L, Set.of("OPERATOR"));

        assertThatThrownBy(() -> reportService.getReports(operator, "Bearer token", null, "보류", 1, 5))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("status must be one of: AI 작성 중, 생성 실패, 초안, 작성 중, 완료.");
    }

    @Test
    void returnsOnlyCurrentUsersAiReportDraftsForCompletionMonitoring() {
        ReportService reportService = reportService();
        JwtUser reviewer = new JwtUser(7L, Set.of("SAFETY_REVIEWER"));
        List<AiReportDraftMonitorItem> reports = List.of(new AiReportDraftMonitorItem(30L, "AI 안전 검토 보고서", "AI 작성 중"));
        when(reportMapper.findAiDraftMonitorItems(7L)).thenReturn(reports);

        assertThat(reportService.getAiDraftMonitor(reviewer)).isEqualTo(reports);

        verify(reportMapper).findAiDraftMonitorItems(7L);
    }

    @Test
    void safetyReviewerQueriesAllAuthors() {
        ReportService reportService = reportService();
        JwtUser reviewer = new JwtUser(3L, Set.of("SAFETY_REVIEWER"));
        when(reportMapper.countReports(null, null, null)).thenReturn(2L);
        when(reportMapper.findReports(null, null, null, 5, 0L))
                .thenReturn(List.of(
                        new ReportListItem(1L, 1L, null, "운영 보고서", "완료", LocalDateTime.now()),
                        new ReportListItem(2L, 2L, null, "다른 운영 보고서", "초안", LocalDateTime.now())));
        when(authorDirectoryClient.findByIds(List.of(1L, 2L), "Bearer token"))
                .thenReturn(List.of(
                        new AuthorDirectoryClient.AuthorSummary(1L, "김운영"),
                        new AuthorDirectoryClient.AuthorSummary(2L, "박운영")));

        ReportListResponse response = reportService.getReports(reviewer, "Bearer token", null, null, 1, 5);

        assertThat(response.totalCount()).isEqualTo(2);
        verify(reportMapper).findReports(eq(null), eq(null), eq(null), eq(5), eq(0L));
    }

    @Test
    void createsDraftAndLinksSimulationResultsWithoutStartingEditing() {
        ReportService reportService = reportService();
        ReportContent content = new ReportContent("개요", "분석", "개선");
        LocalDateTime now = LocalDateTime.now();
        doAnswer(invocation -> {
                    invocation.<ReportDraftInsert>getArgument(0).setId(30L);
                    return 1;
                })
                .when(reportMapper)
                .insertDraft(org.mockito.ArgumentMatchers.any());
        when(reportMapper.findDetailById(30L))
                .thenReturn(new ReportDetailRow(
                        30L,
                        7L,
                        "현재 배치안 안전 검토 보고서",
                        "{\"overview\":\"개요\",\"analysis\":\"분석\",\"improvements\":\"개선\"}",
                        "초안",
                        now,
                        now));
        when(reportMapper.findSimulationResultIds(30L)).thenReturn(List.of(10L, 20L));

        var response = reportService.createDraft(7L, "현재 배치안 안전 검토 보고서", content, List.of(10L, 20L));

        assertThat(response.id()).isEqualTo(30L);
        assertThat(response.status()).isEqualTo("초안");
        assertThat(response.content()).isEqualTo(content);
        ArgumentCaptor<ReportDraftInsert> insertCaptor = ArgumentCaptor.forClass(ReportDraftInsert.class);
        verify(reportMapper).insertDraft(insertCaptor.capture());
        assertThat(insertCaptor.getValue().getAuthorId()).isEqualTo(7L);
        assertThat(insertCaptor.getValue().getStatus()).isEqualTo("초안");
        verify(reportMapper).insertSimulationLinks(30L, List.of(10L, 20L));
        verify(reportMapper, never()).startEditing(30L);
    }

    @Test
    void createsAiGenerationJobWithStoredSelection() {
        ReportService reportService = reportService();
        AiReportDraftCreateRequest request = new AiReportDraftCreateRequest(20L, List.of(10L));
        doAnswer(invocation -> {
                    invocation.<ReportDraftInsert>getArgument(0).setId(30L);
                    return 1;
                })
                .when(reportMapper)
                .insertDraft(org.mockito.ArgumentMatchers.any());
        var response = reportService.createAiGeneration(7L, request);

        assertThat(response.id()).isEqualTo(30L);
        assertThat(response.status()).isEqualTo("AI 작성 중");
        ArgumentCaptor<ReportDraftInsert> insertCaptor = ArgumentCaptor.forClass(ReportDraftInsert.class);
        verify(reportMapper).insertDraft(insertCaptor.capture());
        assertThat(insertCaptor.getValue().getStatus()).isEqualTo("AI 작성 중");
        assertThat(insertCaptor.getValue().getGenerationRequest()).contains("sourceSimulationResultId");
        verify(reportMapper).insertSimulationLinks(30L, List.of(20L, 10L));
    }

    @Test
    void restartsFailedAiGenerationWithStoredSelection() {
        ReportService reportService = reportService();
        LocalDateTime now = LocalDateTime.now();
        when(reportMapper.findDetailById(30L))
                .thenReturn(new ReportDetailRow(30L, 7L, "AI 안전 검토 보고서", "{}", "생성 실패", now, now));
        when(reportMapper.findAiGenerationRequest(30L))
                .thenReturn("{\"sourceSimulationResultId\":20,\"comparisonSimulationResultIds\":[10]}");
        when(reportMapper.restartAiGeneration(30L)).thenReturn(1);

        var request = reportService.restartAiGeneration(new JwtUser(7L, Set.of("OPERATOR")), 30L);

        assertThat(request.sourceSimulationResultId()).isEqualTo(20L);
        assertThat(request.comparisonSimulationResultIds()).containsExactly(10L);
        verify(reportMapper).restartAiGeneration(30L);
    }

    @Test
    void rejectsUpdatesUntilAiGenerationCompletes() {
        ReportService reportService = reportService();
        LocalDateTime now = LocalDateTime.now();
        when(reportMapper.findDetailById(30L))
                .thenReturn(new ReportDetailRow(30L, 7L, "AI 안전 검토 보고서", "{}", "AI 작성 중", now, now));
        ReportUpdateRequest request = new ReportUpdateRequest("변경 제목", new ReportContent("개요", "분석", "개선"), "작성 중");

        assertThatThrownBy(() -> reportService.updateReport(new JwtUser(7L, Set.of("OPERATOR")), 30L, request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("AI 보고서 초안 생성이 완료되지 않았습니다.");

        verify(reportMapper, never())
                .updateReport(
                        org.mockito.ArgumentMatchers.anyLong(),
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.anyString(),
                        org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void rejectsInvalidDraftTitleAndSimulationResultIdsBeforeInsert() {
        ReportService reportService = reportService();
        ReportContent content = new ReportContent("개요", "분석", "개선");

        assertThatThrownBy(() -> reportService.createDraft(7L, "가".repeat(201), content, List.of(10L)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> reportService.createDraft(7L, "보고서", content, List.of(10L, 10L)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> reportService.createDraft(7L, "보고서", content, java.util.Arrays.asList(10L, null)))
                .isInstanceOf(IllegalArgumentException.class);

        verify(reportMapper, never()).insertDraft(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void returnsVisualContextsForLinkedResultsWithoutStartingEditing() {
        ReportService reportService = reportService();
        LocalDateTime now = LocalDateTime.now();
        when(reportMapper.findDetailById(30L)).thenReturn(new ReportDetailRow(30L, 7L, "보고서", "{}", "초안", now, now));
        when(reportMapper.findSimulationResultIds(30L)).thenReturn(List.of(20L, 10L));
        when(visualContextClient.findAll(List.of(20L, 10L), "Bearer token")).thenReturn(List.of());

        var response = reportService.getVisualContexts(new JwtUser(7L, Set.of("OPERATOR")), 30L, "Bearer token");

        assertThat(response).isEmpty();
        verify(visualContextClient).findAll(List.of(20L, 10L), "Bearer token");
        verify(reportMapper, never()).startEditing(30L);
    }

    @Test
    void enrichesVisualContextsWithRisksFromTheirExactLayoutVersions() {
        ReportService reportService = reportService();
        LocalDateTime now = LocalDateTime.now();
        when(reportMapper.findDetailById(30L)).thenReturn(new ReportDetailRow(30L, 7L, "보고서", "{}", "초안", now, now));
        when(reportMapper.findSimulationResultIds(30L)).thenReturn(List.of(20L));
        ReportVisualContextResponse context =
                new ReportVisualContextResponse(20L, 200L, 300L, 400L, "현재 배치안", null, List.of(), List.of());
        when(visualContextClient.findAll(List.of(20L), "Bearer token")).thenReturn(List.of(context));
        Risk risk = new Risk();
        risk.setId(50L);
        risk.setLayoutVersionId(400L);
        risk.setTitle("무대 앞 적치물");
        risk.setDescription("통로 폭을 좁힐 수 있음");
        risk.setSeverity("HIGH");
        risk.setStartX(30.0);
        risk.setStartY(40.0);
        risk.setEndX(10.0);
        risk.setEndY(20.0);
        when(riskMapper.findByLayoutVersionIds(List.of(400L))).thenReturn(List.of(risk));

        var response = reportService.getVisualContexts(new JwtUser(7L, Set.of("OPERATOR")), 30L, "Bearer token");

        assertThat(response).hasSize(1);
        assertThat(response.get(0).riskZones())
                .containsExactly(new ReportVisualContextResponse.RiskZone(
                        50L,
                        "무대 앞 적치물",
                        "통로 폭을 좁힐 수 있음",
                        "HIGH",
                        new ReportVisualContextResponse.Bounds(10, 20, 20, 20)));
        verify(riskMapper).findByLayoutVersionIds(List.of(400L));
    }

    @Test
    void rejectsVisualContextAccessBeforeCallingSimulationService() {
        ReportService reportService = reportService();
        LocalDateTime now = LocalDateTime.now();
        when(reportMapper.findDetailById(30L))
                .thenReturn(new ReportDetailRow(30L, 8L, "다른 사용자 보고서", "{}", "작성 중", now, now));

        assertThatThrownBy(
                        () -> reportService.getVisualContexts(new JwtUser(7L, Set.of("OPERATOR")), 30L, "Bearer token"))
                .isInstanceOf(ForbiddenException.class);

        verifyNoInteractions(visualContextClient);
    }

    @Test
    void deletesOwnedReport() {
        ReportService reportService = reportService();
        LocalDateTime now = LocalDateTime.now();
        when(reportMapper.findDetailById(30L))
                .thenReturn(new ReportDetailRow(30L, 7L, "내 보고서", "{}", "작성 중", now, now));
        when(reportMapper.deleteById(30L)).thenReturn(1);

        reportService.deleteReport(new JwtUser(7L, Set.of("OPERATOR")), 30L);

        verify(reportMapper).deleteById(30L);
    }

    @Test
    void rejectsDeletingAnotherOperatorsReport() {
        ReportService reportService = reportService();
        LocalDateTime now = LocalDateTime.now();
        when(reportMapper.findDetailById(30L))
                .thenReturn(new ReportDetailRow(30L, 8L, "다른 사용자 보고서", "{}", "완료", now, now));

        assertThatThrownBy(() -> reportService.deleteReport(new JwtUser(7L, Set.of("OPERATOR")), 30L))
                .isInstanceOf(ForbiddenException.class);

        verify(reportMapper, never()).deleteById(30L);
    }

    private ReportService reportService() {
        return new ReportService(reportMapper, authorDirectoryClient, visualContextClient, riskMapper, objectMapper);
    }
}
