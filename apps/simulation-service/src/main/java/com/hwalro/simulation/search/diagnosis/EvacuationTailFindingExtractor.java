package com.hwalro.simulation.search.diagnosis;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hwalro.simulation.search.domain.Evidence;
import com.hwalro.simulation.search.domain.Finding;
import com.hwalro.simulation.search.domain.FindingType;
import com.hwalro.simulation.search.domain.Rectangle;
import com.hwalro.simulation.search.domain.TimelineChunk;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class EvacuationTailFindingExtractor {
    private final ObjectMapper objectMapper;

    public EvacuationTailFindingExtractor(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public List<Finding> extract(List<TimelineChunk> chunks) {
        Map<Long, Position> lastPositionByAgent = new HashMap<>();
        List<ExitEvent> exitEvents = new ArrayList<>();
        for (TimelineChunk chunk : chunks) {
            parseChunk(chunk.getFrameData(), lastPositionByAgent, exitEvents);
        }
        if (exitEvents.size() < 10) {
            return List.of();
        }
        exitEvents.sort(Comparator.comparingDouble(ExitEvent::timeSeconds));
        int p90Index = (int) Math.ceil(0.9 * exitEvents.size()) - 1;
        double tailThreshold = exitEvents.get(p90Index).timeSeconds();
        List<ExitEvent> tailEvents = new ArrayList<>();
        Set<Long> tailAgentIds = new HashSet<>();
        for (ExitEvent event : exitEvents) {
            if (event.timeSeconds() >= tailThreshold) {
                tailEvents.add(event);
                tailAgentIds.add(event.agentId());
            }
        }
        double ratio = (double) tailEvents.size() / exitEvents.size();
        double severity = clamp01(ratio);
        Rectangle region = null;
        for (Long agentId : tailAgentIds) {
            Position position = lastPositionByAgent.get(agentId);
            if (position == null) {
                continue;
            }
            if (region == null) {
                region = new Rectangle(position.x(), position.y(), position.x(), position.y());
            } else {
                region = new Rectangle(
                        Math.min(region.startX(), position.x()),
                        Math.min(region.startY(), position.y()),
                        Math.max(region.endX(), position.x()),
                        Math.max(region.endY(), position.y()));
            }
        }
        Evidence evidence = new Evidence("TAIL_EVACUATION_RATIO", ratio, "RATIO", "EXIT_EVENTS");
        String description = String.format("대피 완료 시간이 긴 하위 %d명이 전체 대피의 지연을 주도했습니다.", tailAgentIds.size());
        return List.of(new Finding(FindingType.EVACUATION_TAIL, severity, region, evidence, description));
    }

    private void parseChunk(String frameData, Map<Long, Position> lastPositionByAgent, List<ExitEvent> exitEvents) {
        if (frameData == null || frameData.isBlank()) {
            return;
        }
        try {
            JsonNode root = objectMapper.readTree(frameData);
            JsonNode events = root.get("exitEvents");
            if (events != null && events.isArray()) {
                for (JsonNode event : events) {
                    if (!event.hasNonNull("timeSeconds")
                            || !event.get("timeSeconds").isNumber()) {
                        continue;
                    }
                    if (!event.hasNonNull("agentId") || !event.get("agentId").isNumber()) {
                        continue;
                    }
                    exitEvents.add(new ExitEvent(
                            event.get("timeSeconds").asDouble(),
                            event.get("agentId").asLong()));
                }
            }
            JsonNode frames = root.get("frames");
            if (frames != null && frames.isArray()) {
                for (JsonNode frame : frames) {
                    JsonNode agents = frame.get("agents");
                    if (agents == null || !agents.isArray()) {
                        continue;
                    }
                    for (JsonNode agent : agents) {
                        if (!agent.hasNonNull("agentId")
                                || !agent.get("agentId").isNumber()) {
                            continue;
                        }
                        if (!agent.hasNonNull("x") || !agent.get("x").isNumber()) {
                            continue;
                        }
                        if (!agent.hasNonNull("y") || !agent.get("y").isNumber()) {
                            continue;
                        }
                        lastPositionByAgent.put(
                                agent.get("agentId").asLong(),
                                new Position(
                                        agent.get("x").asDouble(),
                                        agent.get("y").asDouble()));
                    }
                }
            }
        } catch (Exception e) {
            // ignore
        }
    }

    private static double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    private record ExitEvent(double timeSeconds, long agentId) {}

    private record Position(double x, double y) {}
}
