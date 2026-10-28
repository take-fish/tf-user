package com.fishmatrix.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.fishmatrix.user.entity.AuthPermissionEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AuthPermissionMapper extends BaseMapper<AuthPermissionEntity> {
}
