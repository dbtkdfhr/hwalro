package com.hwalro.auth.auth;

public record RefreshTokenData(Long userId, String loginId, boolean rememberMe) {}
