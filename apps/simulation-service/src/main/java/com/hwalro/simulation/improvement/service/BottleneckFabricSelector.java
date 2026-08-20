package com.hwalro.simulation.improvement.service;

import com.hwalro.simulation.improvement.domain.BottleneckArea;
import com.hwalro.simulation.improvement.domain.FabricState;
import com.hwalro.simulation.improvement.geometry.Geometry;
import java.util.List;
import org.springframework.stereotype.Component;

/** 병목 영역 주변 5m 안의 이동 가능한 fabric만 후보 탐색에 전달합니다. */
@Component
public class BottleneckFabricSelector {
    private static final double SEARCH_RADIUS_METERS = 5;

    /** 어떤 병목에라도 5m 이내인 fabric을 기존 ID 순서대로 반환합니다. */
    public List<FabricState> select(List<FabricState> fabrics, List<BottleneckArea> bottlenecks) {
        return fabrics.stream()
                .filter(fabric -> bottlenecks.stream()
                        .anyMatch(bottleneck ->
                                Geometry.distance(fabric.bounds(), bottleneck.bounds()) <= SEARCH_RADIUS_METERS))
                .toList();
    }
}
