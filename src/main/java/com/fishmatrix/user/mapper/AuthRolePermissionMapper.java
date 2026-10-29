package com.fishmatrix.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fishmatrix.user.entity.AuthRolePermissionEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AuthRolePermissionMapper extends BaseMapper<AuthRolePermissionEntity> {
}
