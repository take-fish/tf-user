package com.fishmatrix.user.model;

public record PermissionView(
        String code,
        String name,
        String module,
        String description) {
}
