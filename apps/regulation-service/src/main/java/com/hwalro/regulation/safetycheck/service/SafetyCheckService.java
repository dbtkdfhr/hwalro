package com.hwalro.regulation.safetycheck.service;

import com.hwalro.regulation.common.jwt.ForbiddenException;
import com.hwalro.regulation.common.jwt.JwtUser;
import com.hwalro.regulation.safetycheck.domain.ChecklistTemplate;
import com.hwalro.regulation.safetycheck.domain.InspectionArea;
import com.hwalro.regulation.safetycheck.domain.SafetyInspection;
import com.hwalro.regulation.safetycheck.dto.ChecklistTemplateResponse;
import com.hwalro.regulation.safetycheck.dto.ChecklistTemplateUpdateRequest;
import com.hwalro.regulation.safetycheck.dto.InspectionAreaRequest;
import com.hwalro.regulation.safetycheck.dto.InspectionAreaResponse;
import com.hwalro.regulation.safetycheck.dto.InspectionCreateRequest;
import com.hwalro.regulation.safetycheck.dto.InspectionDetailHeader;
import com.hwalro.regulation.safetycheck.dto.InspectionDetailResponse;
import com.hwalro.regulation.safetycheck.dto.InspectionHistoryResponse;
import com.hwalro.regulation.safetycheck.dto.InspectionItemResponse;
import com.hwalro.regulation.safetycheck.dto.InspectionUpdateRequest;
import com.hwalro.regulation.safetycheck.exception.InspectionAreaNotFoundException;
import com.hwalro.regulation.safetycheck.exception.SafetyInspectionNotFoundException;
import com.hwalro.regulation.safetycheck.mapper.SafetyCheckMapper;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SafetyCheckService {
    private static final int MAX_AREA_NAME_LENGTH = 200;
    private static final int MAX_AREA_DESCRIPTION_LENGTH = 1_000;
    private static final Set<String> ITEM_RESULTS = Set.of("PENDING", "PASS", "REVIEW_REQUIRED", "FAIL");
    private static final Set<String> INSPECTION_STATUSES = Set.of("DRAFT", "COMPLETED");

    private final SafetyCheckMapper safetyCheckMapper;

    public SafetyCheckService(SafetyCheckMapper safetyCheckMapper) {
        this.safetyCheckMapper = safetyCheckMapper;
    }

    public List<InspectionAreaResponse> getAreas(JwtUser user) {
        return safetyCheckMapper.findAreas(resolveInspectorFilter(user));
    }

    public InspectionAreaResponse getArea(Long areaId, JwtUser user) {
        return findArea(areaId, resolveInspectorFilter(user));
    }

    @Transactional
    public InspectionAreaResponse createArea(InspectionAreaRequest request) {
        InspectionArea area = toArea(request);
        safetyCheckMapper.insertArea(area);
        return findArea(area.getId(), null);
    }

    @Transactional
    public InspectionAreaResponse updateArea(Long areaId, InspectionAreaRequest request) {
        InspectionArea area = toArea(request);
        area.setId(areaId);
        if (safetyCheckMapper.updateArea(area) != 1) {
            throw new InspectionAreaNotFoundException(areaId);
        }
        return findArea(areaId, null);
    }

    @Transactional
    public void deleteArea(Long areaId) {
        if (safetyCheckMapper.deactivateArea(areaId) != 1) {
            throw new InspectionAreaNotFoundException(areaId);
        }
    }

    public List<InspectionHistoryResponse> getInspectionHistory(Long areaId, JwtUser user) {
        requireExistingArea(areaId);
        return safetyCheckMapper.findInspectionHistory(areaId, resolveInspectorFilter(user));
    }

    public InspectionDetailResponse getInspection(Long inspectionId, JwtUser user) {
        InspectionDetailHeader header = findHeader(inspectionId);
        requireReadable(user, header);
        return toDetail(header, safetyCheckMapper.findInspectionItems(inspectionId));
    }

    public ChecklistTemplateResponse getChecklistTemplate(Long areaId) {
        requireExistingArea(areaId);
        Long templateId = safetyCheckMapper.findActiveTemplateId(areaId);
        if (templateId == null) {
            return new ChecklistTemplateResponse(null, 0, List.of());
        }
        Integer version = safetyCheckMapper.findTemplateVersion(templateId);
        return new ChecklistTemplateResponse(
                templateId, version == null ? 0 : version, safetyCheckMapper.findTemplateItems(templateId));
    }

    @Transactional
    public ChecklistTemplateResponse updateChecklistTemplate(Long areaId, ChecklistTemplateUpdateRequest request) {
        validateTemplate(request);
        lockArea(areaId);

        int nextVersion = safetyCheckMapper.findNextTemplateVersion(areaId);
        safetyCheckMapper.retireActiveTemplates(areaId);

        ChecklistTemplate template = new ChecklistTemplate();
        template.setInspectionAreaId(areaId);
        template.setVersion(nextVersion);
        safetyCheckMapper.insertChecklistTemplate(template);

        for (int index = 0; index < request.items().size(); index++) {
            ChecklistTemplateUpdateRequest.ItemInput item = request.items().get(index);
            safetyCheckMapper.insertChecklistTemplateItem(
                    template.getId(),
                    item.title().trim(),
                    normalizeComment(item.criterion()),
                    item.category().trim(),
                    index + 1);
        }
        return getChecklistTemplate(areaId);
    }

    @Transactional
    public InspectionDetailResponse createInspection(Long areaId, InspectionCreateRequest request, JwtUser user) {
        Long simulationResultId = validateSimulationResultReference(request);
        requireArea(areaId);
        Long templateId = safetyCheckMapper.findActiveTemplateId(areaId);
        if (templateId == null) {
            throw new IllegalArgumentException("The inspection area does not have an active checklist template.");
        }

        SafetyInspection inspection = new SafetyInspection();
        inspection.setInspectionAreaId(areaId);
        inspection.setChecklistTemplateId(templateId);
        inspection.setSimulationResultId(simulationResultId);
        inspection.setInspectorId(user.userId());
        safetyCheckMapper.insertInspection(inspection);
        safetyCheckMapper.insertInspectionItems(inspection.getId(), templateId);
        return getInspection(inspection.getId(), user);
    }

    @Transactional
    public InspectionDetailResponse getOrCreateOpenInspection(Long areaId, JwtUser user) {
        if (safetyCheckMapper.lockInspectionArea(areaId) == null) {
            throw new InspectionAreaNotFoundException(areaId);
        }
        Long draftId = safetyCheckMapper.findOpenDraftId(areaId, user.userId());
        if (draftId != null) {
            return getInspection(draftId, user);
        }
        return createInspection(areaId, null, user);
    }

    @Transactional
    public InspectionDetailResponse updateInspection(Long inspectionId, InspectionUpdateRequest request, JwtUser user) {
        InspectionDetailHeader header = findHeader(inspectionId);
        requireArea(header.inspectionAreaId());
        if ("COMPLETED".equals(header.status())) {
            throw new IllegalArgumentException("Completed inspections cannot be modified.");
        }
        if (!header.inspectorId().equals(user.userId())) {
            throw new ForbiddenException("Only the assigned inspector can update this inspection.");
        }
        validateUpdate(request);

        int expectedItemCount = safetyCheckMapper.countInspectionItems(inspectionId);
        if (request.items().size() != expectedItemCount) {
            throw new IllegalArgumentException("Every checklist item must be included in the update.");
        }

        LocalDateTime now = LocalDateTime.now();
        for (InspectionUpdateRequest.ItemUpdate item : request.items()) {
            LocalDateTime checkedAt = "PENDING".equals(item.result()) ? null : now;
            if (safetyCheckMapper.updateInspectionItem(
                            inspectionId, item.id(), item.result(), normalizeComment(item.comment()), checkedAt)
                    != 1) {
                throw new IllegalArgumentException("The request contains an item outside this inspection.");
            }
        }

        if ("COMPLETED".equals(request.status()) && safetyCheckMapper.countPendingItems(inspectionId) > 0) {
            throw new IllegalArgumentException("All checklist items must be assessed before completion.");
        }
        LocalDateTime completedAt = "COMPLETED".equals(request.status()) ? now : null;
        if (safetyCheckMapper.updateInspection(
                        inspectionId, request.status(), normalizeComment(request.comment()), completedAt)
                != 1) {
            throw new IllegalArgumentException("The inspection was already completed by another request.");
        }
        return getInspection(inspectionId, user);
    }

    @Transactional
    public void deleteInspection(Long inspectionId, JwtUser user) {
        InspectionDetailHeader header = findHeader(inspectionId);
        if ("COMPLETED".equals(header.status())) {
            throw new IllegalArgumentException("Completed inspections cannot be deleted.");
        }
        boolean isOwner = header.inspectorId().equals(user.userId());
        boolean isAdmin = user.roles().contains("ADMIN");
        if (!isOwner && !isAdmin) {
            throw new ForbiddenException("Only the assigned inspector or an administrator can delete this inspection.");
        }
        if (safetyCheckMapper.deleteInspection(inspectionId) != 1) {
            throw new IllegalArgumentException("Only draft inspections can be deleted.");
        }
    }

    private void requireArea(Long areaId) {
        if (!safetyCheckMapper.areaExists(areaId)) {
            throw new InspectionAreaNotFoundException(areaId);
        }
    }

    private void requireExistingArea(Long areaId) {
        findArea(areaId, null);
    }

    private InspectionAreaResponse findArea(Long areaId, Long inspectorId) {
        InspectionAreaResponse area = safetyCheckMapper.findArea(areaId, inspectorId);
        if (area == null) {
            throw new InspectionAreaNotFoundException(areaId);
        }
        return area;
    }

    private InspectionArea toArea(InspectionAreaRequest request) {
        if (request == null || request.name() == null || request.name().isBlank()) {
            throw new IllegalArgumentException("Inspection area name is required.");
        }
        String name = request.name().trim();
        if (name.length() > MAX_AREA_NAME_LENGTH) {
            throw new IllegalArgumentException("Inspection area name must be 200 characters or fewer.");
        }
        String description = normalizeComment(request.description());
        if (description != null && description.length() > MAX_AREA_DESCRIPTION_LENGTH) {
            throw new IllegalArgumentException("Inspection area description must be 1000 characters or fewer.");
        }
        InspectionArea area = new InspectionArea();
        area.setName(name);
        area.setDescription(description);
        return area;
    }

    private void lockArea(Long areaId) {
        if (safetyCheckMapper.lockInspectionArea(areaId) == null) {
            throw new InspectionAreaNotFoundException(areaId);
        }
    }

    private Long validateSimulationResultReference(InspectionCreateRequest request) {
        if (request == null || request.simulationResultId() == null) {
            return null;
        }
        if (request.simulationResultId() <= 0) {
            throw new IllegalArgumentException("simulationResultId must be a positive number.");
        }
        throw new IllegalArgumentException(
                "simulationResultId cannot be linked until simulation-service provides a validation API.");
    }

    private InspectionDetailHeader findHeader(Long inspectionId) {
        InspectionDetailHeader header = safetyCheckMapper.findInspectionHeader(inspectionId);
        if (header == null) {
            throw new SafetyInspectionNotFoundException(inspectionId);
        }
        return header;
    }

    private Long resolveInspectorFilter(JwtUser user) {
        if (canReadAll(user)) {
            return null;
        }
        if (user.roles().contains("OPERATOR")) {
            return user.userId();
        }
        throw new ForbiddenException("안전 점검 조회 권한이 없습니다.");
    }

    private void requireReadable(JwtUser user, InspectionDetailHeader inspection) {
        if (canReadAll(user)
                || (user.roles().contains("OPERATOR")
                        && inspection.inspectorId().equals(user.userId()))) {
            return;
        }
        throw new ForbiddenException("이 안전 점검을 조회할 권한이 없습니다.");
    }

    private boolean canReadAll(JwtUser user) {
        return user.roles().contains("SAFETY_REVIEWER") || user.roles().contains("ADMIN");
    }

    private void validateUpdate(InspectionUpdateRequest request) {
        if (request == null || !INSPECTION_STATUSES.contains(request.status()) || request.items() == null) {
            throw new IllegalArgumentException("A valid status and checklist items are required.");
        }
        if (request.items().stream()
                .anyMatch(item -> item == null || item.id() == null || !ITEM_RESULTS.contains(item.result()))) {
            throw new IllegalArgumentException("Unsupported checklist item result.");
        }
        long distinctItemCount = request.items().stream()
                .map(InspectionUpdateRequest.ItemUpdate::id)
                .distinct()
                .count();
        if (distinctItemCount != request.items().size()) {
            throw new IllegalArgumentException("Checklist items must not be duplicated.");
        }
    }

    private void validateTemplate(ChecklistTemplateUpdateRequest request) {
        if (request == null || request.items() == null || request.items().isEmpty()) {
            throw new IllegalArgumentException("At least one checklist item is required.");
        }
        if (request.items().size() > 100) {
            throw new IllegalArgumentException("A checklist can contain at most 100 items.");
        }
        for (ChecklistTemplateUpdateRequest.ItemInput item : request.items()) {
            if (item == null
                    || item.title() == null
                    || item.title().isBlank()
                    || item.title().trim().length() > 200
                    || item.category() == null
                    || item.category().isBlank()
                    || item.category().trim().length() > 30) {
                throw new IllegalArgumentException("Each checklist item requires a valid title and category.");
            }
        }
        Set<String> titles = request.items().stream()
                .map(item -> item.title().trim().toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());
        if (titles.size() != request.items().size()) {
            throw new IllegalArgumentException("Checklist item titles must not be duplicated.");
        }
    }

    private String normalizeComment(String comment) {
        if (comment == null || comment.isBlank()) {
            return null;
        }
        return comment.trim();
    }

    private InspectionDetailResponse toDetail(InspectionDetailHeader header, List<InspectionItemResponse> items) {
        return new InspectionDetailResponse(
                header.id(),
                header.inspectionAreaId(),
                header.areaName(),
                header.simulationResultId(),
                header.inspectorId(),
                header.status(),
                header.comment(),
                header.updatedAt(),
                header.completedAt(),
                items);
    }
}
