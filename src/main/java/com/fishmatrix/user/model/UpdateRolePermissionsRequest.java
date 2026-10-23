package com.fishmatrix.user.model;

import java.util.List;

public record UpdateRolePermissionsRequest(List<String> permissions) {
}
