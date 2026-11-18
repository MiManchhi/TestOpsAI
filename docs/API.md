# API 契约

## 当前开发冻结基线

2026-11-17：新增 [三端开发契约 1.0.0](contracts/README.md)（OpenAPI + 共享 schema + 事件/工具/页面映射），依据智能电表 Robot 背景确定。新业务使用 `/api/v2`，内部 `/internal/v1`；既有 `/api/v1/auth/login`、`/users/me` 与下述历史 CRUD 保持现状。新增契约标为 SPECIFIED，不能当作接口已实现；实际运行事实以下述已实现章节和代码为准。最终页面见 [产品设计](PRODUCT_DESIGN.md)。

## v1/v2 共存策略

- **v1 保留范围**：`/api/v1/auth/login`、`/api/v1/users/me`、`/api/v1/knowledge-bases/*`、`/api/v1/test-cases/*`。这些是 M1.1–M1.4 已实现的历史接口，保持现有行为不变。
- **v2 新增范围**：`/api/v2/*` 按冻结契约 1.0.0 实施，包含项目隔离、字符串 ID、数组标签、Robot 来源、运行语义等新能力。Web 新业务页面只接 v2。
- **过渡规则**：v1 知识库/用例 CRUD 与 v2 项目域用例不混用 ID；v1 数字 ID 与 v2 字符串 ID 不互转。最终 Web 新业务只接 v2，v1 CRUD 作为 legacy 保留至 v2 对应功能完全替代后由 ADR 决定废弃。
- **内部 API**：`/internal/v1/*` 仅服务网络开放，不透传用户 JWT。

## M0 已实现

| 服务 | 方法与路径                     | 响应                  | 可见性               |
| ---- | ------------------------------ | --------------------- | -------------------- |
| Java | GET /actuator/health           | 200 `{"status":"UP"}` | 公开，不显示内部组件 |
| Java | GET /actuator/health/liveness  | 200，Actuator probe   | 本地及容器内部       |
| Java | GET /actuator/health/readiness | 200，Actuator probe   | 本地及容器内部       |
| AI   | GET /health/live               | 200 `{"status":"UP"}` | 本地及容器内部       |
| AI   | GET /health/ready              | 200 `{"status":"UP"}` | 本地及容器内部       |

M0 时 Java 的其他匿名路径返回 401；M1.2 已开放下述登录入口，其余业务入口需认证。AI 其他路径为 404。M0 无业务 OpenAPI，无假实现接口。Python 默认关闭交互文档及 schema 端点。

容器 Nginx 公开 `GET /healthz`，返回 200、`text/plain`、`OK`；它仅表示 Web 入口可用。完整六服务状态、代理与资源检查由 [容器冒烟](../scripts/smoke.ps1) 执行，不能只用该路径推定整套系统健康。

## M1.1 已实现：结构化错误与请求追踪

- 所有非健康端点的错误响应统一为 `{ code, message, requestId }`。
- 每个请求分配 UUID v4 `X-Request-Id`：客户端可提供，否则服务端生成；响应头回传。
- 当前覆盖：404（NOT_FOUND）、500（INTERNAL_ERROR）；401 由 Spring Security 返回，响应头仍携带 requestId。
- 内部异常详情不暴露；日志通过 MDC 关联 requestId。

## M1.2 已实现：身份与授权

更新：2026-11-17。以下契约以 Java M1.2 实现为准，Web 登录使用同源 Java 入口。

| 方法 | 路径                 | 请求                                  | 权限   | 成功响应                                      |
| ---- | -------------------- | ------------------------------------- | ------ | --------------------------------------------- |
| POST | `/api/v1/auth/login` | JSON `{ username, password }`         | 公开   | 200 JSON `{ accessToken, expiresIn }`         |
| GET  | `/api/v1/users/me`   | `Authorization: Bearer <accessToken>` | 已认证 | 200 JSON `{ id, username, roles, createdAt }` |

- 登录字段为字符串；令牌默认有效期 1800 秒，`expiresIn` 单位为秒。无刷新令牌或服务端退出接口。
- 当前用户 `id` 为数字，`username` 为字符串，`roles` 为角色码字符串数组，`createdAt` 为 ISO-8601 UTC 时间。
- 凭据错误返回 401，`code=INVALID_CREDENTIALS`；缺失或无效令牌返回 401，权限拒绝返回 403。认证/授权错误 JSON 为 `{ code, message, requestId }`，响应携带 `X-Request-Id`。
- Web 将令牌与到期时间存入 `sessionStorage`，不保存密码；刷新后调用当前用户接口重新验证。403 保留会话并提示无权限；401 或本地过期清理会话。退出仅清理当前标签页会话，不撤销已签发 JWT。
- Web 页面路径为 `/login`、`/overview`、`/knowledge`、`/test-cases`。M1.3 知识库/用例 CRUD 已实现；Web v2 用例目录已合入 main（基于冻结契约的 contract provider，真实 v2 API 联调仍 BLOCKED）。

