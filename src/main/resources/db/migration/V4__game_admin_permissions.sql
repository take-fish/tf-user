-- 比赛域管理权限：排行榜治理与比赛记录治理。
-- 权限码发布后不得改名；ADMIN 角色由查询侧动态拿到完整目录，这里仍补一份映射，
-- 便于直接在 auth_role_permission 里查看管理员实际拥有的权限。
INSERT INTO auth_permission (code, name, module, description, created_at) VALUES
    ('game.leaderboard.manage', '管理排行榜', 'game', '调整排行榜数值、展示顺序与可见性', NOW(3)),
    ('game.record.manage', '管理比赛记录', 'game', '调整比赛记录的显示状态与展示顺序', NOW(3))
ON DUPLICATE KEY UPDATE name = VALUES(name), module = VALUES(module),
                        description = VALUES(description);

INSERT IGNORE INTO auth_role_permission (role, permission_code, created_at)
SELECT 'ADMIN', code, NOW(3)
  FROM auth_permission
 WHERE code IN ('game.leaderboard.manage', 'game.record.manage');
