package com.hwalro.simulation.drawing.dto;

import java.math.BigDecimal;

public record OutsideWallDto(String name, BigDecimal startX, BigDecimal startY, BigDecimal endX, BigDecimal endY) {}
