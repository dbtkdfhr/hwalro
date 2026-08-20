package com.hwalro.simulation.search.domain;

import java.util.List;

public record ChangeSet(int schemaVersion, String coordinateUnit, List<ChangeOp> ops) {
    public ChangeSet {
        ops = List.copyOf(ops);
    }
}
