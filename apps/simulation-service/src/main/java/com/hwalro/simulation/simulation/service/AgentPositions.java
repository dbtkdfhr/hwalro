package com.hwalro.simulation.simulation.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hwalro.simulation.simulation.dto.SimulationDtos.PointDto;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;

/**
 * {@code simulation_initial_states.agent_positions}의 저장 형식({@code [[x,y],...]})을 다루는 유일한 지점이다.
 *
 * <p>에이전트는 별도 테이블 없이 이 JSON 컬럼에만 존재하고, 배열 인덱스가 곧 에이전트 번호다.
 * 시뮬레이션 생성·수정과 배치 개선안 채택이 모두 같은 형식을 써야 하므로 여기 한 곳에 모아 둔다.
 */
public final class AgentPositions {
    private AgentPositions() {}

    public static String write(ObjectMapper objectMapper, List<PointDto> positions) {
        try {
            List<List<BigDecimal>> compact = positions.stream()
                    .map(point -> List.of(point.x(), point.y()))
                    .toList();
            return objectMapper.writeValueAsString(compact);
        } catch (IOException exception) {
            throw new IllegalStateException("에이전트 좌표를 저장 형식으로 변환하지 못했습니다.", exception);
        }
    }

    public static List<PointDto> read(ObjectMapper objectMapper, String json) {
        try {
            List<List<BigDecimal>> compact = objectMapper.readValue(json, new TypeReference<>() {});
            return compact.stream()
                    .map(position -> {
                        if (position == null || position.size() != 2) {
                            throw new IllegalStateException("저장된 에이전트 좌표 형식이 올바르지 않습니다.");
                        }
                        return new PointDto(position.get(0), position.get(1));
                    })
                    .toList();
        } catch (IOException exception) {
            throw new IllegalStateException("저장된 에이전트 좌표를 읽지 못했습니다.", exception);
        }
    }
}
