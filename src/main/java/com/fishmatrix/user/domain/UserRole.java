package com.fishmatrix.user.domain;

import com.baomidou.mybatisplus.annotation.IEnum;

/** 账号角色。权限判定以权限码为准，角色只用于默认权限集与展示。 */
public enum UserRole implements IEnum<String> {
    USER,
    ADMIN;

    @Override
    public String getValue() {
        return name();
    }
}
