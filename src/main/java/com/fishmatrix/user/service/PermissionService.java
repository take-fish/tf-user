package com.fishmatrix.user.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fishmatrix.common.error.ApiException;
import com.fishmatrix.user.auth.PermissionCodes;
import com.fishmatrix.user.converter.UserConverters;
import com.fishmatrix.user.entity.AuthPermissionEntity;
import com.fishmatrix.user.entity.AuthRolePermissionEntity;
import com.fishmatrix.user.mapper.AuthPermissionMapper;
import com.fishmatrix.user.mapper.AuthRolePermissionMapper;
import com.fishmatrix.user.model.PermissionCatalogView;
import com.fishmatrix.user.model.PermissionView;
import com.fishmatrix.user.model.RolePermissionsView;
import com.fishmatrix.user.model.UserAccount;
import com.fishmatrix.user.model.UserView;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 权限只由用户中台维护：角色到权限码的映射存数据库，其他服务只消费身份头中的权限快照。
 */
@Service
@RequiredArgsConstructor
public class PermissionService {

    private static final List<String> ROLES = List.of(
            UserAccount.ROLE_USER, UserAccount.ROLE_ADMIN);

    private final AuthPermissionMapper permissionMapper;
    private final AuthRolePermissionMapper rolePermissionMapper;

    public List<String> permissionsFor(UserAccount account) {
        if (account == null) {
            return List.of();
        }
        if (account.isAdmin()) {
            // 管理员始终拿到完整目录，新增权限无需再补数据
            return allCodes();
        }
        return codesFor(account.role());
    }

    public UserView view(UserAccount account) {
        List<String> codes = permissionsFor(account);
        return account.toView(List.of(account.role()), codes);
    }

    public PermissionCatalogView catalog() {
        List<PermissionView> all = catalogEntries();
        List<RolePermissionsView> roles = ROLES.stream()
                .map(role -> new RolePermissionsView(role, roleLabel(role), codesForRole(role)))
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

        Set<String> codes = new LinkedHashSet<>();
        if (requested != null) {
            for (String code : requested) {
                if (code != null && !code.isBlank()) {
                    codes.add(code.trim());
                }
            }
        }

        long known = countKnown(codes);
        if (known != codes.size()) {
            List<String> catalog = catalogEntries().stream().map(PermissionView::code).toList();
            List<String> unknown = new ArrayList<>(codes);
            unknown.removeAll(catalog);
            throw new ApiException(HttpStatus.BAD_REQUEST, "UNKNOWN_PERMISSION",
                    "未知的权限码：" + String.join(", ", unknown));
        }
        if (UserAccount.ROLE_ADMIN.equals(normalized)
                && !codes.contains(PermissionCodes.USER_ROLE_MANAGE)) {
            throw new ApiException(HttpStatus.CONFLICT, "ADMIN_LOCKOUT",
                    "管理员角色必须保留角色配置权限，避免无法再进入权限管理");
        }

        replaceRolePermissions(normalized, codes);
        return new RolePermissionsView(normalized, roleLabel(normalized), List.copyOf(codes));
    }

    private void replaceRolePermissions(String role, Set<String> codes) {
        rolePermissionMapper.delete(Wrappers.<AuthRolePermissionEntity>lambdaQuery()
                .eq(AuthRolePermissionEntity::getRole, role));
        if (codes.isEmpty()) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        List<AuthRolePermissionEntity> rows = codes.stream().map(code -> {
            AuthRolePermissionEntity entity = new AuthRolePermissionEntity();
            entity.setRole(role);
            entity.setPermissionCode(code);
            entity.setCreatedAt(now);
            return entity;
        }).toList();
        rolePermissionMapper.insert(rows);
    }

    private long countKnown(Collection<String> codes) {
        if (codes.isEmpty()) {
            return 0L;
        }
        return permissionMapper.selectCount(Wrappers.<AuthPermissionEntity>lambdaQuery()
                .in(AuthPermissionEntity::getCode, codes));
    }

    private List<String> codesForRole(String role) {
        return UserAccount.ROLE_ADMIN.equals(role) ? allCodes() : codesFor(role);
    }

    private List<String> allCodes() {
        return catalogEntries().stream().map(PermissionView::code).toList();
    }

    private List<String> codesFor(String role) {
        return rolePermissionMapper.selectList(Wrappers.<AuthRolePermissionEntity>lambdaQuery()
                        .eq(AuthRolePermissionEntity::getRole, role)
                        .orderByAsc(AuthRolePermissionEntity::getPermissionCode))
                .stream()
                .map(AuthRolePermissionEntity::getPermissionCode)
                .toList();
    }

    private List<PermissionView> catalogEntries() {
        return permissionMapper.selectList(Wrappers.<AuthPermissionEntity>lambdaQuery()
                        .orderByAsc(AuthPermissionEntity::getModule)
                        .orderByAsc(AuthPermissionEntity::getCode))
                .stream()
                .map(UserConverters::toPermissionView)
                .toList();
    }

    private static String roleLabel(String role) {
        return UserAccount.ROLE_ADMIN.equals(role) ? "管理员" : "普通用户";
    }
}
