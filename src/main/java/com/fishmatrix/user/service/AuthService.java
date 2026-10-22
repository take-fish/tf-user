package com.fishmatrix.user.service;

import com.fishmatrix.common.error.ApiException;
import com.fishmatrix.user.model.UserAccount;
import com.fishmatrix.user.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final SessionService sessions;
    private final UserRepository users;

    public AuthService(SessionService sessions, UserRepository users) {
        this.sessions = sessions;
        this.users = users;
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

    public void logout(String authorization) {
        sessions.revoke(SessionService.bearerToken(authorization));
    }

    private static ApiException unauthorized() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "请先登录");
    }
}
