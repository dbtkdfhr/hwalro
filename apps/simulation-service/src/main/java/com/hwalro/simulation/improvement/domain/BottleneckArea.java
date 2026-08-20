package com.hwalro.simulation.improvement.domain;

import com.hwalro.simulation.improvement.geometry.RotatedRectangle;

/** 원본 시뮬레이션 결과에서 읽은 병목 영역과 지속 시간입니다. */
public record BottleneckArea(RotatedRectangle bounds, double startTimeSeconds, double endTimeSeconds) {}
