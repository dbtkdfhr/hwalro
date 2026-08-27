package com.hwalro.simulation.search.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.hwalro.simulation.search.domain.ChangeOp;
import com.hwalro.simulation.search.domain.ChangeSet;
import com.hwalro.simulation.simulation.dto.SimulationDtos.DrawingGeometryDto;
import com.hwalro.simulation.simulation.dto.SimulationDtos.ExitDto;
import com.hwalro.simulation.simulation.dto.SimulationDtos.FabricRectDto;
import com.hwalro.simulation.simulation.dto.SimulationDtos.PointDto;
import com.hwalro.simulation.simulation.dto.SimulationDtos.SimulationSetupResponse;
import com.hwalro.simulation.simulation.exception.InvalidSimulationGeometryException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * 후보 시행은 구조물을 옮긴 배치를 엔진에 넘긴다. 원본 좌표를 그대로 물려주면 사람이 집기 안에서
 * 출발해 엔진이 기하 구성 단계에서 죽으므로, 채택 경로와 같은 재배치가 여기서도 일어나야 한다.
 */
class ChangeSetApplierTest {
    private static final long FABRIC_ID = 7L;

    private final ChangeSetApplier applier = new ChangeSetApplier();

    @Test
    void relocatesAgentsThatTheRotatedFabricNowCovers() {
        // 원본 집기는 (3,3)-(7,6)이라 (4,6.2)는 그 밖이다. 90도 회전하면 집기가 (3.5,2.5)-(6.5,6.5)를
        // 차지하면서 바로 그 자리를 덮는다.
        SimulationSetupResponse baseline = setup(List.of(point(4, 6.2), point(1, 1)));

        SimulationSetupResponse mutated = applier.apply(baseline, rotate(BigDecimal.valueOf(90)));

        List<PointDto> agents = mutated.agentPositions();
        assertThat(agents).hasSize(2);
        // 덮인 사람은 집기 밖으로 밀려나고, 멀리 있던 사람은 그대로다.
        assertThat(insideRotatedFabric(agents.get(0))).isFalse();
        assertThat(agents.get(1).x().doubleValue()).isEqualTo(1.0);
        assertThat(agents.get(1).y().doubleValue()).isEqualTo(1.0);
    }

    @Test
    void leavesAgentsUntouchedWhenTheChangeDoesNotReachThem() {
        SimulationSetupResponse baseline = setup(List.of(point(1, 1), point(1.8, 1)));

        SimulationSetupResponse mutated = applier.apply(baseline, rotate(BigDecimal.valueOf(90)));

        assertThat(mutated.agentPositions().get(0).x().doubleValue()).isEqualTo(1.0);
        assertThat(mutated.agentPositions().get(0).y().doubleValue()).isEqualTo(1.0);
        assertThat(mutated.agentPositions().get(1).x().doubleValue()).isEqualTo(1.8);
        assertThat(mutated.agentPositions().get(1).y().doubleValue()).isEqualTo(1.0);
    }

    @Test
    void failsWhenTheChangedLayoutHasNoRoomForTheAgents() {
        // 방을 거의 다 채우는 집기로 바꾸면 60명이 설 자리가 없다.
        List<PointDto> crowd = java.util.stream.IntStream.range(0, 60)
                .mapToObj(index -> point(5, 5))
                .toList();
        SimulationSetupResponse baseline = setup(crowd);

        ChangeSet huge = new ChangeSet(
                1,
                "METER",
                List.of(new ChangeOp(
                        "MOVE_FABRIC", FABRIC_ID, transform(3, 3, 7, 6, 0), transform(0.4, 0.4, 9.6, 9.3, 0))));

        assertThatThrownBy(() -> applier.apply(baseline, huge)).isInstanceOf(InvalidSimulationGeometryException.class);
    }

    private boolean insideRotatedFabric(PointDto agent) {
        // (3,3)-(7,6)을 중심 (5,4.5) 기준으로 90도 회전하면 (3.5,2.5)-(6.5,6.5)를 차지한다.
        double x = agent.x().doubleValue();
        double y = agent.y().doubleValue();
        return x > 3.5 && x < 6.5 && y > 2.5 && y < 6.5;
    }

    private ChangeSet rotate(BigDecimal degrees) {
        return new ChangeSet(
                1,
                "METER",
                List.of(new ChangeOp(
                        "MOVE_FABRIC",
                        FABRIC_ID,
                        transform(3, 3, 7, 6, 0),
                        new ChangeOp.FabricTransform(
                                BigDecimal.valueOf(3),
                                BigDecimal.valueOf(3),
                                BigDecimal.valueOf(7),
                                BigDecimal.valueOf(6),
                                degrees))));
    }

    private static ChangeOp.FabricTransform transform(
            double startX, double startY, double endX, double endY, double rotation) {
        return new ChangeOp.FabricTransform(
                BigDecimal.valueOf(startX),
                BigDecimal.valueOf(startY),
                BigDecimal.valueOf(endX),
                BigDecimal.valueOf(endY),
                BigDecimal.valueOf(rotation));
    }

    private static PointDto point(double x, double y) {
        return new PointDto(BigDecimal.valueOf(x), BigDecimal.valueOf(y));
    }

    private SimulationSetupResponse setup(List<PointDto> agents) {
        DrawingGeometryDto drawing = new DrawingGeometryDto(
                1L,
                "테스트 배치",
                BigDecimal.valueOf(10),
                BigDecimal.valueOf(10),
                List.of(point(0, 0), point(10, 0), point(10, 10), point(0, 10)),
                List.of(),
                List.of(),
                List.of(new FabricRectDto(
                        FABRIC_ID,
                        "집기 1",
                        BigDecimal.valueOf(3),
                        BigDecimal.valueOf(3),
                        BigDecimal.valueOf(7),
                        BigDecimal.valueOf(6),
                        BigDecimal.ZERO)),
                List.of(),
                List.of(new ExitDto(
                        2L,
                        "비상구 1",
                        BigDecimal.valueOf(10),
                        BigDecimal.valueOf(4),
                        BigDecimal.valueOf(10),
                        BigDecimal.valueOf(6))));
        return new SimulationSetupResponse(
                11L,
                12L,
                null,
                "기준 시뮬레이션",
                "COMPLETED",
                LocalDateTime.now(),
                1,
                "SFM_DEFAULT_V2",
                "HAZARD_RADIAL_EXP_V3",
                agents.size(),
                BigDecimal.valueOf(1.25),
                BigDecimal.ZERO,
                agents,
                List.of(),
                List.of(2L),
                drawing,
                false);
    }
}
