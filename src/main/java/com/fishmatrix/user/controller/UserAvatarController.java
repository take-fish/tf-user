package com.fishmatrix.user.controller;

import com.fishmatrix.common.error.ApiException;
import com.fishmatrix.user.model.UserAccount;
import com.fishmatrix.user.model.UserView;
import com.fishmatrix.user.service.AuthService;
import com.fishmatrix.user.service.PermissionService;
import com.fishmatrix.user.service.UserAvatarService;
import java.time.Duration;
import java.util.regex.Pattern;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 头像写入需要登录；读取公开，供聊天、比赛等列表直接使用 {@code <img>} 展示。
 */
@RestController
@RequestMapping("/api/v1/user")
public class UserAvatarController {

    private static final Pattern USER_ID = Pattern.compile("[uU]?\\d{1,18}");

    private final AuthService auth;
    private final PermissionService permissions;
    private final UserAvatarService avatars;

    public UserAvatarController(AuthService auth, PermissionService permissions,
            UserAvatarService avatars) {
        this.auth = auth;
        this.permissions = permissions;
        this.avatars = avatars;
    }

    @PostMapping(value = "/auth/avatar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public UserView upload(
            @RequestHeader(value = "Authorization", required = false) String authorization,
            @RequestPart("file") MultipartFile file) {
        UserAccount account = auth.requireActiveUser(authorization);
        return permissions.view(avatars.upload(account, file));
    }

    @DeleteMapping("/auth/avatar")
    public UserView clear(
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        UserAccount account = auth.requireActiveUser(authorization);
        return permissions.view(avatars.clear(account));
    }

    @GetMapping("/avatars/{userId}")
    public ResponseEntity<Resource> avatar(@PathVariable String userId) {
        UserAvatarService.AvatarFile file = avatars.load(parseUserId(userId));
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.contentType()))
                .cacheControl(CacheControl.maxAge(Duration.ofMinutes(5)).cachePublic())
                .header("X-Content-Type-Options", "nosniff")
                .body(file.resource());
    }

    private static long parseUserId(String value) {
        String cleaned = value == null ? "" : value.strip();
        if (!USER_ID.matcher(cleaned).matches()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_USER_ID", "用户标识不合法");
        }
        long id = Long.parseLong(cleaned.replaceFirst("^[uU]", ""));
        if (id <= 0) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "INVALID_USER_ID", "用户标识不合法");
        }
        return id;
    }
}
