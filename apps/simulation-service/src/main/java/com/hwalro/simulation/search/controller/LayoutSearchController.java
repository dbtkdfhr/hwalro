package com.hwalro.simulation.search.controller;

import com.hwalro.simulation.common.jwt.JwtAuthInterceptor;
import com.hwalro.simulation.common.jwt.JwtUser;
import com.hwalro.simulation.common.jwt.RequireRole;
import com.hwalro.simulation.search.domain.LayoutSearchEntity;
import com.hwalro.simulation.search.dto.LayoutSearchDtos.CancellationResponse;
import com.hwalro.simulation.search.dto.LayoutSearchDtos.LayoutSearchResponse;
import com.hwalro.simulation.search.dto.LayoutSearchDtos.PreparedSimulationDto;
import com.hwalro.simulation.search.dto.LayoutSearchDtos.StartStudyRequest;
import com.hwalro.simulation.search.dto.LayoutSearchDtos.StartStudyResponse;
import com.hwalro.simulation.search.service.CandidateAdoptionService;
import com.hwalro.simulation.search.service.LayoutSearchOrchestrator;
import com.hwalro.simulation.search.service.LayoutSearchQueryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@Tag(name = "Layout Searches", description = "배치 개선안 탐색 API")
@RequireRole({"OPERATOR", "SAFETY_REVIEWER", "ADMIN"})
public class LayoutSearchController {
    private static final String DEFAULT_BUDGET = "STANDARD";

    private final LayoutSearchQueryService layoutSearchQueryService;
    private final LayoutSearchOrchestrator layoutSearchOrchestrator;
    private final CandidateAdoptionService candidateAdoptionService;

    public LayoutSearchController(
            LayoutSearchQueryService layoutSearchQueryService,
            LayoutSearchOrchestrator layoutSearchOrchestrator,
            CandidateAdoptionService candidateAdoptionService) {
        this.layoutSearchQueryService = layoutSearchQueryService;
        this.layoutSearchOrchestrator = layoutSearchOrchestrator;
        this.candidateAdoptionService = candidateAdoptionService;
    }

    @PostMapping("/simulations/{simulationId}/layout-searches")
    @Operation(summary = "배치 개선안 탐색 시작", description = "완료된 기준 시뮬레이션에서 배치 개선안 탐색을 시작합니다.")
    public StartStudyResponse start(
            @PathVariable long simulationId,
            @RequestBody(required = false) StartStudyRequest request,
            @RequestAttribute(JwtAuthInterceptor.REQUEST_ATTRIBUTE_USER) JwtUser user) {
        LayoutSearchEntity search = layoutSearchOrchestrator.start(
                simulationId,
                user,
                DEFAULT_BUDGET,
                request == null ? null : request.constraints(),
                request != null && request.verify());
        return new StartStudyResponse(search.getId(), search.getStatus());
    }

    @GetMapping("/simulations/{simulationId}/layout-searches/latest")
    @Operation(summary = "최신 배치 개선안 탐색 조회", description = "기준 시뮬레이션의 가장 최근 탐색을 반환합니다.")
    public LayoutSearchResponse latest(
            @PathVariable long simulationId,
            @RequestAttribute(JwtAuthInterceptor.REQUEST_ATTRIBUTE_USER) JwtUser user) {
        return layoutSearchQueryService.getLatest(simulationId, user);
    }

    @GetMapping("/layout-searches/{searchId}")
    @Operation(summary = "배치 개선안 탐색 단건 조회", description = "폴링 대상 탐색 상세를 반환합니다.")
    public LayoutSearchResponse get(
            @PathVariable long searchId, @RequestAttribute(JwtAuthInterceptor.REQUEST_ATTRIBUTE_USER) JwtUser user) {
        return layoutSearchQueryService.getSearch(searchId, user);
    }

    @PostMapping("/layout-searches/{searchId}/cancellation")
    @Operation(summary = "배치 개선안 탐색 취소", description = "진행 중인 탐색을 취소하고 검증된 후보는 결과로 남깁니다.")
    public CancellationResponse cancel(
            @PathVariable long searchId, @RequestAttribute(JwtAuthInterceptor.REQUEST_ATTRIBUTE_USER) JwtUser user) {
        LayoutSearchEntity search = layoutSearchOrchestrator.cancel(searchId, user);
        return new CancellationResponse(search.getId(), search.getStatus());
    }

    @PostMapping("/layout-searches/{searchId}/candidates/{candidateId}/simulation-preparation")
    @Operation(summary = "개선안 시뮬레이션 준비", description = "실측 개선이 확인된 후보로 독립적인 시뮬레이션 초안을 준비합니다.")
    public PreparedSimulationDto prepareSimulation(
            @PathVariable long searchId,
            @PathVariable long candidateId,
            @RequestAttribute(JwtAuthInterceptor.REQUEST_ATTRIBUTE_USER) JwtUser user) {
        return candidateAdoptionService.prepare(searchId, candidateId, user);
    }
}
