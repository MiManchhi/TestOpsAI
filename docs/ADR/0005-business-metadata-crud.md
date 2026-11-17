# ADR-0005: 业务元数据最小 CRUD 与数据级权限

- 状态：ACCEPTED
- 日期：2026-11-17
- 里程碑：M1.3
- 关联：ADR-0003（身份基线）、ADR-0004（JWT RBAC）

## 背景

M1.3 需要在身份认证基础上实现两个核心业务域的最小 CRUD：知识库（knowledge bases）和测试用例（test cases）。需要定义数据模型、API 契约、数据级权限规则和分页策略。

## 决策

### 数据模型

- **knowledge_bases**：归属 owner_id，visibility 枚举（PRIVATE/SHARED/PUBLIC），status 控制归档。UQ(owner_id, name) 防止同用户重名。
- **kb_members**：复合主键 (kb_id, user_id)，access_level 区分 READ/WRITE。仅 visibility=SHARED 时使用。CASCADE 删除。
- **test_cases**：归属 owner_id，project_key/module/title 为核心字段，tags 逗号分隔 VARCHAR(512)，priority 枚举 P0-P3。组合索引覆盖主要查询路径。

### DELETE 语义

使用 status=ARCHIVED 代替物理删除或独立 deleted_at 字段。延续 M1.1 "不过早固化"原则；后续如需恢复或审计再引入专门机制。

### 数据级权限

- 知识库：PRIVATE 仅 owner；SHARED 查 kb_members；PUBLIC 所有已认证可读；ADMIN 全量。权限检查在 Service 层显式执行。
- 测试用例：owner 或 ADMIN 可写；所有已认证用户可读（MVP 简化，后续可按项目授权）。
- 不使用 Spring Security 注解做资源级检查；Service 方法内抛 AccessDeniedException，由 GlobalExceptionHandler 统一转 403。

### 分页与排序

- 统一响应格式 `{items, total, page, size}`。
- 排序字段白名单校验（createdAt, updatedAt, title, priority），非法字段返回 400 VALIDATION_ERROR。
- page/size 参数类型约束，size 上限 100。
- PageResponse 先在 knowledge 域内定义；第二个域出现时提取到 platform/web。遵循"先平铺后提取"原则。

### tags 存储

逗号分隔 VARCHAR(512)。MVP 不需要独立标签表；后续按真实查询需求拆分。

### kb_members 管理

M1.3 不暴露独立 members API；通过 Service 内部方法和种子数据管理。M2 前端需要时再加 POST/DELETE /api/v1/knowledge-bases/{id}/members。

## 后果

- 新增 V5 Flyway 迁移，三表 + 种子数据。
- 新增 modules/knowledge 和 modules/testcase 两个业务域，各含 record + Repository + Service + Controller + DTOs。
- GlobalExceptionHandler 扩展 KB_NOT_FOUND、TC_NOT_FOUND、KB_NAME_DUPLICATE、VALIDATION_ERROR 错误码。
- 集成测试覆盖 CRUD、权限、分页、过滤、排序白名单和错误码。
- 不新增环境变量，无需修改 compose.yaml 或 CI 配置。
