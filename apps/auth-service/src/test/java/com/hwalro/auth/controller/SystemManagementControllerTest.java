package com.hwalro.auth.controller;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hwalro.auth.config.SecurityConfig;
import com.hwalro.auth.dto.SystemManagementResponse;
import com.hwalro.auth.jwt.JwtTokenProvider;
import com.hwalro.auth.security.JwtAuthenticationFilter;
import com.hwalro.auth.security.RestAuthenticationEntryPoint;
import com.hwalro.auth.service.SystemManagementService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(SystemManagementController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, RestAuthenticationEntryPoint.class})
class SystemManagementControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private SystemManagementService service;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @Test
    void allowsAdministratorToReadSystemManagementData() throws Exception {
        when(service.getSystemManagementData()).thenReturn(new SystemManagementResponse(List.of(), List.of()));

        mockMvc.perform(get("/api/admin/system-management").with(user("admin").authorities(() -> "ROLE_ADMIN")))
                .andExpect(status().isOk());
    }

    @Test
    void rejectsNonAdministrator() throws Exception {
        mockMvc.perform(get("/api/admin/system-management")
                        .with(user("operator").authorities(() -> "ROLE_OPERATOR")))
                .andExpect(status().isForbidden());

        verifyNoInteractions(service);
    }

    @Test
    void rejectsNonPositiveUserId() throws Exception {
        mockMvc.perform(patch("/api/admin/system-management/users/-1/enabled")
                        .with(user("admin").authorities(() -> "ROLE_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"enabled\":true}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(service);
    }

    @Test
    void rejectsPasswordLongerThanBcryptUtf8ByteLimit() throws Exception {
        String requestBody = objectMapper.writeValueAsString(Map.of(
                "loginId", "korean-password", "password", "가".repeat(25), "name", "테스트 사용자", "roleIds", List.of(1)));

        mockMvc.perform(post("/api/admin/system-management/users")
                        .with(user("admin").authorities(() -> "ROLE_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("비밀번호는 UTF-8 기준 72바이트 이하여야 합니다."));

        verifyNoInteractions(service);
    }
}
