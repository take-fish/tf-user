package com.fishmatrix.user.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fishmatrix.common.error.ApiException;
import com.fishmatrix.user.converter.UserConverters;
import com.fishmatrix.user.domain.UserRole;
import com.fishmatrix.user.domain.UserStatus;
import com.fishmatrix.user.entity.UserAccountEntity;
import com.fishmatrix.user.mapper.UserAccountMapper;
import com.fishmatrix.user.model.LoginRequest;
import com.fishmatrix.user.model.LoginResult;
import com.fishmatrix.user.model.RegisterRequest;
import com.fishmatrix.user.model.RegisterResult;
import com.fishmatrix.user.model.UserAccount;
import com.fishmatrix.user.model.UserPage;
import com.fishmatrix.user.model.UserStats;
import com.fishmatrix.user.model.UserView;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserAccountService {

    private static final Set<String> STATUSES = Set.of(
            UserAccount.STATUS_PENDING,
            UserAccount.STATUS_ACTIVE,
            UserAccount.STATUS_REJECTED,
            UserAccount.STATUS_DISABLED);

    private final UserAccountMapper users;
    private final PasswordEncoder passwordEncoder;
    private final SessionService sessions;
    private final PermissionService permissions;
    private final AuthService auth;

    @Transactional
    public RegisterResult register(RegisterRequest request) {
        String username = UserValidation.normalizeUsername(request.username());
        String password = request.password();
        UserValidation.requireUsername(username);
        UserValidation.requirePassword(password);

        if (countByUsername(username) > 0) {
            throw usernameTaken();
        }

        LocalDateTime now = LocalDateTime.now();
        UserAccountEntity entity = new UserAccountEntity();
        entity.setUsername(username);
        entity.setPasswordHash(passwordEncoder.encode(password));
        entity.setNickname(username);
        entity.setRole(UserRole.USER);
        entity.setStatus(UserStatus.PENDING);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);
        try {
            users.insert(entity);
        } catch (DuplicateKeyException exception) {
            // 并发注册撞唯一索引时，仍然返回同一个“名号已被占用”的语义
            throw usernameTaken();
        }

        long id = entity.getId() == null ? 0L : entity.getId();
        log.info("user registration queued id={} username={} status={}",
                id, username, UserAccount.STATUS_PENDING);
        return new RegisterResult(id, UserAccount.STATUS_PENDING, "等待通过中");
    }

    @Transactional
    public LoginResult login(LoginRequest request) {
        String username = UserValidation.normalizeUsername(request.username());
        String password = request.password() == null ? "" : request.password();

        UserAccount account = findByUsername(username);
        if (account == null) {
            throw invalidCredentials();
        }

        if (!passwordEncoder.matches(password, account.passwordHash())) {
            throw invalidCredentials();
        }

        switch (account.status()) {
            case UserAccount.STATUS_PENDING -> throw new ApiException(
                    HttpStatus.FORBIDDEN, "PENDING_APPROVAL", "还在等待通过中，请稍后再来");
            case UserAccount.STATUS_REJECTED -> throw new ApiException(
                    HttpStatus.FORBIDDEN, "REJECTED", "这次没能通过审批，请联系管理员");
            case UserAccount.STATUS_DISABLED -> throw new ApiException(
                    HttpStatus.FORBIDDEN, "DISABLED", "账号已停用，请联系管理员");
            case UserAccount.STATUS_ACTIVE -> {
                // 正常登录
            }
            default -> throw invalidCredentials();
        }

        users.touchLastLogin(account.id(), LocalDateTime.now());
        // 刷新令牌进 Redis（可撤销），访问令牌是本地可验签的 JWT（短有效期）
        String refreshToken = sessions.create(account.id(), account.role());
        return auth.issueTokens(account, refreshToken);
    }

    public UserPage listUsers(String status, String query, int page, int size) {
        String normalizedStatus = status == null || status.isBlank()
                ? null : status.trim().toUpperCase();
        if (normalizedStatus != null && !STATUSES.contains(normalizedStatus)) {
            throw new IllegalArgumentException("未知的用户状态：" + status);
        }
        int safePage = Math.max(1, page);
        int safeSize = Math.min(100, Math.max(1, size));
        int offset = (safePage - 1) * safeSize;

        Keyword keyword = Keyword.of(query);
        List<UserView> items = users.search(
                        normalizedStatus, keyword.text(), keyword.id(), safeSize, offset)
                .stream()
                .map(UserConverters::toModel)
                .map(permissions::view)
                .toList();
        long total = users.countUsers(normalizedStatus, keyword.text(), keyword.id());
        return new UserPage(items, safePage, safeSize, total, stats());
    }

    @Transactional
    public UserView changeStatus(long targetId, String action, UserAccount admin) {
        UserAccount target = findById(targetId);
        if (target == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "没有找到这个用户");
        }

        if (target.id() == admin.id() && ("reject".equals(action) || "disable".equals(action))) {
            throw new ApiException(HttpStatus.CONFLICT, "SELF_ACTION", "不能对自己执行这个操作");
        }

        LocalDateTime now = LocalDateTime.now();
        switch (action) {
            case "approve" -> {
                requireStatus(target, Set.of(UserAccount.STATUS_PENDING, UserAccount.STATUS_REJECTED));
                users.updateStatus(target.id(), UserAccount.STATUS_ACTIVE, admin.username(), now);
            }
            case "reject" -> {
                requireStatus(target, Set.of(UserAccount.STATUS_PENDING));
                users.updateStatus(target.id(), UserAccount.STATUS_REJECTED, admin.username(), now);
            }
            case "disable" -> {
                requireStatus(target, Set.of(UserAccount.STATUS_ACTIVE));
                users.updateStatus(target.id(), UserAccount.STATUS_DISABLED, admin.username(), now);
            }
            case "enable" -> {
                requireStatus(target, Set.of(UserAccount.STATUS_DISABLED));
                users.updateStatus(target.id(), UserAccount.STATUS_ACTIVE, admin.username(), now);
            }
            default -> throw new ApiException(
                    HttpStatus.BAD_REQUEST, "BAD_ACTION", "不支持的操作：" + action);
        }

        return permissions.view(findById(target.id()));
    }

    public UserStats stats() {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (UserAccountMapper.StatusCount row : users.selectStatusCounts()) {
            counts.put(row.status(), row.total());
        }
        long total = counts.values().stream().mapToLong(Long::longValue).sum();
        long pending = counts.getOrDefault(UserAccount.STATUS_PENDING, 0L);
        long active = counts.getOrDefault(UserAccount.STATUS_ACTIVE, 0L);
        long rejected = counts.getOrDefault(UserAccount.STATUS_REJECTED, 0L);
        long disabled = counts.getOrDefault(UserAccount.STATUS_DISABLED, 0L);
        return new UserStats(total, pending, active, rejected, disabled);
    }

    private UserAccount findByUsername(String username) {
        return UserConverters.toModel(users.selectOne(Wrappers.<UserAccountEntity>lambdaQuery()
                .eq(UserAccountEntity::getUsername, username)));
    }

    private UserAccount findById(long id) {
        return UserConverters.toModel(users.selectById(id));
    }

    private long countByUsername(String username) {
        return users.selectCount(Wrappers.<UserAccountEntity>lambdaQuery()
                .eq(UserAccountEntity::getUsername, username));
    }

    private static void requireStatus(UserAccount target, Set<String> allowed) {
        if (!allowed.contains(target.status())) {
            throw new ApiException(HttpStatus.CONFLICT, "INVALID_STATE",
                    "当前状态不支持这个操作：" + target.status());
        }
    }

    private static ApiException invalidCredentials() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "INVALID_CREDENTIALS", "名号或口号不对");
    }

    private static ApiException usernameTaken() {
        return new ApiException(HttpStatus.CONFLICT, "USERNAME_TAKEN", "这个名号已经有人用了");
    }

    /**
     * 管理台搜索框：支持按名号模糊匹配，也支持 {@code #123} / {@code 123} 按 ID 精确匹配。
     */
    record Keyword(String text, Long id) {

        static Keyword of(String query) {
            if (query == null || query.isBlank()) {
                return new Keyword(null, null);
            }
            String text = query.trim();
            if (text.startsWith("#")) {
                text = text.substring(1);
            }
            Long id = null;
            try {
                id = Long.parseLong(text);
            } catch (NumberFormatException ignored) {
                // 非数字关键字只按名号搜索
            }
            return new Keyword(text, id);
        }
    }
}
