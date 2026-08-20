package com.hwalro.simulation.result.service;

import com.hwalro.simulation.common.jwt.ForbiddenException;
import com.hwalro.simulation.common.jwt.JwtUser;
import com.hwalro.simulation.result.dto.SimulationReportVisualContextResponse;
import com.hwalro.simulation.result.exception.SimulationResultNotFoundException;
import com.hwalro.simulation.result.mapper.SimulationReportContextMapper;
import com.hwalro.simulation.result.mapper.SimulationReportContextMapper.SummaryRow;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class SimulationReportVisualContextService {
    private static final int MAX_RESULT_COUNT = 6;

    private final SimulationReportContextMapper mapper;
    private final SimulationResultDetailService detailService;

    public SimulationReportVisualContextService(
            SimulationReportContextMapper mapper, SimulationResultDetailService detailService) {
        this.mapper = mapper;
        this.detailService = detailService;
    }

    public List<SimulationReportVisualContextResponse> findAll(List<Long> simulationResultIds, JwtUser user) {
        List<Long> validatedIds = validate(simulationResultIds);
        Long createdByScope = resolveCreatedByScope(user);
        Map<Long, SummaryRow> summaries = new HashMap<>();
        for (SummaryRow summary : mapper.findSummaries(validatedIds, createdByScope)) {
            summaries.put(summary.simulationResultId(), summary);
        }
        for (Long id : validatedIds) {
            if (!summaries.containsKey(id)) {
                throw new SimulationResultNotFoundException(id);
            }
        }

        return validatedIds.stream()
                .map(id -> {
                    SummaryRow summary = summaries.get(id);
                    return new SimulationReportVisualContextResponse(
                            id,
                            summary.simulationId(),
                            summary.layoutTitle(),
                            detailService.findDrawing(summary.simulationId()),
                            detailService.findBottlenecks(id));
                })
                .toList();
    }

    private Long resolveCreatedByScope(JwtUser user) {
        if (user == null) {
            throw new ForbiddenException("시뮬레이션 결과 조회 권한이 없습니다.");
        }
        if (user.roles().contains("SAFETY_REVIEWER") || user.roles().contains("ADMIN")) {
            return null;
        }
        if (user.roles().contains("OPERATOR") && user.userId() != null) {
            return user.userId();
        }
        throw new ForbiddenException("시뮬레이션 결과 조회 권한이 없습니다.");
    }

    private List<Long> validate(List<Long> ids) {
        if (ids == null || ids.isEmpty() || ids.size() > MAX_RESULT_COUNT) {
            throw new IllegalArgumentException("시뮬레이션 결과 ID는 1개 이상 6개 이하여야 합니다.");
        }
        Set<Long> uniqueIds = new HashSet<>();
        for (Long id : ids) {
            if (id == null || id <= 0) {
                throw new IllegalArgumentException("시뮬레이션 결과 ID는 양수여야 합니다.");
            }
            if (!uniqueIds.add(id)) {
                throw new IllegalArgumentException("시뮬레이션 결과 ID는 중복될 수 없습니다.");
            }
        }
        return List.copyOf(ids);
    }
}
