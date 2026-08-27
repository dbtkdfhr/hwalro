package com.hwalro.regulation.safetycheck.controller;

import com.hwalro.regulation.common.jwt.JwtAuthInterceptor;
import com.hwalro.regulation.common.jwt.JwtUser;
import com.hwalro.regulation.common.jwt.RequireRole;
import com.hwalro.regulation.safetycheck.dto.ChecklistTemplateResponse;
import com.hwalro.regulation.safetycheck.dto.ChecklistTemplateUpdateRequest;
import com.hwalro.regulation.safetycheck.dto.InspectionAreaRequest;
import com.hwalro.regulation.safetycheck.dto.InspectionAreaResponse;
import com.hwalro.regulation.safetycheck.dto.InspectionCreateRequest;
import com.hwalro.regulation.safetycheck.dto.InspectionDetailResponse;
import com.hwalro.regulation.safetycheck.dto.InspectionHistoryResponse;
import com.hwalro.regulation.safetycheck.dto.InspectionUpdateRequest;
import com.hwalro.regulation.safetycheck.service.SafetyCheckService;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/safety-checks")
@Tag(name = "Safety checks", description = "Area-based safety checklist API")
@RequireRole({"ADMIN", "OPERATOR", "SAFETY_REVIEWER", "GENERAL_EMPLOYEE"})
public class SafetyCheckController {
    private final SafetyCheckService safetyCheckService;

    public SafetyCheckController(SafetyCheckService safetyCheckService) {
        this.safetyCheckService = safetyCheckService;
    }

    @GetMapping("/areas")
    public List<InspectionAreaResponse> getAreas(
            @RequestAttribute(JwtAuthInterceptor.REQUEST_ATTRIBUTE_USER) JwtUser user,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization) {
        return safetyCheckService.getAreas(user, authorization);
    }

    @GetMapping("/areas/{areaId}")
    public InspectionAreaResponse getArea(
            @PathVariable Long areaId,
            @RequestAttribute(JwtAuthInterceptor.REQUEST_ATTRIBUTE_USER) JwtUser user,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization) {
        return safetyCheckService.getArea(areaId, user, authorization);
    }

    @PostMapping("/areas")
    @RequireRole({"ADMIN", "SAFETY_REVIEWER"})
    @ResponseStatus(HttpStatus.CREATED)
    public InspectionAreaResponse createArea(@RequestBody InspectionAreaRequest request) {
        return safetyCheckService.createArea(request);
    }

    @PutMapping("/areas/{areaId}")
    @RequireRole({"ADMIN", "SAFETY_REVIEWER"})
    public InspectionAreaResponse updateArea(@PathVariable Long areaId, @RequestBody InspectionAreaRequest request) {
        return safetyCheckService.updateArea(areaId, request);
    }

    @DeleteMapping("/areas/{areaId}")
    @RequireRole({"ADMIN", "SAFETY_REVIEWER"})
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteArea(@PathVariable Long areaId) {
        safetyCheckService.deleteArea(areaId);
    }

    @GetMapping("/areas/{areaId}/inspections")
    public List<InspectionHistoryResponse> getInspectionHistory(
            @PathVariable Long areaId,
            @RequestAttribute(JwtAuthInterceptor.REQUEST_ATTRIBUTE_USER) JwtUser user,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization) {
        return safetyCheckService.getInspectionHistory(areaId, user, authorization);
    }

    @GetMapping("/areas/{areaId}/checklist-template")
    public ChecklistTemplateResponse getChecklistTemplate(
            @PathVariable Long areaId,
            @RequestAttribute(JwtAuthInterceptor.REQUEST_ATTRIBUTE_USER) JwtUser user,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization) {
        return safetyCheckService.getChecklistTemplate(areaId, user, authorization);
    }

    @PutMapping("/areas/{areaId}/checklist-template")
    @RequireRole({"ADMIN", "SAFETY_REVIEWER"})
    public ChecklistTemplateResponse updateChecklistTemplate(
            @PathVariable Long areaId, @RequestBody ChecklistTemplateUpdateRequest request) {
        return safetyCheckService.updateChecklistTemplate(areaId, request);
    }

    @PostMapping("/areas/{areaId}/inspections")
    public InspectionDetailResponse createInspection(
            @PathVariable Long areaId,
            @RequestBody(required = false) InspectionCreateRequest request,
            @RequestAttribute(JwtAuthInterceptor.REQUEST_ATTRIBUTE_USER) JwtUser user,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization) {
        return safetyCheckService.createInspection(areaId, request, user, authorization);
    }

    @PostMapping("/areas/{areaId}/inspections/current")
    public InspectionDetailResponse getOrCreateOpenInspection(
            @PathVariable Long areaId,
            @RequestAttribute(JwtAuthInterceptor.REQUEST_ATTRIBUTE_USER) JwtUser user,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization) {
        return safetyCheckService.getOrCreateOpenInspection(areaId, user, authorization);
    }

    @GetMapping("/inspections/{inspectionId}")
    public InspectionDetailResponse getInspection(
            @PathVariable Long inspectionId,
            @RequestAttribute(JwtAuthInterceptor.REQUEST_ATTRIBUTE_USER) JwtUser user,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization) {
        return safetyCheckService.getInspection(inspectionId, user, authorization);
    }

    @PutMapping("/inspections/{inspectionId}")
    public InspectionDetailResponse updateInspection(
            @PathVariable Long inspectionId,
            @RequestBody InspectionUpdateRequest request,
            @RequestAttribute(JwtAuthInterceptor.REQUEST_ATTRIBUTE_USER) JwtUser user,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization) {
        return safetyCheckService.updateInspection(inspectionId, request, user, authorization);
    }

    @PutMapping("/inspections/{inspectionId}/snapshot")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void saveSnapshot(
            @PathVariable Long inspectionId,
            @RequestParam(required = false) Long layoutId,
            @RequestParam(required = false) Long layoutVersionId,
            @RequestBody byte[] image,
            @RequestAttribute(JwtAuthInterceptor.REQUEST_ATTRIBUTE_USER) JwtUser user,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization) {
        safetyCheckService.saveSnapshot(inspectionId, image, layoutId, layoutVersionId, user, authorization);
    }

    @GetMapping("/inspections/{inspectionId}/snapshot")
    public ResponseEntity<byte[]> getSnapshot(
            @PathVariable Long inspectionId,
            @RequestAttribute(JwtAuthInterceptor.REQUEST_ATTRIBUTE_USER) JwtUser user,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization) {
        byte[] image = safetyCheckService.getSnapshotImage(inspectionId, user, authorization);
        if (image == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "스냅샷이 없습니다.");
        }
        return ResponseEntity.ok().contentType(MediaType.IMAGE_PNG).body(image);
    }

    @DeleteMapping("/inspections/{inspectionId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteInspection(
            @PathVariable Long inspectionId,
            @RequestAttribute(JwtAuthInterceptor.REQUEST_ATTRIBUTE_USER) JwtUser user,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization) {
        safetyCheckService.deleteInspection(inspectionId, user, authorization);
    }
}
