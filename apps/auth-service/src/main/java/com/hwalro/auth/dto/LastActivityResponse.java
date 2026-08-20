package com.hwalro.auth.dto;

import java.time.LocalDateTime;

public record LastActivityResponse(String activityType, Long resourceId, LocalDateTime occurredAt) {}
