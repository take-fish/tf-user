# tf-user

FishMatrix（摸鱼矩阵）用户中心服务：账号注册/登录、审批、会话与用户中台 —— Spring Boot 3 + JDK 17。

- 端口：`8101`
- 健康检查：`GET /actuator/health`、`GET /api/v1/user/health`
- 用户中台：`GET /admin/`（直连）或经网关 `GET /api/v1/user/admin/index.html`
- 认证接口：`/api/v1/user/auth/{register,login,me,logout}`，请求头 `Authorization: Bearer <token>`
- 管理接口：`/api/v1/user/admin/users`（仅 ADMIN），审批/拒绝/停用/启用
- 账号表：`user_account`（由 Flyway `db/migration/V1__baseline.sql` 创建）；会话：Redis `fm:user:session:*`
- 业务容器默认 `SPRING_FLYWAY_ENABLED=false`、`SQL_INIT_MODE=never`，表结构由独立迁移任务管理

管理员初始化受开关控制，默认关闭：

```bash
export USER_ADMIN_USERNAME=admin
export USER_ADMIN_PASSWORD=<至少 6 位>
export USER_ADMIN_SEED_ENABLED=true
# 仅当 seed-enabled=true 时执行：不存在则创建，已存在则同步密码并恢复 ACTIVE；
# 关闭时启动日志会提示“管理员自动同步已关闭”，不写库。
```

生产/测试请分别使用 `scripts/ensure-user-admin-secrets.sh`（位于 tf-deploy）生成到 `.env`。

## 本地开发

```bash
mvn spring-boot:run    # 启动开发服务
mvn -B test            # 运行测试
```

## 构建

```bash
mvn -B -DskipTests package   # 产物 target/tf-user.jar
java -jar target/tf-user.jar
```

## 分支模型

- `master`：保护分支，只接受 PR（CI 必须通过，squash 合并），合并后自动发版
- `test`：联调分支，开发分支直接合入（不走 PR），可直接推送
- 开发流：`feature/*` 开发 → 直接合入 `test` 联调 → 验证 OK 后从开发分支开 PR 合 `master` → 自动发版
