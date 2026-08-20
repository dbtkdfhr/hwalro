package com.hwalro.simulation.drawing.dto;

import java.math.BigDecimal;

public record ExitDto(String name, BigDecimal startX, BigDecimal startY, BigDecimal endX, BigDecimal endY) {}
