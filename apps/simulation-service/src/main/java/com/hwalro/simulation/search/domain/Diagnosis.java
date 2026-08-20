package com.hwalro.simulation.search.domain;

import java.util.List;

public record Diagnosis(List<Finding> findings) {
    public Diagnosis {
        findings = List.copyOf(findings);
    }
}
