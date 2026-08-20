package com.hwalro.simulation.drawing.dto;

import java.time.LocalDateTime;

public record DrawingSummary(
        Long id, String title, String description, Long createdBy, LocalDateTime createdAt, int simulationCount) {}
