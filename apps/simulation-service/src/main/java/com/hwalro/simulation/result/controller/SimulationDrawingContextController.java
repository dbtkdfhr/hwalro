package com.hwalro.simulation.result.controller;

import com.hwalro.simulation.common.jwt.JwtAuthInterceptor;
import com.hwalro.simulation.common.jwt.JwtUser;
import com.hwalro.simulation.common.jwt.RequireRole;
import com.hwalro.simulation.result.dto.SimulationDrawingContextRequest;
import com.hwalro.simulation.result.dto.SimulationDrawingContextResponse;
import com.hwalro.simulation.result.service.SimulationDrawingContextService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/simulation-results/drawing-contexts")
@Tag(name = "Simulation Results", description = "시뮬레이션 결과 조회 API")
@RequireRole({"OPERATOR", "SAFETY_REVIEWER", "ADMIN"})
public class SimulationDrawingContextController {
    private final SimulationDrawingContextService service;

    public SimulationDrawingContextController(SimulationDrawingContextService service) {
        this.service = service;
    }

    @PostMapping
    @Operation(summary = "주의 항목 미리보기용 시뮬레이션 결과 도면 조회")
    public List<SimulationDrawingContextResponse> findAll(
            @RequestBody SimulationDrawingContextRequest request,
            @RequestAttribute(JwtAuthInterceptor.REQUEST_ATTRIBUTE_USER) JwtUser user) {
        return service.findAll(request == null ? null : request.simulationResultIds(), user);
    }
}
