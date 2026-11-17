# Java 模块

更新：2026-11-17。状态：IMPLEMENTED（M1.1/M1.2；历史本机验证见 session）。

Java 21 / Spring Boot 3.5.16，入口 [TestOpsApplication](../../apps/server/src/main/java/io/testopsai/server/TestOpsApplication.java)，依赖锁定在 [pom](../../apps/server/pom.xml)。业务归 modules/identity，跨域认证与错误处理归 platform；按真实职责拆分，不预建空层。

M1.1/M1.2 已有 JDBC/Flyway 身份表与 V1–V4 迁移、BCrypt、JWT 签发/验证、登录与当前用户接口、结构化错误和 requestId。公开健康和登录，其余入口默认要求认证；角色检查不等于资源级授权。外部契约见 [API](../../docs/API.md)，设计见 [身份 ADR](../../docs/ADR/0003-identity-data-baseline.md) 和 [JWT ADR](../../docs/ADR/0004-jwt-auth-rbac.md)。

配置使用 MYSQL_* 和 JWT_SECRET；涉及配置变化必须同步审查 Compose server 环境、依赖启动顺序和 CI compose/containers 注入。迁移只前进，不修改已执行脚本。

历史验证：[M1.1 session](../sessions/2026-11-17-06-m1-1-data-baseline.md)、[M1.2 session](../sessions/2026-11-17-07-m1-2-auth.md)。记录含真实数据库 Maven verify、完整门禁（其中 DB 测试可跳过）及经 Nginx 的登录/用户探测；本轮协作初始化没有重跑这些业务验证。实现已进入 e11c558，随后有 aeed610 CI 配置修复；最新远端结果须另查，不能引用 M0 CI 推断。

M1.3 业务 CRUD、资源级权限、M1.4 缓存限流仍 PLANNED；当前具体任务只在 [CURRENT](../CURRENT.md) 维护。
