package com.hwalro.regulation.report.controller;

import com.hwalro.regulation.common.jwt.JwtAuthInterceptor;
import com.hwalro.regulation.common.jwt.JwtUser;
import com.hwalro.regulation.common.jwt.RequireRole;
import com.hwalro.regulation.report.dto.AiReportDraftCreateRequest;
import com.hwalro.regulation.report.dto.AiReportDraftJobResponse;
import com.hwalro.regulation.report.dto.AiReportDraftMonitorItem;
import com.hwalro.regulation.report.dto.ReportDetailResponse;
import com.hwalro.regulation.report.dto.ReportListResponse;
import com.hwalro.regulation.report.dto.ReportUpdateRequest;
import com.hwalro.regulation.report.dto.ReportVisualContextResponse;
import com.hwalro.regulation.report.service.AiReportDraftService;
import com.hwalro.regulation.report.service.ReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reports")
@Tag(name = "Reports", description = "안전 검토 보고서 관리 API")
@RequireRole({"OPERATOR", "SAFETY_REVIEWER", "ADMIN"})
/** 보고서 목록 조회를 프론트엔드에 제공한다. */
public class ReportController {
    private final ReportService reportService;
    private final AiReportDraftService aiReportDraftService;

    public ReportController(ReportService reportService, AiReportDraftService aiReportDraftService) {
        this.reportService = reportService;
        this.aiReportDraftService = aiReportDraftService;
    }

    @GetMapping
    @Operation(summary = "보고서 목록 조회", description = "제목 검색, 상태 필터 및 페이지네이션을 지원합니다.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "보고서 목록 조회 성공"),
        @ApiResponse(responseCode = "400", description = "지원하지 않는 상태값 또는 잘못된 페이지 요청")
    })
    public ReportListResponse getReports(
            @Parameter(hidden = true) @RequestAttribute(JwtAuthInterceptor.REQUEST_ATTRIBUTE_USER) JwtUser user,
            @Parameter(hidden = true) @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization,
            @Parameter(description = "보고서 제목 검색어") @RequestParam(required = false) String query,
            @Parameter(description = "상태: AI 작성 중, 생성 실패, 초안, 작성 중, 완료") @RequestParam(required = false) String status,
            @Parameter(description = "1부터 시작하는 페이지 번호") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "페이지당 조회 건수") @RequestParam(defaultValue = "5") int size) {
        return reportService.getReports(user, authorization, query, status, page, size);
    }

    @GetMapping("/ai-drafts/monitor")
    @Operation(summary = "내 AI 보고서 생성 상태 조회", description = "현재 사용자가 생성한 최근 AI 보고서의 상태를 조회합니다.")
    public List<AiReportDraftMonitorItem> getAiDraftMonitor(
            @Parameter(hidden = true) @RequestAttribute(JwtAuthInterceptor.REQUEST_ATTRIBUTE_USER) JwtUser user) {
        return reportService.getAiDraftMonitor(user);
    }

    @GetMapping("/{id}")
    @Operation(summary = "보고서 상세 조회", description = "초안 보고서는 첫 상세 조회 시 작성 중 상태로 전환됩니다.")
    public ReportDetailResponse getReport(
            @PathVariable Long id,
            @Parameter(hidden = true) @RequestAttribute(JwtAuthInterceptor.REQUEST_ATTRIBUTE_USER) JwtUser user) {
        return reportService.getReport(user, id);
    }

    @GetMapping("/{id}/visual-contexts")
    @Operation(summary = "보고서 첨부 시뮬레이션 미니맵 조회")
    public List<ReportVisualContextResponse> getVisualContexts(
            @PathVariable Long id,
            @Parameter(hidden = true) @RequestAttribute(JwtAuthInterceptor.REQUEST_ATTRIBUTE_USER) JwtUser user,
            @Parameter(hidden = true) @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization) {
        return reportService.getVisualContexts(user, id, authorization);
    }

    @PutMapping("/{id}")
    @Operation(summary = "보고서 저장", description = "보고서 제목, 구역별 본문 및 상태를 저장합니다.")
    public ReportDetailResponse updateReport(
            @PathVariable Long id,
            @RequestBody ReportUpdateRequest request,
            @Parameter(hidden = true) @RequestAttribute(JwtAuthInterceptor.REQUEST_ATTRIBUTE_USER) JwtUser user) {
        return reportService.updateReport(user, id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "보고서 삭제", description = "접근 권한이 있는 보고서와 연결 정보를 삭제합니다.")
    public void deleteReport(
            @PathVariable Long id,
            @Parameter(hidden = true) @RequestAttribute(JwtAuthInterceptor.REQUEST_ATTRIBUTE_USER) JwtUser user) {
        reportService.deleteReport(user, id);
    }

    @PostMapping("/ai-drafts")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(summary = "AI 보고서 초안 생성", description = "AI 초안 생성 작업을 등록하고 즉시 반환합니다.")
    public AiReportDraftJobResponse createAiDraft(
            @RequestBody AiReportDraftCreateRequest request,
            @Parameter(hidden = true) @RequestAttribute(JwtAuthInterceptor.REQUEST_ATTRIBUTE_USER) JwtUser user,
            @Parameter(hidden = true) @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization) {
        return aiReportDraftService.create(user, authorization, request);
    }

    @PostMapping("/{id}/ai-draft/retry")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(summary = "AI 보고서 초안 생성 재시도", description = "생성 실패한 보고서를 동일한 시뮬레이션 설정으로 다시 생성합니다.")
    public AiReportDraftJobResponse retryAiDraft(
            @PathVariable Long id,
            @Parameter(hidden = true) @RequestAttribute(JwtAuthInterceptor.REQUEST_ATTRIBUTE_USER) JwtUser user,
            @Parameter(hidden = true) @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization) {
        return aiReportDraftService.retry(user, authorization, id);
    }
}
