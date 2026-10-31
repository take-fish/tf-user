-- 智能助手（tf-agent）权限：并入用户中台的权限目录，agent 侧不再自建角色/权限体系。
-- 权限码发布后不得改名；ADMIN 由查询侧动态拿到完整目录，这里仍补一份映射便于核对。

INSERT INTO auth_permission (code, name, module, description, created_at) VALUES
    ('agent.chat.use', '使用智能助手', 'agent', '与智能助手对话、上传知识库文件（需管理员审核）', NOW(3)),
    ('agent.kb.manage', '知识库管理', 'agent', '审核/驳回文档、维护分类、删除与重建索引', NOW(3)),
    ('agent.profile.manage', '展示管理', 'agent', '维护 Agent 名称、3D 形象、问候语与推荐问题', NOW(3)),
    ('agent.console.view', 'Agent 管理台', 'agent', '查看工具调用流水与配置工具开关', NOW(3)),
    ('agent.deploy.trigger', '触发发布/重启', 'agent', '确认并执行发布、重启等写操作', NOW(3))
ON DUPLICATE KEY UPDATE name = VALUES(name), module = VALUES(module),
                        description = VALUES(description);

INSERT IGNORE INTO auth_role_permission (role, permission_code, created_at)
SELECT 'ADMIN', code, NOW(3)
  FROM auth_permission
 WHERE code LIKE 'agent.%';

INSERT IGNORE INTO auth_role_permission (role, permission_code, created_at) VALUES
    ('USER', 'agent.chat.use', NOW(3));
