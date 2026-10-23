package com.fishmatrix.user.controller;

import com.fishmatrix.user.auth.PermissionCodes;
import com.fishmatrix.user.model.PermissionCatalogView;
import com.fishmatrix.user.model.RolePermissionsView;
import com.fishmatrix.user.model.UpdateRolePermissionsRequest;
import com.fishmatrix.user.model.UserAccount;
import com.fishmatrix.user.model.UserPage;
import com.fishmatrix.user.model.UserView;
import com.fishmatrix.user.service.AuthService;
import com.fishmatrix.user.service.PermissionService;
import com.fishmatrix.user.service.UserAccountService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/user/admin")
public class AdminController {

    private final UserAccountService accounts;
    private final PermissionService permissions;
    private final AuthService auth;

    public AdminController(UserAccountService accounts, PermissionService permissions,
            AuthService auth) {
        this.accounts = accounts;
        this.permissions = permissions;
        this.auth = auth;
    }

    @GetMapping("/users")
    public UserPage users(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestParam(required = false) String status,
            @RequestParam(required = false, name = "q") String query,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        auth.requireAdmin(authorization);
        return accounts.listUsers(status, query, page, size);
    }

    @PostMapping("/users/{id}/approve")
    public UserView approve(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable long id) {
        return change(authorization, id, "approve");
    }

    @PostMapping("/users/{id}/reject")
    public UserView reject(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable long id) {
        return change(authorization, id, "reject");
    }

    @PostMapping("/users/{id}/disable")
    public UserView disable(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable long id) {
        return change(authorization, id, "disable");
    }

    @PostMapping("/users/{id}/enable")
    public UserView enable(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable long id) {
        return change(authorization, id, "enable");
    }

    @GetMapping("/permissions")
    public PermissionCatalogView permissions(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        auth.requireAdmin(authorization);
        return permissions.catalog();
    }

    @PutMapping("/roles/{role}/permissions")
    public RolePermissionsView updateRolePermissions(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @PathVariable String role,
            @RequestBody UpdateRolePermissionsRequest request) {
        UserAccount admin = auth.requirePermission(authorization,
                PermissionCodes.USER_ROLE_MANAGE);
        return permissions.updateRole(role,
                request == null ? null : request.permissions(), admin);
    }

    private UserView change(String authorization, long id, String action) {
        UserAccount admin = auth.requireAdmin(authorization);
        return accounts.changeStatus(id, action, admin);
    }
}
