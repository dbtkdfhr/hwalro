package com.hwalro.auth.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record UserSummaryResponse(
        @Schema(description = "사용자 ID", example = "1") Long id,
        @Schema(description = "표시 이름", example = "김안전") String name) {}
