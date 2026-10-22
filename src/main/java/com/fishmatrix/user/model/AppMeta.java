package com.fishmatrix.user.model;

/**
 * 运行环境标识。用于登录页和用户中台明确展示当前连接的是生产还是测试数据，
 * 避免把测试环境的申请错看成生产申请。
 */
public record AppMeta(
        String environment,
        String environmentLabel,
        String database,
        String namespace,
        String version) {
}
