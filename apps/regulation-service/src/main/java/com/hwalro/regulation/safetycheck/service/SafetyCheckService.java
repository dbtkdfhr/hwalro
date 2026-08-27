package com.hwalro.regulation.safetycheck.service;

import com.hwalro.regulation.common.jwt.ForbiddenException;
import com.hwalro.regulation.common.jwt.JwtUser;
import com.hwalro.regulation.risk.dto.LayoutDrawingContextResponse;
import com.hwalro.regulation.safetycheck.client.SafetyCheckDrawingContextClient;
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
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SafetyCheckService {
    private static final Logger log = LoggerFactory.getLogger(SafetyCheckService.class);
    private static final int MAX_AREA_NAME_LENGTH = 200;
    private static final int MAX_AREA_DESCRIPTION_LENGTH = 1_000;
    // ponytail: 스냅샷을 MySQL MEDIUMBLOB에 저장한다(상한 8MB). 용량이 커지면 오브젝트 스토리지로 분리한다.
    private static final int MAX_SNAPSHOT_BYTES = 8 * 1024 * 1024;
    private static final Set<String> ITEM_RESULTS = Set.of("PENDING", "PASS", "REVIEW_REQUIRED", "FAIL");
    private static final Set<String> INSPECTION_STATUSES = Set.of("DRAFT", "COMPLETED");

    private final SafetyCheckMapper safetyCheckMapper;
    private final SafetyCheckDrawingContextClient drawingContextClient;

    public SafetyCheckService(
            SafetyCheckMapper safetyCheckMapper, SafetyCheckDrawingContextClient drawingContextClient) {
        this.safetyCheckMapper = safetyCheckMapper;
        this.drawingContextClient = drawingContextClient;
    }

    public List<InspectionAreaResponse> getAreas(JwtUser user, String authorization) {
        return enrichLayoutTitles(safetyCheckMapper.findAreas(resolveInspectorFilter(user)), authorization);
    }

    public InspectionAreaResponse getArea(Long areaId, JwtUser user, String authorization) {
        InspectionAreaResponse area = findArea(areaId, resolveInspectorFilter(user));
        return enrichLayoutTitles(List.of(area), authorization).get(0);
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
        return getInspectionHistory(areaId, user, null);
    }

    public List<InspectionHistoryResponse> getInspectionHistory(Long areaId, JwtUser user, String authorization) {
        requireExistingArea(areaId);
        return safetyCheckMapper.findInspectionHistory(areaId, resolveInspectorFilter(user));
    }

    public InspectionDetailResponse getInspection(Long inspectionId, JwtUser user) {
        return getInspection(inspectionId, user, null);
    }

    public InspectionDetailResponse getInspection(Long inspectionId, JwtUser user, String authorization) {
        InspectionDetailHeader header = findHeader(inspectionId);
        requireReadable(user, header);
        return toDetail(header, safetyCheckMapper.findInspectionItems(inspectionId));
    }

    public ChecklistTemplateResponse getChecklistTemplate(Long areaId) {
        return loadChecklistTemplate(areaId);
    }

    public ChecklistTemplateResponse getChecklistTemplate(Long areaId, JwtUser user, String authorization) {
        return loadChecklistTemplate(areaId);
    }

    private ChecklistTemplateResponse loadChecklistTemplate(Long areaId) {
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
        return createInspection(areaId, request, user, null);
    }

    @Transactional
    public InspectionDetailResponse createInspection(
            Long areaId, InspectionCreateRequest request, JwtUser user, String authorization) {
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
        inspection.setLayoutId(safetyCheckMapper.findAreaLayoutId(areaId));
        inspection.setInspectorId(user.userId());
        safetyCheckMapper.insertInspection(inspection);
        safetyCheckMapper.insertInspectionItems(inspection.getId(), templateId);
        return getInspection(inspection.getId(), user, authorization);
    }

    @Transactional
    public InspectionDetailResponse getOrCreateOpenInspection(Long areaId, JwtUser user) {
        return getOrCreateOpenInspection(areaId, user, null);
    }

    @Transactional
    public InspectionDetailResponse getOrCreateOpenInspection(Long areaId, JwtUser user, String authorization) {
        if (safetyCheckMapper.lockInspectionArea(areaId) == null) {
            throw new InspectionAreaNotFoundException(areaId);
        }
        Long draftId = safetyCheckMapper.findOpenDraftId(areaId, user.userId());
        if (draftId != null) {
            return getInspection(draftId, user, authorization);
        }
        return createInspection(areaId, null, user, authorization);
    }

    @Transactional
    public InspectionDetailResponse updateInspection(Long inspectionId, InspectionUpdateRequest request, JwtUser user) {
        return updateInspection(inspectionId, request, user, null);
    }

    @Transactional
    public InspectionDetailResponse updateInspection(
            Long inspectionId, InspectionUpdateRequest request, JwtUser user, String authorization) {
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
            validateMarker(item);
            LocalDateTime checkedAt = "PENDING".equals(item.result()) ? null : now;
            if (safetyCheckMapper.updateInspectionItem(
                            inspectionId,
                            item.id(),
                            item.result(),
                            normalizeComment(item.comment()),
                            item.markerX(),
                            item.markerY(),
                            checkedAt)
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
        return getInspection(inspectionId, user, authorization);
    }

    @Transactional
    public void deleteInspection(Long inspectionId, JwtUser user) {
        deleteInspection(inspectionId, user, null);
    }

    @Transactional
    public void deleteInspection(Long inspectionId, JwtUser user, String authorization) {
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

    public void saveSnapshot(Long inspectionId, byte[] image, Long layoutId, Long layoutVersionId, JwtUser user) {
        saveSnapshot(inspectionId, image, layoutId, layoutVersionId, user, null);
    }

    public void saveSnapshot(
            Long inspectionId, byte[] image, Long layoutId, Long layoutVersionId, JwtUser user, String authorization) {
        if (image == null || image.length == 0) {
            throw new IllegalArgumentException("스냅샷 이미지가 비어 있습니다.");
        }
        if (image.length > MAX_SNAPSHOT_BYTES) {
            throw new IllegalArgumentException("스냅샷 이미지는 8MB 이하여야 합니다.");
        }
        if (layoutVersionId != null && layoutVersionId <= 0) {
            throw new IllegalArgumentException("도면 버전 ID는 양수여야 합니다.");
        }
        if (layoutId != null && layoutId <= 0) {
            throw new IllegalArgumentException("도면 ID는 양수여야 합니다.");
        }
        InspectionDetailHeader header = findHeader(inspectionId);
        if ("COMPLETED".equals(header.status())) {
            throw new IllegalArgumentException("완료된 점검에는 스냅샷을 저장할 수 없습니다.");
        }
        boolean isOwner = header.inspectorId().equals(user.userId());
        if (!isOwner && !user.roles().contains("ADMIN")) {
            throw new ForbiddenException("점검자 본인만 스냅샷을 저장할 수 있습니다.");
        }
        if (safetyCheckMapper.updateSnapshotImage(inspectionId, image, layoutId, layoutVersionId) != 1) {
            throw new IllegalArgumentException("임시 저장 상태의 점검에만 스냅샷을 저장할 수 있습니다.");
        }
    }

    public byte[] getSnapshotImage(Long inspectionId) {
        findHeader(inspectionId);
        return safetyCheckMapper.findSnapshotImage(inspectionId);
    }

    public byte[] getSnapshotImage(Long inspectionId, JwtUser user, String authorization) {
        InspectionDetailHeader header = findHeader(inspectionId);
        requireReadable(user, header);
        return safetyCheckMapper.findSnapshotImage(inspectionId);
    }

    private void validateMarker(InspectionUpdateRequest.ItemUpdate item) {
        Double markerX = item.markerX();
        Double markerY = item.markerY();
        if (markerX == null && markerY == null) {
            return;
        }
        if (markerX == null || markerY == null) {
            throw new IllegalArgumentException("항목 위치는 markerX와 markerY를 함께 입력해야 합니다.");
        }
        if (markerX < 0 || markerX > 1 || markerY < 0 || markerY > 1) {
            throw new IllegalArgumentException("항목 위치는 0 이상 1 이하 값이어야 합니다.");
        }
    }

    private List<InspectionAreaResponse> enrichLayoutTitles(List<InspectionAreaResponse> areas, String authorization) {
        List<Long> layoutIds = areas.stream()
                .map(InspectionAreaResponse::layoutId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (layoutIds.isEmpty()) {
            return areas;
        }
        Map<Long, String> titles = new HashMap<>();
        try {
            for (LayoutDrawingContextResponse context :
                    drawingContextClient.findLayoutContexts(layoutIds, authorization)) {
                titles.put(context.layoutId(), context.layoutTitle());
            }
        } catch (RuntimeException exception) {
            log.warn("Failed to resolve inspection area drawing titles. layoutIds={}", layoutIds, exception);
        }
        return areas.stream()
                .map(area -> area.layoutId() == null
                        ? area
                        : new InspectionAreaResponse(
                                area.id(),
                                area.name(),
                                area.description(),
                                area.layoutId(),
                                titles.get(area.layoutId()),
                                area.active(),
                                area.inspectionCount(),
                                area.lastInspectedAt(),
                                area.hasActiveTemplate()))
                .toList();
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
        if (request.layoutId() != null && request.layoutId() <= 0) {
            throw new IllegalArgumentException("도면 ID는 양수여야 합니다.");
        }
        area.setLayoutId(request.layoutId());
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
        if (user.roles().contains("OPERATOR") || user.roles().contains("GENERAL_EMPLOYEE")) {
            return user.userId();
        }
        throw new ForbiddenException("안전 점검 조회 권한이 없습니다.");
    }

    private void requireReadable(JwtUser user, InspectionDetailHeader inspection) {
        if (canReadAll(user)
                || ((user.roles().contains("OPERATOR") || user.roles().contains("GENERAL_EMPLOYEE"))
                        && user.userId().equals(inspection.inspectorId()))) {
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
                header.layoutId(),
                header.layoutVersionId(),
                header.areaLayoutId(),
                header.hasSnapshot(),
                header.inspectorId(),
                header.status(),
                header.comment(),
                header.updatedAt(),
                header.completedAt(),
                items);
    }
}
