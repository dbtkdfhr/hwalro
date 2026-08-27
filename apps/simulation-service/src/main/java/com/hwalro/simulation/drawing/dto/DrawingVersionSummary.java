package com.hwalro.simulation.drawing.dto;

import java.time.LocalDateTime;

public record DrawingVersionSummary(Long layoutVersionId, Integer version, String status, LocalDateTime createdAt) {}
