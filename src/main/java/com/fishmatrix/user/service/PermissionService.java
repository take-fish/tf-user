package com.fishmatrix.user.service;

import com.fishmatrix.common.error.ApiException;
import com.fishmatrix.user.auth.PermissionCodes;
import com.fishmatrix.user.model.PermissionCatalogView;
import com.fishmatrix.user.model.PermissionView;
import com.fishmatrix.user.model.RolePermissionsView;
import com.fishmatrix.user.model.UserAccount;
import com.fishmatrix.user.model.UserView;
import com.fishmatrix.user.repository.PermissionRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 权限只由用户中台维护：角色到权限码的映射存数据库，其他服务只消费身份头中的权限快照。
 */
@Service
public class PermissionService {

    private static final List<String> ROLES = List.of(
            UserAccount.ROLE_USER, UserAccount.ROLE_ADMIN);

    private final PermissionRepository permissions;

    public PermissionService(PermissionRepository permissions) {
        this.permissions = permissions;
    }

    public List<String> permissionsFor(UserAccount account) {
        if (account == null) return List.of();
        if (account.isAdmin()) {
            // 管理员始终拿到完整目录，新增权限无需再补数据
            return permissions.findAll().stream().map(PermissionView::code).toList();
        }
        return permissions.findCodesByRole(account.role());
    }

    public UserView view(UserAccount account) {
        List<String> codes = permissionsFor(account);
        return account.toView(List.of(account.role()), codes);
    }

    public PermissionCatalogView catalog() {
        List<PermissionView> all = permissions.findAll();
        List<RolePermissionsView> roles = ROLES.stream()
                .map(role -> new RolePermissionsView(role, roleLabel(role),
                        permissionsFor(role)))
                .toList();
        return new PermissionCatalogView(all, roles);
    }

    @Transactional
    public RolePermissionsView updateRole(String role, Collection<String> requested,
            UserAccount actor) {
        String normalized = role == null ? "" : role.trim().toUpperCase();
        if (!ROLES.contains(normalized)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "UNKNOWN_ROLE", "未知的角色：" + role);
        }
        if (!actor.isAdmin()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "需要管理员权限");
        }

        Set<String> codes = new LinkedHashSet<>(requested == null ? List.of() : requested);
        codes.removeIf(code -> code == null || code.isBlank());
        codes = new LinkedHashSet<>(codes.stream().map(String::trim).toList());

        long known = permissions.countCodes(codes);
        if (known != codes.size()) {
            List<String> unknown = new ArrayList<>(codes);
            unknown.removeAll(permissions.findAll().stream().map(PermissionView::code).toList());
            throw new ApiException(HttpStatus.BAD_REQUEST, "UNKNOWN_PERMISSION",
                    "未知的权限码：" + String.join(", ", unknown));
        }
        if (UserAccount.ROLE_ADMIN.equals(normalized)
                && !codes.contains(PermissionCodes.USER_ROLE_MANAGE)) {
            throw new ApiException(HttpStatus.CONFLICT, "ADMIN_LOCKOUT",
                    "管理员角色必须保留角色配置权限，避免无法再进入权限管理");
        }

        permissions.replaceRolePermissions(normalized, codes, LocalDateTime.now());
        return new RolePermissionsView(normalized, roleLabel(normalized), List.copyOf(codes));
    }

    private List<String> permissionsFor(String role) {
        if (UserAccount.ROLE_ADMIN.equals(role)) {
            return permissions.findAll().stream().map(PermissionView::code).toList();
        }
        return permissions.findCodesByRole(role);
    }

    private static String roleLabel(String role) {
        return UserAccount.ROLE_ADMIN.equals(role) ? "管理员" : "普通用户";
    }
}
