package com.hwalro.simulation.search.diagnosis;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hwalro.simulation.search.domain.Evidence;
import com.hwalro.simulation.search.domain.Finding;
import com.hwalro.simulation.search.domain.FindingType;
import com.hwalro.simulation.search.domain.Rectangle;
import com.hwalro.simulation.search.domain.StoredBottleneck;
import java.util.ArrayList;
import java.util.List;

public class BottleneckFindingExtractor {
    private final ObjectMapper objectMapper;

    public BottleneckFindingExtractor(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public List<Finding> extract(List<StoredBottleneck> bottlenecks) {
        List<Finding> findings = new ArrayList<>();
        for (StoredBottleneck bottleneck : bottlenecks) {
            Rectangle region = parseRegion(bottleneck.getGeometry());
            if (region == null) {
                continue;
            }
            if (bottleneck.getStartTimeSeconds() == null || bottleneck.getEndTimeSeconds() == null) {
                continue;
            }
            double durationSeconds = bottleneck.getEndTimeSeconds() - bottleneck.getStartTimeSeconds();
            if (durationSeconds <= 0) {
                continue;
            }
            if (bottleneck.getPeakDensity() == null || bottleneck.getThresholdValue() == null) {
                continue;
            }
            double peakDensity = bottleneck.getPeakDensity();
            double thresholdValue = bottleneck.getThresholdValue();
            if (peakDensity <= 0 || thresholdValue <= 0) {
                continue;
            }
            double base = peakDensity / thresholdValue;
            double durationFactor = clamp01(durationSeconds / 300.0);
            double severity = clamp01(base * (0.7 + 0.3 * durationFactor));
            Evidence evidence = new Evidence("PEAK_DENSITY", peakDensity, "PERSON_PER_M2", "DETECTED_BOTTLENECK");
            String description = String.format("병목 구역이 임계 밀집도 이상으로 %.0f초 동안 지속되어 보행 흐름을 지연시켰습니다.", durationSeconds);
            findings.add(new Finding(FindingType.BOTTLENECK, severity, region, evidence, description));
        }
        return findings;
    }

    private Rectangle parseRegion(String geometry) {
        if (geometry == null || geometry.isBlank()) {
            return null;
        }
        try {
            JsonNode node = objectMapper.readTree(geometry);
            if (!hasNumeric(node, "x")
                    || !hasNumeric(node, "y")
                    || !hasNumeric(node, "width")
                    || !hasNumeric(node, "height")) {
                return null;
            }
            double x = node.get("x").asDouble();
            double y = node.get("y").asDouble();
            return new Rectangle(
                    x,
                    y,
                    x + node.get("width").asDouble(),
                    y + node.get("height").asDouble());
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean hasNumeric(JsonNode node, String field) {
        return node.hasNonNull(field) && node.get(field).isNumber();
    }

    private static double clamp01(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }
}
