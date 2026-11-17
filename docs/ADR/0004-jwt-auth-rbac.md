# ADR-0004：M1.2 JWT 认证与角色码授权

更新：2026-11-17。状态：采纳（M1.2 实施中）。

## 背景与决定

M1.1 建立了身份基线和结构化错误契约，但所有非健康路径仍返回 denyAll 401。M1.2 引入真实的认证与授权机制。

- JWT 签名：HMAC-SHA256，密钥从环境变量 JWT_SECRET 注入（至少 32 字节）；不使用默认值，启动时校验。
- Token 有效期：30 分钟；不含 refresh token 机制（后续按需引入）。
- 密码存储：BCrypt cost 10；仅存哈希，不暴露内部异常。
- RBAC：Spring Security hasRole() 角色码检查（ROLE_ADMIN / ROLE_USER）；数据级权限留 M1.3。
- 401/403 统一 body：自定义 AuthenticationEntryPoint + AccessDeniedHandler 返回 {code, message, requestId}。
- 演示管理员：V2/V3 迁移创建 admin/admin123；仅限本地开发，生产环境必须更改。
- 依赖：jjwt 0.12.6（api/impl/jackson），锁定版本。

## 约束、成本与替代

- JWT 无状态意味着无法服务端撤销单个 token；如需即时撤销需引入黑名单或短有效期 + refresh token。
- BCrypt cost 10 在单机下约 100ms/次验证；高并发场景可调低或换 Argon2。
- 角色码硬编码在 Spring Security 表达式中；动态权限需额外表和管理接口。
- compose.yaml 必须显式传递 JWT_SECRET（吸取 M1.1 MYSQL_* 遗漏教训）。

兼容性依据：Spring Boot 3.5.x、Spring Security 6.x、jjwt 0.12.x。
