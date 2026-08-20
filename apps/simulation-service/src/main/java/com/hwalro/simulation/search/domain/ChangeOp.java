package com.hwalro.simulation.search.domain;

import java.math.BigDecimal;

public record ChangeOp(String type, Long fabricId, FabricTransform before, FabricTransform after) {
    public record FabricTransform(
            BigDecimal startX, BigDecimal startY, BigDecimal endX, BigDecimal endY, BigDecimal rotation) {}
}
