package com.hwalro.auth.security;

import java.util.List;

public record AuthenticatedUser(Long userId, String loginId, List<String> roles) {}
