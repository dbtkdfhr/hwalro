package com.hwalro.simulation.common.jwt;

import java.util.Set;

public record JwtUser(Long userId, Set<String> roles) {}
