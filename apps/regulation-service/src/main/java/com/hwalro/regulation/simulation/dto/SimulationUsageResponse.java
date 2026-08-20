package com.hwalro.regulation.simulation.dto;

public record SimulationUsageResponse(boolean usedInRisks, boolean usedInReports) {
    public boolean isInUse() {
        return usedInRisks || usedInReports;
    }
}
