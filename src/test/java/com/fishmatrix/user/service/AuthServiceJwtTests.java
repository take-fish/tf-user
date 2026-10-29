package com.fishmatrix.user.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fishmatrix.common.auth.JwtIssuer;
import com.fishmatrix.common.auth.JwtVerifier;
import com.fishmatrix.common.autoconfigure.FishAuthProperties;
import com.fishmatrix.common.error.ApiException;
import com.fishmatrix.user.entity.UserAccountEntity;
import com.fishmatrix.user.mapper.UserAccountMapper;
import com.fishmatrix.user.model.LoginResult;
import com.fishmatrix.user.model.UserAccount;
import com.fishmatrix.user.testsupport.UserFixtures;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

/**
 * 用户中台的 JWT 侧行为：签发、本地验签、刷新、撤销。
 * 这里用真实密钥对跑签发+验签，只把数据库和 Redis 换成 mock。
 */
@ExtendWith(MockitoExtension.class)
class AuthServiceJwtTests {

    private static final String KID = "k1";

    @Mock
    private UserAccountMapper users;

    @Mock
    private SessionService sessions;

    @Mock
    private PermissionService permissions;

    private AuthService service;
    private FishAuthProperties properties;

    private static KeyPair ed25519() {
        try {
            return KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static String b64(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String rawPublicKey(KeyPair pair) {
        byte[] spki = pair.getPublic().getEncoded();
        return b64(Arrays.copyOfRange(spki, spki.length - 32, spki.length));
    }

    @BeforeEach
    void setUp() {
        KeyPair pair = ed25519();
        properties = new FishAuthProperties();
        properties.getJwt().setKeyId(KID);
        properties.getJwt().setIssuer("fishmatrix");
        properties.getJwt().setPublicKey(rawPublicKey(pair));
        properties.getJwt().setPrivateKey(Base64.getEncoder().encodeToString(pair.getPrivate().getEncoded()));

        lenient().when(permissions.permissionsFor(org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of("reader.public.upload"));
        lenient().when(permissions.view(org.mockito.ArgumentMatchers.any()))
                .thenAnswer(invocation -> {
                    UserAccount account = invocation.getArgument(0);
                    return account.toView(List.of(account.role()), List.of("reader.public.upload"));
                });

        service = new AuthService(sessions, users, permissions,
                new JwtIssuer(properties), new JwtVerifier(properties));
    }

    private UserAccountEntity activeAccount() {
        UserAccountEntity entity = UserFixtures.account(5, "小明", "USER", "ACTIVE");
        lenient().when(users.selectById(5L)).thenReturn(entity);
        return entity;
    }

    @Test
    void issuesJwtWithRefreshTokenOnLogin() {
        activeAccount();
        UserAccount account = com.fishmatrix.user.converter.UserConverters.toModel(activeAccount());

        LoginResult result = service.issueTokens(account, "refresh-1");

        assertEquals(3, result.token().split("\\.").length);
        assertEquals("refresh-1", result.refreshToken());
        assertEquals(3600, result.expiresIn());
        assertEquals("小明", result.user().username());
        assertNotNull(new JwtVerifier(properties).verify(result.token()));
    }

    @Test
    void acceptsItsOwnJwtOnProtectedEndpoints() {
        activeAccount();
        UserAccount account = com.fishmatrix.user.converter.UserConverters.toModel(activeAccount());
        String token = service.issueTokens(account, "refresh-1").token();

        UserAccount resolved = service.requireActiveUser("Bearer " + token);

        assertEquals(5, resolved.id());
    }

    @Test
    void rejectsTamperedJwt() {
        activeAccount();
        UserAccount account = com.fishmatrix.user.converter.UserConverters.toModel(activeAccount());
        String token = service.issueTokens(account, "refresh-1").token();
        String tampered = token.substring(0, token.length() - 2) + "xy";

        ApiException exception = assertThrows(ApiException.class,
                () -> service.requireActiveUser("Bearer " + tampered));

        assertEquals(HttpStatus.UNAUTHORIZED, exception.status());
    }

    @Test
    void refreshesAccessTokenFromRefreshToken() {
        activeAccount();
        when(sessions.resolve("refresh-1"))
                .thenReturn(Optional.of(new SessionService.SessionUser(5, "USER")));

        LoginResult result = service.refresh("refresh-1");

        assertEquals("refresh-1", result.refreshToken());
        assertEquals(3, result.token().split("\\.").length);
        assertEquals(5, new JwtVerifier(properties).verify(result.token()).id());
        // 刷新不是重新登录，不能动刷新令牌
        verify(sessions, never()).create(anyLong(), org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void rejectsUnknownRefreshToken() {
        when(sessions.resolve("nope")).thenReturn(Optional.empty());

        ApiException exception = assertThrows(ApiException.class, () -> service.refresh("nope"));

        assertEquals("INVALID_REFRESH_TOKEN", exception.code());
    }

    @Test
    void logoutRevokesRefreshTokenButNotJwt() {
        activeAccount();
        UserAccount account = com.fishmatrix.user.converter.UserConverters.toModel(activeAccount());
        String jwt = service.issueTokens(account, "refresh-1").token();

        service.logout("Bearer " + jwt);
        service.logout("Bearer refresh-1");

        // JWT 无状态，撤销的是刷新令牌
        verify(sessions, never()).revoke(jwt);
        verify(sessions).revoke("refresh-1");
    }
}
