package com.hwalro.regulation.report.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hwalro.regulation.common.jwt.ForbiddenException;
import com.hwalro.regulation.common.jwt.JwtUser;
import com.hwalro.regulation.report.ReportStatus;
import com.hwalro.regulation.report.client.AuthorDirectoryClient;
import com.hwalro.regulation.report.client.SimulationReportVisualContextClient;
import com.hwalro.regulation.report.dto.AiReportDraftCreateRequest;
import com.hwalro.regulation.report.dto.AiReportDraftJobResponse;
import com.hwalro.regulation.report.dto.AiReportDraftMonitorItem;
import com.hwalro.regulation.report.dto.ReportContent;
import com.hwalro.regulation.report.dto.ReportDetailResponse;
import com.hwalro.regulation.report.dto.ReportDetailRow;
import com.hwalro.regulation.report.dto.ReportDraftInsert;
import com.hwalro.regulation.report.dto.ReportListItem;
import com.hwalro.regulation.report.dto.ReportListResponse;
import com.hwalro.regulation.report.dto.ReportUpdateRequest;
import com.hwalro.regulation.report.dto.ReportVisualContextResponse;
import com.hwalro.regulation.report.exception.ReportNotFoundException;
import com.hwalro.regulation.report.mapper.ReportMapper;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
/** 보고서 목록의 검색·상태 필터·페이지네이션 규칙을 소유한다. */
public class ReportService {
    private static final Logger log = LoggerFactory.getLogger(ReportService.class);
    private static final int MAX_PAGE = 100_000;
    private static final int MAX_PAGE_SIZE = 100;

    private final ReportMapper reportMapper;
    private final AuthorDirectoryClient authorDirectoryClient;
    private final SimulationReportVisualContextClient visualContextClient;
    private final ObjectMapper objectMapper;

    public ReportService(
            ReportMapper reportMapper,
            AuthorDirectoryClient authorDirectoryClient,
            SimulationReportVisualContextClient visualContextClient,
            ObjectMapper objectMapper) {
        this.reportMapper = reportMapper;
        this.authorDirectoryClient = authorDirectoryClient;
        this.visualContextClient = visualContextClient;
        this.objectMapper = objectMapper;
    }

    public ReportListResponse getReports(
            JwtUser user, String authorization, String query, String status, int page, int size) {
        validatePage(page, size);

        String normalizedQuery = StringUtils.hasText(query) ? query.trim() : null;
        String normalizedStatus = ReportStatus.validate(status);
        Long authorId = resolveAuthorId(user);
        long totalCount = reportMapper.countReports(normalizedQuery, normalizedStatus, authorId);
        long offset = (long) (page - 1) * size;
        List<ReportListItem> items =
                reportMapper.findReports(normalizedQuery, normalizedStatus, authorId, size, offset);
        Map<Long, String> authorNames = findAuthorNames(items, authorization);
        List<ReportListItem> namedItems = items.stream()
                .map(report -> report.withAuthorName(authorNames.get(report.authorId())))
                .toList();

        return new ReportListResponse(
                Math.toIntExact(totalCount), page, size, offset + namedItems.size() < totalCount, namedItems);
    }

    public List<AiReportDraftMonitorItem> getAiDraftMonitor(JwtUser user) {
        return reportMapper.findAiDraftMonitorItems(user.userId());
    }

    @Transactional
    public ReportDetailResponse getReport(JwtUser user, Long reportId) {
        ReportDetailRow report = findReport(reportId);
        requireAccessible(user, report);
        if (ReportStatus.AI_IN_PROGRESS.value().equals(report.status())
                || ReportStatus.GENERATION_FAILED.value().equals(report.status())) {
            throw new IllegalArgumentException("AI 보고서 초안 생성이 완료되지 않았습니다.");
        }
        if (ReportStatus.DRAFT.value().equals(report.status())) {
            reportMapper.startEditing(reportId);
            report = findReport(reportId);
        }
        return toDetailResponse(report);
    }

    public ReportDetailResponse updateReport(JwtUser user, Long reportId, ReportUpdateRequest request) {
        ReportDetailRow report = findReport(reportId);
        requireAccessible(user, report);
        requireGeneratedReport(report);
        validateUpdateRequest(request);
        String updatedStatus = requireEditableStatus(request.status());
        if (reportMapper.updateReport(
                        reportId, request.title().trim(), serializeContent(request.content()), updatedStatus)
                != 1) {
            throw new IllegalStateException("보고서 상태가 변경되어 저장할 수 없습니다.");
        }
        return getReport(user, reportId);
    }

    @Transactional
    public void deleteReport(JwtUser user, Long reportId) {
        ReportDetailRow report = findReport(reportId);
        requireAccessible(user, report);
        if (reportMapper.deleteById(reportId) != 1) {
            throw new ReportNotFoundException(reportId);
        }
    }

