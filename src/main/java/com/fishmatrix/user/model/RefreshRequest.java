package com.fishmatrix.user.model;

/** 刷新访问令牌的请求体。 */
public record RefreshRequest(String refreshToken) {
}
