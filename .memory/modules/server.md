# Java 模块

2026-11-17 集成补充：main 已快进至 41ef74c，包含 M1.4 Redis 缓存/登录限流（实现与原验证见 [M1.4](../sessions/2026-11-17-12-m1-4-cache-ratelimit.md)）。后续 Robot 业务按 [契约 1.0.0](../../docs/contracts/README.md)、[领域模型](../../docs/DOMAIN_MODEL.md) 和 ADR-0007 开发；新业务使用 v2，已有 v1 语义不变。契约冻结不代表新业务接口已实现；进度见 DESIGN-CONTRACT-001。

更新：2026-11-17。状态：IMPLEMENTED（M1.1/M1.2/M1.3；历史本机验证见 session）。

Java 21 / Spring Boot 3.5.16，入口 [TestOpsApplication](../../apps/server/src/main/java/io/testopsai/server/TestOpsApplication.java)，依赖锁定在 [pom](../../apps/server/pom.xml)。业务归 modules/identity、modules/knowledge、modules/testcase，跨域认证与错误处理归 platform；按真实职责拆分，不预建空层。

M1.1/M1.2 已有 JDBC/Flyway 身份表与 V1–V4 迁移、BCrypt、JWT 签发/验证、登录与当前用户接口、结构化错误和 requestId。M1.3 新增 V5 迁移（knowledge_bases、kb_members、test_cases + 种子数据）、知识库 CRUD + 数据级权限（PRIVATE/SHARED/PUBLIC + ADMIN 全量）、测试用例 CRUD + 过滤/排序白名单 + owner/ADMIN 写权限。公开健康和登录，其余入口默认要求认证。外部契约见 [API](../../docs/API.md)，设计见 [身份 ADR](../../docs/ADR/0003-identity-data-baseline.md)、[JWT ADR](../../docs/ADR/0004-jwt-auth-rbac.md) 和 [业务元数据 ADR](../../docs/ADR/0005-business-metadata-crud.md)。

配置使用 MYSQL_* 和 JWT_SECRET；涉及配置变化必须同步审查 Compose server 环境、依赖启动顺序和 CI compose/containers 注入。迁移只前进，不修改已执行脚本。

历史验证：[M1.1 session](../sessions/2026-11-17-06-m1-1-data-baseline.md)、[M1.2 session](../sessions/2026-11-17-07-m1-2-auth.md)、[M1.3 session](../sessions/2026-11-17-11-m1-3-kb-tc-crud.md)。M1.3 验证含 JDK 21 编译、Spotless、Maven verify（非 DB 测试通过，DB 测试因无 MYSQL_PASSWORD 跳过）。

M1.4 缓存限流仍 PLANNED；当前具体任务只在 [CURRENT](../CURRENT.md) 维护。

2026-11-17 主线集成补验：完整 scripts/check.ps1 退出 0，Java verify/Spotless（33 项，28 项 DB 测试跳过）；不作为真实数据库验证或部署证据。见 [Web 集成 session](../sessions/2026-11-17-12-web-auth-publish.md)。
