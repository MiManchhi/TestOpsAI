# M1.1 数据基线实施

更新：2026-11-17。状态：IN_PROGRESS。

## 目标与起点

- 用户已同意从 M1.1 数据基线开始；重要决策需提问；允许按 Conventional Commits 创建提交；推送仍需单独授权。
- 起点 b58e301，main 跟踪 origin/main，工作区干净。M0 容器交付与 CI 已通过；本轮不修改容器/CI，不进入 M1.2+。
- 验收：首批迁移可重复执行；用户/角色关系与约束有真实 MySQL 集成测试证据；DATABASE/API/ADR/模块记忆同步；scripts/check.ps1 通过。

## 计划

1. 读取 ADR、API、DATABASE 及现有 Java 骨架，冻结 M1.2 认证所需最小 schema（users/roles/user_roles）。
2. 引入必要依赖（MySQL driver、MyBatis-Plus、Flyway 等），锁定版本；更新 pom 与 lock。
3. 编写 V1 迁移与回滚策略说明；实现基础实体/Repository/集成测试配置。
4. 补充失败路径与约束测试（唯一、外键、空值、重复迁移）。
5. 同步文档与记忆；运行完整门禁；按规范提交。

## 决策待确认

- ID 生成策略、时间精度、软删除/审计字段范围、错误码与结构化错误格式需在实施前冻结。

## 风险

- schema 过早固化；仅保留认证必需字段，其余延后。
- 依赖升级影响既有门禁；锁定版本并在提交前全量检查。

## 已确认决策（2026-11-17）

- 主键：BIGINT 自增。
- 时间：DATETIME(6) UTC，统一存储 UTC，边界转换时区。
- 审计字段：M1.1 仅 created_at / updated_at；软删除与版本号延后到 M1.3+ 视真实需求引入。
- 结构化错误：同步冻结最小格式 { code, message, requestId }；requestId 在 Java 入口生成并透传日志。
- 范围：仅 users / roles / user_roles 三表及必要约束；不为预想业务提前建表或加字段。

## 实施结果与验证（2026-11-17）

### 变更清单

- pom.xml：新增 spring-boot-starter-jdbc、mysql-connector-j、flyway-core、flyway-mysql。
- application.yaml：增加 spring.datasource（MYSQL_* 环境变量）、spring.flyway 配置。
- db/migration/V1__identity_baseline.sql：users / roles / user_roles 三表，BIGINT 自增，DATETIME(6) UTC，唯一/外键约束。
- platform/error/RequestIdFilter.java：生成/透传 X-Request-Id，MDC 绑定 requestId。
- platform/error/ErrorResponse.java：record(code, message, requestId)。
- platform/error/GlobalExceptionHandler.java：404 NOT_FOUND、500 INTERNAL_ERROR 统一格式；内部异常不泄露。
- ErrorContractTest.java：验证健康端点、401 路径均携带 UUID requestId 头；Flyway/DataSource 在非 DB 测试中禁用。
- IdentitySchemaIntegrationTest.java：验证表存在、唯一约束、外键；@EnabledIfEnvironmentVariable(MYSQL_PASSWORD)。
- BoundaryTest.java：补充 Flyway/DataSource 排除属性，保持原有断言。
- docs/DATABASE.md：标记 M1.1 已实现，保留后续草案。
- docs/API.md：补充 M1.1 结构化错误与 requestId 说明。
- .memory/modules/server.md：更新为 IN_PROGRESS，列出 M1.1 产物。
- docs/ADR/0003-identity-data-baseline.md：记录四项决策及代价。

### 真实验证命令与结果

| 命令                               | 退出码 | 证据范围                                                                                                           |
| ---------------------------------- | ------ | ------------------------------------------------------------------------------------------------------------------ |
| ./scripts/maven.ps1 spotless:apply | 0      | Java 格式统一                                                                                                      |
| mvn verify（含 MYSQL_PASSWORD）    | 0      | 9 tests, 0 failures；Flyway 迁移成功；约束测试通过                                                                 |
| scripts/check.ps1                  | 0      | 目录/记忆/Prettier/Vue/Java/Python/PS 全部门禁通过；IdentitySchemaIntegrationTest 因无 MYSQL_PASSWORD 跳过（预期） |

### 状态

- M1.1 数据基线：VERIFIED（本机 MySQL 8.4.8 + JDK 21 + Spring Boot 3.5.16）。
- 结构化错误契约：VERIFIED（requestId 头在 401/健康端点均存在；404 body 统一待 M1.2 自定义 EntryPoint）。
- BLOCKED：无。推送授权仍需用户单独确认。
