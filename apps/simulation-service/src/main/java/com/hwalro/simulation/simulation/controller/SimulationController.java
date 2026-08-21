package com.hwalro.simulation.simulation.controller;

import com.hwalro.simulation.common.jwt.JwtAuthInterceptor;
import com.hwalro.simulation.common.jwt.JwtUser;
import com.hwalro.simulation.common.jwt.RequireRole;
import com.hwalro.simulation.simulation.dto.SimulationDtos.DraftCreateRequest;
import com.hwalro.simulation.simulation.dto.SimulationDtos.HeatmapChunkResponse;
import com.hwalro.simulation.simulation.dto.SimulationDtos.PlacementAdjustmentDraftRequest;
import com.hwalro.simulation.simulation.dto.SimulationDtos.SetupUpdateRequest;
import com.hwalro.simulation.simulation.dto.SimulationDtos.SimulationExecutionResponse;
import com.hwalro.simulation.simulation.dto.SimulationDtos.SimulationOverviewPageResponse;
import com.hwalro.simulation.simulation.dto.SimulationDtos.SimulationOverviewResponse;
import com.hwalro.simulation.simulation.dto.SimulationDtos.SimulationRoutingValidationResponse;
import com.hwalro.simulation.simulation.dto.SimulationDtos.SimulationSetupResponse;
import com.hwalro.simulation.simulation.dto.SimulationDtos.SimulationSummaryResponse;
import com.hwalro.simulation.simulation.dto.SimulationDtos.SimulationWorkSummaryResponse;
import com.hwalro.simulation.simulation.dto.SimulationDtos.TimelineChunkResponse;
import com.hwalro.simulation.simulation.service.SimulationExecutionService;
import com.hwalro.simulation.simulation.service.SimulationService;
import io.swagger.v3.oas.annotations.Operation;
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
@RequestMapping("/api/simulations")
@Tag(name = "Simulations", description = "시뮬레이션 배치 설정 API")
@RequireRole({"OPERATOR", "SAFETY_REVIEWER", "ADMIN"})
public class SimulationController {
    private final SimulationService simulationService;
    private final SimulationExecutionService simulationExecutionService;

    public SimulationController(
            SimulationService simulationService, SimulationExecutionService simulationExecutionService) {
        this.simulationService = simulationService;
        this.simulationExecutionService = simulationExecutionService;
    }

    @GetMapping
    @Operation(summary = "같은 도면 버전의 시뮬레이션 목록 조회")
    public List<SimulationSummaryResponse> list(
            @RequestParam Long layoutVersionId,
            @RequestAttribute(JwtAuthInterceptor.REQUEST_ATTRIBUTE_USER) JwtUser user) {
        return simulationService.list(layoutVersionId, user);
    }

    @GetMapping("/overview")
    @Operation(summary = "접근 가능한 전체 시뮬레이션 목록 조회")
    public SimulationOverviewPageResponse overview(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String query,
            @RequestAttribute(JwtAuthInterceptor.REQUEST_ATTRIBUTE_USER) JwtUser user) {
        return simulationService.listOverview(page, size, query, user);
    }

    @GetMapping("/monitor")
    @Operation(summary = "현재 사용자의 실행 중 및 최근 완료 시뮬레이션 조회")
    public List<SimulationOverviewResponse> monitor(
            @RequestAttribute(JwtAuthInterceptor.REQUEST_ATTRIBUTE_USER) JwtUser user) {
        return simulationService.listMonitor(user);
    }

    @GetMapping("/summary")
    @Operation(summary = "현재 사용자의 시뮬레이션 업무 현황 집계 조회")
    public SimulationWorkSummaryResponse summary(
            @RequestAttribute(JwtAuthInterceptor.REQUEST_ATTRIBUTE_USER) JwtUser user) {
        return simulationService.getWorkSummary(user);
    }

    @GetMapping("/{id}/overview")
    @Operation(summary = "시뮬레이션 단건 개요 조회")
    public SimulationOverviewResponse overviewById(
            @PathVariable Long id, @RequestAttribute(JwtAuthInterceptor.REQUEST_ATTRIBUTE_USER) JwtUser user) {
        return simulationService.getOverview(id, user);
    }

