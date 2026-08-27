package com.hwalro.auth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.hwalro.auth.dto.CreateUserRequest;
import com.hwalro.auth.dto.SystemManagementResponse;
import com.hwalro.auth.mapper.SystemManagementMapper;
import com.hwalro.auth.mapper.SystemManagementMapper.NewUserRow;
import com.hwalro.auth.mapper.SystemManagementMapper.RoleRow;
import com.hwalro.auth.mapper.SystemManagementMapper.UserRoleRow;
import com.hwalro.auth.mapper.SystemManagementMapper.UserRow;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class SystemManagementServiceTest {
    @Mock
    private SystemManagementMapper mapper;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private SystemManagementService service;

    @Test
    void returnsUsersWithAssignedRolesAndAvailableRoles() {
        LocalDateTime createdAt = LocalDateTime.of(2026, 8, 3, 14, 28);
        when(mapper.findAllUsers()).thenReturn(List.of(new UserRow(1L, "safety", "김안전", true, createdAt)));
        when(mapper.findAllUserRoles())
                .thenReturn(List.of(new UserRoleRow(1L, "SAFETY_REVIEWER"), new UserRoleRow(1L, "ADMIN")));
        when(mapper.findAllRoles()).thenReturn(List.of(new RoleRow(1L, "SAFETY_REVIEWER", "안전 검토자")));

        SystemManagementResponse response = service.getSystemManagementData();

        assertThat(response.users()).hasSize(1);
        assertThat(response.users().get(0).roles()).containsExactly("SAFETY_REVIEWER", "ADMIN");
        assertThat(response.roles())
                .containsExactly(new SystemManagementResponse.RoleSummary(1L, "SAFETY_REVIEWER", "안전 검토자"));
    }

    @Test
    void createsUserWithEncodedPasswordAndAssignedRoles() {
        when(mapper.existsByLoginId("operator")).thenReturn(false);
        when(mapper.findAllRoles())
                .thenReturn(
                        List.of(new RoleRow(1L, "OPERATOR", "운영 담당자"), new RoleRow(2L, "SAFETY_REVIEWER", "안전 검토자")));
        when(passwordEncoder.encode("password123")).thenReturn("encoded-password");
        when(mapper.insertUser(any(NewUserRow.class))).thenAnswer(invocation -> {
            invocation.<NewUserRow>getArgument(0).setUserId(10L);
            return 1;
        });
        LocalDateTime createdAt = LocalDateTime.of(2026, 8, 3, 16, 0);
        when(mapper.findUserById(10L)).thenReturn(new UserRow(10L, "operator", "운영 담당자", true, createdAt));

        SystemManagementResponse.UserSummary created =
                service.createUser(new CreateUserRequest(" operator ", "password123", " 운영 담당자 ", List.of(1L, 1L)));

        assertThat(created.userId()).isEqualTo(10L);
        assertThat(created.loginId()).isEqualTo("operator");
        assertThat(created.roles()).containsExactly("OPERATOR");
        assertThat(created.createdAt()).isEqualTo(createdAt);
        verify(mapper).insertUserRoles(eq(10L), eq(List.of(1L)));
    }

    @Test
    void createsUserWithGeneralEmployeeRole() {
        when(mapper.existsByLoginId("employee")).thenReturn(false);
        when(mapper.findAllRoles()).thenReturn(List.of(new RoleRow(4L, "GENERAL_EMPLOYEE", "일반 직원")));
        when(passwordEncoder.encode("password123")).thenReturn("encoded-password");
        when(mapper.insertUser(any(NewUserRow.class))).thenAnswer(invocation -> {
            invocation.<NewUserRow>getArgument(0).setUserId(11L);
            return 1;
        });
        LocalDateTime createdAt = LocalDateTime.of(2026, 8, 22, 9, 0);
        when(mapper.findUserById(11L)).thenReturn(new UserRow(11L, "employee", "일반 직원", true, createdAt));

        SystemManagementResponse.UserSummary created =
                service.createUser(new CreateUserRequest("employee", "password123", "일반 직원", List.of(4L)));

        assertThat(created.roles()).containsExactly("GENERAL_EMPLOYEE");
        verify(mapper).insertUserRoles(eq(11L), eq(List.of(4L)));
    }

    @Test
    void updatesUserEnabledState() {
        when(mapper.updateUserEnabled(1L, false)).thenReturn(1);

        service.updateUserEnabled(1L, false);

        verify(mapper).updateUserEnabled(1L, false);
    }

    @Test
    void rejectsEnabledStateUpdateWhenUserDoesNotExist() {
        when(mapper.updateUserEnabled(99L, false)).thenReturn(0);

        assertThatThrownBy(() -> service.updateUserEnabled(99L, false))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("404 NOT_FOUND");
    }
}
