package com.hwalro.simulation.result.service;

import com.hwalro.simulation.common.jwt.ForbiddenException;
import com.hwalro.simulation.common.jwt.JwtUser;
import com.hwalro.simulation.result.dto.SimulationReportContextResponse;
import com.hwalro.simulation.result.dto.SimulationReportContextResponse.Bottleneck;
import com.hwalro.simulation.result.dto.SimulationReportContextResponse.Metric;
import com.hwalro.simulation.result.exception.SimulationResultNotFoundException;
import com.hwalro.simulation.result.mapper.SimulationReportContextMapper;
import com.hwalro.simulation.result.mapper.SimulationReportContextMapper.BottleneckRow;
import com.hwalro.simulation.result.mapper.SimulationReportContextMapper.MetricRow;
import com.hwalro.simulation.result.mapper.SimulationReportContextMapper.SummaryRow;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class SimulationReportContextService {
    private static final int MAX_RESULT_COUNT = 6;

    private final SimulationReportContextMapper mapper;

    public SimulationReportContextService(SimulationReportContextMapper mapper) {
        this.mapper = mapper;
    }

    public List<SimulationReportContextResponse> findAll(List<Long> simulationResultIds, JwtUser user) {
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
        Map<Long, List<Metric>> metrics = mapper.findMetrics(validatedIds).stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        MetricRow::simulationResultId,
                        java.util.stream.Collectors.mapping(
                                row -> new Metric(row.metricType(), row.metricValue(), row.unit()),
                                java.util.stream.Collectors.toList())));
        Map<Long, List<Bottleneck>> bottlenecks = mapper.findBottlenecks(validatedIds).stream()
                .collect(java.util.stream.Collectors.groupingBy(
                        BottleneckRow::simulationResultId,
                        java.util.stream.Collectors.mapping(
                                row -> new Bottleneck(
                                        row.bottleneckOrder(),
                                        row.startTimeSeconds(),
                                        row.endTimeSeconds(),
                                        row.peakDensity(),
                                        row.thresholdValue()),
                                java.util.stream.Collectors.toList())));

        return validatedIds.stream()
                .map(id -> {
                    SummaryRow summary = summaries.get(id);
                    return new SimulationReportContextResponse(
                            id,
                            summary.simulationId(),
                            summary.layoutId(),
                            summary.layoutVersionId(),
                            summary.layoutTitle(),
                            metrics.getOrDefault(id, List.of()),
                            bottlenecks.getOrDefault(id, List.of()));
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
