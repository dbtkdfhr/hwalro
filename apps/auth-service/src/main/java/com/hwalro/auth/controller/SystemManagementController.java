package com.hwalro.auth.controller;

import com.hwalro.auth.dto.CreateUserRequest;
import com.hwalro.auth.dto.SystemManagementResponse;
import com.hwalro.auth.dto.SystemManagementResponse.UserSummary;
import com.hwalro.auth.dto.UpdateUserEnabledRequest;
import com.hwalro.auth.service.SystemManagementService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/system-management")
@PreAuthorize("hasAuthority('ROLE_ADMIN')")
public class SystemManagementController {
    private final SystemManagementService service;

    public SystemManagementController(SystemManagementService service) {
        this.service = service;
    }

    @GetMapping
    public SystemManagementResponse getSystemManagementData() {
        return service.getSystemManagementData();
    }

    @PostMapping("/users")
    @ResponseStatus(HttpStatus.CREATED)
    public UserSummary createUser(@Valid @RequestBody CreateUserRequest request) {
        return service.createUser(request);
    }

    @PatchMapping("/users/{userId}/enabled")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void updateUserEnabled(
            @Positive @PathVariable Long userId, @Valid @RequestBody UpdateUserEnabledRequest request) {
        service.updateUserEnabled(userId, request.enabled());
    }
}
