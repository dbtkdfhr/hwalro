package com.hwalro.simulation.result.exception;

public class SimulationResultNotFoundException extends RuntimeException {
    public SimulationResultNotFoundException(Long simulationResultId) {
        super("시뮬레이션 결과를 찾을 수 없습니다: " + simulationResultId);
    }
}
