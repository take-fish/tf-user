package com.fishmatrix.user.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/** 权限目录项：用户中台是权限码的唯一事实来源。 */
@Data
@TableName("auth_permission")
public class AuthPermissionEntity {

    @TableId(type = IdType.INPUT)
    private String code;

    private String name;

    private String module;

    private String description;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
