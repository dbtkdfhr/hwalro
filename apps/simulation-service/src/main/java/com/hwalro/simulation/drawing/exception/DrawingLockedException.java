package com.hwalro.simulation.drawing.exception;

public class DrawingLockedException extends RuntimeException {
    public DrawingLockedException() {
        super("시뮬레이션이 참조한 도면 버전은 수정할 수 없습니다.");
    }
}
