package com.fishmatrix.user.repository;

import com.fishmatrix.user.model.UserAccount;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.simple.SimpleJdbcInsert;
import org.springframework.stereotype.Repository;

@Repository
public class UserRepository {

    private static final String COLUMNS =
            "id, username, password_hash, nickname, role, status, created_at, updated_at, "
                    + "approved_at, approved_by, last_login_at";

    private static final RowMapper<UserAccount> ROW_MAPPER = (rs, rowNum) -> new UserAccount(
            rs.getLong("id"),
            rs.getString("username"),
            rs.getString("password_hash"),
            rs.getString("nickname"),
            rs.getString("role"),
            rs.getString("status"),
            localDateTime(rs, "created_at"),
            localDateTime(rs, "updated_at"),
            localDateTime(rs, "approved_at"),
            rs.getString("approved_by"),
            localDateTime(rs, "last_login_at"));

    private final JdbcTemplate jdbc;
    private final SimpleJdbcInsert insert;

    public UserRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
        this.insert = new SimpleJdbcInsert(jdbc)
                .withTableName("user_account")
                .usingGeneratedKeyColumns("id");
    }

    private static LocalDateTime localDateTime(ResultSet rs, String column) throws SQLException {
        Timestamp timestamp = rs.getTimestamp(column);
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }

    public Optional<UserAccount> findByUsername(String username) {
        List<UserAccount> rows = jdbc.query(
                "SELECT " + COLUMNS + " FROM user_account WHERE username = ?",
                ROW_MAPPER, username);
        return rows.stream().findFirst();
    }

    public Optional<UserAccount> findById(long id) {
        List<UserAccount> rows = jdbc.query(
                "SELECT " + COLUMNS + " FROM user_account WHERE id = ?",
                ROW_MAPPER, id);
        return rows.stream().findFirst();
    }

    public boolean existsByUsername(String username) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM user_account WHERE username = ?",
                Integer.class, username);
        return count != null && count > 0;
    }

    public long insert(String username, String passwordHash, String nickname, String role,
            String status, LocalDateTime now) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("username", username)
                .addValue("password_hash", passwordHash)
                .addValue("nickname", nickname)
                .addValue("role", role)
                .addValue("status", status)
                .addValue("created_at", now)
                .addValue("updated_at", now);
        Number id = insert.executeAndReturnKey(params);
        return id.longValue();
    }

    public void updatePasswordAndActivateAdmin(long id, String passwordHash, LocalDateTime now) {
        jdbc.update(
                "UPDATE user_account SET password_hash = ?, nickname = username, role = 'ADMIN', "
                        + "status = 'ACTIVE', approved_at = COALESCE(approved_at, ?), "
                        + "approved_by = COALESCE(approved_by, 'system'), updated_at = ? WHERE id = ?",
                passwordHash, now, now, id);
    }

    public void updateStatus(long id, String status, String approvedBy, LocalDateTime now) {
        jdbc.update(
                "UPDATE user_account SET status = ?, approved_at = ?, approved_by = ?, updated_at = ? "
                        + "WHERE id = ?",
                status, now, approvedBy, now, id);
    }

    public void touchLastLogin(long id, LocalDateTime now) {
        jdbc.update(
                "UPDATE user_account SET last_login_at = ?, updated_at = ? WHERE id = ?",
                now, now, id);
    }

    public List<UserAccount> search(String status, String query, int limit, int offset) {
        List<Object> params = new ArrayList<>();
        String where = whereClause(status, query, params);
        params.add(limit);
        params.add(offset);
        return jdbc.query(
                "SELECT " + COLUMNS + " FROM user_account" + where
                        + " ORDER BY CASE WHEN status = 'PENDING' THEN 0 ELSE 1 END, id DESC"
                        + " LIMIT ? OFFSET ?",
                ROW_MAPPER, params.toArray());
    }

    public long count(String status, String query) {
        List<Object> params = new ArrayList<>();
        String where = whereClause(status, query, params);
        Long total = jdbc.queryForObject(
                "SELECT COUNT(*) FROM user_account" + where, Long.class, params.toArray());
        return total == null ? 0 : total;
    }

    public Map<String, Long> statusCounts() {
        Map<String, Long> counts = new LinkedHashMap<>();
        jdbc.query("SELECT status, COUNT(*) AS total FROM user_account GROUP BY status", rs -> {
            counts.put(rs.getString("status"), rs.getLong("total"));
        });
        return counts;
    }

    private static String whereClause(String status, String query, List<Object> params) {
        StringBuilder where = new StringBuilder(" WHERE 1 = 1");
        if (status != null && !status.isBlank()) {
            where.append(" AND status = ?");
            params.add(status);
        }
        if (query != null && !query.isBlank()) {
            String keyword = query.trim();
            if (keyword.startsWith("#")) {
                keyword = keyword.substring(1);
            }
            where.append(" AND (username LIKE ?");
            params.add("%" + keyword + "%");
            try {
                long id = Long.parseLong(keyword);
                where.append(" OR id = ?");
                params.add(id);
            } catch (NumberFormatException ignored) {
                // 非数字关键字只按名号搜索
            }
            where.append(")");
        }
        return where.toString();
    }
}
