package com.hwalro.simulation.drawing.dto;

import java.math.BigDecimal;

/** 구역의 기본·대체 비상구가 이 ID를 참조한다. 새로 그린 비상구는 {@code id}가 null이다. */
public record ExitDto(Long id, String name, BigDecimal startX, BigDecimal startY, BigDecimal endX, BigDecimal endY) {}
