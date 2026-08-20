package com.hwalro.simulation.improvement.service;

import org.springframework.stereotype.Component;

/** 결과 생성 API와의 임시 연동 지점입니다. 결과 생성 기능 병합 시 이 클래스만 실제 호출로 교체합니다. */
@Component
public class SimulationResultGenerationClient {

    public SimulationStartResult start(long sourceSimulationId, long layoutVersionId, long requestedBy) {
        throw new IllegalStateException("시뮬레이션 결과 생성 API가 아직 연결되지 않았습니다.");
    }

    public record SimulationStartResult(long simulationId, String status) {}
}
