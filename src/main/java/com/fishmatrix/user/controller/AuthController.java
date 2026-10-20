package com.fishmatrix.user.controller;

import com.fishmatrix.user.model.LoginRequest;
import com.fishmatrix.user.model.LoginResult;
import com.fishmatrix.user.model.RegisterRequest;
import com.fishmatrix.user.model.RegisterResult;
import com.fishmatrix.user.model.UserView;
import com.fishmatrix.user.service.AuthService;
import com.fishmatrix.user.service.UserAccountService;
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
public class AuthController {

    private final UserAccountService accounts;
    private final AuthService auth;

    public AuthController(UserAccountService accounts, AuthService auth) {
        this.accounts = accounts;
        this.auth = auth;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public RegisterResult register(@RequestBody RegisterRequest request) {
        return accounts.register(request);
    }

    @PostMapping("/login")
    public LoginResult login(@RequestBody LoginRequest request) {
        return accounts.login(request);
    }

    @GetMapping("/me")
    public UserView me(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        return auth.requireActiveUser(authorization).toView();
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        auth.logout(authorization);
    }
}
