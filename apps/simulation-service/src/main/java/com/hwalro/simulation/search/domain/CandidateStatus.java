package com.hwalro.simulation.search.domain;

public enum CandidateStatus {
    GENERATED,
    REJECTED_CONSTRAINT,
    QUEUED,
    RUNNING,
    EVALUATED,
    NOT_IMPROVED,
    FAILED
}
