package com.fishmatrix.user.service;

import com.fishmatrix.common.auth.CurrentUser;
import com.fishmatrix.common.auth.JwtIssuer;
import com.fishmatrix.common.auth.JwtVerifier;
import com.fishmatrix.common.error.ApiException;
import com.fishmatrix.user.converter.UserConverters;
import com.fishmatrix.user.mapper.UserAccountMapper;
import com.fishmatrix.user.model.LoginResult;
import com.fishmatrix.user.model.UserAccount;
import com.fishmatrix.user.model.UserView;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final SessionService sessions;
    private final UserAccountMapper users;
    private final PermissionService permissions;
    private final JwtIssuer jwtIssuer;
    private final JwtVerifier jwtVerifier;

    /**
     * 登录态的唯一可信来源。业务服务与网关通过本地验签自行解析 JWT，
     * 只有旧的不透明令牌才需要来这里做 introspection。
     */
    public UserView requireIdentity(String authorization) {
        return permissions.view(requireActiveUser(authorization));
    }

    /**
     * 解析当前用户：优先本地验签 JWT，其次回退到 Redis 会话（兼容旧的不透明令牌）。
     */
    public UserAccount requireActiveUser(String authorization) {
        String token = SessionService.bearerToken(authorization);
        if (token == null) {
            throw unauthorized();
        }

        if (jwtVerifier.isConfigured() && JwtVerifier.looksLikeJwt(token)) {
            CurrentUser identity = jwtVerifier.verify(token);
            UserAccount account = findById(identity.id());
            if (account == null) {
                throw unauthorized();
            }
            requireUsable(account);
            return account;
        }

        SessionService.SessionUser session = sessions.resolve(token)
                .orElseThrow(AuthService::unauthorized);
        UserAccount account = findById(session.userId());
        if (account == null) {
            sessions.revoke(token);
            throw unauthorized();
        }
        requireUsable(account);
        return account;
    }

    /**
     * 用账号当前的权限快照签发访问令牌。
     * 未配置私钥时退回不透明会话令牌，保证滚动升级期间仍可登录。
     */
    public LoginResult issueTokens(UserAccount account, String refreshToken) {
        UserView view = permissions.view(account);
        if (!jwtIssuer.isConfigured()) {
            String legacy = refreshToken != null
                    ? refreshToken : sessions.create(account.id(), account.role());
            return new LoginResult(legacy, null, 0, view);
        }
        CurrentUser identity = JwtIssuer.currentUser(
                account.id(), account.username(), account.nickname(), account.role(),
                List.of(account.role()), view.permissions());
        return new LoginResult(jwtIssuer.issue(identity), refreshToken,
                jwtIssuer.accessTtl().toSeconds(), view);
    }

    /** 用刷新令牌换新的访问令牌；刷新令牌本身保持滑动续期。 */
    public LoginResult refresh(String refreshToken) {
        SessionService.SessionUser session = sessions.resolve(refreshToken)
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_REFRESH_TOKEN",
                        "登录状态已失效，请重新登录"));
        UserAccount account = findById(session.userId());
        if (account == null) {
            sessions.revoke(refreshToken);
            throw unauthorized();
        }
        requireUsable(account);
        return issueTokens(account, refreshToken);
    }

    public boolean can(UserAccount account, String permission) {
        return permissions.permissionsFor(account).contains(permission);
    }

    public UserAccount requireAdmin(String authorization) {
        UserAccount account = requireActiveUser(authorization);
        if (!account.isAdmin()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "需要管理员权限");
        }
        return account;
    }

    public UserAccount requirePermission(String authorization, String permission) {
        UserAccount account = requireActiveUser(authorization);
        if (!can(account, permission)) {
            throw new ApiException(HttpStatus.FORBIDDEN, "PERMISSION_DENIED",
                    "没有执行这个操作的权限");
        }
        return account;
    }

    /**
     * 退出登录：撤销刷新令牌。访问令牌是无状态的，靠较短的有效期自然过期。
     */
    public void logout(String authorization) {
        String token = SessionService.bearerToken(authorization);
        if (token != null && !JwtVerifier.looksLikeJwt(token)) {
            sessions.revoke(token);
        }
    }

    public UserAccount findById(long id) {
        return UserConverters.toModel(users.selectById(id));
    }

    private static void requireUsable(UserAccount account) {
        if (!account.isActive()) {
            throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "登录已失效，请重新登录");
        }
    }

    private static ApiException unauthorized() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "请先登录");
    }
}
