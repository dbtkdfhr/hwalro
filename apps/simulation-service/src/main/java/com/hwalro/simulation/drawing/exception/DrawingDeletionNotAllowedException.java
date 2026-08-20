package com.hwalro.simulation.drawing.exception;

public class DrawingDeletionNotAllowedException extends RuntimeException {
    public DrawingDeletionNotAllowedException(Long id) {
        super("시뮬레이션이 실행된 도면은 삭제할 수 없습니다. 도면 ID: " + id);
    }
}
