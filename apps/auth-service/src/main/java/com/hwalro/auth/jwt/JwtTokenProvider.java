package com.hwalro.auth.jwt;

import com.hwalro.auth.domain.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.UUID;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Component;

@Component
public class JwtTokenProvider {

    public static final String TOKEN_TYPE_ACCESS = "access";
    public static final String TOKEN_TYPE_REFRESH = "refresh";
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

    public String createAccessToken(User user) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(properties.accessTokenExpirationMinutes(), ChronoUnit.MINUTES);
        return Jwts.builder()
                .issuer(properties.issuer())
                .subject(user.getLoginId())
                .claim(CLAIM_TYPE, TOKEN_TYPE_ACCESS)
                .claim(CLAIM_USER_ID, user.getUserId())
                .claim(CLAIM_USER_ROLES, user.getRoles())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .signWith(signingKey)
                .compact();
    }

    public IssuedRefreshToken createRefreshToken(User user) {
        String jti = UUID.randomUUID().toString();
        Instant now = Instant.now();
        Instant expiresAt = now.plus(properties.refreshTokenExpirationDays(), ChronoUnit.DAYS);
        String token = Jwts.builder()
                .issuer(properties.issuer())
                .subject(user.getLoginId())
                .claim(CLAIM_TYPE, TOKEN_TYPE_REFRESH)
                .claim(CLAIM_USER_ID, user.getUserId())
                .claim(CLAIM_USER_ROLES, user.getRoles())
                .id(jti)
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiresAt))
                .signWith(signingKey)
                .compact();
        return new IssuedRefreshToken(token, jti);
    }

    public Claims parseToken(String token) {
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

    public void requireType(Claims claims, String expectedType) {
        String actualType = claims.get(CLAIM_TYPE, String.class);
        if (!expectedType.equals(actualType)) {
            throw new InvalidTokenException("토큰 타입이 일치하지 않습니다.");
        }
    }

    public long getAccessTokenTtlSeconds() {
        return properties.accessTokenExpirationMinutes() * 60;
    }

    public long getRefreshTokenTtlSeconds() {
        return properties.refreshTokenExpirationDays() * 24 * 60 * 60;
    }
}
