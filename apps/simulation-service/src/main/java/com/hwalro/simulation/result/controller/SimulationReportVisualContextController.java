package com.hwalro.simulation.result.controller;

import com.hwalro.simulation.common.jwt.JwtAuthInterceptor;
import com.hwalro.simulation.common.jwt.JwtUser;
import com.hwalro.simulation.common.jwt.RequireRole;
import com.hwalro.simulation.result.dto.SimulationReportVisualContextRequest;
import com.hwalro.simulation.result.dto.SimulationReportVisualContextResponse;
import com.hwalro.simulation.result.service.SimulationReportVisualContextService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/simulation-results/report-visual-contexts")
@Tag(name = "Simulation Results", description = "시뮬레이션 결과 조회 API")
@RequireRole({"OPERATOR", "SAFETY_REVIEWER", "ADMIN"})
public class SimulationReportVisualContextController {
    private final SimulationReportVisualContextService service;

    public SimulationReportVisualContextController(SimulationReportVisualContextService service) {
        this.service = service;
    }

    @PostMapping
    @Operation(summary = "보고서 미니맵용 시뮬레이션 결과 조회")
    public List<SimulationReportVisualContextResponse> findAll(
            @RequestBody SimulationReportVisualContextRequest request,
            @RequestAttribute(JwtAuthInterceptor.REQUEST_ATTRIBUTE_USER) JwtUser user) {
        return service.findAll(request == null ? null : request.simulationResultIds(), user);
    }
}
