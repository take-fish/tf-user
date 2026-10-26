package com.fishmatrix.user.model;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

public record UserAccount(
        long id,
        String username,
        String passwordHash,
        String nickname,
        String avatarKey,
        String avatarContentType,
        LocalDateTime avatarUpdatedAt,
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

    public boolean hasAvatar() {
        return avatarKey != null && !avatarKey.isBlank();
    }

    public UserView toView() {
        return toView(List.of(role), List.of());
    }

    public UserView toView(List<String> roles, List<String> permissions) {
        return new UserView(id, username, nickname, avatarUrl(), role, status, createdAt,
                approvedAt, approvedBy, lastLoginAt, roles, permissions);
    }

    /**
     * 头像地址由存储标识推导，读取接口公开；带版本参数避免更新后命中旧缓存。
     */
    public String avatarUrl() {
        if (!hasAvatar() || avatarUpdatedAt == null) return null;
        long version = avatarUpdatedAt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        return "/api/v1/user/avatars/" + id + "?v=" + version;
    }
}
