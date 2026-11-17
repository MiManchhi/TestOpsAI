# M1.4 缓存与限流

日期：2026-11-17。状态：IMPLEMENTED。分支：codex/server-m1。

## 已完成

### 依赖与配置
- pom.xml: spring-boot-starter-data-redis
- application.yaml: Redis host/port/password/timeout/pool 配置
- compose.yaml: server 服务添加 REDIS_HOST/PORT/PASSWORD + depends_on redis

### platform/cache
- CacheProperties: @ConfigurationProperties, kbListTtlSeconds (默认 60)
- RedisCacheService: get(Class)/get(Type)/set/evict/isAvailable，全部 try-catch 降级

### platform/ratelimit
- RateLimitProperties: windowSeconds (60), maxRequests (10)
- RateLimiter: 接口 tryAcquire/retryAfterSeconds
- RedisRateLimiter: Lua 脚本原子滑动窗口计数器，Redis 异常降级放行
- RateLimitFilter: 仅拦截 POST /api/v1/auth/login，超限返回 429 + Retry-After

### KnowledgeBaseService 缓存
- list() 读缓存 kb:list:{page}:{size}，TTL 60s
- create/update/archive 写后 evict kb:list:*

### SecurityConfiguration
- @EnableConfigurationProperties 注册 CacheProperties + RateLimitProperties
- RateLimitFilter 注册在 JWT filter 之前

### 集成测试
- RateLimitApiTest: loginWithinLimitSucceeds + loginExceedingLimitReturns429 (需 REDIS_PASSWORD)
- CacheIntegrationTest: cachedListReturnsSameResult + cacheInvalidatedOnCreate (需 MYSQL_PASSWORD)

### 文档
- ADR-0006: Redis 缓存与登录限流决策记录
- API.md: M1.4 章节（限流 + 缓存 + RATE_LIMITED 错误码）

### 已有测试修复
- BoundaryTest/ErrorContractTest: 排除 RedisAutoConfiguration + MockBean RedisCacheService/RateLimiter

## 验收结果

- ✅ Spotless: 52 files clean
- ✅ Maven verify: BUILD SUCCESS (37 run, 0 fail, 32 skipped)
- ✅ Prettier: All files formatted
- ✅ Memory check: 26 records passed
- ✅ Layout check: passed
- ✅ npm check: ESLint + TypeScript + Vitest + Build passed
