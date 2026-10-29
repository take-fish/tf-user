package com.fishmatrix.user.service;

import com.fishmatrix.common.error.ApiException;
import com.fishmatrix.common.storage.FileStorage;
import com.fishmatrix.common.storage.StoredFile;
import com.fishmatrix.user.converter.UserConverters;
import com.fishmatrix.user.mapper.UserAccountMapper;
import com.fishmatrix.user.model.UserAccount;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * 用户头像的落盘与读取。头像属于可公开展示的身份信息，
 * 读取接口不校验登录态；写入只允许本人操作。
 */
@Service
@Slf4j
public class UserAvatarService {

    private final FileStorage storage;
    private final UserAccountMapper users;
    private final long maxBytes;

    public UserAvatarService(FileStorage storage, UserAccountMapper users,
            @Value("${fm.user.avatar.max-bytes:2097152}") long maxBytes) {
        this.storage = storage;
        this.users = users;
        this.maxBytes = maxBytes <= 0 ? 2L * 1024 * 1024 : maxBytes;
    }

    @Transactional
    public UserAccount upload(UserAccount account, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest("请选择头像图片");
        }
        if (file.getSize() > maxBytes) {
            throw ApiException.badRequest("头像不能超过 " + (maxBytes / 1024 / 1024) + "MB");
        }
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException exception) {
            throw ApiException.badRequest("头像读取失败");
        }
        String contentType = detectContentType(bytes);
        if (contentType == null) {
            throw ApiException.badRequest("仅支持 PNG、JPEG、GIF、WebP 图片");
        }

        StoredFile stored;
        try (InputStream input = new ByteArrayInputStream(bytes)) {
            stored = storage.store(input, "avatar" + extensionOf(contentType), contentType,
                    "avatars/" + account.id(), maxBytes);
        } catch (IOException exception) {
            throw ApiException.internal("头像保存失败", exception);
        }

        String previousKey = account.avatarKey();
        try {
            users.updateAvatar(account.id(), stored.storageKey(), contentType, LocalDateTime.now());
        } catch (RuntimeException exception) {
            storage.delete(stored.storageKey());
            throw exception;
        }
        if (previousKey != null && !previousKey.equals(stored.storageKey())) {
            storage.delete(previousKey);
        }
        return require(account.id(), "账号不存在");
    }

    @Transactional
    public UserAccount clear(UserAccount account) {
        String previousKey = account.avatarKey();
        users.updateAvatar(account.id(), null, null, LocalDateTime.now());
        if (previousKey != null) {
            try {
                storage.delete(previousKey);
            } catch (RuntimeException exception) {
                log.warn("清理旧头像失败 key={}: {}", previousKey, exception.getMessage());
            }
        }
        return require(account.id(), "账号不存在");
    }

    public AvatarFile load(long userId) {
        UserAccount account = require(userId, "头像不存在");
        if (!account.hasAvatar()) {
            throw ApiException.notFound("头像不存在");
        }
        Resource resource = storage.load(account.avatarKey());
        String contentType = account.avatarContentType() == null
                ? "image/png"
                : account.avatarContentType();
        return new AvatarFile(resource, contentType);
    }

    public record AvatarFile(Resource resource, String contentType) {
    }

    /** 读取账号并转成对外模型；不存在时按调用方语义抛出 404。 */
    private UserAccount require(long userId, String message) {
        UserAccount account = UserConverters.toModel(users.selectById(userId));
        if (account == null) {
            throw ApiException.notFound(message);
        }
        return account;
    }

    private static String extensionOf(String contentType) {
        return switch (contentType) {
            case "image/jpeg" -> ".jpg";
            case "image/gif" -> ".gif";
            case "image/webp" -> ".webp";
            default -> ".png";
        };
    }

    static String detectContentType(byte[] data) {
        if (data == null || data.length < 6) return null;
        if (startsWith(data, new int[] {0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A})) {
            return "image/png";
        }
        if (startsWith(data, new int[] {0xFF, 0xD8, 0xFF})) {
            return "image/jpeg";
        }
        if (data[0] == 'G' && data[1] == 'I' && data[2] == 'F'
                && data[3] == '8' && (data[4] == '7' || data[4] == '9') && data[5] == 'a') {
            return "image/gif";
        }
        if (data.length >= 12
                && data[0] == 'R' && data[1] == 'I' && data[2] == 'F' && data[3] == 'F'
                && data[8] == 'W' && data[9] == 'E' && data[10] == 'B' && data[11] == 'P') {
            return "image/webp";
        }
        return null;
    }

    private static boolean startsWith(byte[] data, int[] prefix) {
        if (data.length < prefix.length) return false;
        for (int index = 0; index < prefix.length; index++) {
            if ((data[index] & 0xFF) != prefix[index]) return false;
        }
        return true;
    }
}
