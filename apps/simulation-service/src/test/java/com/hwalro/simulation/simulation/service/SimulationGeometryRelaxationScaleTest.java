package com.hwalro.simulation.simulation.service;

import static org.assertj.core.api.Assertions.assertThatCode;

import com.hwalro.simulation.drawing.domain.Fabric;
import com.hwalro.simulation.drawing.domain.OutsideWall;
import com.hwalro.simulation.simulation.dto.SimulationDtos.PointDto;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * 채택은 사용자가 버튼을 누르고 기다리는 동작이므로, 큰 배치에서도 재배치가 제때 끝나야 한다.
 * 반복 상한(MAX_RELAX_ITERATIONS)과 활성 집합 최적화가 함께 무너지면 여기서 먼저 드러난다.
 */
class SimulationGeometryRelaxationScaleTest {
    @Test
    @Timeout(30)
    void relaxesALargeCrowdWithinTheTimeBudget() {
        // 60m x 60m 방에 0.65m 격자로 약 5000명을 세우고, 그 한가운데로 구조물을 옮긴다.
        List<PointDto> boundary = SimulationGeometry.assembleBoundary(
                List.of(wall(0, 0, 60, 0), wall(60, 0, 60, 60), wall(60, 60, 0, 60), wall(0, 60, 0, 0)),
                decimal(60),
                decimal(60));

        List<PointDto> agents = new ArrayList<>();
        for (int row = 0; row < 71 && agents.size() < 5_000; row++) {
            for (int column = 0; column < 71 && agents.size() < 5_000; column++) {
                agents.add(point(2.0 + column * 0.65, 2.0 + row * 0.65));
            }
        }
        List<Fabric> fabrics = List.of(fabric(20, 20, 26, 26));

        assertThatCode(() -> {
                    List<PointDto> relaxed =
                            SimulationGeometry.relaxAgents(agents, boundary, List.of(), List.of(), fabrics, List.of());
                    SimulationGeometry.validateSetup(
                            relaxed, List.of(), boundary, List.of(), List.of(), fabrics, List.of());
                })
                .doesNotThrowAnyException();
    }

    private static OutsideWall wall(double startX, double startY, double endX, double endY) {
        OutsideWall wall = new OutsideWall();
        wall.setName("outside");
        wall.setStartX(decimal(startX));
        wall.setStartY(decimal(startY));
        wall.setEndX(decimal(endX));
        wall.setEndY(decimal(endY));
        return wall;
    }

    private static Fabric fabric(double startX, double startY, double endX, double endY) {
        Fabric fabric = new Fabric();
        fabric.setName("fabric");
        fabric.setStartX(decimal(startX));
        fabric.setStartY(decimal(startY));
        fabric.setEndX(decimal(endX));
        fabric.setEndY(decimal(endY));
        fabric.setRotation(decimal(0));
        return fabric;
    }

    private static PointDto point(double x, double y) {
        return new PointDto(decimal(x), decimal(y));
    }

    private static BigDecimal decimal(double value) {
        return BigDecimal.valueOf(value);
    }
}
