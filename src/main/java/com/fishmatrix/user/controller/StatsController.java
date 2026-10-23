package com.fishmatrix.user.controller;

import com.fishmatrix.user.model.UserStats;
import com.fishmatrix.user.model.UserStatsView;
import com.fishmatrix.user.service.AuthService;
import com.fishmatrix.user.service.UserAccountService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/user")
public class StatsController {

    private final AuthService auth;
    private final UserAccountService accounts;

    public StatsController(AuthService auth, UserAccountService accounts) {
        this.auth = auth;
        this.accounts = accounts;
    }

    /**
     * 已登录用户可见的规模统计，首页用“启用用户数”作为总人数分母。
     */
    @GetMapping("/stats")
    public UserStatsView stats(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        auth.requireIdentity(authorization);
        UserStats stats = accounts.stats();
        return new UserStatsView(stats.active(), stats.total());
    }
}