- JWT 签名密钥由环境变量 `JWT_SECRET` 提供（至少 32 字节）；token 有效期 30 分钟。
- 401 响应统一为 `{code: "UNAUTHORIZED", message, requestId}`；403 为 `{code: "FORBIDDEN", message, requestId}`。
- 登录失败返回 `{code: "INVALID_CREDENTIALS", message, requestId}`。
- RBAC 使用 Spring Security `hasRole()` 角色码检查；数据级权限留 M1.3。
- V2/V3 迁移种子 ADMIN/USER 角色和演示管理员 admin/admin123（仅限本地开发）。

## M1.3 已实现：知识库与测试用例 CRUD + 数据级权限

### 知识库 API

| 方法   | 路径                         | 用途         | 权限                            | 请求体                                        | 响应                         |
| ------ | ---------------------------- | ------------ | ------------------------------- | --------------------------------------------- | ---------------------------- |
| GET    | /api/v1/knowledge-bases      | 列表（分页） | 已认证                          | —                                             | `{items, total, page, size}` |
| POST   | /api/v1/knowledge-bases      | 创建         | 已认证                          | `{name, description?, visibility?}`           | `{id, name, ...}` 201        |
| GET    | /api/v1/knowledge-bases/{id} | 详情         | 读权限（见下方规则）            | —                                             | `{id, name, ...}`            |
| PUT    | /api/v1/knowledge-bases/{id} | 更新         | 写权限（owner 或 WRITE member） | `{name?, description?, visibility?, status?}` | `{id, name, ...}`            |
| DELETE | /api/v1/knowledge-bases/{id} | 归档         | 写权限                          | —                                             | 204                          |

**数据级权限规则：**

- PRIVATE：仅 owner 可读写
- SHARED：owner + kb_members 中对应 access_level 的用户
- PUBLIC：所有已认证用户可读，仅 owner 可写
- ADMIN 角色可读写所有知识库

### 测试用例 API

| 方法   | 路径                    | 用途              | 权限           | 请求体                                                           | 响应                         |
| ------ | ----------------------- | ----------------- | -------------- | ---------------------------------------------------------------- | ---------------------------- |
| GET    | /api/v1/test-cases      | 列表（分页+过滤） | 已认证         | —                                                                | `{items, total, page, size}` |
| POST   | /api/v1/test-cases      | 创建              | 已认证         | `{projectKey, module, title, tags?, priority?, expectedResult?}` | `{id, ...}` 201              |
| GET    | /api/v1/test-cases/{id} | 详情              | 已认证         | —                                                                | `{id, ...}`                  |
| PUT    | /api/v1/test-cases/{id} | 更新              | owner 或 ADMIN | `{title?, module?, tags?, priority?, expectedResult?, status?}`  | `{id, ...}`                  |
| DELETE | /api/v1/test-cases/{id} | 归档              | owner 或 ADMIN | —                                                                | 204                          |

**过滤参数（GET 列表）：**

- `projectKey`：精确匹配
- `module`：模糊匹配（LIKE）
- `priority`：精确匹配
- `status`：默认 ACTIVE
- `page`（默认 0）、`size`（默认 20，最大 100）
- `sort`：白名单 createdAt, updatedAt, title, priority；默认 createdAt,desc

### 分页响应格式

```json
{
  "items": [...],
  "total": 42,
  "page": 0,
  "size": 20
}
```

### 错误码扩展

| code              | HTTP | 场景                         |
| ----------------- | ---- | ---------------------------- |
| KB_NOT_FOUND      | 404  | 知识库不存在                 |
| TC_NOT_FOUND      | 404  | 测试用例不存在               |
| KB_NAME_DUPLICATE | 409  | 同一 owner 下知识库名重复    |
| VALIDATION_ERROR  | 400  | 请求体校验失败或排序字段非法 |
| FORBIDDEN         | 403  | 数据级权限检查未通过         |

### M1.4 已实现：登录限流与知识库缓存

**登录限流：**

- `POST /api/v1/auth/login` 受滑动窗口限流保护：60 秒内同一 IP 最多 10 次。
- 超限返回 429 `{code: "RATE_LIMITED", message, requestId}` + `Retry-After` 响应头。
- Redis 不可用时降级放行，记录 WARN 日志。

**知识库列表缓存：**

- `GET /api/v1/knowledge-bases` 结果缓存 60 秒（Redis）。
- 创建/更新/归档操作自动失效缓存。
- Redis 不可用时透明降级为直接查库。

**错误码扩展：**

| code         | HTTP | 场景                 |
| ------------ | ---- | -------------------- |
| RATE_LIMITED | 429  | 登录接口超过频率限制 |

## M1–M4 原草案（历史，已由 1.0.0 冻结契约取代）

原草案设想外部 REST 前缀 `/api/v1`，具体 schema 尚未冻结。后续新增业务改按上面的 v2 契约实施，不能继续据此各自定义字段。Java 仍为唯一浏览器业务入口，错误保持 `code/message/requestId`。

原流式事件名 `message.delta/tool.start/tool.end/message.done/error` 尚未运行实现，现由冻结契约的 `turn.* / stage.changed / tool.started / tool.finished / answer.delta / citation.available / draft.available` 替代。不能混用两套事件名称；心跳仍为 SSE 注释行。
