package com.fishmatrix.user.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
public class SessionService {

    public record SessionUser(long userId, String role) {
    }

    private static final String KEY_PREFIX = "fm:user:session:";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final StringRedisTemplate redis;
    private final Duration ttl;

    public SessionService(StringRedisTemplate redis,
            @Value("${fm.user.session-ttl-hours:168}") long ttlHours) {
        this.redis = redis;
        this.ttl = Duration.ofHours(Math.max(1, ttlHours));
    }

    public String create(long userId, String role) {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        redis.opsForValue().set(key(token), userId + ":" + role, ttl);
        return token;
    }

    public Optional<SessionUser> resolve(String token) {
        if (token == null || token.isBlank()) return Optional.empty();
        String value = redis.opsForValue().get(key(token));
        if (value == null) return Optional.empty();

        int separator = value.indexOf(':');
        if (separator <= 0) {
            redis.delete(key(token));
            return Optional.empty();
        }
        try {
            long userId = Long.parseLong(value.substring(0, separator));
            String role = value.substring(separator + 1);
            redis.expire(key(token), ttl);
            return Optional.of(new SessionUser(userId, role));
        } catch (NumberFormatException exception) {
            redis.delete(key(token));
            return Optional.empty();
        }
    }

    public void revoke(String token) {
        if (token != null && !token.isBlank()) {
            redis.delete(key(token));
        }
    }

    public static String bearerToken(String authorization) {
        if (authorization == null) return null;
        String value = authorization.trim();
        if (!value.regionMatches(true, 0, "Bearer ", 0, 7)) return null;
        String token = value.substring(7).trim();
        return token.isEmpty() ? null : token;
    }

    private static String key(String token) {
        return KEY_PREFIX + sha256(token);
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 不可用", exception);
        }
    }
}
