# ADR-0009：v2 用例目录与会话前查询基础

- 状态：ACCEPTED
- 日期：2026-11-18
- 里程碑：M1.6（SERVER-V2-001 第二检查点）
- 关联：ADR-0007（契约优先）、ADR-0008（v2 项目隔离）

## 背景

契约 1.0.0 冻结了项目内 Robot 套件、用例精确查询与选择快照（listSuites、listTestCases、getTestCase、createTestCase、updateTestCase、createCaseSelection）。ADR-0008 已交付项目/成员隔离，本轮补上该契约要求的第一批业务读取与写入。

## 决定

### 表与 v1 的边界

v1 的 `test_cases`（owner_id + 逗号标签 + 无版本）语义按契约保持不动。v2 使用新表，不修改既有迁移：

- `suites`：唯一（project_id, source_path, revision），自引用 parent_id；父子同项目。
- `project_test_cases`：唯一（project_id, stable_key），version 乐观锁；suite_id 可空（MANUAL 用例不属于套件）。
- `project_case_tags`：主键（case_id, tag），精确标签查询；不使用 LIKE 子串替代。
- `case_selections` + `selection_items`：UUID 主键、不可变（case_id, version）集合、摘要与 24 小时到期。

物理表名带 `project_` 前缀，避免与 v1 同名表冲突（Flyway 不能对已执行脚本改名）。`docs/DOMAIN_MODEL.md` 中的 `test_cases` 为聚合层命名，物理表以此 ADR 为准。

### 查询语义

- 同一 `CaseFilter` 记录同时服务 HTTP 列表与后续 `search_test_cases` 工具，禁止两套实现。
- `module` 精确、`query` 对 title/documentation 大小写不敏感包含（LIKE 转义 `%`、`_`）、`tags` 精确且 `tagsMatch` 默认 ALL、`suiteId` 默认含子孙（服务端递归 CTE 解析）。
- `sort` 只接受 title/updatedAt 的 asc/desc 四种组合，末位以 ID 排序保证稳定；其余 400。
- `lifecycleStatus` 默认 ACTIVE（列表不默认展示归档用例）；`page`/`size` 越界返回 400，不静默截断。
- 资源 ID 输出为十进制字符串；跨项目访问一律 404（不泄露存在性）。

### 写入语义

- 创建人工用例要求 `Idempotency-Key`（UUID）；同键同内容重放返回原用例，同键不同内容 409 IDEMPOTENCY_CONFLICT。哈希覆盖规范化后的请求字段。
- MANUAL 用例的 `stableKey` 为 `manual:<uuid>`；ROBOT 用例的 stableKey 由导入切片按契约生成。
- PATCH 必须带 `version`；CAS 更新失败返回 409 VERSION_CONFLICT；ROBOT 用例正文只读，返回 409 ROBOT_CASE_READ_ONLY。
- 选择快照：EXPLICIT 与 FILTER 互斥（同时出现或 FILTER 缺 filter 一律 400）；EXPLICIT 的 caseIds 必须全部存在于本项目（否则 404 CASE_NOT_FOUND）；FILTER 结果按同一 CaseFilter 计算并应用 excludedCaseIds，超过 1000 条返回 422 SELECTION_TOO_LARGE。摘要为排序后 `caseId:version` 换行串的 SHA-256。

### 权限

- 读取：项目成员（VIEWER 及以上）；写入：EDITOR 及以上；ADMIN 全局。
- 每次请求重新解析项目成员关系，不缓存授权结果。

## 后果

- 新增 V8 迁移（4 张表 + 自造演示套件/用例/标签）。
- 新增 modules/catalog；复用 ProjectService 的成员/角色校验，新增 platform/security/CurrentUserResolver 消除控制器重复的用户解析。
- GlobalExceptionHandler 新增 CASE_NOT_FOUND、SUITE_NOT_FOUND、VERSION_CONFLICT、ROBOT_CASE_READ_ONLY、SELECTION_TOO_LARGE，并把请求体不可读/参数类型错误统一为 400 VALIDATION_ERROR。
- 集成测试 CaseCatalogApiTest 需真实 MySQL；未配置时跳过，不能当作已联调。
- Robot 导入、运行结果、知识文档、会话 SSE、工具网关仍为后续切片。
