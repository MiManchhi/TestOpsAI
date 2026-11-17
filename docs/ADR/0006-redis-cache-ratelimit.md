# ADR-0006: Redis 缓存与登录限流

- 状态：ACCEPTED
- 日期：2026-11-17
- 里程碑：M1.4
- 关联：ADR-0003（身份基线）、ADR-0005（业务元数据）

## 背景

M1.3 完成后，知识库列表和登录接口成为高频路径。PROJECT_BLUEPRINT 6.1 明确要求登录接口需限流。Redis 已在 compose.yaml 中部署但未被 Java 服务使用。需要在真实业务路径上引入 Redis，同时验证故障降级能力。

## 决策

### 使用范围

仅在以下两个真实业务路径引入 Redis：

1. **登录接口限流**：防暴力破解，滑动窗口计数器（Lua 脚本原子操作），60s/10次/IP。
2. **知识库列表缓存**：GET /knowledge-bases 高频读、低频写，TTL 60s，写操作主动 evict。

### 不做的事

- 不用 Redis 存 session / 业务数据
- 不做分布式锁、消息队列、Pub-Sub
- JWT 黑名单留 M2（前端有登出需求时再加）
- 测试用例列表暂不缓存（流量不够大）

### 降级策略

所有 Redis 操作 try-catch，绝不因 Redis 故障导致核心业务不可用：

- **读操作**（缓存/限流查询）：Redis 异常 → 跳过缓存 / 放行请求 + WARN 日志
- **写操作**（缓存写入/限流计数）：Redis 异常 → 静默忽略 + WARN 日志
- 健康检查区分 DB-ready 和 cache-degraded

### 技术选择

- Spring Data Redis + Lettuce（Spring Boot 默认），不引入 Redisson
- 限流算法：滑动窗口计数器，Lua 脚本保证原子性
- 缓存序列化：Jackson JSON，复用现有 ObjectMapper
- 配置：@ConfigurationProperties，TTL/限流参数可调

### 环境变量

新增 REDIS_HOST、REDIS_PORT、REDIS_PASSWORD；compose.yaml server 服务 depends_on redis。

## 后果

- pom.xml 新增 spring-boot-starter-data-redis
- 新增 platform/cache 和 platform/ratelimit 包
- KnowledgeBaseService 注入 RedisCacheService
- SecurityConfiguration 注册 RateLimitFilter
- 集成测试需 REDIS_PASSWORD 环境
- 429 RATE_LIMITED 错误码加入 API 契约
