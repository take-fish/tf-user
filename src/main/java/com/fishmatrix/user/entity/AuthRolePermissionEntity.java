package com.fishmatrix.user.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** 角色 → 权限码映射，复合主键 (role, permission_code)，因此没有 @TableId。 */
@Data
@TableName("auth_role_permission")
public class AuthRolePermissionEntity {

    private String role;

    private String permissionCode;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
