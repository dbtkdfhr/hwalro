package com.hwalro.simulation.search.diagnosis;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hwalro.simulation.drawing.domain.LayoutExit;
import com.hwalro.simulation.search.domain.Evidence;
import com.hwalro.simulation.search.domain.Finding;
import com.hwalro.simulation.search.domain.FindingType;
import com.hwalro.simulation.search.domain.TimelineChunk;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ExitBalanceFindingExtractor {
    private final ObjectMapper objectMapper;

    public ExitBalanceFindingExtractor(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public List<Finding> extract(List<TimelineChunk> chunks, List<LayoutExit> exits) {
        DemandSnapshot snapshot = demandSnapshot(frameDataOf(chunks), exits);
        if (snapshot == null) {
            return List.of();
        }
        double meanDemand = meanDemand(snapshot.demandByExit().values());
        List<Finding> findings = new ArrayList<>();
        for (Map.Entry<Long, Double> entry : snapshot.demandByExit().entrySet()) {
            if (entry.getValue() <= meanDemand) {
                continue;
            }
            double severity = clamp01((entry.getValue() - meanDemand) / meanDemand);
            LayoutExit exit = snapshot.exitById().get(entry.getKey());
            Evidence evidence =
                    new Evidence("EXIT_DEMAND_DENSITY", entry.getValue(), "PERSON_PER_METER", "EXIT_EVENTS");
            String description = String.format("출구 '%s'의 수요 밀도가 평균보다 높아 대피 흐름이 편중되었습니다.", exit.getName());
            findings.add(new Finding(FindingType.EXIT_IMBALANCE, severity, null, evidence, description));
        }
        findings.sort(Comparator.comparingDouble(Finding::severity).reversed());
        return findings;
    }

    /**
     * 가장 편중된 출구의 심각도. 사용된 출구가 둘 미만이면 편중이 정의되지 않으므로 null이다.
     */
    public Double worstExitSeverity(List<String> frameDataList, List<LayoutExit> exits) {
        DemandSnapshot snapshot = demandSnapshot(frameDataList, exits);
        if (snapshot == null) {
            return null;
        }
        double meanDemand = meanDemand(snapshot.demandByExit().values());
        double worst = 0.0;
        for (Double demand : snapshot.demandByExit().values()) {
            if (demand > meanDemand) {
                worst = Math.max(worst, clamp01((demand - meanDemand) / meanDemand));
            }
        }
        return worst;
    }

    private DemandSnapshot demandSnapshot(List<String> frameDataList, List<LayoutExit> exits) {
        Map<Long, Integer> eventCountByExit = new HashMap<>();
        for (String frameData : frameDataList) {
            collectExitEvents(frameData, eventCountByExit);
        }
        Map<Long, LayoutExit> exitById = new HashMap<>();
        Map<Long, Double> widthByExit = new HashMap<>();
        for (LayoutExit exit : exits) {
            if (exit.getId() == null) {
                continue;
            }
            double width = width(exit);
            if (width <= 0) {
                continue;
            }
            exitById.put(exit.getId(), exit);
            widthByExit.put(exit.getId(), width);
        }
        Map<Long, Double> demandByExit = new HashMap<>();
        for (Map.Entry<Long, Integer> entry : eventCountByExit.entrySet()) {
            Double width = widthByExit.get(entry.getKey());
            if (width == null || entry.getValue() <= 0) {
                continue;
            }
            demandByExit.put(entry.getKey(), entry.getValue() / width);
        }
        return demandByExit.size() < 2 ? null : new DemandSnapshot(demandByExit, exitById);
    }

    private record DemandSnapshot(Map<Long, Double> demandByExit, Map<Long, LayoutExit> exitById) {}

    private static double meanDemand(java.util.Collection<Double> demands) {
        double sum = 0.0;
        for (Double demand : demands) {
            sum += demand;
        }
        return sum / demands.size();
    }

    private static List<String> frameDataOf(List<TimelineChunk> chunks) {
        List<String> frameDataList = new ArrayList<>(chunks.size());
        for (TimelineChunk chunk : chunks) {
            frameDataList.add(chunk.getFrameData());
        }
        return frameDataList;
    }

    private void collectExitEvents(String frameData, Map<Long, Integer> eventCountByExit) {
        if (frameData == null || frameData.isBlank()) {
            return;
        }
        try {
            JsonNode root = objectMapper.readTree(frameData);
            JsonNode events = root.get("exitEvents");
            if (events == null || !events.isArray()) {
                return;
            }
            for (JsonNode event : events) {
                if (!event.hasNonNull("exitId") || !event.get("exitId").isNumber()) {
                    continue;
                }
                eventCountByExit.merge(event.get("exitId").asLong(), 1, Integer::sum);
            }
        } catch (Exception e) {
            // ignore
        }
    }

    private static double width(LayoutExit exit) {
        if (exit.getStartX() == null || exit.getStartY() == null || exit.getEndX() == null || exit.getEndY() == null) {
            return 0.0;
        }
        double dx = exit.getEndX().doubleValue() - exit.getStartX().doubleValue();
        double dy = exit.getEndY().doubleValue() - exit.getStartY().doubleValue();
        return Math.hypot(dx, dy);
    }

    private static double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }
}
