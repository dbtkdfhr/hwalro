package com.hwalro.simulation.result.controller;

import com.hwalro.simulation.common.jwt.JwtAuthInterceptor;
import com.hwalro.simulation.common.jwt.JwtUser;
import com.hwalro.simulation.common.jwt.RequireRole;
import com.hwalro.simulation.result.dto.ComparableSimulationPageResponse;
import com.hwalro.simulation.result.dto.SimulationResultDetailResponse;
import com.hwalro.simulation.result.service.SimulationResultDetailService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/simulations")
@Tag(name = "Simulation Results", description = "시뮬레이션 결과 조회 API")
@RequireRole({"OPERATOR", "SAFETY_REVIEWER", "ADMIN"})
public class SimulationResultDetailController {
    private final SimulationResultDetailService service;

    public SimulationResultDetailController(SimulationResultDetailService service) {
        this.service = service;
    }

    @GetMapping("/{simulationId}/result")
    @Operation(summary = "시뮬레이션 결과 상세 조회")
    public SimulationResultDetailResponse find(
            @PathVariable Long simulationId,
            @RequestAttribute(JwtAuthInterceptor.REQUEST_ATTRIBUTE_USER) JwtUser user) {
        return service.find(simulationId, user);
    }

    @GetMapping("/{simulationId}/result/comparable-simulations")
    @Operation(summary = "비교 가능한 시뮬레이션 목록 조회")
    public ComparableSimulationPageResponse findComparableSimulations(
            @PathVariable Long simulationId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "5") int size,
            @RequestAttribute(JwtAuthInterceptor.REQUEST_ATTRIBUTE_USER) JwtUser user) {
        return service.findComparableSimulations(simulationId, page, size, user);
    }
}
