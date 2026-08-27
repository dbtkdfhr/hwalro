package com.hwalro.simulation.drawing.dto;

import java.math.BigDecimal;

/**
 * 저장된 벽의 식별자를 함께 노출한다. 구역 멤버십이 이 ID를 참조하므로 도면을 저장해도 ID가 유지되어야 한다. 새로 그린 벽은 {@code id}가 null이다.
 */
public record WallDto(
        Long id,
        String name,
        BigDecimal startX,
        BigDecimal startY,
        BigDecimal endX,
        BigDecimal endY,
        Integer displayOrder) {}
