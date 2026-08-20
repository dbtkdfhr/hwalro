package com.hwalro.auth.controller.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @Schema(description = "로그인 아이디", example = "reviewer") @NotBlank(message = "아이디를 입력해주세요.") String loginId,
        @Schema(description = "비밀번호", example = "reviewer1234") @NotBlank(message = "비밀번호를 입력해주세요.") String password,
        @Schema(description = "로그인 상태 유지 여부 (true: 리프레시 토큰 7일 유지, false: 세션 쿠키)", example = "true")
                boolean rememberMe) {}
