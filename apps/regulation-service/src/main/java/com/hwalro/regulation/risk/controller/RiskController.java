package com.hwalro.regulation.risk.controller;

import com.hwalro.regulation.common.jwt.JwtAuthInterceptor;
import com.hwalro.regulation.common.jwt.JwtUser;
import com.hwalro.regulation.common.jwt.RequireRole;
import com.hwalro.regulation.risk.dto.RiskCreateRequest;
import com.hwalro.regulation.risk.dto.RiskDrawingContextResponse;
import com.hwalro.regulation.risk.dto.RiskListResponse;
import com.hwalro.regulation.risk.dto.RiskResponse;
import com.hwalro.regulation.risk.dto.RiskUpdateRequest;
import com.hwalro.regulation.risk.service.RiskService;
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
@RequestMapping("/api/risks")
@RequireRole({"ADMIN", "OPERATOR", "SAFETY_REVIEWER"})
public class RiskController {
    private final RiskService riskService;

    public RiskController(RiskService riskService) {
        this.riskService = riskService;
    }

    @GetMapping
    public RiskListResponse list(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String query,
            @RequestAttribute(JwtAuthInterceptor.REQUEST_ATTRIBUTE_USER) JwtUser user,
            @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization) {
        return riskService.list(page, size, query, user, authorization);
    }

    @GetMapping("/by-result/{simulationResultId}")
    public List<RiskResponse> listBySimulationResult(
            @PathVariable Long simulationResultId,
            @RequestAttribute(JwtAuthInterceptor.REQUEST_ATTRIBUTE_USER) JwtUser user) {
        return riskService.listBySimulationResult(simulationResultId, user);
    }

    @GetMapping("/by-result/{simulationResultId}/drawing")
    public RiskDrawingContextResponse getDrawingContext(
            @PathVariable Long simulationResultId, @RequestHeader(HttpHeaders.AUTHORIZATION) String authorization) {
        return riskService.getDrawingContext(simulationResultId, authorization);
    }

    @GetMapping("/{id}")
    public RiskResponse get(
            @PathVariable Long id, @RequestAttribute(JwtAuthInterceptor.REQUEST_ATTRIBUTE_USER) JwtUser user) {
        return riskService.get(id, user);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RiskResponse create(
            @RequestBody RiskCreateRequest request,
            @RequestAttribute(JwtAuthInterceptor.REQUEST_ATTRIBUTE_USER) JwtUser user) {
        return riskService.create(request, user.userId());
    }

    @PutMapping("/{id}")
    public RiskResponse update(
            @PathVariable Long id,
            @RequestBody RiskUpdateRequest request,
            @RequestAttribute(JwtAuthInterceptor.REQUEST_ATTRIBUTE_USER) JwtUser user) {
        return riskService.update(id, request, user);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @PathVariable Long id, @RequestAttribute(JwtAuthInterceptor.REQUEST_ATTRIBUTE_USER) JwtUser user) {
        riskService.delete(id, user);
    }
}
