package com.hwalro.simulation.improvement.domain;

import com.hwalro.simulation.improvement.geometry.RotatedRectangle;
import java.util.List;

/** 개선안 탐색에 필요한 원본 시뮬레이션·도면 입력을 한 번에 전달합니다. */
public record ImprovementSource(
        long simulationId,
        double floorWidth,
        double floorHeight,
        List<FabricState> fabrics,
        List<RotatedRectangle> fixedObstacles,
        List<RotatedRectangle> exits,
        List<BottleneckArea> bottlenecks,
        List<HeatmapChunk> heatmapChunks) {}
