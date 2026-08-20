package com.hwalro.auth.auth;

import com.hwalro.auth.domain.User;

public record AuthResult(TokenPair tokenPair, User user, boolean rememberMe) {}
