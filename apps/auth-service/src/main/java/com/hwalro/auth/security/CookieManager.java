package com.hwalro.auth.security;

import com.hwalro.auth.jwt.JwtTokenProvider;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

@Component
public class CookieManager {

    public static final String REFRESH_TOKEN_COOKIE = "REFRESH_TOKEN";

    private static final String COOKIE_PATH_AUTH = "/api/auth";

    private final JwtTokenProvider jwtTokenProvider;
    private final String sameSite;
    private final boolean secure;

    public CookieManager(
            JwtTokenProvider jwtTokenProvider,
            @Value("${cookie.same-site:Lax}") String sameSite,
            @Value("${cookie.secure:true}") boolean secure) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.sameSite = sameSite;
        this.secure = secure;
    }

    public void setRefreshTokenCookie(HttpServletResponse response, String refreshToken, boolean rememberMe) {
        ResponseCookie.ResponseCookieBuilder cookie = ResponseCookie.from(REFRESH_TOKEN_COOKIE, refreshToken)
                .httpOnly(true)
                .secure(secure)
                .path(COOKIE_PATH_AUTH)
                .sameSite(sameSite);
        if (rememberMe) {
            cookie.maxAge(jwtTokenProvider.getRefreshTokenTtlSeconds());
        }
        response.addHeader("Set-Cookie", cookie.build().toString());
    }

    public void clearTokens(HttpServletResponse response) {
        response.addHeader(
                "Set-Cookie",
                ResponseCookie.from(REFRESH_TOKEN_COOKIE, "")
                        .httpOnly(true)
                        .secure(secure)
                        .path(COOKIE_PATH_AUTH)
                        .maxAge(0)
                        .sameSite(sameSite)
                        .build()
                        .toString());
    }

    public String getRefreshToken(HttpServletRequest request) {
        return getCookieValue(request, REFRESH_TOKEN_COOKIE);
    }

    private String getCookieValue(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (name.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }
}
