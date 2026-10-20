package com.fishmatrix.user.service;

import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

public final class UserValidation {

    private static final Pattern USERNAME_PATTERN =
            Pattern.compile("^[A-Za-z0-9_\\-\\u4e00-\\u9fa5]{2,32}$");

    private UserValidation() {
    }

    public static String normalizeUsername(String raw) {
        return raw == null ? "" : raw.trim();
    }

    public static void requireUsername(String username) {
        if (username.isEmpty()) {
            throw new IllegalArgumentException("请先告诉我，你是谁？");
        }
        if (!USERNAME_PATTERN.matcher(username).matches()) {
            throw new IllegalArgumentException("名号需为 2–32 位，可用中英文、数字、下划线或短横线");
        }
    }

    public static void requirePassword(String password) {
        if (password == null || password.isEmpty()) {
            throw new IllegalArgumentException("口号不能为空");
        }
        if (password.length() < 6 || password.length() > 64
                || password.getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new IllegalArgumentException("口号长度需为 6–64 个字符（且不超过 72 字节）");
        }
    }
}
