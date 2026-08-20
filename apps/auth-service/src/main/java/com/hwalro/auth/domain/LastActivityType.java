package com.hwalro.auth.domain;

import java.util.Arrays;
import java.util.Optional;

/**
 * 사용자가 마지막으로 머문 작업 종류.
 *
 * <p>auth-service는 작업의 위치만 기록하고 도면·시뮬레이션의 상태 값은 소유하지 않는다. 실제 이동 경로는 프론트엔드가 이 값과 resourceId로 구성한다.
 */
public enum LastActivityType {
    LAYOUT_EDIT,
    SIMULATION_SETUP,
    SIMULATION_RESULT;

    public static Optional<LastActivityType> from(String value) {
        if (value == null) {
            return Optional.empty();
        }
        String normalized = value.trim();
        return Arrays.stream(values())
                .filter(type -> type.name().equals(normalized))
                .findFirst();
    }
}
