-- 统一权限目录：用户中台是角色与权限的唯一事实来源。
-- 业务表里的 role 继续保留，避免历史数据迁移；新代码以权限码判断能力。

CREATE TABLE auth_permission (
    code VARCHAR(64) NOT NULL,
    name VARCHAR(64) NOT NULL,
    module VARCHAR(32) NOT NULL,
    description VARCHAR(255) NOT NULL DEFAULT '',
    created_at DATETIME(3) NOT NULL,
    PRIMARY KEY (code),
    KEY idx_auth_permission_module (module)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

CREATE TABLE auth_role_permission (
    role VARCHAR(16) NOT NULL,
    permission_code VARCHAR(64) NOT NULL,
    created_at DATETIME(3) NOT NULL,
    PRIMARY KEY (role, permission_code),
    KEY idx_auth_role_permission_code (permission_code),
    CONSTRAINT fk_auth_role_permission_permission
        FOREIGN KEY (permission_code) REFERENCES auth_permission (code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci;

INSERT INTO auth_permission (code, name, module, description, created_at) VALUES
    ('user.account.manage', '账号审批', 'user', '审批、停用和启用账号', NOW(3)),
    ('user.role.manage', '角色权限配置', 'user', '维护角色与权限的对应关系', NOW(3)),
    ('chat.message.manage', '聊天治理', 'chat', '治理任意会话中的消息', NOW(3)),
    ('reader.public.upload', '上传公共书籍', 'reader', '向公共书库导入书籍', NOW(3)),
    ('reader.private.manage', '管理个人书库', 'reader', '维护自己的书籍、书单和分享', NOW(3)),
    ('reader.category.manage', '管理书籍分类', 'reader', '维护公共书库的分类', NOW(3)),
    ('reader.content.manage.any', '治理任意书籍', 'reader', '编辑、下架或删除任意书籍', NOW(3)),
    ('music.public.upload', '上传公共曲目', 'music', '向公共曲库导入音频', NOW(3)),
    ('music.private.manage', '管理个人音乐', 'music', '维护自己的曲目、歌单和分享', NOW(3)),
    ('music.category.manage', '管理音乐分类', 'music', '维护公共曲库的分类', NOW(3)),
    ('music.content.manage.any', '治理任意曲目', 'music', '编辑、下架或删除任意曲目', NOW(3));

-- 管理员拥有完整目录。权限目录后续新增项时，查询侧对 ADMIN 仍会动态返回全部权限。
INSERT INTO auth_role_permission (role, permission_code, created_at)
SELECT 'ADMIN', code, NOW(3) FROM auth_permission;

-- 普通用户默认可以上传公共内容，并管理自己的私有内容。
INSERT INTO auth_role_permission (role, permission_code, created_at) VALUES
    ('USER', 'reader.public.upload', NOW(3)),
    ('USER', 'reader.private.manage', NOW(3)),
    ('USER', 'music.public.upload', NOW(3)),
    ('USER', 'music.private.manage', NOW(3));
