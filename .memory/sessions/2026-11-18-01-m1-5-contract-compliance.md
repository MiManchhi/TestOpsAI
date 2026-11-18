# M1.5 契约合规与 v2 项目隔离

日期：2026-11-18。状态：IMPLEMENTED。分支：codex/server-m1。

## 已完成

### P0 既有 v1 合规修复

- RequestIdFilter: 始终生成有效 UUID，校验客户端 ID 格式（maxLength 128, alphanumeric+hyphen）
- Jackson: write-dates-as-timestamps=false（ISO-8601 UTC 输出）
- Jackson: fail-on-unknown-properties=true（v2 拒绝未知字段）

### P1 v2 基础设施

- V6 迁移: projects + project_members 表 + DEMO 种子项目绑定 admin
- Project 域 (modules/project/): 8 个文件
  - Project.java, ProjectMember.java (record 领域对象)
  - ProjectRepository.java (JdbcTemplate CRUD + 成员管理 + 权限查询)
  - ProjectService.java (OWNER/EDITOR/VIEWER + ADMIN 权限框架, hasAtLeast 层级比较)
  - ProjectController.java (/api/v2/projects/* 7 个端点)
  - CreateProjectRequest, PatchProjectRequest, SetMemberRequest, ProjectResponse, MemberResponse (DTOs)
  - ProjectNotFoundException, ProjectKeyDuplicateException, LastOwnerException, NotProjectMemberException
- 幂等键框架 (platform/idempotency/): IdempotencyService (Redis TTL 24h) + IdempotencyConflictException
- PageResponse 提取到 platform/web/PageResponse + validatePagination 静态方法
- GlobalExceptionHandler 扩展: PROJECT_NOT_FOUND(404), PROJECT_KEY_DUPLICATE(409), LAST_OWNER(409), IDEMPOTENCY_CONFLICT(409)
- v2 字符串 ID: DTO 层 String.valueOf(id)

### 集成测试

- ProjectApiTest: 8 个方法 (listProjects, createAndGet, duplicateKey409, invalidKey400, update, memberManagement, unauthenticated401, nonExistent404)

### 文档

- ADR-0008: v2 项目隔离与契约合规决策记录
- API.md: M1.5 章节 (项目 API v2 + 通用规则 + 错误码)

### 已有测试修复

- BoundaryTest/ErrorContractTest: 添加 MockBean ProjectRepository + IdempotencyService

## 验收结果

- ✅ Spotless: 70 files clean
- ✅ Maven verify: BUILD SUCCESS (45 run, 0 fail, 40 skipped)
- ✅ Contracts check: 109 schemas, 65 operations, 827 refs passed
- ✅ Memory check: passed
- ✅ Layout check: passed
- ✅ Prettier: fixed and passing
- ⚠️ npm check: Web workspace vue-router type errors (pre-existing from Web team merge 4cc2d1c, not M1.5)
