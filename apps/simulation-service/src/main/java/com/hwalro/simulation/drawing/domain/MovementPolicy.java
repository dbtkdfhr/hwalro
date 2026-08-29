package com.hwalro.simulation.drawing.domain;

public enum MovementPolicy {
    FREE,
    WITHIN_ZONE,
    FIXED;

    public static MovementPolicy from(String value) {
        if (value == null || value.isBlank()) {
            return WITHIN_ZONE;
        }
        try {
            return valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("지원하지 않는 이동 정책입니다: " + value, exception);
        }
    }
}
