package com.fishmatrix.user.repository;

import com.fishmatrix.user.model.PermissionView;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

@Repository
public class PermissionRepository {

    private static final RowMapper<PermissionView> PERMISSION_MAPPER = (rs, rowNum) ->
            new PermissionView(
                    rs.getString("code"),
                    rs.getString("name"),
                    rs.getString("module"),
                    rs.getString("description"));

    private final JdbcTemplate jdbc;

    public PermissionRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<PermissionView> findAll() {
        return jdbc.query(
                "SELECT code, name, module, description FROM auth_permission ORDER BY module, code",
                PERMISSION_MAPPER);
    }

    public List<String> findCodesByRole(String role) {
        if (role == null || role.isBlank()) return List.of();
        return jdbc.queryForList(
                "SELECT permission_code FROM auth_role_permission WHERE role = ? "
                        + "ORDER BY permission_code",
                String.class, role);
    }

    public void replaceRolePermissions(String role, Collection<String> codes, LocalDateTime now) {
        jdbc.update("DELETE FROM auth_role_permission WHERE role = ?", role);
        if (codes == null || codes.isEmpty()) return;
        jdbc.batchUpdate(
                "INSERT INTO auth_role_permission (role, permission_code, created_at) "
                        + "VALUES (?, ?, ?)",
                codes.stream()
                        .map(code -> new Object[] {role, code, now})
                        .toList());
    }

    public long countCodes(Collection<String> codes) {
        if (codes == null || codes.isEmpty()) return 0;
        String placeholders = String.join(",", codes.stream().map(code -> "?").toList());
        Long count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM auth_permission WHERE code IN (" + placeholders + ")",
                Long.class, codes.toArray());
        return count == null ? 0 : count;
    }
}
