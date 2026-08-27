package com.hwalro.auth.controller;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.hwalro.auth.auth.AuthService;
import com.hwalro.auth.config.SecurityConfig;
import com.hwalro.auth.domain.User;
import com.hwalro.auth.jwt.JwtTokenProvider;
import com.hwalro.auth.security.CookieManager;
import com.hwalro.auth.security.JwtAuthenticationFilter;
import com.hwalro.auth.security.RestAuthenticationEntryPoint;
import com.hwalro.auth.service.UserLastActivityService;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, RestAuthenticationEntryPoint.class})
class EmployeeDirectoryAuthorizationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private CookieManager cookieManager;

    @MockitoBean
    private UserLastActivityService userLastActivityService;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    private static User employee() {
        User user = new User();
        user.setUserId(9L);
        user.setLoginId("employee");
        user.setName("일반 직원");
        return user;
    }

    @Test
    void allowsOperatorToReadTheEmployeeDirectory() throws Exception {
        when(authService.findActiveEmployees(null)).thenReturn(List.of(employee()));

        mockMvc.perform(get("/api/auth/employees").with(user("operator").authorities(() -> "ROLE_OPERATOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(9))
                .andExpect(jsonPath("$[0].name").value("일반 직원"))
                // 로그인 ID·역할은 계약에 없다. 최소 노출을 회귀로 못박는다.
                .andExpect(jsonPath("$[0].loginId").doesNotExist())
                .andExpect(jsonPath("$[0].roles").doesNotExist());
    }

    @Test
    void allowsSafetyReviewerAndAdministrator() throws Exception {
        when(authService.findActiveEmployees(null)).thenReturn(List.of());

        mockMvc.perform(get("/api/auth/employees").with(user("reviewer").authorities(() -> "ROLE_SAFETY_REVIEWER")))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/auth/employees").with(user("admin").authorities(() -> "ROLE_ADMIN")))
                .andExpect(status().isOk());
    }

    @Test
    void rejectsGeneralEmployeeReadingTheDirectory() throws Exception {
        mockMvc.perform(get("/api/auth/employees").with(user("employee").authorities(() -> "ROLE_GENERAL_EMPLOYEE")))
                .andExpect(status().isForbidden());

        verifyNoInteractions(authService);
    }

    @Test
    void passesRequestedIdsThroughForAssignmentValidation() throws Exception {
        when(authService.findActiveEmployees(List.of(9L))).thenReturn(List.of(employee()));

        mockMvc.perform(get("/api/auth/employees")
                        .param("ids", "9")
                        .with(user("operator").authorities(() -> "ROLE_OPERATOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(9));
    }
}
