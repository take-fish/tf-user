package com.fishmatrix.user.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
class SessionServiceTests {

    @Mock
    private StringRedisTemplate redis;

    @Mock
    private ValueOperations<String, String> valueOps;

    private SessionService sessions;

    @BeforeEach
    void setUp() {
        lenient().when(redis.opsForValue()).thenReturn(valueOps);
        sessions = new SessionService(redis, 24);
    }

    @Test
    void createsHashedSessionKey() {
        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);

        String token = sessions.create(7, "USER");

        verify(valueOps).set(key.capture(), eq("7:USER"), eq(Duration.ofHours(24)));
        assertTrue(key.getValue().startsWith("fm:user:session:"));
        assertFalse(key.getValue().contains(token), "Redis key 不应保存明文 token");
        assertTrue(token.length() >= 40);
    }

    @Test
    void resolvesAndRenewsSession() {
        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        String token = sessions.create(7, "ADMIN");
        verify(valueOps).set(key.capture(), eq("7:ADMIN"), eq(Duration.ofHours(24)));
        when(valueOps.get(key.getValue())).thenReturn("7:ADMIN");

        Optional<SessionService.SessionUser> resolved = sessions.resolve(token);

        assertTrue(resolved.isPresent());
        assertEquals(7, resolved.get().userId());
        assertEquals("ADMIN", resolved.get().role());
        verify(redis).expire(key.getValue(), Duration.ofHours(24));
    }

    @Test
    void revokesToken() {
        ArgumentCaptor<String> key = ArgumentCaptor.forClass(String.class);
        String token = sessions.create(9, "USER");
        verify(valueOps).set(key.capture(), eq("9:USER"), eq(Duration.ofHours(24)));

        sessions.revoke(token);

        verify(redis).delete(key.getValue());
    }

    @Test
    void parsesBearerHeader() {
        assertEquals("abc", SessionService.bearerToken("Bearer abc"));
        assertEquals("abc", SessionService.bearerToken("bearer   abc  "));
        assertNull(SessionService.bearerToken(null));
        assertNull(SessionService.bearerToken("abc"));
        assertNull(SessionService.bearerToken("Bearer   "));
    }
}
