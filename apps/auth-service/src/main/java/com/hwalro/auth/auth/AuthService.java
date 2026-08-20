package com.hwalro.auth.auth;

import com.hwalro.auth.domain.User;
import com.hwalro.auth.jwt.InvalidTokenException;
import com.hwalro.auth.jwt.IssuedRefreshToken;
import com.hwalro.auth.jwt.JwtTokenProvider;
import com.hwalro.auth.mapper.UserMapper;
import io.jsonwebtoken.Claims;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenStore refreshTokenStore;

    public AuthService(
            UserMapper userMapper,
            PasswordEncoder passwordEncoder,
            JwtTokenProvider jwtTokenProvider,
            RefreshTokenStore refreshTokenStore) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtTokenProvider = jwtTokenProvider;
        this.refreshTokenStore = refreshTokenStore;
    }

    public AuthResult login(String loginId, String rawPassword, boolean rememberMe) {
        User user = userMapper.findByLoginId(loginId);
        if (user == null || !passwordEncoder.matches(rawPassword, user.getPassword())) {
            throw new BadCredentialsException("아이디 또는 비밀번호가 일치하지 않습니다.");
        }
        if (!user.isEnabled()) {
            throw new DisabledException("비활성화된 계정입니다.");
        }
        loadRoles(user);
        return new AuthResult(issueTokenPair(user, rememberMe), user, rememberMe);
    }

    public AuthResult refresh(String refreshToken) {
        Claims claims = jwtTokenProvider.parseToken(refreshToken);
        jwtTokenProvider.requireType(claims, JwtTokenProvider.TOKEN_TYPE_REFRESH);

        Long userId = claims.get(JwtTokenProvider.CLAIM_USER_ID, Long.class);
        String jti = claims.getId();

        RefreshTokenData stored = refreshTokenStore.consume(jti);
        if (stored == null) {
            refreshTokenStore.deleteAllByUserId(userId);
            throw new InvalidTokenException("이미 사용된 리프레시 토큰입니다. 모든 세션이 해제됩니다.");
        }
        if (!stored.userId().equals(userId)) {
            throw new InvalidTokenException("리프레시 토큰의 사용자 정보가 일치하지 않습니다.");
        }

        User user = userMapper.findByLoginId(claims.getSubject());
        if (user == null) {
            throw new BadCredentialsException("존재하지 않는 사용자입니다.");
        }
        loadRoles(user);
        return new AuthResult(issueTokenPair(user, stored.rememberMe()), user, stored.rememberMe());
    }

    public void logout(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return;
        }
        try {
            Claims claims = jwtTokenProvider.parseToken(refreshToken);
            jwtTokenProvider.requireType(claims, JwtTokenProvider.TOKEN_TYPE_REFRESH);
            refreshTokenStore.delete(claims.getId());
        } catch (InvalidTokenException e) {
            log.debug("이미 무효화된 리프레시 토큰으로 로그아웃 요청이 들어왔습니다.", e);
        }
    }

    public User findUserWithRoles(String loginId) {
        User user = userMapper.findByLoginId(loginId);
        if (user != null) {
            loadRoles(user);
        }
        return user;
    }

    public List<User> findUsersByIds(List<Long> userIds) {
        return userIds.isEmpty() ? List.of() : userMapper.findByUserIds(userIds);
    }

    private void loadRoles(User user) {
        user.setRoles(userMapper.findRoleNamesByLoginId(user.getLoginId()));
    }

    private TokenPair issueTokenPair(User user, boolean rememberMe) {
        String accessToken = jwtTokenProvider.createAccessToken(user);
        IssuedRefreshToken refreshToken = jwtTokenProvider.createRefreshToken(user);
        refreshTokenStore.save(
                refreshToken.jti(),
                new RefreshTokenData(user.getUserId(), user.getLoginId(), rememberMe),
                jwtTokenProvider.getRefreshTokenTtlSeconds());
        return new TokenPair(accessToken, refreshToken.token(), refreshToken.jti());
    }
}
