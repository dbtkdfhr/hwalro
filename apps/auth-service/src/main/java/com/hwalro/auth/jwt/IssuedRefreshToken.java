package com.hwalro.auth.jwt;

public record IssuedRefreshToken(String token, String jti) {}
