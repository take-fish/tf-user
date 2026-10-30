package com.fishmatrix.user.controller;

import com.fishmatrix.user.model.LoginRequest;
import com.fishmatrix.user.model.LoginResult;
import com.fishmatrix.user.model.RefreshRequest;
import com.fishmatrix.user.model.RegisterRequest;
import com.fishmatrix.user.model.RegisterResult;
import com.fishmatrix.user.model.UserView;
import com.fishmatrix.user.service.AuthService;
import com.fishmatrix.user.service.UserAccountService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/user/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserAccountService accounts;
    private final AuthService auth;

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public RegisterResult register(@RequestBody RegisterRequest request) {
        return accounts.register(request);
    }

    @PostMapping("/login")
    public LoginResult login(@RequestBody LoginRequest request) {
        return accounts.login(request);
    }

    /**
     * 登录态校验与身份快照。网关用它做 token introspection，业务服务也可直接调用。
     */
    @GetMapping("/me")
    public UserView me(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        return auth.requireIdentity(authorization);
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        auth.logout(authorization);
    }

    /**
     * 用刷新令牌换新的访问令牌。访问令牌有效期短，客户端在 401 时静默调用这里。
     */
    @PostMapping("/refresh")
    public LoginResult refresh(@RequestBody RefreshRequest request) {
        return auth.refresh(request.refreshToken());
    }
}
