package com.fishmatrix.user.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fishmatrix.common.error.ApiException;
import com.fishmatrix.user.auth.PermissionCodes;
import com.fishmatrix.user.entity.AuthPermissionEntity;
import com.fishmatrix.user.entity.AuthRolePermissionEntity;
import com.fishmatrix.user.mapper.AuthPermissionMapper;
import com.fishmatrix.user.mapper.AuthRolePermissionMapper;
import com.fishmatrix.user.model.PermissionCatalogView;
import com.fishmatrix.user.model.PermissionView;
import com.fishmatrix.user.model.RolePermissionsView;
import com.fishmatrix.user.model.UserAccount;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class PermissionServiceTests {

    @Mock
    private AuthPermissionMapper permissionMapper;

    @Mock
    private AuthRolePermissionMapper rolePermissionMapper;

    private PermissionService service;

    private final List<AuthPermissionEntity> catalog = PermissionCodes.ALL.stream()
            .map(PermissionServiceTests::permission)
            .toList();

    @BeforeEach
    void setUp() {
        service = new PermissionService(permissionMapper, rolePermissionMapper);
    }

    @Test
    void adminAlwaysReceivesFullCatalog() {
        when(permissionMapper.selectList(any())).thenReturn(catalog);
        when(rolePermissionMapper.selectList(any())).thenReturn(rolePermissions(
                PermissionCodes.DEFAULT_USER));

        PermissionCatalogView view = service.catalog();

        assertEquals(PermissionCodes.ALL, view.permissions().stream()
                .map(PermissionView::code).toList());
        RolePermissionsView admin = role(view, "ADMIN");
        assertEquals(PermissionCodes.ALL, admin.permissions());
        RolePermissionsView user = role(view, "USER");
        assertEquals(PermissionCodes.DEFAULT_USER, user.permissions());
    }

    @Test
    void adminPermissionsIgnoreStoredRows() {
        when(permissionMapper.selectList(any())).thenReturn(catalog);

        assertEquals(PermissionCodes.ALL,
                service.permissionsFor(account("ADMIN")));
    }

    @Test
    void normalizesAndSavesRolePermissions() {
        when(permissionMapper.selectCount(any())).thenReturn(2L);

        RolePermissionsView saved = service.updateRole(" user ", List.of(
                " " + PermissionCodes.READER_CATEGORY_MANAGE + " ",
                PermissionCodes.MUSIC_CATEGORY_MANAGE,
                PermissionCodes.MUSIC_CATEGORY_MANAGE,
                " "), account("ADMIN"));

        assertEquals("USER", saved.role());
        assertEquals("普通用户", saved.label());
        assertEquals(List.of(PermissionCodes.READER_CATEGORY_MANAGE,
                PermissionCodes.MUSIC_CATEGORY_MANAGE), saved.permissions());

        verify(rolePermissionMapper).delete(any());
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<AuthRolePermissionEntity>> captor =
                ArgumentCaptor.forClass(List.class);
        verify(rolePermissionMapper).insert(captor.capture());
        assertEquals(Set.of(PermissionCodes.READER_CATEGORY_MANAGE,
                        PermissionCodes.MUSIC_CATEGORY_MANAGE),
                Set.copyOf(captor.getValue().stream()
                        .map(AuthRolePermissionEntity::getPermissionCode).toList()));
    }

    @Test
    void rejectsUnknownRole() {
        ApiException exception = assertThrows(ApiException.class,
                () -> service.updateRole("GUEST", List.of(), account("ADMIN")));

        assertEquals(HttpStatus.BAD_REQUEST, exception.status());
        assertEquals("UNKNOWN_ROLE", exception.code());
        verify(rolePermissionMapper, never()).delete(any());
    }

    @Test
    void rejectsUnknownPermissionCode() {
        when(permissionMapper.selectCount(any())).thenReturn(1L);
        when(permissionMapper.selectList(any())).thenReturn(catalog);

        ApiException exception = assertThrows(ApiException.class,
                () -> service.updateRole("USER", List.of(
                        PermissionCodes.READER_PUBLIC_UPLOAD, "reader.nope"), account("ADMIN")));

        assertEquals(HttpStatus.BAD_REQUEST, exception.status());
        assertEquals("UNKNOWN_PERMISSION", exception.code());
        assertTrue(exception.getMessage().contains("reader.nope"));
        verify(rolePermissionMapper, never()).delete(any());
    }

    @Test
    void keepsRoleManagementPermissionOnAdmin() {
        when(permissionMapper.selectCount(any())).thenReturn(1L);

        ApiException exception = assertThrows(ApiException.class,
                () -> service.updateRole("ADMIN",
                        List.of(PermissionCodes.READER_PUBLIC_UPLOAD), account("ADMIN")));

        assertEquals(HttpStatus.CONFLICT, exception.status());
        assertEquals("ADMIN_LOCKOUT", exception.code());
        verify(rolePermissionMapper, never()).delete(any());
    }

    @Test
    void onlyAdminMayChangeRolePermissions() {
        ApiException exception = assertThrows(ApiException.class,
                () -> service.updateRole("USER",
                        List.of(PermissionCodes.READER_PUBLIC_UPLOAD), account("USER")));

        assertEquals(HttpStatus.FORBIDDEN, exception.status());
        assertEquals("FORBIDDEN", exception.code());
        verify(rolePermissionMapper, never()).delete(any());
    }

    @Test
    void emptyRequestRevokesAllUserPermissions() {
        RolePermissionsView saved = service.updateRole("USER", null, account("ADMIN"));

        assertEquals(List.of(), saved.permissions());
        verify(rolePermissionMapper).delete(any());
        verify(rolePermissionMapper, never()).insert(anyCollection());
    }

    private static List<AuthRolePermissionEntity> rolePermissions(List<String> codes) {
        return codes.stream().map(code -> {
            AuthRolePermissionEntity entity = new AuthRolePermissionEntity();
            entity.setRole("USER");
            entity.setPermissionCode(code);
            entity.setCreatedAt(LocalDateTime.of(2026, 10, 20, 9, 0));
            return entity;
        }).toList();
    }

    private static AuthPermissionEntity permission(String code) {
        AuthPermissionEntity entity = new AuthPermissionEntity();
        entity.setCode(code);
        entity.setName(code);
        entity.setModule(moduleOf(code));
        entity.setDescription(code);
        entity.setCreatedAt(LocalDateTime.of(2026, 10, 20, 9, 0));
        return entity;
    }

    private static RolePermissionsView role(PermissionCatalogView view, String role) {
        return view.roles().stream()
                .filter(item -> item.role().equals(role))
                .findFirst()
                .orElseThrow();
    }

    private static String moduleOf(String code) {
        int dot = code.indexOf('.');
        return dot < 0 ? "other" : code.substring(0, dot);
    }

    private static UserAccount account(String role) {
        LocalDateTime now = LocalDateTime.of(2026, 10, 23, 9, 0);
        return new UserAccount(1, "tester", "hash", "tester", null, null, null, role, "ACTIVE",
                now, now, now, "admin", now);
    }
}
