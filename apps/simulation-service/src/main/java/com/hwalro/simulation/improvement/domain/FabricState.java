package com.hwalro.simulation.improvement.domain;

import com.hwalro.simulation.improvement.geometry.RotatedRectangle;

/** 개선안 계산에 필요한 이동 가능 시설물의 현재 도형입니다. */
public record FabricState(long id, RotatedRectangle bounds) {}
