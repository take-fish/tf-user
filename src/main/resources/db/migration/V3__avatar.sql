-- 用户头像：文件落本地存储卷，数据库只保存存储标识与内容类型。
-- avatar_updated_at 同时作为头像 URL 的版本号，避免浏览器继续使用旧图片。
ALTER TABLE user_account ADD COLUMN avatar_key VARCHAR(160);
ALTER TABLE user_account ADD COLUMN avatar_content_type VARCHAR(64);
ALTER TABLE user_account ADD COLUMN avatar_updated_at DATETIME(3);
