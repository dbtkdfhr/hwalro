package com.hwalro.auth.auth;

public record TokenPair(String accessToken, String refreshToken, String refreshJti) {}
