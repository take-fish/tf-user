package com.fishmatrix.user.model;

import java.util.List;

/**
 * 单个角色的权限配置。{@code permissions} 为已授予的权限码。
 */
public record RolePermissionsView(
        String role,
        String label,
        List<String> permissions) {
}