    public List<ReportVisualContextResponse> getVisualContexts(JwtUser user, Long reportId, String authorization) {
        if (!StringUtils.hasText(authorization)) {
            throw new IllegalArgumentException("Authorization 헤더가 필요합니다.");
        }
        ReportDetailRow report = findReport(reportId);
        requireAccessible(user, report);
        List<Long> simulationResultIds = reportMapper.findSimulationResultIds(reportId);
        if (simulationResultIds.isEmpty()) {
            return List.of();
        }
        return visualContextClient.findAll(simulationResultIds, authorization);
    }

    @Transactional
    public ReportDetailResponse createDraft(
            Long authorId, String title, ReportContent content, List<Long> simulationResultIds) {
        if (authorId == null
                || authorId <= 0
                || !StringUtils.hasText(title)
                || title.trim().length() > 200
                || content == null) {
            throw new IllegalArgumentException("보고서 초안 저장 정보가 올바르지 않습니다.");
        }
        if (simulationResultIds == null || simulationResultIds.isEmpty()) {
            throw new IllegalArgumentException("연결할 시뮬레이션 결과가 필요합니다.");
        }
        HashSet<Long> uniqueResultIds = new HashSet<>();
        for (Long simulationResultId : simulationResultIds) {
            if (simulationResultId == null || simulationResultId <= 0 || !uniqueResultIds.add(simulationResultId)) {
                throw new IllegalArgumentException("시뮬레이션 결과 ID는 양수이며 중복될 수 없습니다.");
            }
        }
        ReportDraftInsert draft =
                new ReportDraftInsert(authorId, title.trim(), serializeContent(content), ReportStatus.DRAFT.value());
        reportMapper.insertDraft(draft);
        if (draft.getId() == null) {
            throw new IllegalStateException("보고서 초안 ID를 생성하지 못했습니다.");
        }
        reportMapper.insertSimulationLinks(draft.getId(), List.copyOf(simulationResultIds));
        return toDetailResponse(findReport(draft.getId()));
    }

    @Transactional
    public AiReportDraftJobResponse createAiGeneration(Long authorId, AiReportDraftCreateRequest request) {
        if (authorId == null || authorId <= 0 || request == null) {
            throw new IllegalArgumentException("AI 보고서 생성 정보가 올바르지 않습니다.");
        }
        List<Long> simulationResultIds = generationResultIds(request);
        ReportContent emptyContent = new ReportContent("", "", "");
        ReportDraftInsert draft = new ReportDraftInsert(
                authorId,
                "AI 안전 검토 보고서",
                serializeContent(emptyContent),
                ReportStatus.AI_IN_PROGRESS.value(),
                serializeGenerationRequest(request));
        reportMapper.insertDraft(draft);
        if (draft.getId() == null) {
            throw new IllegalStateException("AI 보고서 작업 ID를 생성하지 못했습니다.");
        }
        reportMapper.insertSimulationLinks(draft.getId(), simulationResultIds);
        return new AiReportDraftJobResponse(draft.getId(), ReportStatus.AI_IN_PROGRESS.value());
    }

    @Transactional
    public AiReportDraftCreateRequest restartAiGeneration(JwtUser user, Long reportId) {
        ReportDetailRow report = findReport(reportId);
        requireAccessible(user, report);
        if (!ReportStatus.GENERATION_FAILED.value().equals(report.status())) {
            throw new IllegalArgumentException("생성 실패한 AI 보고서만 재시도할 수 있습니다.");
        }
        AiReportDraftCreateRequest request =
                deserializeGenerationRequest(reportMapper.findAiGenerationRequest(reportId));
        if (reportMapper.restartAiGeneration(reportId) != 1) {
            throw new IllegalStateException("AI 보고서 생성 상태를 변경하지 못했습니다.");
        }
        return request;
    }

    @Transactional
    public void completeAiGeneration(Long reportId, String title, ReportContent content) {
        if (reportId == null
                || reportId <= 0
                || !StringUtils.hasText(title)
                || title.trim().length() > 200
                || content == null) {
            throw new IllegalArgumentException("AI 보고서 초안 저장 정보가 올바르지 않습니다.");
        }
        if (reportMapper.completeAiGeneration(reportId, title.trim(), serializeContent(content)) != 1) {
            throw new IllegalStateException("AI 보고서 초안 상태를 변경하지 못했습니다.");
        }
    }

    @Transactional
    public void failAiGeneration(Long reportId) {
        reportMapper.failAiGeneration(reportId);
    }

    @Transactional
    public int failStaleAiGenerations(LocalDateTime cutoff) {
        return reportMapper.failStaleAiGenerations(cutoff);
    }

