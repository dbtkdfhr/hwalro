package com.hwalro.regulation.risk.service;

import com.hwalro.regulation.common.jwt.ForbiddenException;
import com.hwalro.regulation.common.jwt.JwtUser;
import com.hwalro.regulation.report.client.AuthorDirectoryClient;
import com.hwalro.regulation.report.exception.SimulationServiceException;
import com.hwalro.regulation.report.exception.SimulationServiceTimeoutException;
import com.hwalro.regulation.risk.client.RiskDrawingContextClient;
import com.hwalro.regulation.risk.domain.Risk;
import com.hwalro.regulation.risk.dto.AttachedLawRef;
import com.hwalro.regulation.risk.dto.RiskAttachedLaw;
import com.hwalro.regulation.risk.dto.RiskCreateRequest;
import com.hwalro.regulation.risk.dto.RiskDrawingContextResponse;
import com.hwalro.regulation.risk.dto.RiskListResponse;
import com.hwalro.regulation.risk.dto.RiskResponse;
import com.hwalro.regulation.risk.dto.RiskUpdateRequest;
import com.hwalro.regulation.risk.exception.RiskNotFoundException;
import com.hwalro.regulation.risk.mapper.RiskMapper;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class RiskService {
    private static final Logger log = LoggerFactory.getLogger(RiskService.class);
    private static final int MAX_PAGE_SIZE = 100;
    private static final int MAX_PAGE = 100_000;
    private static final int MAX_TITLE_LENGTH = 200;
    private static final int MAX_DESCRIPTION_LENGTH = 10_000;
    private static final int MAX_ATTACHED_LAWS = 10;
    private static final int MAX_LAW_SERIAL_NUMBER_LENGTH = 30;
    private static final int MAX_LAW_ARTICLE_NUMBER_LENGTH = 100;
    private static final Set<String> ALLOWED_SEVERITIES = Set.of("높음", "보통", "낮음");
    private static final Set<String> ALLOWED_STATUSES = Set.of("임시저장", "조치 중", "완료");
    private static final String ROLE_ADMIN = "ADMIN";
    private static final String ROLE_OPERATOR = "OPERATOR";
    private static final String ROLE_REVIEWER = "SAFETY_REVIEWER";

    private final RiskMapper riskMapper;
    private final RiskDrawingContextClient drawingContextClient;
    private final AuthorDirectoryClient authorDirectoryClient;

    public RiskService(
            RiskMapper riskMapper,
            RiskDrawingContextClient drawingContextClient,
            AuthorDirectoryClient authorDirectoryClient) {
        this.riskMapper = riskMapper;
        this.drawingContextClient = drawingContextClient;
        this.authorDirectoryClient = authorDirectoryClient;
    }

    public RiskListResponse list(int page, int size, JwtUser user, String authorization) {
        return list(page, size, null, user, authorization);
    }

    public RiskListResponse list(int page, int size, String query, JwtUser user, String authorization) {
        validatePage(page, size);
        String normalizedQuery = StringUtils.hasText(query) ? query.trim() : null;
        Long assigneeFilter = resolveAssigneeFilter(user);
        long totalCount = riskMapper.count(assigneeFilter, normalizedQuery);
        List<Risk> risks = riskMapper.findPage((page - 1) * size, size, assigneeFilter, normalizedQuery);
        Map<Long, List<AttachedLawRef>> attachedLawsByRiskId =
                fetchAttachedLawsByRiskIds(risks.stream().map(Risk::getId).toList());
        Map<Long, String> assigneeNames = findAssigneeNames(risks, authorization);
        Map<Long, String> simulationTitles = findSimulationTitles(risks, authorization);
        List<RiskResponse> items = risks.stream()
                .map(risk -> toResponse(
                        risk,
                        attachedLawsByRiskId.getOrDefault(risk.getId(), List.of()),
                        assigneeNames.get(risk.getAssigneeId()),
                        simulationTitles.get(risk.getSimulationResultId())))
                .toList();
        return new RiskListResponse((int) totalCount, page, size, page * size < totalCount, items);
    }

    public List<RiskResponse> listBySimulationResult(Long simulationResultId, JwtUser user) {
        if (simulationResultId == null || simulationResultId <= 0) {
            throw new IllegalArgumentException("시뮬레이션 결과 ID는 양수여야 합니다.");
        }
        Long assigneeFilter = resolveAssigneeFilter(user);
        List<Risk> risks = riskMapper.findBySimulationResultId(simulationResultId, assigneeFilter);
        Map<Long, List<AttachedLawRef>> attachedLawsByRiskId =
                fetchAttachedLawsByRiskIds(risks.stream().map(Risk::getId).toList());
        return risks.stream()
                .map(risk -> toResponse(risk, attachedLawsByRiskId.getOrDefault(risk.getId(), List.of())))
                .toList();
    }

    public RiskDrawingContextResponse getDrawingContext(Long simulationResultId, String authorization) {
        if (simulationResultId == null || simulationResultId <= 0) {
            throw new IllegalArgumentException("시뮬레이션 결과 ID는 양수여야 합니다.");
        }
        if (!StringUtils.hasText(authorization)) {
            throw new IllegalArgumentException("Authorization 헤더가 필요합니다.");
        }
        RiskDrawingContextResponse context = drawingContextClient.findOne(simulationResultId, authorization);
        if (context == null) {
            throw new SimulationServiceException("시뮬레이션 도면을 조회할 수 없습니다.");
        }
        return context;
    }

    public RiskResponse get(Long id, JwtUser user) {
        Risk risk = findByIdOrThrow(id);
        requireAccessible(risk, user);
        return toResponse(
                risk, fetchAttachedLawsByRiskIds(List.of(risk.getId())).getOrDefault(risk.getId(), List.of()));
    }

    @Transactional
    public RiskResponse create(RiskCreateRequest request, Long assigneeId) {
        validateFields(request.title(), request.severity(), request.status(), request.description());
        validateSimulationResultId(request.simulationResultId());
        validateGeometry(request);
        List<AttachedLawRef> attachedLaws = validateAttachedLaws(request.attachedLaws());
        Risk risk = new Risk();
        risk.setAssigneeId(assigneeId);
        risk.setSimulationResultId(request.simulationResultId());
        risk.setTitle(request.title().trim());
        risk.setDescription(request.description());
        risk.setStartX(request.startX());
        risk.setStartY(request.startY());
        risk.setEndX(request.endX());
        risk.setEndY(request.endY());
        risk.setSeverity(request.severity().trim());
        risk.setStatus(request.status().trim());
        riskMapper.insert(risk);
        if (!attachedLaws.isEmpty()) {
            riskMapper.insertAttachedLaws(risk.getId(), attachedLaws);
        }
        return toResponse(findByIdOrThrow(risk.getId()), attachedLaws);
    }

    @Transactional
    public RiskResponse update(Long id, RiskUpdateRequest request, JwtUser user) {
        validateFields(request.title(), request.severity(), request.status(), request.description());
        Risk risk = findByIdOrThrow(id);
        requireAccessible(risk, user);
        risk.setTitle(request.title().trim());
        risk.setDescription(request.description());
        risk.setSeverity(request.severity().trim());
        risk.setStatus(request.status().trim());
        riskMapper.update(risk);
        List<AttachedLawRef> attachedLaws;
        if (request.attachedLaws() == null) {
            attachedLaws = fetchAttachedLawsByRiskIds(List.of(id)).getOrDefault(id, List.of());
        } else {
            attachedLaws = validateAttachedLaws(request.attachedLaws());
            riskMapper.deleteAttachedLawsByRiskId(id);
            if (!attachedLaws.isEmpty()) {
                riskMapper.insertAttachedLaws(id, attachedLaws);
            }
        }
        return toResponse(risk, attachedLaws);
    }

    public void delete(Long id, JwtUser user) {
        Risk risk = findByIdOrThrow(id);
        requireAccessible(risk, user);
        riskMapper.deleteById(id);
    }

    private boolean canManageAll(Set<String> roles) {
        return roles.contains(ROLE_ADMIN) || roles.contains(ROLE_REVIEWER);
    }

    private Long resolveAssigneeFilter(JwtUser user) {
        if (canManageAll(user.roles())) {
            return null;
        }
        if (user.roles().contains(ROLE_OPERATOR)) {
            return user.userId();
        }
        throw new ForbiddenException("접근 권한이 없습니다.");
    }

    private void requireAccessible(Risk risk, JwtUser user) {
        if (canManageAll(user.roles())) {
            return;
        }
        if (user.roles().contains(ROLE_OPERATOR) && user.userId().equals(risk.getAssigneeId())) {
            return;
        }
        throw new ForbiddenException("접근 권한이 없습니다.");
    }

    private Risk findByIdOrThrow(Long id) {
        Risk risk = riskMapper.findById(id);
        if (risk == null) {
            throw new RiskNotFoundException(id);
        }
        return risk;
    }

    private Map<Long, String> findAssigneeNames(List<Risk> risks, String authorization) {
        List<Long> assigneeIds = risks.stream()
                .map(Risk::getAssigneeId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (assigneeIds.isEmpty()) {
            return Map.of();
        }
        try {
            return authorDirectoryClient.findByIds(assigneeIds, authorization).stream()
                    .collect(Collectors.toMap(
                            AuthorDirectoryClient.AuthorSummary::id, AuthorDirectoryClient.AuthorSummary::name));
        } catch (RuntimeException exception) {
            log.warn("Failed to resolve assignee names. assigneeIds={}", assigneeIds, exception);
            return Map.of();
        }
    }

    private RiskResponse toResponse(Risk risk, List<AttachedLawRef> attachedLaws) {
        return toResponse(risk, attachedLaws, null, null);
    }

    private RiskResponse toResponse(
            Risk risk, List<AttachedLawRef> attachedLaws, String assigneeName, String simulationTitle) {
        return new RiskResponse(
                risk.getId(),
                risk.getSimulationResultId(),
                risk.getAssigneeId(),
                assigneeName,
                risk.getTitle(),
                risk.getDescription(),
                risk.getStartX(),
                risk.getStartY(),
                risk.getEndX(),
                risk.getEndY(),
                risk.getSeverity(),
                risk.getStatus(),
                risk.getCreatedAt(),
                attachedLaws,
                simulationTitle);
    }

    private Map<Long, String> findSimulationTitles(List<Risk> risks, String authorization) {
        List<Long> resultIds = risks.stream()
                .map(Risk::getSimulationResultId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        Map<Long, String> titles = new HashMap<>();
        if (resultIds.isEmpty()) {
            return titles;
        }
        int batchSize = drawingContextClient.maxResultCount();
        for (int start = 0; start < resultIds.size(); start += batchSize) {
            List<Long> batch = resultIds.subList(start, Math.min(resultIds.size(), start + batchSize));
            try {
                for (RiskDrawingContextResponse context : drawingContextClient.findAll(batch, authorization)) {
                    titles.put(context.simulationResultId(), context.title());
                }
            } catch (SimulationServiceTimeoutException exception) {
                log.warn("Failed to resolve simulation titles. resultIds={}", batch, exception);
            } catch (RuntimeException exception) {
                log.warn(
                        "Simulation title batch rejected. Falling back to per-result lookup. resultIds={}",
                        batch,
                        exception);
                for (Long resultId : batch) {
                    resolveSimulationTitle(resultId, authorization, titles);
                }
            }
        }
        return titles;
    }

    private void resolveSimulationTitle(Long resultId, String authorization, Map<Long, String> titles) {
        try {
            RiskDrawingContextResponse context = drawingContextClient.findOne(resultId, authorization);
            if (context != null) {
                titles.put(resultId, context.title());
            }
        } catch (RuntimeException exception) {
            log.warn("Failed to resolve simulation title. resultId={}", resultId, exception);
        }
    }

    private Map<Long, List<AttachedLawRef>> fetchAttachedLawsByRiskIds(List<Long> riskIds) {
        if (riskIds.isEmpty()) {
            return Map.of();
        }
        Map<Long, List<AttachedLawRef>> result = new LinkedHashMap<>();
        for (RiskAttachedLaw attachedLaw : riskMapper.findAttachedLawsByRiskIds(riskIds)) {
            result.computeIfAbsent(attachedLaw.riskId(), key -> new ArrayList<>())
                    .add(new AttachedLawRef(attachedLaw.lawSerialNumber(), attachedLaw.lawArticleNumber()));
        }
        return result;
    }

    private List<AttachedLawRef> validateAttachedLaws(List<AttachedLawRef> attachedLaws) {
        if (attachedLaws == null) {
            return List.of();
        }
        Map<String, AttachedLawRef> unique = new LinkedHashMap<>();
        for (AttachedLawRef ref : attachedLaws) {
            String lawSerialNumber =
                    ref.lawSerialNumber() == null ? null : ref.lawSerialNumber().trim();
            String lawArticleNumber = ref.lawArticleNumber() == null
                    ? null
                    : ref.lawArticleNumber().trim();
            if (!StringUtils.hasText(lawSerialNumber) || !StringUtils.hasText(lawArticleNumber)) {
                throw new IllegalArgumentException("법령 일련번호와 조문 번호를 입력해 주세요.");
            }
            if (lawSerialNumber.length() > MAX_LAW_SERIAL_NUMBER_LENGTH) {
                throw new IllegalArgumentException("법령 일련번호는 30자 이하여야 합니다.");
            }
            if (lawArticleNumber.length() > MAX_LAW_ARTICLE_NUMBER_LENGTH) {
                throw new IllegalArgumentException("법령 조문 번호는 100자 이하여야 합니다.");
            }
            unique.putIfAbsent(
                    lawSerialNumber + "|" + lawArticleNumber, new AttachedLawRef(lawSerialNumber, lawArticleNumber));
        }
        if (unique.size() > MAX_ATTACHED_LAWS) {
            throw new IllegalArgumentException("첨부할 법령 조문은 최대 10개까지 가능합니다.");
        }
        return List.copyOf(unique.values());
    }

    private void validateSimulationResultId(Long simulationResultId) {
        if (simulationResultId != null && simulationResultId <= 0) {
            throw new IllegalArgumentException("시뮬레이션 결과 ID는 양수여야 합니다.");
        }
    }

    private void validateGeometry(RiskCreateRequest request) {
        boolean anyProvided = request.startX() != null
                || request.startY() != null
                || request.endX() != null
                || request.endY() != null;
        boolean allProvided = request.startX() != null
                && request.startY() != null
                && request.endX() != null
                && request.endY() != null;
        if (anyProvided != allProvided) {
            throw new IllegalArgumentException("위험 구역 좌표는 startX, startY, endX, endY를 모두 함께 입력해야 합니다.");
        }
        if (allProvided && (request.endX() < request.startX() || request.endY() < request.startY())) {
            throw new IllegalArgumentException("위험 구역 좌표는 endX가 startX 이상, endY가 startY 이상이어야 합니다.");
        }
    }

    private void validatePage(int page, int size) {
        if (page < 1 || page > MAX_PAGE || size < 1 || size > MAX_PAGE_SIZE) {
            throw new IllegalArgumentException("page는 1 이상 100000 이하, size는 1 이상 100 이하여야 합니다.");
        }
    }

    private void validateFields(String title, String severity, String status, String description) {
        if (!StringUtils.hasText(title)) {
            throw new IllegalArgumentException("위험 항목명을 입력해 주세요.");
        }
        if (title.length() > MAX_TITLE_LENGTH) {
            throw new IllegalArgumentException("위험 항목명은 200자 이하여야 합니다.");
        }
        if (!StringUtils.hasText(severity) || !ALLOWED_SEVERITIES.contains(severity.trim())) {
            throw new IllegalArgumentException("위험도는 높음, 보통, 낮음 중 하나여야 합니다.");
        }
        if (!StringUtils.hasText(status) || !ALLOWED_STATUSES.contains(status.trim())) {
            throw new IllegalArgumentException("상태는 임시저장, 조치 중, 완료 중 하나여야 합니다.");
        }
        if (description != null && description.length() > MAX_DESCRIPTION_LENGTH) {
            throw new IllegalArgumentException("설명은 10000자 이하여야 합니다.");
        }
    }
}
