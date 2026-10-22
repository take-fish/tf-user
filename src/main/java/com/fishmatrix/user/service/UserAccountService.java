package com.fishmatrix.user.service;

import com.fishmatrix.common.error.ApiException;
import com.fishmatrix.user.model.LoginRequest;
import com.fishmatrix.user.model.LoginResult;
import com.fishmatrix.user.model.RegisterRequest;
import com.fishmatrix.user.model.RegisterResult;
import com.fishmatrix.user.model.UserAccount;
import com.fishmatrix.user.model.UserPage;
import com.fishmatrix.user.model.UserStats;
import com.fishmatrix.user.model.UserView;
import com.fishmatrix.user.repository.UserRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserAccountService {

    private static final Logger log = LoggerFactory.getLogger(UserAccountService.class);

    private static final Set<String> STATUSES = Set.of(
            UserAccount.STATUS_PENDING,
            UserAccount.STATUS_ACTIVE,
            UserAccount.STATUS_REJECTED,
            UserAccount.STATUS_DISABLED);

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final SessionService sessions;

    public UserAccountService(UserRepository users, PasswordEncoder passwordEncoder,
            SessionService sessions) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.sessions = sessions;
    }

    @Transactional
    public RegisterResult register(RegisterRequest request) {
        String username = UserValidation.normalizeUsername(request.username());
        String password = request.password();
        UserValidation.requireUsername(username);
        UserValidation.requirePassword(password);

        if (users.existsByUsername(username)) {
            throw new ApiException(HttpStatus.CONFLICT, "USERNAME_TAKEN", "这个名号已经有人用了");
        }

        long id;
        try {
            id = users.insert(username, passwordEncoder.encode(password), username,
                    UserAccount.ROLE_USER, UserAccount.STATUS_PENDING, LocalDateTime.now());
        } catch (DuplicateKeyException exception) {
            throw new ApiException(HttpStatus.CONFLICT, "USERNAME_TAKEN", "这个名号已经有人用了");
        }

        log.info("user registration queued id={} username={} status={}",
                id, username, UserAccount.STATUS_PENDING);
        return new RegisterResult(id, UserAccount.STATUS_PENDING, "等待通过中");
    }

    @Transactional
    public LoginResult login(LoginRequest request) {
        String username = UserValidation.normalizeUsername(request.username());
        String password = request.password() == null ? "" : request.password();

        UserAccount account = users.findByUsername(username)
                .orElseThrow(UserAccountService::invalidCredentials);

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
        String token = sessions.create(account.id(), account.role());
        return new LoginResult(token, account.toView());
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

        List<UserView> items = users.search(normalizedStatus, query, safeSize, offset)
                .stream()
                .map(UserAccount::toView)
                .toList();
        long total = users.count(normalizedStatus, query);
        return new UserPage(items, safePage, safeSize, total, stats());
    }

    @Transactional
    public UserView changeStatus(long targetId, String action, UserAccount admin) {
        UserAccount target = users.findById(targetId)
                .orElseThrow(() -> new ApiException(
                        HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "没有找到这个用户"));

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

        return users.findById(target.id()).map(UserAccount::toView).orElseThrow();
    }

    public UserStats stats() {
        Map<String, Long> counts = users.statusCounts();
        long total = counts.values().stream().mapToLong(Long::longValue).sum();
        long pending = counts.getOrDefault(UserAccount.STATUS_PENDING, 0L);
        long active = counts.getOrDefault(UserAccount.STATUS_ACTIVE, 0L);
        long rejected = counts.getOrDefault(UserAccount.STATUS_REJECTED, 0L);
        long disabled = counts.getOrDefault(UserAccount.STATUS_DISABLED, 0L);
        return new UserStats(total, pending, active, rejected, disabled);
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
}
