package com.hwalro.simulation.zone.domain;

public enum ZoneElementKind {
    WALL,
    PILLAR,
    FABRIC;

    public static ZoneElementKind from(String value) {
        if (value == null) {
            throw new IllegalArgumentException("구역 구성원 종류가 필요합니다.");
        }
        try {
            return valueOf(value);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("알 수 없는 구역 구성원 종류입니다: " + value);
        }
    }
}
