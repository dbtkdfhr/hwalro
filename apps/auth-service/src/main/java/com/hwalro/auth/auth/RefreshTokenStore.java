package com.hwalro.auth.auth;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class RefreshTokenStore {

    private static final String KEY_PREFIX = "auth:refresh:";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public RefreshTokenStore(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    public void save(String jti, RefreshTokenData data, long ttlSeconds) {
        try {
            redisTemplate
                    .opsForValue()
                    .set(key(jti), objectMapper.writeValueAsString(data), Duration.ofSeconds(ttlSeconds));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("리프레시 토큰 직렬화에 실패했습니다.", e);
        }
    }

    public RefreshTokenData consume(String jti) {
        String raw = redisTemplate.opsForValue().getAndDelete(key(jti));
        if (raw == null) {
            return null;
        }
        try {
            return objectMapper.readValue(raw, RefreshTokenData.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("리프레시 토큰 역직렬화에 실패했습니다.", e);
        }
    }

    public void delete(String jti) {
        redisTemplate.delete(key(jti));
    }

    public void deleteAllByUserId(Long userId) {
        try (Cursor<String> cursor = redisTemplate.scan(
                ScanOptions.scanOptions().match(KEY_PREFIX + "*").count(100).build())) {
            while (cursor.hasNext()) {
                String key = cursor.next();
                RefreshTokenData data = findByKey(key);
                if (data != null && data.userId().equals(userId)) {
                    redisTemplate.delete(key);
                }
            }
        }
    }

    private RefreshTokenData findByKey(String key) {
        String raw = redisTemplate.opsForValue().get(key);
        if (raw == null) {
            return null;
        }
        try {
            return objectMapper.readValue(raw, RefreshTokenData.class);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    private String key(String jti) {
        return KEY_PREFIX + jti;
    }
}
