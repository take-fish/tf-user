package com.fishmatrix.user.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.fishmatrix.user.domain.UserRole;
import com.fishmatrix.user.domain.UserStatus;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 用户账号。时间列沿用 DATETIME(3)，因此这里用 LocalDateTime 而不是 Instant，
 * 与既有 API 返回的时间口径保持一致。
 */
@Data
@TableName("user_account")
public class UserAccountEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String username;

    private String passwordHash;

    private String nickname;

    private String avatarKey;

    private String avatarContentType;

    private LocalDateTime avatarUpdatedAt;

    private UserRole role;

    private UserStatus status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    private LocalDateTime approvedAt;

    private String approvedBy;

    private LocalDateTime lastLoginAt;
}
