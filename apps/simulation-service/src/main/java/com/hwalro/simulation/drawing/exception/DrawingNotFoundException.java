package com.hwalro.simulation.drawing.exception;

public class DrawingNotFoundException extends RuntimeException {
    public DrawingNotFoundException(Long id) {
        super("도면을 찾을 수 없습니다: " + id);
    }
}
