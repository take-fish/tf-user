package com.fishmatrix.user.model;

import java.util.List;

/**
 * 中台权限配置页所需的完整目录与角色授权快照。
 */
public record PermissionCatalogView(
        List<PermissionView> permissions,
        List<RolePermissionsView> roles) {
}