    @PostMapping("/drafts")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "시뮬레이션 DRAFT 생성")
    public SimulationSetupResponse createDraft(
            @RequestBody DraftCreateRequest request,
            @RequestAttribute(JwtAuthInterceptor.REQUEST_ATTRIBUTE_USER) JwtUser user) {
        return simulationService.createDraft(request, user);
    }

    @PostMapping("/{failedId}/placement-adjustment-draft")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "실패한 시뮬레이션에서 배치 조정 초안 생성")
    public SimulationSetupResponse createPlacementAdjustmentDraft(
            @PathVariable Long failedId,
            @RequestBody PlacementAdjustmentDraftRequest request,
            @RequestAttribute(JwtAuthInterceptor.REQUEST_ATTRIBUTE_USER) JwtUser user) {
        return simulationService.createPlacementAdjustmentDraft(failedId, request, user);
    }

    @GetMapping("/{id}/setup")
    @Operation(summary = "시뮬레이션 배치 설정 조회")
    public SimulationSetupResponse getSetup(
            @PathVariable Long id, @RequestAttribute(JwtAuthInterceptor.REQUEST_ATTRIBUTE_USER) JwtUser user) {
        return simulationService.getSetup(id, user);
    }

    @PutMapping("/{id}/setup")
    @Operation(summary = "시뮬레이션 배치 설정 저장")
    public SimulationSetupResponse updateSetup(
            @PathVariable Long id,
            @RequestBody SetupUpdateRequest request,
            @RequestAttribute(JwtAuthInterceptor.REQUEST_ATTRIBUTE_USER) JwtUser user) {
        return simulationService.updateSetup(id, request, user);
    }

    @PostMapping("/{id}/routing-validation")
    @Operation(summary = "시뮬레이션 초기 대피 경로 검증")
    public SimulationRoutingValidationResponse validateRouting(
            @PathVariable Long id, @RequestAttribute(JwtAuthInterceptor.REQUEST_ATTRIBUTE_USER) JwtUser user) {
        return simulationExecutionService.validateRouting(id, user);
    }

    @PostMapping("/{id}/execute")
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(summary = "시뮬레이션 실행 요청")
    public SimulationExecutionResponse execute(
            @PathVariable Long id, @RequestAttribute(JwtAuthInterceptor.REQUEST_ATTRIBUTE_USER) JwtUser user) {
        return simulationExecutionService.execute(id, user);
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "시뮬레이션 실행 취소")
    public SimulationExecutionResponse cancel(
            @PathVariable Long id, @RequestAttribute(JwtAuthInterceptor.REQUEST_ATTRIBUTE_USER) JwtUser user) {
        return simulationExecutionService.cancel(id, user);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "시뮬레이션 삭제")
    public void delete(
            @PathVariable Long id,
            @RequestAttribute(JwtAuthInterceptor.REQUEST_ATTRIBUTE_USER) JwtUser user,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization) {
        simulationService.delete(id, user, authorization);
    }

    @GetMapping("/{id}/execution")
    @Operation(summary = "시뮬레이션 실행 상태 및 결과 조회")
    public SimulationExecutionResponse getExecution(
            @PathVariable Long id, @RequestAttribute(JwtAuthInterceptor.REQUEST_ATTRIBUTE_USER) JwtUser user) {
        return simulationExecutionService.getExecution(id, user);
    }

    @GetMapping("/{id}/timeline/{chunkSequence}")
    @Operation(summary = "시뮬레이션 타임라인 청크 조회")
    public TimelineChunkResponse getTimeline(
            @PathVariable Long id,
            @PathVariable int chunkSequence,
            @RequestAttribute(JwtAuthInterceptor.REQUEST_ATTRIBUTE_USER) JwtUser user) {
        return simulationExecutionService.getTimeline(id, chunkSequence, user);
    }

    @GetMapping("/{id}/heatmap/{chunkSequence}")
    @Operation(summary = "시뮬레이션 히트맵 청크 조회")
    public HeatmapChunkResponse getHeatmap(
            @PathVariable Long id,
            @PathVariable int chunkSequence,
            @RequestAttribute(JwtAuthInterceptor.REQUEST_ATTRIBUTE_USER) JwtUser user) {
        return simulationExecutionService.getHeatmap(id, chunkSequence, user);
    }
}
