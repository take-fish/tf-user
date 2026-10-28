package com.fishmatrix.user.domain;

import com.baomidou.mybatisplus.annotation.IEnum;

/** 账号状态：注册后待审批，管理员审批/停用/启用。 */
public enum UserStatus implements IEnum<String> {
    PENDING,
    ACTIVE,
    REJECTED,
    DISABLED;

    @Override
    public String getValue() {
        return name();
    }
}
