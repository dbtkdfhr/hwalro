package com.hwalro.regulation.common.jwt;

import java.util.Set;

public record JwtUser(Long userId, Set<String> roles) {}
