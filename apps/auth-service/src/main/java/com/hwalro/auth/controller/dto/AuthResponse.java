package com.hwalro.auth.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record AuthResponse(
        @Schema(description = "액세스 토큰 (Authorization: Bearer 헤더로 사용)") String accessToken,
        @Schema(description = "사용자 정보") UserResponse user) {}
