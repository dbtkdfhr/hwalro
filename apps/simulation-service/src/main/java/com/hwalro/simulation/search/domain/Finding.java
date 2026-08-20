package com.hwalro.simulation.search.domain;

public record Finding(FindingType type, double severity, Rectangle region, Evidence evidence, String description) {}
