package com.hwalro.regulation.report.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.hwalro.regulation.common.jwt.JwtUser;
import com.hwalro.regulation.law.dto.RegulationArticle;
import com.hwalro.regulation.law.dto.RegulationDetail;
import com.hwalro.regulation.law.service.RegulationService;
import com.hwalro.regulation.report.ai.ReportDraftGenerator;
import com.hwalro.regulation.report.ai.ReportDraftInput;
import com.hwalro.regulation.report.client.SimulationReportContextClient;
import com.hwalro.regulation.report.client.SimulationReportContextClient.Context;
import com.hwalro.regulation.report.dto.AiReportDraftCreateRequest;
import com.hwalro.regulation.report.dto.AiReportDraftJobResponse;
import com.hwalro.regulation.report.dto.ReportContent;
import com.hwalro.regulation.risk.domain.Risk;
import com.hwalro.regulation.risk.dto.RiskAttachedLaw;
import com.hwalro.regulation.risk.mapper.RiskMapper;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AiReportDraftServiceTest {
    @Mock
    private SimulationReportContextClient simulationClient;

    @Mock
    private RiskMapper riskMapper;

    @Mock
    private RegulationService regulationService;

    @Mock
    private ReportDraftGenerator generator;

    @Mock
    private ReportService reportService;

    private final JwtUser user = new JwtUser(7L, Set.of("OPERATOR"));

    @Test
    void createsJobBeforeRunningGenerationAndCompletesItAsDraft() {
        AtomicReference<Runnable> task = new AtomicReference<>();
        Executor executor = task::set;
        AiReportDraftCreateRequest request = new AiReportDraftCreateRequest(10L, List.of(20L));
        AiReportDraftJobResponse job = new AiReportDraftJobResponse(30L, "AI 작성 중");
        when(reportService.createAiGeneration(7L, request)).thenReturn(job);

        AiReportDraftJobResponse response = service(executor).create(user, "Bearer token", request);

        assertThat(response).isEqualTo(job);
        assertThat(task.get()).isNotNull();
        verifyNoInteractions(simulationClient, riskMapper, generator);
        verify(reportService, never()).completeAiGeneration(any(), any(), any());

        Context source = new Context(10L, 100L, 1000L, 1100L, "현재 배치안", List.of(), List.of());
        Context comparison = new Context(20L, 200L, 2000L, 2200L, "비교 배치안", List.of(), List.of());
        when(simulationClient.findAll(List.of(10L, 20L), "Bearer token")).thenReturn(List.of(source, comparison));
        Risk risk = new Risk();
        risk.setId(55L);
        risk.setLayoutId(1000L);
        risk.setLayoutVersionId(1100L);
        risk.setTitle("주의 구역");
        risk.setDescription("사용자 지정");
        risk.setSeverity("HIGH");
        when(riskMapper.findByLayoutVersionIds(List.of(1100L, 2200L))).thenReturn(List.of(risk));
        when(riskMapper.findAttachedLawsByRiskIds(List.of(55L)))
                .thenReturn(List.of(new RiskAttachedLaw(55L, "123", "제10조"), new RiskAttachedLaw(55L, "123", "제11조")));
        when(regulationService.getDetail("123"))
                .thenReturn(new RegulationDetail(
                        "123",
                        "law-1",
                        "소방시설 설치 및 관리에 관한 법률",
                        "법률",
                        "소방청",
                        "20260101",
                        "20260101",
                        List.of(
                                new RegulationArticle("10", "피난시설", "피난시설을 훼손하거나 막아서는 안 된다.", "20260101", false),
                                new RegulationArticle("11", "피난 통로", "피난 통로의 유효 폭을 유지해야 한다.", "20260101", false))));
        ReportContent content = new ReportContent("개요", "분석", "개선");
        when(generator.generate(any())).thenReturn(content);

        task.get().run();

        ArgumentCaptor<ReportDraftInput> inputCaptor = ArgumentCaptor.forClass(ReportDraftInput.class);
        verify(generator).generate(inputCaptor.capture());
        assertThat(inputCaptor.getValue().source()).isEqualTo(source);
        assertThat(inputCaptor.getValue().comparisons()).containsExactly(comparison);
        assertThat(inputCaptor.getValue().risks())
                .containsExactly(new ReportDraftInput.Risk(
                        1000L,
                        1100L,
                        "주의 구역",
                        "사용자 지정",
                        "HIGH",
                        List.of(
                                new ReportDraftInput.Law(
                                        "123", "소방시설 설치 및 관리에 관한 법률", "제10조", "피난시설", "피난시설을 훼손하거나 막아서는 안 된다."),
                                new ReportDraftInput.Law(
                                        "123", "소방시설 설치 및 관리에 관한 법률", "제11조", "피난 통로", "피난 통로의 유효 폭을 유지해야 한다."))));
        verify(regulationService).getDetail("123");
        verify(reportService).completeAiGeneration(30L, "현재 배치안 안전 검토 보고서", content);
        verify(reportService, never()).failAiGeneration(30L);
    }

    @Test
    void marksJobFailedWhenBackgroundGenerationFails() {
        AtomicReference<Runnable> task = new AtomicReference<>();
        AiReportDraftCreateRequest request = new AiReportDraftCreateRequest(10L, List.of());
        when(reportService.createAiGeneration(7L, request)).thenReturn(new AiReportDraftJobResponse(30L, "AI 작성 중"));
        when(simulationClient.findAll(List.of(10L), "Bearer token")).thenThrow(new IllegalStateException("failure"));

        service(task::set).create(user, "Bearer token", request);
        task.get().run();

        verify(reportService).failAiGeneration(30L);
        verify(reportService, never()).completeAiGeneration(any(), any(), any());
    }

    @Test
    void continuesGenerationWithLawReferenceWhenLawDetailLookupFails() {
        AtomicReference<Runnable> task = new AtomicReference<>();
        AiReportDraftCreateRequest request = new AiReportDraftCreateRequest(10L, List.of());
        when(reportService.createAiGeneration(7L, request)).thenReturn(new AiReportDraftJobResponse(30L, "AI 작성 중"));
        Context source = new Context(10L, 100L, 1000L, 1100L, "현재 배치안", List.of(), List.of());
        when(simulationClient.findAll(List.of(10L), "Bearer token")).thenReturn(List.of(source));
        Risk risk = new Risk();
        risk.setId(55L);
        risk.setLayoutId(1000L);
        risk.setLayoutVersionId(1100L);
        risk.setTitle("주의 구역");
        when(riskMapper.findByLayoutVersionIds(List.of(1100L))).thenReturn(List.of(risk));
        when(riskMapper.findAttachedLawsByRiskIds(List.of(55L)))
                .thenReturn(List.of(new RiskAttachedLaw(55L, "123", "제10조")));
        when(regulationService.getDetail("123")).thenThrow(new IllegalStateException("law api unavailable"));
        ReportContent content = new ReportContent("개요", "분석", "개선");
        when(generator.generate(any())).thenReturn(content);

        service(task::set).create(user, "Bearer token", request);
        task.get().run();

        ArgumentCaptor<ReportDraftInput> inputCaptor = ArgumentCaptor.forClass(ReportDraftInput.class);
        verify(generator).generate(inputCaptor.capture());
        assertThat(inputCaptor.getValue().risks().get(0).laws())
                .containsExactly(new ReportDraftInput.Law("123", null, "제10조", null, null));
        verify(reportService).completeAiGeneration(30L, "현재 배치안 안전 검토 보고서", content);
        verify(reportService, never()).failAiGeneration(30L);
    }

    @Test
    void retriesFailedJobWithStoredSelection() {
        AtomicReference<Runnable> task = new AtomicReference<>();
        AiReportDraftCreateRequest storedRequest = new AiReportDraftCreateRequest(10L, List.of(20L));
        when(reportService.restartAiGeneration(user, 30L)).thenReturn(storedRequest);

        AiReportDraftJobResponse response = service(task::set).retry(user, "Bearer token", 30L);

        assertThat(response).isEqualTo(new AiReportDraftJobResponse(30L, "AI 작성 중"));
        assertThat(task.get()).isNotNull();
    }

    @Test
    void marksOnlyStaleJobsFailedWhenRecoveryRuns() {
        when(reportService.failStaleAiGenerations(any())).thenReturn(2);

        service(Runnable::run).failStaleGenerations();

        ArgumentCaptor<java.time.LocalDateTime> cutoffCaptor = ArgumentCaptor.forClass(java.time.LocalDateTime.class);
        verify(reportService).failStaleAiGenerations(cutoffCaptor.capture());
        assertThat(cutoffCaptor.getValue())
                .isBefore(java.time.LocalDateTime.now().minusMinutes(29));
    }

    @Test
    void rejectsInvalidResultSelectionBeforeCreatingJob() {
        AiReportDraftService service = service(Runnable::run);

        assertThatThrownBy(() -> service.create(user, "Bearer token", null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.create(user, "Bearer token", new AiReportDraftCreateRequest(1L, List.of(1L))))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.create(
                        user, "Bearer token", new AiReportDraftCreateRequest(1L, List.of(2L, 3L, 4L, 5L, 6L, 7L))))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(simulationClient, riskMapper, generator, reportService);
    }

    private AiReportDraftService service(Executor executor) {
        return new AiReportDraftService(
                simulationClient, riskMapper, regulationService, generator, reportService, executor);
    }
}
