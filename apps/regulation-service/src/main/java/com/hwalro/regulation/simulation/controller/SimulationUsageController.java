package com.hwalro.regulation.simulation.controller;

import com.hwalro.regulation.common.jwt.RequireRole;
import com.hwalro.regulation.report.mapper.ReportMapper;
import com.hwalro.regulation.simulation.dto.SimulationUsageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/regulations/simulation-usage")
@Tag(name = "Regulation Simulation Usage", description = "시뮬레이션 결과 참조 여부 확인 API")
@RequireRole({"OPERATOR", "SAFETY_REVIEWER", "ADMIN"})
public class SimulationUsageController {
    private final ReportMapper reportMapper;

    public SimulationUsageController(ReportMapper reportMapper) {
        this.reportMapper = reportMapper;
    }

    @GetMapping("/{simulationResultId}")
    @Operation(summary = "시뮬레이션 결과가 보고서에서 참조 중인지 조회")
    public SimulationUsageResponse getSimulationUsage(@PathVariable Long simulationResultId) {
        boolean usedInReports = reportMapper.countBySimulationResultId(simulationResultId) > 0;
        return new SimulationUsageResponse(usedInReports);
    }
}
