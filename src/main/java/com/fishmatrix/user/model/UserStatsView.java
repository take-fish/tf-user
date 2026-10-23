package com.fishmatrix.user.model;

/**
 * 面向普通登录用户的统计快照，不包含审核相关的明细。
 */
public record UserStatsView(long active, long total) {
}
