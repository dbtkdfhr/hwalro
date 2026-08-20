package com.hwalro.simulation.search.domain;

public record MetricDelta(String metricType, double baseline, double measured, double difference, double ratio) {}
