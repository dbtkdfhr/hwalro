package com.hwalro.simulation.drawing.dto;

import java.math.BigDecimal;

public record FabricDto(
        String name, BigDecimal startX, BigDecimal startY, BigDecimal endX, BigDecimal endY, BigDecimal rotation) {}
