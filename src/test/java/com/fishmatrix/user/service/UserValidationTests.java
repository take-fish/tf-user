package com.fishmatrix.user.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class UserValidationTests {

    @Test
    void acceptsChineseAndAsciiUsernames() {
        assertDoesNotThrow(() -> UserValidation.requireUsername("摸鱼小明"));
        assertDoesNotThrow(() -> UserValidation.requireUsername("dev_user-01"));
        assertEquals("小明", UserValidation.normalizeUsername(" 小明 "));
    }

    @Test
    void rejectsInvalidUsernames() {
        assertThrows(IllegalArgumentException.class, () -> UserValidation.requireUsername(""));
        assertThrows(IllegalArgumentException.class, () -> UserValidation.requireUsername("a"));
        assertThrows(IllegalArgumentException.class,
                () -> UserValidation.requireUsername("名字里 有空格"));
        assertThrows(IllegalArgumentException.class,
                () -> UserValidation.requireUsername("a".repeat(33)));
    }

    @Test
    void enforcesPasswordRules() {
        assertDoesNotThrow(() -> UserValidation.requirePassword("secret12"));
        assertThrows(IllegalArgumentException.class, () -> UserValidation.requirePassword(""));
        assertThrows(IllegalArgumentException.class, () -> UserValidation.requirePassword("12345"));
        assertThrows(IllegalArgumentException.class,
                () -> UserValidation.requirePassword("a".repeat(65)));
        // BCrypt 的 72 字节上限
        assertThrows(IllegalArgumentException.class,
                () -> UserValidation.requirePassword("口".repeat(25)));
    }
}
