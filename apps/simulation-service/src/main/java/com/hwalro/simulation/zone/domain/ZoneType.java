package com.hwalro.simulation.zone.domain;

/** 구역 용도. 문자열 컬럼 {@code layout_zones.zone_type}에 {@code name()}으로 저장한다. */
public enum ZoneType {
    WORK,
    STORAGE,
    PASSAGE,
    /** 배치 개선안 탐색이 구조물을 놓지 못하는 영역. 별도 사각형 편집기를 두지 않고 구역으로 표현한다. */
    EXCLUSION,
    OTHER;

    public static ZoneType from(String value) {
        if (value == null) {
            return OTHER;
        }
        for (ZoneType type : values()) {
            if (type.name().equals(value)) {
                return type;
            }
        }
        throw new IllegalArgumentException("지원하지 않는 구역 유형입니다: " + value);
    }
}
