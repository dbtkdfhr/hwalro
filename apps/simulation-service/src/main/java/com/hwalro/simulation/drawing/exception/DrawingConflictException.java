package com.hwalro.simulation.drawing.exception;

public class DrawingConflictException extends RuntimeException {
    public DrawingConflictException(Long id) {
        super("도면이 다른 사용자에 의해 수정되었습니다: " + id);
    }
}
