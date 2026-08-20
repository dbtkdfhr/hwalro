package com.hwalro.auth.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record UpdateLastActivityRequest(@NotBlank String activityType, @NotNull @Positive Long resourceId) {}
