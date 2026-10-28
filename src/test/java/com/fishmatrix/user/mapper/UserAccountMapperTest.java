package com.fishmatrix.user.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fishmatrix.user.converter.UserConverters;
import com.fishmatrix.user.domain.UserRole;
import com.fishmatrix.user.domain.UserStatus;
import com.fishmatrix.user.entity.UserAccountEntity;
import com.fishmatrix.user.model.UserAccount;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest(properties = {"spring.cloud.nacos.discovery.enabled=false"})
@Transactional
class UserAccountMapperTest {

    @Autowired
    private UserAccountMapper users;

    @Test
    void searchesByUsernameAndApplicationId() {
        long id = insert("repo_probe", UserStatus.PENDING);

        List<UserAccount> byName = search("repo_probe");
        List<UserAccount> byHash = search("#" + id);
        List<UserAccount> byPlainId = search(Long.toString(id));

        assertTrue(byName.stream().anyMatch(user -> user.id() == id));
        assertTrue(byHash.stream().anyMatch(user -> user.id() == id));
        assertTrue(byPlainId.stream().anyMatch(user -> user.id() == id));
    }

    @Test
    void keepsPendingUsersFirst() {
        insert("pending_probe", UserStatus.PENDING);
        insert("active_probe", UserStatus.ACTIVE);

        List<UserAccountEntity> page = users.search(null, "probe", null, 10, 0);

        assertEquals("PENDING", page.get(0).getStatus().name());
    }

    @Test
    void countsByFilterAndStatus() {
        insert("count_probe", UserStatus.PENDING);
        insert("count_probe2", UserStatus.PENDING);

        assertEquals(2L, users.countUsers("PENDING", "count_probe", null));

        Map<String, Long> counts = users.selectStatusCounts().stream()
                .collect(Collectors.toMap(
                        UserAccountMapper.StatusCount::status, UserAccountMapper.StatusCount::total));
        assertEquals(2L, counts.get("PENDING"));
    }

    @Test
    void updatesAndClearsAvatar() {
        LocalDateTime now = LocalDateTime.now();
        long id = insert("avatar_probe", UserStatus.ACTIVE);

        users.updateAvatar(id, "avatars/" + id + "/probe.png", "image/png", now);
        UserAccount withAvatar = require(id);
        assertTrue(withAvatar.hasAvatar());
        assertEquals("image/png", withAvatar.avatarContentType());
        assertTrue(withAvatar.avatarUrl().startsWith("/api/v1/user/avatars/" + id + "?v="));

        users.updateAvatar(id, null, null, now);
        assertFalse(require(id).hasAvatar());
    }

    @Test
    void activateAdminResetsPasswordAndKeepsFirstApproval() {
        LocalDateTime first = LocalDateTime.of(2026, 10, 20, 9, 0);
        long id = insert("admin_probe", UserStatus.PENDING);
        users.updateStatus(id, UserStatus.ACTIVE.name(), "seed", first);

        users.activateAdmin(id, "hash2", first.plusDays(1));

        UserAccount account = require(id);
        assertEquals(UserRole.ADMIN.name(), account.role());
        assertEquals(UserStatus.ACTIVE.name(), account.status());
        assertEquals("hash2", account.passwordHash());
        assertEquals(first, account.approvedAt());
        assertEquals("seed", account.approvedBy());
    }

    @Test
    void touchesLastLogin() {
        long id = insert("login_probe", UserStatus.ACTIVE);
        LocalDateTime now = LocalDateTime.now();

        users.touchLastLogin(id, now);

        assertNotNull(require(id).lastLoginAt());
    }

    private long insert(String username, UserStatus status) {
        UserAccountEntity entity = new UserAccountEntity();
        LocalDateTime now = LocalDateTime.now();
        entity.setUsername(username);
        entity.setPasswordHash("hash");
        entity.setNickname(username);
        entity.setRole(UserRole.USER);
        entity.setStatus(status);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        users.insert(entity);
        return entity.getId();
    }

    private List<UserAccount> search(String query) {
        String text = query.startsWith("#") ? query.substring(1) : query;
        Long id = null;
        try {
            id = Long.parseLong(text);
        } catch (NumberFormatException ignored) {
            // 按名号搜索
        }
        return users.search(null, text, id, 10, 0).stream()
                .map(UserConverters::toModel)
                .toList();
    }

    private UserAccount require(long id) {
        return UserConverters.toModel(users.selectById(id));
    }
}
