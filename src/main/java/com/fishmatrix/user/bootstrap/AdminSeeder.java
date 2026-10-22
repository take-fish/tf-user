package com.fishmatrix.user.bootstrap;

import com.fishmatrix.user.model.UserAccount;
import com.fishmatrix.user.repository.UserRepository;
import com.fishmatrix.user.service.UserValidation;
import java.time.LocalDateTime;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class AdminSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminSeeder.class);

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final String username;
    private final String password;

    public AdminSeeder(UserRepository users, PasswordEncoder passwordEncoder,
            @Value("${fm.user.admin.username:admin}") String username,
            @Value("${fm.user.admin.password:}") String password) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.username = UserValidation.normalizeUsername(username);
        this.password = password == null ? "" : password;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (password.isBlank()) {
            log.warn("USER_ADMIN_PASSWORD 未配置：跳过管理员初始化，用户中台暂时无法审批账号");
            return;
        }

        try {
            UserValidation.requireUsername(username);
            UserValidation.requirePassword(password);
        } catch (IllegalArgumentException exception) {
            log.error("管理员账号配置不合法：{}", exception.getMessage());
            return;
        }

        Optional<UserAccount> existing = users.findByUsername(username);
        LocalDateTime now = LocalDateTime.now();

        if (existing.isEmpty()) {
            users.insert(username, passwordEncoder.encode(password), username,
                    UserAccount.ROLE_ADMIN, UserAccount.STATUS_ACTIVE, now);
            log.info("已创建管理员账号：{}", username);
            return;
        }

        UserAccount account = existing.get();
        boolean upToDate = account.isAdmin()
                && account.isActive()
                && passwordEncoder.matches(password, account.passwordHash());
        if (!upToDate) {
            users.updatePasswordAndActivateAdmin(account.id(), passwordEncoder.encode(password), now);
            log.info("已同步管理员账号：{}", username);
        }
    }
}
