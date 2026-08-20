package com.hwalro.simulation.improvement.domain;

/** 이후 점수 계산에서 해석할 원본 히트맵 JSON 청크입니다. */
public record HeatmapChunk(int chunkSequence, String densityData) {}
