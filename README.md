# tf-user

FishMatrix（摸鱼矩阵）用户中心服务：账号注册/登录、审批、会话、统一 RBAC 与身份中台 —— Spring Boot 3 + JDK 17。

- 端口：`8101`
- 健康检查：`GET /actuator/health`、`GET /api/v1/user/health`
- 用户中台：`GET /admin/`（直连）或经网关 `GET /api/v1/user/admin/index.html`
- 认证接口：`/api/v1/user/auth/{register,login,me,logout}`，请求头 `Authorization: Bearer <token>`
- 管理接口：`/api/v1/user/admin/users`（仅 ADMIN），审批/拒绝/停用/启用
- 权限接口：`/api/v1/user/admin/permissions`、`/api/v1/user/admin/roles/{role}/permissions`
- 账号表：`user_account`；权限表：`auth_permission` / `auth_role_permission`（Flyway `db/migration/V2__rbac.sql` 创建）；会话：Redis `fm:user:session:*`
- 业务容器默认 `SPRING_FLYWAY_ENABLED=false`、`SQL_INIT_MODE=never`，表结构由独立迁移任务管理

## 统一权限（RBAC）

权限只在用户中台维护，各业务服务不建自己的权限表。网关登录后向业务服务注入签名身份头，业务服务通过 `tf-common` 的 `CurrentUser` 读取权限快照。

### 权限码

| 权限码 | 说明 |
|---|---|
| `user.account.manage` | 用户审批与启停 |
| `user.role.manage` | 角色授权 |
| `chat.message.manage` | 聊天消息管理 |
| `reader.public.upload` | 上传公共书籍 |
| `reader.private.manage` | 管理个人书库/书单 |
| `reader.category.manage` | 维护书籍分类 |
| `reader.content.manage.any` | 删除/编辑任意书籍 |
| `music.public.upload` | 上传公共音乐 |
| `music.private.manage` | 管理个人歌单 |
| `music.category.manage` | 维护音乐分类 |
| `music.content.manage.any` | 删除/编辑任意音乐 |

角色 `USER` 默认拥有 reader / music 的 `public.upload` 与 `private.manage`；角色 `ADMIN` 始终持有全部权限，不接受编辑。

### 接口

| 方法 | 路径 | 权限 | 说明 |
|---|---|---|---|
| GET | `/api/v1/user/auth/me` | 登录 | 身份快照（id、username、nickname、role、roles、permissions），网关 introspection 也用它 |
| GET | `/api/v1/user/stats` | 登录 | 用户规模快照 `{active,total}`，首页用启用用户数作为总人数分母 |
| GET | `/api/v1/user/admin/permissions` | ADMIN | 角色列表 + 全量权限目录 + 各角色已有授权 |
| PUT | `/api/v1/user/admin/roles/{role}/permissions` | `user.role.manage` | body `{"permissions":["reader.public.upload", ...]}`，整体覆盖 |

保存规则：角色名去空格并转大写；权限去空、去重；未知角色返回 400 `UNKNOWN_ROLE`，未知权限码返回 400 `UNKNOWN_PERMISSION`；ADMIN 若被移除 `user.role.manage` 返回 409 `ADMIN_LOCKOUT`；空数组表示撤销该角色全部权限。修改即时生效，业务服务最迟在身份缓存 TTL（默认 30 秒）后拿到新快照。

### 管理页

`/admin/` 提供两个标签页：

- **用户审批**：按状态/关键字筛选，审批、拒绝、停用、启用；
- **角色权限**：左侧角色列表，右侧按模块分组的权限勾选，支持草稿、撤销、保存；无 `user.role.manage` 时只读，ADMIN 行只读并标注“全部权限”。

页面通过 Bearer token 调用上述接口，不直接读数据库。

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
