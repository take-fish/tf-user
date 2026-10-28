package com.fishmatrix.user.converter;

import com.fishmatrix.user.domain.UserRole;
import com.fishmatrix.user.domain.UserStatus;
import com.fishmatrix.user.entity.AuthPermissionEntity;
import com.fishmatrix.user.entity.UserAccountEntity;
import com.fishmatrix.user.model.PermissionView;
import com.fishmatrix.user.model.UserAccount;

/**
 * 实体 ⇄ 对外模型的无状态转换。
 *
 * <p>模型仍是按字符串 role/status 暴露（前端与其它服务已经在用），
 * 实体侧用枚举保证库里只会写入合法值，转换处收口。</p>
 */
public final class UserConverters {

    private UserConverters() {
    }

    public static UserAccount toModel(UserAccountEntity entity) {
        if (entity == null) {
            return null;
        }
        return new UserAccount(
                entity.getId() == null ? 0L : entity.getId(),
                entity.getUsername(),
                entity.getPasswordHash(),
                entity.getNickname(),
                entity.getAvatarKey(),
                entity.getAvatarContentType(),
                entity.getAvatarUpdatedAt(),
                name(entity.getRole()),
                name(entity.getStatus()),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                entity.getApprovedAt(),
                entity.getApprovedBy(),
                entity.getLastLoginAt());
    }

    public static UserAccountEntity toEntity(UserAccount account) {
        UserAccountEntity entity = new UserAccountEntity();
        entity.setId(account.id());
        entity.setUsername(account.username());
        entity.setPasswordHash(account.passwordHash());
        entity.setNickname(account.nickname());
        entity.setAvatarKey(account.avatarKey());
        entity.setAvatarContentType(account.avatarContentType());
        entity.setAvatarUpdatedAt(account.avatarUpdatedAt());
        entity.setRole(account.role() == null ? null : UserRole.valueOf(account.role()));
        entity.setStatus(account.status() == null ? null : UserStatus.valueOf(account.status()));
        entity.setCreatedAt(account.createdAt());
        entity.setUpdatedAt(account.updatedAt());
        entity.setApprovedAt(account.approvedAt());
        entity.setApprovedBy(account.approvedBy());
        entity.setLastLoginAt(account.lastLoginAt());
        return entity;
    }

    public static PermissionView toPermissionView(AuthPermissionEntity entity) {
        if (entity == null) {
            return null;
        }
        return new PermissionView(
                entity.getCode(), entity.getName(), entity.getModule(), entity.getDescription());
    }

    private static String name(Enum<?> value) {
        return value == null ? null : value.name();
    }
}
