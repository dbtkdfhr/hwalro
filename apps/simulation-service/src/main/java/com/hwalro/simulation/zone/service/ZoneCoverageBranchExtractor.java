package com.hwalro.simulation.zone.service;

import com.hwalro.simulation.simulation.dto.SimulationDtos.PointDto;
import com.hwalro.simulation.simulation.engine.SimulationEngineRunner.RouteCoverage;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ZoneCoverageBranchExtractor {
    private ZoneCoverageBranchExtractor() {}

    public static Result extract(RouteCoverage coverage, ZoneBounds zone) {
        Map<Integer, List<PointDto>> pointsByLabel = new LinkedHashMap<>();
        int sampleCount = 0;
        int unassignedSampleCount = 0;
        for (int row = 0; row < coverage.rows(); row++) {
            for (int column = 0; column < coverage.columns(); column++) {
                PointDto point = pointAt(coverage, column, row);
                if (!zone.contains(point)) {
                    continue;
                }
                sampleCount++;
                int label = coverage.labels().get(row * coverage.columns() + column);
                if (label < 0) {
                    unassignedSampleCount++;
                    continue;
                }
                pointsByLabel
                        .computeIfAbsent(label, ignored -> new ArrayList<>())
                        .add(point);
            }
        }

        List<Branch> branches = pointsByLabel.entrySet().stream()
                .map(entry -> new Branch(
                        coverage.exitIds().get(entry.getKey()),
                        representativePoint(entry.getValue()),
                        entry.getValue().size()))
                .toList();
        return new Result(branches, sampleCount, unassignedSampleCount);
    }

    private static PointDto pointAt(RouteCoverage coverage, int column, int row) {
        return new PointDto(
                coverage.originX().add(coverage.step().multiply(BigDecimal.valueOf(column))),
                coverage.originY().add(coverage.step().multiply(BigDecimal.valueOf(row))));
    }

    private static PointDto representativePoint(List<PointDto> points) {
        double centerX = points.stream()
                .mapToDouble(point -> point.x().doubleValue())
                .average()
                .orElseThrow();
        double centerY = points.stream()
                .mapToDouble(point -> point.y().doubleValue())
                .average()
                .orElseThrow();
        PointDto representative = points.get(0);
        double shortestDistance = squaredDistance(representative, centerX, centerY);
        for (int index = 1; index < points.size(); index++) {
            PointDto candidate = points.get(index);
            double distance = squaredDistance(candidate, centerX, centerY);
            if (distance < shortestDistance) {
                representative = candidate;
                shortestDistance = distance;
            }
        }
        return representative;
    }

    private static double squaredDistance(PointDto point, double x, double y) {
        double deltaX = point.x().doubleValue() - x;
        double deltaY = point.y().doubleValue() - y;
        return deltaX * deltaX + deltaY * deltaY;
    }

    public record ZoneBounds(BigDecimal x, BigDecimal y, BigDecimal width, BigDecimal height) {
        boolean contains(PointDto point) {
            return point.x().compareTo(x) >= 0
                    && point.x().compareTo(x.add(width)) <= 0
                    && point.y().compareTo(y) >= 0
                    && point.y().compareTo(y.add(height)) <= 0;
        }
    }

    public record Branch(Long exitId, PointDto representativePoint, int sampleCount) {}

    public record Result(List<Branch> branches, int sampleCount, int unassignedSampleCount) {
        public Result {
            branches = List.copyOf(branches);
        }

        public boolean isolated() {
            return sampleCount > 0 && unassignedSampleCount == sampleCount;
        }
    }
}
