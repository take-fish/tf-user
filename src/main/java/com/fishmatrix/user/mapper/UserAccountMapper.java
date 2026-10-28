package com.fishmatrix.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fishmatrix.user.entity.UserAccountEntity;
import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface UserAccountMapper extends BaseMapper<UserAccountEntity> {

    /**
     * 管理员初始化/同步：重置密码并强制提升为 ADMIN，approved_at/approved_by 只在为空时补齐。
     */
    int activateAdmin(@Param("id") long id,
            @Param("passwordHash") String passwordHash,
            @Param("now") LocalDateTime now);

    int updateStatus(@Param("id") long id,
            @Param("status") String status,
            @Param("approvedBy") String approvedBy,
            @Param("now") LocalDateTime now);

    /** 头像字段允许清空，所以不能用 updateById（null 字段会被跳过）。 */
    int updateAvatar(@Param("id") long id,
            @Param("storageKey") String storageKey,
            @Param("contentType") String contentType,
            @Param("now") LocalDateTime now);

    int touchLastLogin(@Param("id") long id, @Param("now") LocalDateTime now);

    /** status/keyword/id 都由服务层预先归一化，SQL 只负责拼装。 */
    List<UserAccountEntity> search(@Param("status") String status,
            @Param("keyword") String keyword,
            @Param("id") Long id,
            @Param("limit") int limit,
            @Param("offset") int offset);

    long countUsers(@Param("status") String status,
            @Param("keyword") String keyword,
            @Param("id") Long id);

    List<StatusCount> selectStatusCounts();

    record StatusCount(String status, long total) {
    }
}
