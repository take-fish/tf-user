package com.fishmatrix.user.model;

/**
 * 登录/刷新结果。
 *
 * <p>{@code token} 是 JWT 访问令牌（短有效期，服务端本地验签）；
 * {@code refreshToken} 是不透明刷新令牌（存 Redis，可撤销）。旧客户端只读 {@code token} 仍然可用。</p>
 */
public record LoginResult(String token, String refreshToken, long expiresIn, UserView user) {

    /** 兼容旧调用点：没有刷新令牌时使用。 */
    public LoginResult(String token, UserView user) {
        this(token, null, 0, user);
    }
}
