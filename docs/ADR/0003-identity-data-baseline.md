# ADR-0003：M1.1 身份数据基线与结构化错误契约

更新：2026-11-17。状态：采纳（M1.1 实施中）。

## 背景与决定

M0 已完成工程骨架与容器交付，但无业务表与统一错误格式。为避免后续认证/业务反复调整 schema 与契约，在 M1.1 冻结最小身份基线与错误响应格式。

- 主键：BIGINT 自增。理由：单机 MySQL 下实现简单、索引紧凑；个人项目无需全局唯一 ID，后续如需对外暴露可加编码/UUID 别名。
- 时间：DATETIME(6) UTC。理由：微秒精度覆盖 Java Instant / Python datetime；统一 UTC 避免时区歧义，边界转换业务时区。
- 审计字段：M1.1 仅 created_at / updated_at。理由：满足认证与基本追踪；软删除、版本号等延后到 M1.3+ 视真实需求引入，避免过早固化。
- 结构化错误：{ code, message, requestId } + X-Request-Id 响应头。理由：与 API 契约草案一致；requestId 在 Java 入口生成并透传日志，便于排查；内部异常不泄露。
- 范围：仅 users / roles / user_roles 三表及必要约束；不为预想业务提前建表或加字段。

## 约束、成本与替代

- BIGINT 自增不适合未来多主写入或跨环境合并；若后续出现该需求，需评估 UUID v7 或 Snowflake 迁移成本。
- DATETIME(6) 不自动感知时区，应用层必须保证写入 UTC；已在 Jackson 与 JDBC URL 中显式指定 serverTimezone=UTC。
- 错误契约当前仅覆盖 404/500；401/403 由 Spring Security 返回，后续 M1.2 需扩展自定义 AuthenticationEntryPoint / AccessDeniedHandler 以统一 body。
- Flyway baseline-on-migrate=false，要求全新库或已有兼容 schema；CI/本地首次启动需确保库为空或使用正确凭据。

兼容性依据：Spring Boot 3.5.x、MySQL 8.4.x、Flyway 10.x；版本锁定于 pom.xml。
