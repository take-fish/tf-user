package com.fishmatrix.user.testsupport;

import com.fishmatrix.user.domain.UserRole;
import com.fishmatrix.user.domain.UserStatus;
import com.fishmatrix.user.entity.UserAccountEntity;
import java.time.LocalDateTime;

/** 测试用的实体夹具：把常用的字段默认值集中在一处，避免每个测试重复拼装。 */
public final class UserFixtures {

    public static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 20, 9, 0);

    private UserFixtures() {
    }

    public static UserAccountEntity account(long id, String username, String role, String status) {
        return account(id, username, "hash", role, status, null);
    }

    public static UserAccountEntity account(long id, String username, String passwordHash,
            String role, String status, String avatarKey) {
        UserAccountEntity entity = new UserAccountEntity();
        entity.setId(id);
        entity.setUsername(username);
        entity.setPasswordHash(passwordHash);
        entity.setNickname(username);
        entity.setRole(role == null ? null : UserRole.valueOf(role));
        entity.setStatus(status == null ? null : UserStatus.valueOf(status));
        entity.setCreatedAt(NOW);
        entity.setUpdatedAt(NOW);
        if (avatarKey != null) {
            entity.setAvatarKey(avatarKey);
            entity.setAvatarContentType("image/png");
            entity.setAvatarUpdatedAt(NOW);
        }
        return entity;
    }
}