    private Long resolveAuthorId(JwtUser user) {
        if (user.roles().contains("SAFETY_REVIEWER") || user.roles().contains("ADMIN")) {
            return null;
        }
        if (user.roles().contains("OPERATOR")) {
            return user.userId();
        }
        throw new ForbiddenException("보고서 목록 조회 권한이 없습니다.");
    }

    private Map<Long, String> findAuthorNames(List<ReportListItem> items, String authorization) {
        List<Long> authorIds = items.stream()
                .map(ReportListItem::authorId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (authorIds.isEmpty()) {
            return Map.of();
        }
        try {
            return authorDirectoryClient.findByIds(authorIds, authorization).stream()
                    .collect(Collectors.toMap(
                            AuthorDirectoryClient.AuthorSummary::id, AuthorDirectoryClient.AuthorSummary::name));
        } catch (RuntimeException exception) {
            log.warn("Failed to resolve author names. authorIds={}", authorIds, exception);
            return Map.of();
        }
    }

    private ReportDetailRow findReport(Long reportId) {
        ReportDetailRow report = reportMapper.findDetailById(reportId);
        if (report == null) {
            throw new ReportNotFoundException(reportId);
        }
        return report;
    }

    private void requireAccessible(JwtUser user, ReportDetailRow report) {
        if (user.roles().contains("SAFETY_REVIEWER") || user.roles().contains("ADMIN")) {
            return;
        }
        if (user.roles().contains("OPERATOR") && user.userId().equals(report.authorId())) {
            return;
        }
        throw new ForbiddenException("이 보고서에 접근할 권한이 없습니다.");
    }

    private void requireGeneratedReport(ReportDetailRow report) {
        if (ReportStatus.AI_IN_PROGRESS.value().equals(report.status())
                || ReportStatus.GENERATION_FAILED.value().equals(report.status())) {
            throw new IllegalArgumentException("AI 보고서 초안 생성이 완료되지 않았습니다.");
        }
    }

    private void validateUpdateRequest(ReportUpdateRequest request) {
        if (request == null
                || !StringUtils.hasText(request.title())
                || request.title().trim().length() > 200) {
            throw new IllegalArgumentException("보고서 제목은 1자 이상 200자 이하여야 합니다.");
        }
        if (request.content() == null) {
            throw new IllegalArgumentException("보고서 본문이 필요합니다.");
        }
    }

    private String requireEditableStatus(String status) {
        String normalizedStatus = ReportStatus.validate(status);
        if (!ReportStatus.IN_PROGRESS.value().equals(normalizedStatus)
                && !ReportStatus.COMPLETED.value().equals(normalizedStatus)) {
            throw new IllegalArgumentException("보고서 상태는 작성 중 또는 완료만 설정할 수 있습니다.");
        }
        return normalizedStatus;
    }

    private ReportDetailResponse toDetailResponse(ReportDetailRow report) {
        return new ReportDetailResponse(
                report.id(),
                report.authorId(),
                report.title(),
                deserializeContent(report.content()),
                report.status(),
                report.createdAt(),
                report.updatedAt(),
                reportMapper.findSimulationResultIds(report.id()));
    }

    private ReportContent deserializeContent(String content) {
        if (!StringUtils.hasText(content)) {
            return new ReportContent("", "", "");
        }
        try {
            return objectMapper.readValue(content, ReportContent.class);
        } catch (JsonProcessingException exception) {
            return new ReportContent(content, "", "");
        }
    }

    private List<Long> generationResultIds(AiReportDraftCreateRequest request) {
        List<Long> comparisons = request.comparisonSimulationResultIds() == null
                ? List.of()
                : List.copyOf(request.comparisonSimulationResultIds());
        java.util.ArrayList<Long> resultIds = new java.util.ArrayList<>();
        resultIds.add(request.sourceSimulationResultId());
        resultIds.addAll(comparisons);
        return List.copyOf(resultIds);
    }

    private String serializeGenerationRequest(AiReportDraftCreateRequest request) {
        try {
            return objectMapper.writeValueAsString(request);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("AI 보고서 생성 설정을 저장할 수 없습니다.", exception);
        }
    }

    private AiReportDraftCreateRequest deserializeGenerationRequest(String request) {
        if (!StringUtils.hasText(request)) {
            throw new IllegalStateException("저장된 AI 보고서 생성 설정이 없습니다.");
        }
        try {
            return objectMapper.readValue(request, AiReportDraftCreateRequest.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("저장된 AI 보고서 생성 설정을 읽을 수 없습니다.", exception);
        }
    }

    private String serializeContent(ReportContent content) {
        try {
            return objectMapper.writeValueAsString(content);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("보고서 본문을 저장할 수 없습니다.", exception);
        }
    }

    private void validatePage(int page, int size) {
        if (page < 1 || page > MAX_PAGE || size < 1 || size > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("page must be between 1 and 100000 and size must be between 1 and 100.");
        }
    }
}
