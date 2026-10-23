package com.fishmatrix.user.controller;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fishmatrix.common.autoconfigure.FishCommonProperties;
import com.fishmatrix.common.error.ApiException;
import com.fishmatrix.common.error.GlobalExceptionHandler;
import com.fishmatrix.user.model.UserStats;
import com.fishmatrix.user.service.AuthService;
import com.fishmatrix.user.service.UserAccountService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class StatsControllerTest {

    private AuthService auth;
    private UserAccountService accounts;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        auth = mock(AuthService.class);
        accounts = mock(UserAccountService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new StatsController(auth, accounts))
                .setControllerAdvice(new GlobalExceptionHandler(new FishCommonProperties()))
                .build();
    }

    @Test
    void returnsActiveUserCount() throws Exception {
        when(accounts.stats()).thenReturn(new UserStats(12, 2, 9, 1, 0));

        mockMvc.perform(get("/api/v1/user/stats").header("Authorization", "Bearer token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(9))
                .andExpect(jsonPath("$.total").value(12));
    }

    @Test
    void requiresLogin() throws Exception {
        when(auth.requireIdentity("Bearer expired")).thenThrow(
                new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "请先登录"));

        mockMvc.perform(get("/api/v1/user/stats").header("Authorization", "Bearer expired"))
                .andExpect(status().isUnauthorized());
    }
}
