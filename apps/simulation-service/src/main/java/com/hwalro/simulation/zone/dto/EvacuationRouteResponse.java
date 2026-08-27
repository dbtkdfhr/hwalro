package com.hwalro.simulation.zone.dto;

import com.hwalro.simulation.simulation.dto.SimulationDtos.ExitDto;
import com.hwalro.simulation.simulation.dto.SimulationDtos.PointDto;
import java.util.List;

/**
 * 구역의 정적 대피 경로. 평상시 기준이며 실시간 상황을 반영하지 않는다.
 *
 * <p>시뮬레이션 식별자·지표는 담지 않는다. 직원에게 운영 정보를 흘리지 않기 위함이다.
 *
 * @param status {@code AVAILABLE} | {@code UNREACHABLE} | {@code NOT_CONFIGURED}
 * @param origin 구역 중심점. 걸을 수 없는 자리라 출발점을 옮겼더라도 요청한 중심점 그대로다.
 * @param routeOrigin 실제 경로 계산에 사용한 시작점
 * @param originAdjusted 구역 중심점에서 시작점을 보정했는지
 * @param unavailableReason 경로를 제공하지 못한 구체적인 사유. 경로가 있으면 null
 * @param defaultExit 구역에 배정된 비상구. 배정이 없으면 null이다.
 * @param recommendedExitId 실제로 안내하는 비상구. 배정이 있으면 그것이고, 없으면 걸어서 가장 가까운 곳이다.
 * @param exitChoice {@code ASSIGNED}(배정된 비상구) | {@code NEAREST}(걸어서 가장 가까운 비상구)
 * @param distanceMeters 안내 경로를 따라 걷는 거리(m)
 * @param narrowestMeters 경로에서 가장 좁은 지점의 통로 반폭(m). 작을수록 사람이 몰렸을 때 막히기 쉽다.
 * @param partitions 구역 안에서 비상구가 갈리는 영역들. 배정된 비상구가 있으면 나눌 이유가 없어 비어 있다.
 */
public record EvacuationRouteResponse(
        Long zoneId,
        String zoneName,
        PointDto origin,
        PointDto routeOrigin,
        boolean originAdjusted,
        String status,
        String unavailableReason,
        ExitDto defaultExit,
        Long recommendedExitId,
        String recommendedExitName,
        String exitChoice,
        double distanceMeters,
        Double narrowestMeters,
        List<PointDto> waypoints,
        List<ZoneExitPartitionDto> partitions) {}
