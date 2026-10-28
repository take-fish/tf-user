package com.fishmatrix.user.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fishmatrix.common.error.ApiException;
import com.fishmatrix.user.entity.UserAccountEntity;
import com.fishmatrix.user.mapper.UserAccountMapper;
import com.fishmatrix.user.model.LoginRequest;
import com.fishmatrix.user.model.LoginResult;
import com.fishmatrix.user.model.RegisterRequest;
import com.fishmatrix.user.model.RegisterResult;
import com.fishmatrix.user.model.UserAccount;
import com.fishmatrix.user.model.UserStats;
import com.fishmatrix.user.model.UserView;
import com.fishmatrix.user.testsupport.UserFixtures;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;

@ExtendWith(MockitoExtension.class)
class UserAccountServiceTests {

    @Mock
    private UserAccountMapper users;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private SessionService sessions;

    @Mock
    private PermissionService permissions;

    private UserAccountService service;

    @BeforeEach
    void setUp() {
        lenient().when(permissions.view(any()))
                .thenAnswer(invocation -> {
                    UserAccount account = invocation.getArgument(0);
                    return account.toView(List.of(account.role()), List.of());
                });
        service = new UserAccountService(users, passwordEncoder, sessions, permissions);
    }

    @Test
    void registersPendingUser() {
        when(users.selectCount(any())).thenReturn(0L);
        when(passwordEncoder.encode("secret12")).thenReturn("hash");
        when(users.insert(any(UserAccountEntity.class))).thenAnswer(invocation -> {
            UserAccountEntity entity = invocation.getArgument(0);
            entity.setId(7L);
            return 1;
        });

        RegisterResult result = service.register(new RegisterRequest(" 小明 ", "secret12"));

        assertEquals(7L, result.id());
        assertEquals("PENDING", result.status());
        assertEquals("等待通过中", result.message());

        ArgumentCaptor<UserAccountEntity> captor = ArgumentCaptor.forClass(UserAccountEntity.class);
        verify(users).insert(captor.capture());
        UserAccountEntity saved = captor.getValue();
        assertEquals("小明", saved.getUsername());
        assertEquals("hash", saved.getPasswordHash());
        assertEquals("小明", saved.getNickname());
        assertEquals("USER", saved.getRole().name());
        assertEquals("PENDING", saved.getStatus().name());
    }

    @Test
    void rejectsDuplicateUsername() {
        when(users.selectCount(any())).thenReturn(1L);

        ApiException exception = assertThrows(ApiException.class,
                () -> service.register(new RegisterRequest("小明", "secret12")));

        assertEquals(HttpStatus.CONFLICT, exception.status());
        assertEquals("USERNAME_TAKEN", exception.code());
        verify(users, never()).insert(any(UserAccountEntity.class));
    }

    @Test
    void blocksPendingUserAfterCorrectPassword() {
        when(users.selectOne(any()))
                .thenReturn(UserFixtures.account(5, "小明", "USER", "PENDING"));
        when(passwordEncoder.matches("secret12", "hash")).thenReturn(true);

        ApiException exception = assertThrows(ApiException.class,
                () -> service.login(new LoginRequest("小明", "secret12")));

        assertEquals(HttpStatus.FORBIDDEN, exception.status());
        assertEquals("PENDING_APPROVAL", exception.code());
        verify(sessions, never()).create(anyLong(), anyString());
    }

    @Test
    void rejectsWrongPasswordWithGenericMessage() {
        when(users.selectOne(any()))
                .thenReturn(UserFixtures.account(5, "小明", "USER", "ACTIVE"));
        when(passwordEncoder.matches("wrong12", "hash")).thenReturn(false);

        ApiException exception = assertThrows(ApiException.class,
                () -> service.login(new LoginRequest("小明", "wrong12")));

        assertEquals(HttpStatus.UNAUTHORIZED, exception.status());
        assertEquals("INVALID_CREDENTIALS", exception.code());
        assertEquals("名号或口号不对", exception.getMessage());
    }

    @Test
    void logsInActiveUserAndIssuesSession() {
        when(users.selectOne(any()))
                .thenReturn(UserFixtures.account(5, "小明", "USER", "ACTIVE"));
        when(passwordEncoder.matches("secret12", "hash")).thenReturn(true);
        when(sessions.create(5, "USER")).thenReturn("token-abc");

        LoginResult result = service.login(new LoginRequest("小明", "secret12"));

        assertEquals("token-abc", result.token());
        assertEquals("小明", result.user().username());
        verify(users).touchLastLogin(eq(5L), any(LocalDateTime.class));
    }

    @Test
    void approvesPendingUser() {
        UserAccount admin = account(1, "admin", "ADMIN", "ACTIVE");
        when(users.selectById(5L)).thenReturn(
                UserFixtures.account(5, "小明", "USER", "PENDING"),
                UserFixtures.account(5, "小明", "USER", "ACTIVE"));

        UserView result = service.changeStatus(5, "approve", admin);

        assertEquals("ACTIVE", result.status());
        verify(users).updateStatus(eq(5L), eq("ACTIVE"), eq("admin"), any(LocalDateTime.class));
    }

    @Test
    void refusesSelfReject() {
        UserAccount admin = account(1, "admin", "ADMIN", "ACTIVE");
        when(users.selectById(1L)).thenReturn(UserFixtures.account(1, "admin", "ADMIN", "ACTIVE"));

        ApiException exception = assertThrows(ApiException.class,
                () -> service.changeStatus(1, "reject", admin));

        assertEquals(HttpStatus.CONFLICT, exception.status());
        assertEquals("SELF_ACTION", exception.code());
        verify(users, never()).updateStatus(anyLong(), anyString(), anyString(), any());
    }

    @Test
    void refusesInvalidTransition() {
        UserAccount admin = account(1, "admin", "ADMIN", "ACTIVE");
        when(users.selectById(5L)).thenReturn(
                UserFixtures.account(5, "小明", "USER", "DISABLED"));

        ApiException exception = assertThrows(ApiException.class,
                () -> service.changeStatus(5, "disable", admin));

        assertEquals(HttpStatus.CONFLICT, exception.status());
        assertEquals("INVALID_STATE", exception.code());
    }

    @Test
    void aggregatesStats() {
        when(users.selectStatusCounts()).thenReturn(List.of(
                new UserAccountMapper.StatusCount("PENDING", 2),
                new UserAccountMapper.StatusCount("ACTIVE", 3),
                new UserAccountMapper.StatusCount("DISABLED", 1)));

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

    private static UserAccount account(long id, String username, String role, String status) {
        LocalDateTime now = UserFixtures.NOW;
        return new UserAccount(id, username, "hash", username, null, null, null, role, status, now,
                now, null, null, null);
    }
}
