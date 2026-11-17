# Java 模块

更新：2026-11-17。状态：IN_PROGRESS（M1.1 身份基线与错误契约已落地；认证/业务待实现）。

入口：[启动类](../../apps/server/src/main/java/io/testopsai/server/TestOpsApplication.java)，Java 21 / Spring Boot 3.5.16，依赖与插件固定在 [pom](../../apps/server/pom.xml)。

跨域安全配置归 [platform/security](../../apps/server/src/main/java/io/testopsai/server/platform/security/SecurityConfiguration.java)；结构化错误与 requestId 归 [platform/error](../../apps/server/src/main/java/io/testopsai/server/platform/error/)；未来业务归 modules/业务域，先平铺后按真实职责拆分，不建空包。测试对应源码包。

M1.1 新增：

- 持久化：spring-boot-starter-jdbc + mysql-connector-j + flyway-core/flyway-mysql；配置见 [application.yaml](../../apps/server/src/main/resources/application.yaml)，使用 MYSQL_* 环境变量。
- 迁移：[V1__identity_baseline.sql](../../apps/server/src/main/resources/db/migration/V1__identity_baseline.sql)，仅 users/roles/user_roles。
- 错误契约：{ code, message, requestId } + X-Request-Id 头；全局处理 NoResourceFoundException 与兜底 Exception。
- 集成测试：[IdentitySchemaIntegrationTest](../../apps/server/src/test/java/io/testopsai/server/modules/identity/IdentitySchemaIntegrationTest.java)、[ErrorContractTest](../../apps/server/src/test/java/io/testopsai/server/platform/error/ErrorContractTest.java)。

当前健康公开、其他路径默认拒绝；默认用户自动装配已排除。迁移后根目录直接调用 Wrapper 已 verify 成功，2 项边界测试通过；maven.ps1 提供统一入口。新的完整门禁和 clean 重建证据见 [本次 session](../sessions/2026-11-16-04-engineering-layout.md)。

JWT/RBAC、业务 CRUD、缓存限流尚未实现；进入 M1.2+ 时再引入，不因工程骨架可运行就宣称业务完成。
