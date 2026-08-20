package com.hwalro.regulation.risk.dto;

import java.util.List;

public record RiskUpdateRequest(
        String title, String description, String severity, String status, List<AttachedLawRef> attachedLaws) {}
