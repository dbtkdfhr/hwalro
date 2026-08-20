package com.hwalro.auth.dto;

import java.time.LocalDateTime;
import java.util.List;

public record SystemManagementResponse(List<UserSummary> users, List<RoleSummary> roles) {
    public record UserSummary(
            Long userId, String loginId, String name, boolean enabled, LocalDateTime createdAt, List<String> roles) {}

    public record RoleSummary(Long roleId, String roleName, String description) {}
}
