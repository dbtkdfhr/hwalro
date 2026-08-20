package com.hwalro.regulation.common.jwt;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Component;

@Component
public class JwtTokenProvider {

    public static final String TOKEN_TYPE_ACCESS = "access";
    public static final String CLAIM_TYPE = "type";
    public static final String CLAIM_USER_ID = "uid";
    public static final String CLAIM_USER_ROLES = "roles";

    private final JwtProperties properties;
    private final SecretKey signingKey;

    public JwtTokenProvider(JwtProperties properties) {
        this.properties = properties;
        byte[] secretBytes = properties.secret().getBytes(StandardCharsets.UTF_8);
        if (secretBytes.length < 32) {
            throw new IllegalStateException("JWT_SECRET 환경변수가 없거나 너무 짧습니다. 32바이트 이상의 시크릿 키를 설정해주세요.");
        }
        this.signingKey = Keys.hmacShaKeyFor(secretBytes);
    }

    public JwtUser extractUser(String token) {
        Claims claims = parseToken(token);
        requireType(claims, TOKEN_TYPE_ACCESS);
        Long userId = claims.get(CLAIM_USER_ID, Long.class);
        List<?> rawRoles = claims.get(CLAIM_USER_ROLES, List.class);
        Set<String> roles = new HashSet<>();
        if (rawRoles != null) {
            for (Object role : rawRoles) {
                roles.add(String.valueOf(role));
            }
        }
        return new JwtUser(userId, roles);
    }

    private Claims parseToken(String token) {
        try {
            return Jwts.parser()
                    .verifyWith(signingKey)
                    .requireIssuer(properties.issuer())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
        } catch (Exception e) {
            throw new InvalidTokenException("유효하지 않은 토큰입니다.", e);
        }
    }

    private void requireType(Claims claims, String expectedType) {
        String actualType = claims.get(CLAIM_TYPE, String.class);
        if (!expectedType.equals(actualType)) {
            throw new InvalidTokenException("토큰 타입이 일치하지 않습니다.");
        }
    }
}
