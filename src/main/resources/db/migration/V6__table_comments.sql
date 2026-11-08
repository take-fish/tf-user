-- tf-user 全表中文注释
-- 只修改表与字段注释，不改变类型、默认值、索引；列定义逐字取自 MySQL 8.4 的 SHOW CREATE TABLE。
-- 列注释统一用 ALGORITHM=INSTANT，若定义与生产不一致会直接报错，避免静默改结构。

ALTER TABLE `user_account` COMMENT = '用户账号：登录凭据、角色、审批状态与头像';

ALTER TABLE `user_account`
    MODIFY COLUMN `id` bigint NOT NULL AUTO_INCREMENT COMMENT '账号主键，自增',
    MODIFY COLUMN `username` varchar(32) NOT NULL COMMENT '登录账号，全局唯一',
    MODIFY COLUMN `password_hash` varchar(100) NOT NULL COMMENT '登录密码的 BCrypt 哈希，不保存明文',
    MODIFY COLUMN `nickname` varchar(32) NOT NULL COMMENT '展示昵称',
    MODIFY COLUMN `role` varchar(16) NOT NULL DEFAULT 'USER' COMMENT '角色，USER 普通用户 / ADMIN 管理员',
    MODIFY COLUMN `status` varchar(16) NOT NULL DEFAULT 'PENDING' COMMENT '审批状态，PENDING 待审批 / ACTIVE 已启用 / REJECTED 已拒绝 / DISABLED 已停用',
    MODIFY COLUMN `created_at` datetime(3) NOT NULL COMMENT '注册时间',
    MODIFY COLUMN `updated_at` datetime(3) NOT NULL COMMENT '基本信息最后更新时间',
    MODIFY COLUMN `approved_at` datetime(3) DEFAULT NULL COMMENT '审批通过时间，未审批为空',
    MODIFY COLUMN `approved_by` varchar(32) DEFAULT NULL COMMENT '审批管理员账号，未审批为空',
    MODIFY COLUMN `last_login_at` datetime(3) DEFAULT NULL COMMENT '最近一次登录成功时间',
    MODIFY COLUMN `avatar_key` varchar(160) DEFAULT NULL COMMENT '头像文件存储键，为空表示未设置头像',
    MODIFY COLUMN `avatar_content_type` varchar(64) DEFAULT NULL COMMENT '头像文件 MIME 类型',
    MODIFY COLUMN `avatar_updated_at` datetime(3) DEFAULT NULL COMMENT '头像最后更新时间',
    ALGORITHM=INSTANT;

ALTER TABLE `auth_permission` COMMENT = '权限目录：权限码、名称与所属模块';

ALTER TABLE `auth_permission`
    MODIFY COLUMN `code` varchar(64) NOT NULL COMMENT '权限码主键，如 user.approve',
    MODIFY COLUMN `name` varchar(64) NOT NULL COMMENT '权限名称',
    MODIFY COLUMN `module` varchar(32) NOT NULL COMMENT '所属模块，如 user / chat / reader',
    MODIFY COLUMN `description` varchar(255) NOT NULL DEFAULT '' COMMENT '权限说明',
    MODIFY COLUMN `created_at` datetime(3) NOT NULL COMMENT '记录创建时间',
    ALGORITHM=INSTANT;

ALTER TABLE `auth_role_permission` COMMENT = '角色与权限的绑定关系';

ALTER TABLE `auth_role_permission`
    MODIFY COLUMN `role` varchar(16) NOT NULL COMMENT '角色，USER 普通用户 / ADMIN 管理员',
    MODIFY COLUMN `permission_code` varchar(64) NOT NULL COMMENT '权限码，关联 auth_permission.code',
    MODIFY COLUMN `created_at` datetime(3) NOT NULL COMMENT '绑定时间',
    ALGORITHM=INSTANT;
