package com.hwalro.simulation.search.domain;

public enum SearchStatus {
    PENDING,
    DIAGNOSING,
    GENERATING,
    VERIFYING,
    COMPLETED,
    NO_IMPROVEMENT,
    FAILED,
    CANCELLED
}
