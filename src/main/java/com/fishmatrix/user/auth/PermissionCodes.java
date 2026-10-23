package com.fishmatrix.user.auth;

import java.util.List;

/**
 * 全局权限码。格式为 {@code 模块.资源.动作}，一旦发布不得改名；
 * 新增能力时同时更新数据库迁移中的权限目录。
 */
public final class PermissionCodes {

    public static final String USER_ACCOUNT_MANAGE = "user.account.manage";
    public static final String USER_ROLE_MANAGE = "user.role.manage";
    public static final String CHAT_MESSAGE_MANAGE = "chat.message.manage";

    public static final String READER_PUBLIC_UPLOAD = "reader.public.upload";
    public static final String READER_PRIVATE_MANAGE = "reader.private.manage";
    public static final String READER_CATEGORY_MANAGE = "reader.category.manage";
    public static final String READER_CONTENT_MANAGE_ANY = "reader.content.manage.any";

    public static final String MUSIC_PUBLIC_UPLOAD = "music.public.upload";
    public static final String MUSIC_PRIVATE_MANAGE = "music.private.manage";
    public static final String MUSIC_CATEGORY_MANAGE = "music.category.manage";
    public static final String MUSIC_CONTENT_MANAGE_ANY = "music.content.manage.any";

    public static final List<String> ALL = List.of(
            USER_ACCOUNT_MANAGE,
            USER_ROLE_MANAGE,
            CHAT_MESSAGE_MANAGE,
            READER_PUBLIC_UPLOAD,
            READER_PRIVATE_MANAGE,
            READER_CATEGORY_MANAGE,
            READER_CONTENT_MANAGE_ANY,
            MUSIC_PUBLIC_UPLOAD,
            MUSIC_PRIVATE_MANAGE,
            MUSIC_CATEGORY_MANAGE,
            MUSIC_CONTENT_MANAGE_ANY);

    /** 普通用户默认拥有的权限。 */
    public static final List<String> DEFAULT_USER = List.of(
            READER_PUBLIC_UPLOAD,
            READER_PRIVATE_MANAGE,
            MUSIC_PUBLIC_UPLOAD,
            MUSIC_PRIVATE_MANAGE);

    private PermissionCodes() {
    }
}
