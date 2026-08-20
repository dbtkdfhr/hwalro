package com.hwalro.simulation.improvement.domain;

/** 병목 전체와 가장 가까운 출구 경로에서 측정한 평균 통로 폭입니다. */
public record CorridorClearance(double bottleneckAverageWidth, double routeAverageWidth) {}
