package com.fishmatrix.user.service;

import com.fishmatrix.common.error.ApiException;
import com.fishmatrix.user.model.UserAccount;
import com.fishmatrix.user.model.UserView;
import com.fishmatrix.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final SessionService sessions;
    private final UserRepository users;
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

        UserAccount account = users.findById(session.userId())
                .orElseThrow(() -> {
                    sessions.revoke(token);
                    return unauthorized();
                });

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

    private static ApiException unauthorized() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "请先登录");
    }
}
