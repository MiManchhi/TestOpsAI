# API 契约

## M0 已实现

| 服务 | 方法与路径                     | 响应                  | 可见性               |
| ---- | ------------------------------ | --------------------- | -------------------- |
| Java | GET /actuator/health           | 200 `{"status":"UP"}` | 公开，不显示内部组件 |
| Java | GET /actuator/health/liveness  | 200，Actuator probe   | 本地及容器内部       |
| Java | GET /actuator/health/readiness | 200，Actuator probe   | 本地及容器内部       |
| AI   | GET /health/live               | 200 `{"status":"UP"}` | 本地及容器内部       |
| AI   | GET /health/ready              | 200 `{"status":"UP"}` | 本地及容器内部       |

Java 的其他匿名路径返回 401（未来在 M1 实现认证）；AI 其他路径为 404。M0 无业务 OpenAPI，无假实现接口。Python 默认关闭交互文档及 schema 端点。

容器 Nginx 公开 `GET /healthz`，返回 200、`text/plain`、`OK`；它仅表示 Web 入口可用。完整六服务状态、代理与资源检查由 [容器冒烟](../scripts/smoke.ps1) 执行，不能只用该路径推定整套系统健康。

## M1.1 已实现：结构化错误与请求追踪

- 所有非健康端点的错误响应统一为 `{ code, message, requestId }`。
- 每个请求分配 UUID v4 `X-Request-Id`：客户端可提供，否则服务端生成；响应头回传。
- 当前覆盖：404（NOT_FOUND）、500（INTERNAL_ERROR）；401 由 Spring Security 返回，响应头仍携带 requestId。
- 内部异常详情不暴露；日志通过 MDC 关联 requestId。

## M1.2 已实现：身份与授权

| 方法 | 路径               | 用途         | 权限   | 响应                                   |
| ---- | ------------------ | ------------ | ------ | -------------------------------------- |
| POST | /api/v1/auth/login | 登录签发 JWT | 公开   | 200 `{accessToken, expiresIn}` 或 401  |
| GET  | /api/v1/users/me   | 当前用户信息 | 已认证 | 200 `{id, username, roles, createdAt}` |

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

## M1–M4 契约草案

外部 REST 前缀 `/api/v1`；Java 为唯一业务入口。结构化错误含 `code/message/requestId`，鉴权失败 401、资源无权 403，分页排序字段白名单，写入按接口规定支持幂等键。具体 schema 与错误码待 M1 实现时冻结并导出 OpenAPI。

内部 AI 与 Tool API 使用服务身份、时效和最小授权上下文；不直接传递用户 JWT。流式事件为 `message.delta`、`tool.start`、`tool.end`、`message.done`、`error`，心跳用 SSE 注释行。字段草案见立项基线第 6 章，尚未实现。
