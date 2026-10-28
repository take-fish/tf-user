package com.fishmatrix.user.service;

import com.fishmatrix.common.error.ApiException;
import com.fishmatrix.user.converter.UserConverters;
import com.fishmatrix.user.mapper.UserAccountMapper;
import com.fishmatrix.user.model.UserAccount;
import com.fishmatrix.user.model.UserView;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final SessionService sessions;
    private final UserAccountMapper users;
    private final PermissionService permissions;

    /**
     * 登录态的唯一可信来源。网关和各业务服务通过 {@code /auth/me} 拿到这份快照，
     * 不直接读取用户库，也不信任客户端传入的身份字段。
     */
    public UserView requireIdentity(String authorization) {
        return permissions.view(requireActiveUser(authorization));
    }

    public boolean can(UserAccount account, String permission) {
        return permissions.permissionsFor(account).contains(permission);
    }

    public UserAccount requireActiveUser(String authorization) {
        String token = SessionService.bearerToken(authorization);
        SessionService.SessionUser session = sessions.resolve(token)
                .orElseThrow(AuthService::unauthorized);

        UserAccount account = findById(session.userId());
        if (account == null) {
            sessions.revoke(token);
            throw unauthorized();
        }

        if (!account.isActive()) {
            sessions.revoke(token);
            throw new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "登录已失效，请重新登录");
        }
        return account;
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

    public void logout(String authorization) {
        sessions.revoke(SessionService.bearerToken(authorization));
    }

    public UserAccount findById(long id) {
        return UserConverters.toModel(users.selectById(id));
    }

    private static ApiException unauthorized() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "请先登录");
    }
}
