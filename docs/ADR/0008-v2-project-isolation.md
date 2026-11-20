# ADR-0008：v2 项目隔离与契约合规

- 状态：ACCEPTED（现有 v2）。后续权限迁移见 [ADR-0010](0010-ai-workspace.md)。
- 日期：2026-11-18
- 里程碑：M1.5
- 关联：ADR-0007（三端契约优先）、ADR-0003/0005/0006

## 背景

三端开发契约 1.0.0 已冻结（docs/contracts/），定义了 53 个 API 操作，其中 51 个为 SPECIFIED。Java 服务需要遵守契约规则并为 v2 业务建立基础设施。

## 决定

### P0 既有 v1 合规

- RequestIdFilter 始终生成有效 UUID（消除 "unknown"），校验客户端提供的 ID 格式
- Jackson 配置 write-dates-as-timestamps=false，确保 ISO-8601 输出
- Jackson 配置 fail-on-unknown-properties=true，v2 拒绝未知字段
- ErrorResponse 已符合契约 Error schema（code/message/requestId + additionalProperties:false）

### P1 v2 基础设施

- V6 迁移：projects + project_members 表，种子 DEMO 项目绑定 admin
- Project 域：7 个 API 操作（listProjects, createProject, getProject, updateProject, listMembers, setMember, removeMember）
- 项目级权限：OWNER/EDITOR/VIEWER 三级 + ADMIN 全局特权；hasAtLeast 层级比较
- v2 字符串 ID：DTO 层 String.valueOf(id)，契约 Id pattern ^[1-9][0-9]*$ maxLength 19
- 幂等键框架：Redis 存储，TTL 24h，key=idempotency:{actor}:{op}:{key}，降级放行
- 错误码扩展：PROJECT_NOT_FOUND(404), PROJECT_KEY_DUPLICATE(409), LAST_OWNER(409), IDEMPOTENCY_CONFLICT(409)
- PageResponse 提取到 platform/web，含 validatePagination(page, size) 静态方法
- project_key 校验：^[A-Z][A-Z0-9_-]{1,63}$，与契约 ProjectCreate.key pattern 一致

### 不在本轮范围

- S2-S9 业务切片（工作台聚合、Robot 导入、精确查询、运行记录、知识文档 v2、会话 SSE、草稿确认、审计）
- P3 内部 API（Java ↔ Python）
- v1 知识库/用例 CRUD 不修改语义

## 后果

- 新增 V6 Flyway 迁移
- 新增 modules/project 包（8 个文件）
- 新增 platform/idempotency 包（2 个文件）
- 新增 platform/web/PageResponse（从 knowledge/testcase 域提取）
- GlobalExceptionHandler 扩展 4 个新异常处理
- application.yaml 新增 Jackson 序列化/反序列化配置
- 集成测试 ProjectApiTest（8 个方法，需 MYSQL_PASSWORD）
