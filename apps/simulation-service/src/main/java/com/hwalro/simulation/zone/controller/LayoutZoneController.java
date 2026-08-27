package com.hwalro.simulation.zone.controller;

import com.hwalro.simulation.common.jwt.JwtAuthInterceptor;
import com.hwalro.simulation.common.jwt.JwtUser;
import com.hwalro.simulation.common.jwt.RequireRole;
import com.hwalro.simulation.zone.dto.EvacuationRouteResponse;
import com.hwalro.simulation.zone.dto.LayoutZoneDtos.LayoutMetadataResponse;
import com.hwalro.simulation.zone.dto.LayoutZoneDtos.StructureConstraintUpdateRequest;
import com.hwalro.simulation.zone.dto.LayoutZoneDtos.ZoneCreateRequest;
import com.hwalro.simulation.zone.dto.LayoutZoneDtos.ZoneResponse;
import com.hwalro.simulation.zone.dto.LayoutZoneDtos.ZoneUpdateRequest;
import com.hwalro.simulation.zone.service.EvacuationPreviewService;
import com.hwalro.simulation.zone.service.LayoutMetadataService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 구역·구조물 제약 API.
 *
 * <p>클래스 기본은 권한 역할 전용이다. 일반 직원에게 열어야 하는 메서드에만 메서드 레벨 {@code @RequireRole}을 단다 —
 * {@code JwtAuthInterceptor}는 메서드 애노테이션이 있으면 클래스 애노테이션을 보지 않는다.
 */
@RestController
@RequestMapping("/api")
@Tag(name = "Layout Zones", description = "도면 구역·구조물 배치 제약 API")
@RequireRole({"OPERATOR", "SAFETY_REVIEWER", "ADMIN"})
public class LayoutZoneController {
    private final LayoutMetadataService layoutMetadataService;
    private final EvacuationPreviewService evacuationPreviewService;

    public LayoutZoneController(
            LayoutMetadataService layoutMetadataService, EvacuationPreviewService evacuationPreviewService) {
        this.layoutMetadataService = layoutMetadataService;
        this.evacuationPreviewService = evacuationPreviewService;
    }

    @GetMapping("/drawings/{id}/layout-metadata")
    @RequireRole({"OPERATOR", "SAFETY_REVIEWER", "ADMIN", "GENERAL_EMPLOYEE"})
    @Operation(
            summary = "도면 구역 메타데이터 조회",
            description = "구역, 구조물 배치 제약, 배치 제외 영역을 반환합니다. 일반 직원은 배정된 구역과 그 구역 구조물만 볼 수 있습니다.")
    public LayoutMetadataResponse metadata(
            @Parameter(description = "도면 ID") @PathVariable Long id,
            @Parameter(hidden = true) @RequestAttribute(JwtAuthInterceptor.REQUEST_ATTRIBUTE_USER) JwtUser user) {
        return layoutMetadataService.readMetadata(id, user);
    }

    @PostMapping("/drawings/{id}/zones")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "구역 생성", description = "도면 현재 버전에 직사각형 구역을 만듭니다. 담당 직원은 auth-service에서 검증합니다.")
    public ZoneResponse create(
            @Parameter(description = "도면 ID") @PathVariable Long id,
            @RequestBody ZoneCreateRequest request,
            @Parameter(hidden = true) @RequestAttribute(JwtAuthInterceptor.REQUEST_ATTRIBUTE_USER) JwtUser user,
            @Parameter(hidden = true) @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization) {
        return layoutMetadataService.createZone(id, request, user, authorization);
    }

    @PatchMapping("/drawings/{id}/zones/{zoneId}")
    @Operation(summary = "구역 수정", description = "이름·유형·사각형·담당 직원·비상구·구성 구조물을 부분 수정합니다.")
    public ZoneResponse update(
            @Parameter(description = "도면 ID") @PathVariable Long id,
            @Parameter(description = "구역 ID") @PathVariable Long zoneId,
            @RequestBody ZoneUpdateRequest request,
            @Parameter(hidden = true) @RequestAttribute(JwtAuthInterceptor.REQUEST_ATTRIBUTE_USER) JwtUser user,
            @Parameter(hidden = true) @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization) {
        return layoutMetadataService.updateZone(id, zoneId, request, user, authorization);
    }

    @DeleteMapping("/drawings/{id}/zones/{zoneId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "구역 삭제", description = "구역과 그 구역의 구조물 소속 관계를 지웁니다. 구조물 자체는 공용 구조물로 남습니다.")
    public void delete(
            @Parameter(description = "도면 ID") @PathVariable Long id,
            @Parameter(description = "구역 ID") @PathVariable Long zoneId,
            @Parameter(hidden = true) @RequestAttribute(JwtAuthInterceptor.REQUEST_ATTRIBUTE_USER) JwtUser user) {
        layoutMetadataService.deleteZone(id, zoneId, user);
    }

    @PatchMapping("/drawings/{id}/structures/{fabricId}/constraints")
    @RequireRole({"OPERATOR", "SAFETY_REVIEWER", "ADMIN", "GENERAL_EMPLOYEE"})
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(
            summary = "구조물 배치 제약 수정",
            description = "이동 가능 여부·최대 이동 거리·회전 금지·벽 붙임을 저장합니다. 기획/운영 담당자와 안전 검토 권한만 수정할 수 있습니다.")
    public void updateConstraints(
            @Parameter(description = "도면 ID") @PathVariable Long id,
            @Parameter(description = "구조물 ID") @PathVariable Long fabricId,
            @RequestBody StructureConstraintUpdateRequest request,
            @Parameter(hidden = true) @RequestAttribute(JwtAuthInterceptor.REQUEST_ATTRIBUTE_USER) JwtUser user) {
        layoutMetadataService.updateStructureConstraints(id, fabricId, request, user);
    }

    @GetMapping("/drawings/{id}/evacuation-routes")
    @RequireRole({"OPERATOR", "SAFETY_REVIEWER", "ADMIN", "GENERAL_EMPLOYEE"})
    @Operation(
            summary = "도면 대피 경로",
            description = "볼 수 있는 구역의 대피 경로를 반환합니다. 안전 담당자는 도면의 모든 구역을, 일반 직원은 자신에게 배정된 구역만 받습니다. 평상시 기준 정적 경로입니다.")
    public List<EvacuationRouteResponse> evacuationRoutes(
            @Parameter(description = "도면 ID") @PathVariable Long id,
            @Parameter(hidden = true) @RequestAttribute(JwtAuthInterceptor.REQUEST_ATTRIBUTE_USER) JwtUser user) {
        return evacuationPreviewService.previewAll(id, user);
    }
}
