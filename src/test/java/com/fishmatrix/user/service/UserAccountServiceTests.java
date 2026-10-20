package com.fishmatrix.user.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fishmatrix.user.model.ApiException;
import com.fishmatrix.user.model.LoginRequest;
import com.fishmatrix.user.model.LoginResult;
import com.fishmatrix.user.model.RegisterRequest;
import com.fishmatrix.user.model.RegisterResult;
import com.fishmatrix.user.model.UserAccount;
import com.fishmatrix.user.model.UserStats;
import com.fishmatrix.user.model.UserView;
import com.fishmatrix.user.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class UserAccountServiceTests {

    @Mock
    private UserRepository users;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private SessionService sessions;

    private UserAccountService service;

    @BeforeEach
    void setUp() {
        service = new UserAccountService(users, passwordEncoder, sessions);
    }

    @Test
    void registersPendingUser() {
        when(users.existsByUsername("小明")).thenReturn(false);
        when(passwordEncoder.encode("secret12")).thenReturn("hash");

        RegisterResult result = service.register(new RegisterRequest(" 小明 ", "secret12"));

        assertEquals("PENDING", result.status());
        assertEquals("等待通过中", result.message());
        verify(users).insert(eq("小明"), eq("hash"), eq("小明"), eq("USER"), eq("PENDING"),
                any(LocalDateTime.class));
    }

    @Test
    void rejectsDuplicateUsername() {
        when(users.existsByUsername("小明")).thenReturn(true);

        ApiException exception = assertThrows(ApiException.class,
                () -> service.register(new RegisterRequest("小明", "secret12")));

        assertEquals(HttpStatus.CONFLICT, exception.status());
        assertEquals("USERNAME_TAKEN", exception.code());
        verify(users, never()).insert(anyString(), anyString(), anyString(), anyString(),
                anyString(), any());
    }

    @Test
    void blocksPendingUserAfterCorrectPassword() {
        UserAccount pending = account(5, "小明", "hash", "USER", "PENDING");
        when(users.findByUsername("小明")).thenReturn(Optional.of(pending));
        when(passwordEncoder.matches("secret12", "hash")).thenReturn(true);

        ApiException exception = assertThrows(ApiException.class,
                () -> service.login(new LoginRequest("小明", "secret12")));

        assertEquals(HttpStatus.FORBIDDEN, exception.status());
        assertEquals("PENDING_APPROVAL", exception.code());
        verify(sessions, never()).create(anyLong(), anyString());
    }

    @Test
    void rejectsWrongPasswordWithGenericMessage() {
        UserAccount active = account(5, "小明", "hash", "USER", "ACTIVE");
        when(users.findByUsername("小明")).thenReturn(Optional.of(active));
        when(passwordEncoder.matches("wrong12", "hash")).thenReturn(false);

        ApiException exception = assertThrows(ApiException.class,
                () -> service.login(new LoginRequest("小明", "wrong12")));

        assertEquals(HttpStatus.UNAUTHORIZED, exception.status());
        assertEquals("INVALID_CREDENTIALS", exception.code());
        assertEquals("名号或口号不对", exception.getMessage());
    }

    @Test
    void logsInActiveUserAndIssuesSession() {
        UserAccount active = account(5, "小明", "hash", "USER", "ACTIVE");
        when(users.findByUsername("小明")).thenReturn(Optional.of(active));
        when(passwordEncoder.matches("secret12", "hash")).thenReturn(true);
        when(sessions.create(5, "USER")).thenReturn("token-abc");

        LoginResult result = service.login(new LoginRequest("小明", "secret12"));

        assertEquals("token-abc", result.token());
        assertEquals("小明", result.user().username());
        verify(users).touchLastLogin(eq(5L), any(LocalDateTime.class));
    }

    @Test
    void approvesPendingUser() {
        UserAccount admin = account(1, "admin", "hash", "ADMIN", "ACTIVE");
        UserAccount pending = account(5, "小明", "hash", "USER", "PENDING");
        UserAccount approved = account(5, "小明", "hash", "USER", "ACTIVE");
        when(users.findById(5)).thenReturn(Optional.of(pending), Optional.of(approved));

        UserView result = service.changeStatus(5, "approve", admin);

        assertEquals("ACTIVE", result.status());
        verify(users).updateStatus(eq(5L), eq("ACTIVE"), eq("admin"), any(LocalDateTime.class));
    }

    @Test
    void refusesSelfReject() {
        UserAccount admin = account(1, "admin", "hash", "ADMIN", "ACTIVE");
        when(users.findById(1)).thenReturn(Optional.of(admin));

        ApiException exception = assertThrows(ApiException.class,
                () -> service.changeStatus(1, "reject", admin));

        assertEquals(HttpStatus.CONFLICT, exception.status());
        assertEquals("SELF_ACTION", exception.code());
        verify(users, never()).updateStatus(anyLong(), anyString(), anyString(), any());
    }

    @Test
    void refusesInvalidTransition() {
        UserAccount admin = account(1, "admin", "hash", "ADMIN", "ACTIVE");
        UserAccount disabled = account(5, "小明", "hash", "USER", "DISABLED");
        when(users.findById(5)).thenReturn(Optional.of(disabled));

        ApiException exception = assertThrows(ApiException.class,
                () -> service.changeStatus(5, "disable", admin));

        assertEquals(HttpStatus.CONFLICT, exception.status());
        assertEquals("INVALID_STATE", exception.code());
    }

    @Test
    void aggregatesStats() {
        when(users.statusCounts()).thenReturn(Map.of(
                "PENDING", 2L, "ACTIVE", 3L, "DISABLED", 1L));

        UserStats stats = service.stats();

        assertEquals(6, stats.total());
        assertEquals(2, stats.pending());
        assertEquals(3, stats.active());
        assertEquals(0, stats.rejected());
        assertEquals(1, stats.disabled());
    }

    @Test
    void normalizesUsernameAndValidatesInput() {
        assertTrue(assertThrows(IllegalArgumentException.class,
                () -> service.register(new RegisterRequest("a", "secret12")))
                .getMessage().contains("名号"));
    }

    private static UserAccount account(long id, String username, String hash, String role,
            String status) {
        LocalDateTime now = LocalDateTime.of(2026, 10, 20, 9, 0);
        return new UserAccount(id, username, hash, username, role, status, now, now,
                null, null, null);
    }
}
