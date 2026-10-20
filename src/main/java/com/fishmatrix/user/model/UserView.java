package com.fishmatrix.user.model;

import java.time.LocalDateTime;

public record UserView(
        long id,
        String username,
        String nickname,
        String role,
        String status,
        LocalDateTime createdAt,
        LocalDateTime approvedAt,
        String approvedBy,
        LocalDateTime lastLoginAt) {
}
