package com.hwalro.simulation.drawing.dto;

import java.math.BigDecimal;

/**
 * 저장된 구조물의 식별자를 함께 노출한다. 구역 멤버십과 배치 제약이 이 ID를 참조하므로 도면을 저장해도 ID가 유지되어야 한다. 새로 그린 구조물은 {@code id}가 null이다.
 */
public record FabricDto(
        Long id,
        String name,
        BigDecimal startX,
        BigDecimal startY,
        BigDecimal endX,
        BigDecimal endY,
        BigDecimal rotation,
        Integer displayOrder) {}
