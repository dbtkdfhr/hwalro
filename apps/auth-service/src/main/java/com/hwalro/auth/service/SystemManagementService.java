package com.hwalro.auth.service;

import com.hwalro.auth.dto.CreateUserRequest;
import com.hwalro.auth.dto.SystemManagementResponse;
import com.hwalro.auth.dto.SystemManagementResponse.RoleSummary;
import com.hwalro.auth.dto.SystemManagementResponse.UserSummary;
import com.hwalro.auth.mapper.SystemManagementMapper;
import com.hwalro.auth.mapper.SystemManagementMapper.NewUserRow;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class SystemManagementService {
    private final SystemManagementMapper mapper;
    private final PasswordEncoder passwordEncoder;

    public SystemManagementService(SystemManagementMapper mapper, PasswordEncoder passwordEncoder) {
        this.mapper = mapper;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public SystemManagementResponse getSystemManagementData() {
        Map<Long, List<String>> rolesByUser = new HashMap<>();
        mapper.findAllUserRoles().forEach(row -> rolesByUser
                .computeIfAbsent(row.userId(), ignored -> new ArrayList<>())
                .add(row.roleName()));

        List<UserSummary> users = mapper.findAllUsers().stream()
                .map(row -> new UserSummary(
                        row.userId(),
                        row.loginId(),
                        row.name(),
                        row.enabled(),
                        row.createdAt(),
                        List.copyOf(rolesByUser.getOrDefault(row.userId(), List.of()))))
                .toList();

        List<RoleSummary> roles = mapper.findAllRoles().stream()
                .map(row -> new RoleSummary(row.roleId(), row.roleName(), row.description()))
                .toList();

        return new SystemManagementResponse(users, roles);
    }

    @Transactional
    public UserSummary createUser(CreateUserRequest request) {
        String loginId = request.loginId().trim();
        String name = request.name().trim();
        List<Long> roleIds = request.roleIds().stream().distinct().toList();

        if (mapper.existsByLoginId(loginId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 사용 중인 로그인 ID입니다.");
        }

        Map<Long, String> roleNames = new HashMap<>();
        mapper.findAllRoles().forEach(role -> roleNames.put(role.roleId(), role.roleName()));
        if (!roleNames.keySet().containsAll(roleIds)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "존재하지 않는 역할이 포함되어 있습니다.");
        }

        NewUserRow user = new NewUserRow(loginId, passwordEncoder.encode(request.password()), name);
        try {
            mapper.insertUser(user);
        } catch (DuplicateKeyException exception) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "이미 사용 중인 로그인 ID입니다.", exception);
        }
        mapper.insertUserRoles(user.getUserId(), roleIds);

        var createdUser = mapper.findUserById(user.getUserId());
        return new UserSummary(
                createdUser.userId(),
                createdUser.loginId(),
                createdUser.name(),
                createdUser.enabled(),
                createdUser.createdAt(),
                roleIds.stream().map(roleNames::get).toList());
    }

    @Transactional
    public void updateUserEnabled(Long userId, boolean enabled) {
        if (mapper.updateUserEnabled(userId, enabled) == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "사용자를 찾을 수 없습니다.");
        }
    }
}
