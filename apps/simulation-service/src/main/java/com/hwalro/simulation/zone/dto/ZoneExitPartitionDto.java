package com.hwalro.simulation.zone.dto;

import com.hwalro.simulation.simulation.dto.SimulationDtos.PointDto;
import java.util.List;

/**
 * 한 구역 안에서 같은 비상구로 나가게 되는 영역의 대표 경로.
 *
 * <p>구역에 비상구가 배정되어 있지 않으면 칸마다 가장 빨리 닿는 비상구가 다를 수 있다. 도면 전체를 덮는
 * 큰 구역이라면 여러 비상구로 갈라지는 것이 정상이고, 갈래별 대표 경로를 보여주는 것이 대피 계획 검토에
 * 필요한 정보다.
 *
 * @param entryPoint 이 갈래의 출발점. 경계 트리밍 후 {@code waypoints[0]}과 같다.
 * @param waypoints 이 영역의 대표점부터 비상구까지의 경로
 * @param distanceMeters 그 경로를 따라 걷는 거리(m)
 */
public record ZoneExitPartitionDto(
        Long exitId,
        String exitName,
        PointDto entryPoint,
        List<PointDto> waypoints,
        double distanceMeters,
        Double narrowestMeters) {}
