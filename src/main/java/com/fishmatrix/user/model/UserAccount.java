package com.fishmatrix.user.model;

import java.time.LocalDateTime;

public record UserAccount(
        long id,
        String username,
        String passwordHash,
        String nickname,
        String role,
        String status,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        LocalDateTime approvedAt,
        String approvedBy,
        LocalDateTime lastLoginAt) {

    public static final String ROLE_USER = "USER";
    public static final String ROLE_ADMIN = "ADMIN";

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_REJECTED = "REJECTED";
    public static final String STATUS_DISABLED = "DISABLED";

    public boolean isAdmin() {
        return ROLE_ADMIN.equals(role);
    }

    public boolean isActive() {
        return STATUS_ACTIVE.equals(status);
    }

    public UserView toView() {
        return new UserView(id, username, nickname, role, status, createdAt, approvedAt, approvedBy,
                lastLoginAt);
    }
}
