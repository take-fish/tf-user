package com.fishmatrix.user.entity;

import static org.assertj.core.api.Assertions.assertThat;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.lang.reflect.Field;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class EntityMappingTest {

    private static final Map<Class<?>, String> EXPECTED = Map.of(
            UserAccountEntity.class, "user_account",
            AuthPermissionEntity.class, "auth_permission",
            AuthRolePermissionEntity.class, "auth_role_permission");

    private static final Set<String> AUDIT_FIELDS = Set.of("createdAt", "updatedAt");

    @Test
    @DisplayName("实体与表名一一对应，复合主键表没有 @TableId")
    void tablesAndKeys() {
        EXPECTED.forEach((type, expectedTable) -> {
            TableName tableName = type.getAnnotation(TableName.class);
            assertThat(tableName).as("%s 缺少 @TableName", type.getSimpleName()).isNotNull();
            assertThat(tableName.value()).isEqualTo(expectedTable);
        });

        assertThat(hasAnnotatedId(UserAccountEntity.class)).isTrue();
        assertThat(hasAnnotatedId(AuthPermissionEntity.class)).isTrue();
        assertThat(hasAnnotatedId(AuthRolePermissionEntity.class))
                .as("auth_role_permission 是复合主键，不应有 @TableId")
                .isFalse();
    }

    @Test
    @DisplayName("主键策略：账号自增，权限码由业务指定")
    void idTypes() throws NoSuchFieldException {
        Field accountId = UserAccountEntity.class.getDeclaredField("id");
        assertThat(accountId.getAnnotation(TableId.class).type()).isEqualTo(IdType.AUTO);

        Field permissionCode = AuthPermissionEntity.class.getDeclaredField("code");
        assertThat(permissionCode.getAnnotation(TableId.class).type()).isEqualTo(IdType.INPUT);
    }

    @Test
    @DisplayName("审计字段用 LocalDateTime（表结构是 DATETIME(3)）并标注了 fill")
    void auditFieldsUseLocalDateTimeWithFill() {
        int total = 0;
        for (Class<?> type : EXPECTED.keySet()) {
            for (Field field : type.getDeclaredFields()) {
                if (!AUDIT_FIELDS.contains(field.getName())) {
                    continue;
                }
                total++;
                assertThat(field.getType())
                        .as("%s.%s 必须是 LocalDateTime", type.getSimpleName(), field.getName())
                        .isEqualTo(LocalDateTime.class);
                TableField tableField = field.getAnnotation(TableField.class);
                assertThat(tableField)
                        .as("%s.%s 缺少 @TableField", type.getSimpleName(), field.getName())
                        .isNotNull();
                assertThat(tableField.fill())
                        .as("%s.%s 的 fill 不能是 DEFAULT", type.getSimpleName(), field.getName())
                        .isNotEqualTo(FieldFill.DEFAULT);
            }
        }
        assertThat(total).as("审计字段总数应为 4").isEqualTo(4);
    }

    private static boolean hasAnnotatedId(Class<?> type) {
        for (Field field : type.getDeclaredFields()) {
            if (field.isAnnotationPresent(TableId.class)) {
                return true;
            }
        }
        return false;
    }
}
