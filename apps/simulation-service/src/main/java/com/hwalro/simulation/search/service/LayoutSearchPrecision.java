package com.hwalro.simulation.search.service;

import com.hwalro.simulation.search.domain.ChangeOp;
import java.math.BigDecimal;
import java.math.RoundingMode;

final class LayoutSearchPrecision {
    private static final BigDecimal COORDINATE_TOLERANCE = new BigDecimal("0.000001");

    private LayoutSearchPrecision() {}

    static boolean same(BigDecimal left, BigDecimal right) {
        return left.subtract(right).abs().compareTo(COORDINATE_TOLERANCE) <= 0;
    }

    static boolean sameSpan(BigDecimal beforeStart, BigDecimal beforeEnd, BigDecimal afterStart, BigDecimal afterEnd) {
        BigDecimal beforeSpan = beforeEnd.subtract(beforeStart).abs();
        BigDecimal afterSpan = afterEnd.subtract(afterStart).abs();
        return same(beforeSpan, afterSpan);
    }

    static boolean sameTransform(ChangeOp.FabricTransform left, ChangeOp.FabricTransform right) {
        return same(left.startX(), right.startX())
                && same(left.startY(), right.startY())
                && same(left.endX(), right.endX())
                && same(left.endY(), right.endY())
                && same(left.rotation(), right.rotation());
    }

    static String key(BigDecimal value) {
        return value.setScale(4, RoundingMode.HALF_UP).toPlainString();
    }
}
