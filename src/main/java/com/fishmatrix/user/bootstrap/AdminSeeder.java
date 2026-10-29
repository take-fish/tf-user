package com.fishmatrix.user.bootstrap;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fishmatrix.user.converter.UserConverters;
import com.fishmatrix.user.domain.UserRole;
import com.fishmatrix.user.domain.UserStatus;
import com.fishmatrix.user.entity.UserAccountEntity;
import com.fishmatrix.user.mapper.UserAccountMapper;
import com.fishmatrix.user.model.UserAccount;
import com.fishmatrix.user.service.UserValidation;
import java.time.LocalDateTime;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class AdminSeeder implements ApplicationRunner {

    private final UserAccountMapper users;
    private final PasswordEncoder passwordEncoder;
    private final String username;
    private final String password;
    private final boolean seedEnabled;

    public AdminSeeder(UserAccountMapper users, PasswordEncoder passwordEncoder,
            @Value("${fm.user.admin.username:admin}") String username,
            @Value("${fm.user.admin.password:}") String password,
            @Value("${fm.user.admin.seed-enabled:false}") boolean seedEnabled) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.username = UserValidation.normalizeUsername(username);
        this.password = password == null ? "" : password;
        this.seedEnabled = seedEnabled;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!seedEnabled) {
            log.info("管理员自动同步已关闭（fm.user.admin.seed-enabled=false）");
            return;
        }
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

        UserAccount existing = UserConverters.toModel(
                users.selectOne(Wrappers.<UserAccountEntity>lambdaQuery()
                        .eq(UserAccountEntity::getUsername, username)));
        LocalDateTime now = LocalDateTime.now();

        if (existing == null) {
            UserAccountEntity entity = new UserAccountEntity();
            entity.setUsername(username);
            entity.setPasswordHash(passwordEncoder.encode(password));
            entity.setNickname(username);
            entity.setRole(UserRole.ADMIN);
            entity.setStatus(UserStatus.ACTIVE);
            entity.setCreatedAt(now);
            entity.setUpdatedAt(now);
            users.insert(entity);
            log.info("已创建管理员账号：{}", username);
            return;
        }

        boolean upToDate = existing.isAdmin()
                && existing.isActive()
                && passwordEncoder.matches(password, existing.passwordHash());
        if (!upToDate) {
            users.activateAdmin(existing.id(), passwordEncoder.encode(password), now);
            log.info("已同步管理员账号：{}", username);
        }
    }
}
