package com.hwalro.simulation.improvement.domain;

import com.hwalro.simulation.improvement.geometry.RotatedRectangle;

/** 개선안에서 한 fabric에 적용할 위치 또는 회전 변경입니다. */
public record FabricChange(long fabricId, RotatedRectangle before, RotatedRectangle after) {

    /** 원래 위치에서 이동한 실제 거리입니다. 회전만 한 경우에는 0입니다. */
    public double moveDistance() {
        double deltaX = after.center().x() - before.center().x();
        double deltaY = after.center().y() - before.center().y();
        return Math.hypot(deltaX, deltaY);
    }
}
