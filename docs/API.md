# API 契约

## 开发与兼容接口

最终版以[工作台契约说明](contracts/README.md)与[机器可读 OpenAPI](contracts/workspace.openapi.json)为输入。本文余下部分描述已交付的旧端点，供兼容与迁移核对。v1 登录/当前用户继续复用；新的项目授权语义使用 v3。完成状态见[联调记录](../.memory/sessions/2026-11-19-03-three-tier-integration.md)。

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
- 既有 Web 有登录、项目与用例目录等界面，已证实能通的真实链路见联调记录。

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

## M1.6 已实现：v2 用例目录（套件 / 用例 / 选择快照）

按契约 1.0.0 实现项目内 Robot 套件、用例精确查询与跨页选择快照；v1 的知识库与用例 CRUD 语义不变。

### 接口

| 方法  | 路径                                             | 用途                   | 权限        | operationId         |
| ----- | ------------------------------------------------ | ---------------------- | ----------- | ------------------- |
| GET   | /api/v2/projects/{projectId}/test-suites         | Robot 套件树（分页）   | 项目 VIEWER | listSuites          |
| GET   | /api/v2/projects/{projectId}/test-cases          | 精确筛选用例（分页）   | 项目 VIEWER | listTestCases       |
| POST  | /api/v2/projects/{projectId}/test-cases          | 创建人工用例           | 项目 EDITOR | createTestCase      |
| GET   | /api/v2/projects/{projectId}/test-cases/{caseId} | 用例详情               | 项目 VIEWER | getTestCase         |
| PATCH | /api/v2/projects/{projectId}/test-cases/{caseId} | 编辑人工用例（版本锁） | 项目 EDITOR | updateTestCase      |
| POST  | /api/v2/projects/{projectId}/case-selections     | 固化跨页选择集合       | 项目 VIEWER | createCaseSelection |

### 过滤与排序

- `suiteId` + `includeDescendants`（默认 true）：服务端递归解析子孙套件。
- `module` 精确匹配；`query` 对标题与说明做大小写不敏感包含；`tags` 精确匹配，`tagsMatch` 默认 ALL；`priority`、`sourceType`、`lifecycleStatus` 精确。
- `lifecycleStatus` 默认 ACTIVE（列表默认不返回归档用例）。
- `sort` 仅接受 `title,asc`、`title,desc`、`updatedAt,asc`、`updatedAt,desc`，默认 `title,asc`，末位以 ID 稳定排序。
- 分页 `page` 从 0 开始、`size` 1–100、默认 20；越界返回 400，不做静默截断。

### 写入规则

- `POST /test-cases` 必须携带 `Idempotency-Key`（UUID）。同键同内容重放返回首次创建的用例；同键不同内容返回 409 `IDEMPOTENCY_CONFLICT`。
- `PATCH /test-cases/{caseId}` 必须携带 `version`；版本过期返回 409 `VERSION_CONFLICT`；ROBOT 来源用例只读，返回 409 `ROBOT_CASE_READ_ONLY`。
- 选择快照 `mode` 为 `EXPLICIT` 或 `FILTER`，二者互斥：EXPLICIT 必须给出 `caseIds` 且不得带 `filter`/`excludedCaseIds`，FILTER 必须给出 `filter` 且不得带 `caseIds`，否则 400。FILTER 结果超过 1000 条返回 422 `SELECTION_TOO_LARGE`。
- 快照保存不可变 `caseId/version` 集合、摘要与 `expiresAt`（24 小时）。

### 错误码扩展

| code                 | HTTP | 场景                             |
| -------------------- | ---- | -------------------------------- |
| CASE_NOT_FOUND       | 404  | 用例不存在或不属于该项目         |
| SUITE_NOT_FOUND      | 404  | 套件不存在或不属于该项目         |
| VERSION_CONFLICT     | 409  | 用例版本过期                     |
| ROBOT_CASE_READ_ONLY | 409  | Robot 来源用例不可直接编辑       |
| SELECTION_TOO_LARGE  | 422  | FILTER 选择结果超过 1000 条      |
| VALIDATION_ERROR     | 400  | 请求体不可读、参数/枚举/分页非法 |
