package com.hwalro.simulation.common.jwt;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Arrays;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class JwtAuthInterceptor implements HandlerInterceptor {

    public static final String REQUEST_ATTRIBUTE_USER = "jwtUser";

    private final JwtTokenProvider jwtTokenProvider;

    public JwtAuthInterceptor(JwtTokenProvider jwtTokenProvider) {
        this.jwtTokenProvider = jwtTokenProvider;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String authorization = request.getHeader("Authorization");
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            throw new InvalidTokenException("인증 토큰이 없습니다.");
        }
        JwtUser user = jwtTokenProvider.extractUser(authorization.substring("Bearer ".length()));
        request.setAttribute(REQUEST_ATTRIBUTE_USER, user);
        requireRoles(handler, user.roles());
        return true;
    }

    private void requireRoles(Object handler, Set<String> userRoles) {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return;
        }
        RequireRole requireRole = handlerMethod.getMethodAnnotation(RequireRole.class);
        if (requireRole == null) {
            requireRole = handlerMethod.getBeanType().getAnnotation(RequireRole.class);
        }
        if (requireRole != null && Arrays.stream(requireRole.value()).noneMatch(userRoles::contains)) {
            throw new ForbiddenException("접근 권한이 없습니다.");
        }
    }
}
