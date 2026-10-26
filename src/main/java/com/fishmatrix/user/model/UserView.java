package com.fishmatrix.user.model;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 对外身份快照。{@code role} 保留为单角色兼容字段，新代码使用 {@code roles/permissions}。
 */
public record UserView(
        long id,
        String username,
        String nickname,
        /** 头像地址；未设置时为 null，调用方回退首字母占位。 */
        String avatarUrl,
        String role,
        String status,
        LocalDateTime createdAt,
        LocalDateTime approvedAt,
        String approvedBy,
        LocalDateTime lastLoginAt,
        List<String> roles,
        List<String> permissions) {

    public UserView {
        roles = roles == null ? List.of() : List.copyOf(roles);
        permissions = permissions == null ? List.of() : List.copyOf(permissions);
    }

    public boolean hasPermission(String permission) {
        return permissions.contains(permission);
    }
}
