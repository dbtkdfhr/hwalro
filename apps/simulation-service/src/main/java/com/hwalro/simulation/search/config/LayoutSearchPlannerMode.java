package com.hwalro.simulation.search.config;

public enum LayoutSearchPlannerMode {
    IDEAL_ROUTE_DOCKING,
    CONFIGURATION_SPACE_SHAPE_GRID,
    CONFIGURATION_SPACE_SHAPE_PDE;

    public String plannerVersion() {
        return switch (this) {
            case IDEAL_ROUTE_DOCKING -> "IDEAL_ROUTE_DOCKING_V2";
            case CONFIGURATION_SPACE_SHAPE_GRID -> "CONFIGURATION_SPACE_SHAPE_V3_GRID";
            case CONFIGURATION_SPACE_SHAPE_PDE -> "CONFIGURATION_SPACE_SHAPE_V3_PDE";
        };
    }
}
