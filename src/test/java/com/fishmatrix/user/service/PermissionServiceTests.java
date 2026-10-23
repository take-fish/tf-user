package com.fishmatrix.user.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fishmatrix.common.error.ApiException;
import com.fishmatrix.user.auth.PermissionCodes;
import com.fishmatrix.user.model.PermissionCatalogView;
import com.fishmatrix.user.model.PermissionView;
import com.fishmatrix.user.model.RolePermissionsView;
import com.fishmatrix.user.model.UserAccount;
import com.fishmatrix.user.repository.PermissionRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
class PermissionServiceTests {

    @Mock
    private PermissionRepository permissions;

    private PermissionService service;

    private final List<PermissionView> catalog = PermissionCodes.ALL.stream()
            .map(code -> new PermissionView(code, code, moduleOf(code), code))
            .toList();

    @BeforeEach
    void setUp() {
        service = new PermissionService(permissions);
    }

    @Test
    void adminAlwaysReceivesFullCatalog() {
        when(permissions.findAll()).thenReturn(catalog);
        when(permissions.findCodesByRole("USER")).thenReturn(PermissionCodes.DEFAULT_USER);

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
        when(permissions.findAll()).thenReturn(catalog);

        assertEquals(PermissionCodes.ALL,
                service.permissionsFor(account("ADMIN")));
    }

    @Test
    void normalizesAndSavesRolePermissions() {
        when(permissions.countCodes(any())).thenReturn(2L);

        RolePermissionsView saved = service.updateRole(" user ", List.of(
                " " + PermissionCodes.READER_CATEGORY_MANAGE + " ",
                PermissionCodes.MUSIC_CATEGORY_MANAGE,
                PermissionCodes.MUSIC_CATEGORY_MANAGE,
                " "), account("ADMIN"));

        assertEquals("USER", saved.role());
        assertEquals("普通用户", saved.label());
        assertEquals(List.of(PermissionCodes.READER_CATEGORY_MANAGE,
                PermissionCodes.MUSIC_CATEGORY_MANAGE), saved.permissions());
        verify(permissions).replaceRolePermissions(eq("USER"), eq(Set.of(
                PermissionCodes.READER_CATEGORY_MANAGE,
                PermissionCodes.MUSIC_CATEGORY_MANAGE)), any(LocalDateTime.class));
    }

    @Test
    void rejectsUnknownRole() {
        ApiException exception = assertThrows(ApiException.class,
                () -> service.updateRole("GUEST", List.of(), account("ADMIN")));

        assertEquals(HttpStatus.BAD_REQUEST, exception.status());
        assertEquals("UNKNOWN_ROLE", exception.code());
        verify(permissions, never()).replaceRolePermissions(anyString(), any(), any());
    }

    @Test
    void rejectsUnknownPermissionCode() {
        when(permissions.countCodes(any())).thenReturn(1L);
        when(permissions.findAll()).thenReturn(catalog);

        ApiException exception = assertThrows(ApiException.class,
                () -> service.updateRole("USER", List.of(
                        PermissionCodes.READER_PUBLIC_UPLOAD, "reader.nope"), account("ADMIN")));

        assertEquals(HttpStatus.BAD_REQUEST, exception.status());
        assertEquals("UNKNOWN_PERMISSION", exception.code());
        assertTrue(exception.getMessage().contains("reader.nope"));
        verify(permissions, never()).replaceRolePermissions(anyString(), any(), any());
    }

    @Test
    void keepsRoleManagementPermissionOnAdmin() {
        when(permissions.countCodes(any())).thenReturn(1L);

        ApiException exception = assertThrows(ApiException.class,
                () -> service.updateRole("ADMIN",
                        List.of(PermissionCodes.READER_PUBLIC_UPLOAD), account("ADMIN")));

        assertEquals(HttpStatus.CONFLICT, exception.status());
        assertEquals("ADMIN_LOCKOUT", exception.code());
        verify(permissions, never()).replaceRolePermissions(anyString(), any(), any());
    }

    @Test
    void onlyAdminMayChangeRolePermissions() {
        ApiException exception = assertThrows(ApiException.class,
                () -> service.updateRole("USER",
                        List.of(PermissionCodes.READER_PUBLIC_UPLOAD), account("USER")));

        assertEquals(HttpStatus.FORBIDDEN, exception.status());
        assertEquals("FORBIDDEN", exception.code());
        verify(permissions, never()).replaceRolePermissions(anyString(), any(), any());
    }

    @Test
    void emptyRequestRevokesAllUserPermissions() {
        when(permissions.countCodes(any())).thenReturn(0L);

        RolePermissionsView saved = service.updateRole("USER", null, account("ADMIN"));

        assertEquals(List.of(), saved.permissions());
        verify(permissions).replaceRolePermissions(eq("USER"), eq(Set.of()),
                any(LocalDateTime.class));
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
        return new UserAccount(1, "tester", "hash", "tester", role, "ACTIVE", now, now,
                now, "admin", now);
    }
}
