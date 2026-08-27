package com.hwalro.auth.controller;

import com.hwalro.auth.auth.AuthResult;
import com.hwalro.auth.auth.AuthService;
import com.hwalro.auth.controller.dto.AuthResponse;
import com.hwalro.auth.controller.dto.EmployeeSummaryResponse;
import com.hwalro.auth.controller.dto.LoginRequest;
import com.hwalro.auth.controller.dto.UserResponse;
import com.hwalro.auth.controller.dto.UserSummaryResponse;
import com.hwalro.auth.domain.User;
import com.hwalro.auth.dto.LastActivityResponse;
import com.hwalro.auth.dto.UpdateLastActivityRequest;
import com.hwalro.auth.jwt.InvalidTokenException;
import com.hwalro.auth.security.AuthenticatedUser;
import com.hwalro.auth.security.CookieManager;
import com.hwalro.auth.service.UserLastActivityService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Auth", description = "인증 API (액세스 토큰은 응답 바디 + Authorization 헤더, 리프레시 토큰은 HttpOnly 쿠키)")
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final CookieManager cookieManager;
    private final UserLastActivityService userLastActivityService;

    public AuthController(
            AuthService authService, CookieManager cookieManager, UserLastActivityService userLastActivityService) {
        this.authService = authService;
        this.cookieManager = cookieManager;
        this.userLastActivityService = userLastActivityService;
    }

    @Operation(summary = "로그인", description = "아이디/비밀번호로 로그인한다. 응답 바디로 액세스 토큰을 반환하고, 리프레시 토큰은 HttpOnly 쿠키로 설정한다.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "로그인 성공"),
        @ApiResponse(responseCode = "401", description = "아이디 또는 비밀번호 불일치")
    })
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request, HttpServletResponse response) {
        AuthResult result = authService.login(request.loginId(), request.password(), request.rememberMe());
        cookieManager.setRefreshTokenCookie(response, result.tokenPair().refreshToken(), result.rememberMe());
        return ResponseEntity.ok(new AuthResponse(result.tokenPair().accessToken(), toUserResponse(result.user())));
    }

    @Operation(
            summary = "토큰 재발급",
            description =
                    "REFRESH_TOKEN 쿠키로 새 액세스 토큰을 발급한다. 응답 바디로 액세스 토큰을 반환하고, RTR(리프레시 토큰 회전)을 적용해 기존 리프레시 토큰을 무효화한다.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "재발급 성공"),
        @ApiResponse(responseCode = "401", description = "리프레시 토큰이 없거나 유효하지 않음")
    })
    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(HttpServletRequest request, HttpServletResponse response) {
        String refreshToken = cookieManager.getRefreshToken(request);
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new InvalidTokenException("리프레시 토큰이 존재하지 않습니다.");
        }
        AuthResult result = authService.refresh(refreshToken);
        cookieManager.setRefreshTokenCookie(response, result.tokenPair().refreshToken(), result.rememberMe());
        return ResponseEntity.ok(new AuthResponse(result.tokenPair().accessToken(), toUserResponse(result.user())));
    }

    @Operation(summary = "로그아웃", description = "Redis에서 리프레시 토큰을 폐기하고 쿠키를 삭제한다.")
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        authService.logout(cookieManager.getRefreshToken(request));
        cookieManager.clearTokens(response);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "내 정보 조회", description = "액세스 토큰(Authorization 헤더)으로 현재 로그인한 사용자 정보를 반환한다.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "성공"),
        @ApiResponse(responseCode = "401", description = "인증되지 않은 요청")
    })
    @GetMapping("/me")
    public ResponseEntity<UserResponse> me(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser principal) {
        User user = authService.findUserWithRoles(principal.loginId());
        return ResponseEntity.ok(toUserResponse(user));
    }

    @Operation(summary = "마지막 작업 조회", description = "현재 로그인한 사용자가 마지막으로 머문 작업 위치를 반환한다. 기록이 없으면 204를 반환한다.")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "성공"),
        @ApiResponse(responseCode = "204", description = "기록된 마지막 작업 없음"),
        @ApiResponse(responseCode = "401", description = "인증되지 않은 요청")
    })
    @GetMapping("/me/last-activity")
    public ResponseEntity<LastActivityResponse> lastActivity(
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser principal) {
        return userLastActivityService
                .findLastActivity(principal.userId())
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.noContent().build());
    }

    @Operation(summary = "마지막 작업 기록", description = "현재 로그인한 사용자의 마지막 작업 위치를 갱신한다. 사용자 식별은 액세스 토큰만 사용한다.")
    @ApiResponses({
        @ApiResponse(responseCode = "204", description = "기록 성공"),
        @ApiResponse(responseCode = "400", description = "지원하지 않는 작업 유형"),
        @ApiResponse(responseCode = "401", description = "인증되지 않은 요청")
    })
    @PutMapping("/me/last-activity")
    public ResponseEntity<Void> updateLastActivity(
            @Valid @RequestBody UpdateLastActivityRequest request,
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser principal) {
        userLastActivityService.recordLastActivity(principal.userId(), request.activityType(), request.resourceId());
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "사용자 표시 이름 일괄 조회", description = "관리자와 안전 검토자는 여러 사용자를, 운영 담당자는 본인만 조회할 수 있습니다.")
    @GetMapping("/users")
    public List<UserSummaryResponse> users(
            @RequestParam List<Long> ids,
            @Parameter(hidden = true) @AuthenticationPrincipal AuthenticatedUser principal) {
        boolean canReadAllUsers = principal.roles().contains("SAFETY_REVIEWER")
                || principal.roles().contains("ADMIN");
        boolean requestsOnlySelf = ids.stream().allMatch(id -> id.equals(principal.userId()));
        if (!canReadAllUsers && !requestsOnlySelf) {
            throw new AccessDeniedException("다른 사용자의 정보를 조회할 권한이 없습니다.");
        }
        return authService.findUsersByIds(ids).stream()
                .map(user -> new UserSummaryResponse(user.getUserId(), user.getName()))
                .toList();
    }

    @Operation(
            summary = "구역 배정 대상 직원 조회",
            description = "활성 일반 직원 목록을 반환한다. ids를 주면 그중 조건을 만족하는 사용자만 반환하므로 배정 대상 검증에도 쓸 수 있다."
                    + " 관리자·안전 검토자·운영 담당자만 호출할 수 있다.")
    @GetMapping("/employees")
    public List<EmployeeSummaryResponse> employees(@RequestParam(required = false) List<Long> ids) {
        return authService.findActiveEmployees(ids).stream()
                .map(user -> new EmployeeSummaryResponse(user.getUserId(), user.getName()))
                .toList();
    }

    private UserResponse toUserResponse(User user) {
        return new UserResponse(user.getUserId(), user.getLoginId(), user.getName(), user.getRoles());
    }
}
