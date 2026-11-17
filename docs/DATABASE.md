# 数据设计草图

更新：2026-11-17。状态：IMPLEMENTED（M1.1 身份基线迁移已落地；其余域仍为草案）。

## M1.1 已实现：身份基线

- 迁移脚本：`apps/server/src/main/resources/db/migration/V1__identity_baseline.sql`
- 主键：BIGINT 自增；时间：DATETIME(6) UTC；字符集：utf8mb4_unicode_ci。
- 仅包含 `users`、`roles`、`user_roles` 三表及必要唯一/外键约束；无软删除、版本号、业务字段。
- 集成测试覆盖表存在、唯一约束、外键约束；证据见 [.memory/sessions/2026-11-17-06-m1-1-data-baseline.md](../.memory/sessions/2026-11-17-06-m1-1-data-baseline.md)。

| 表         | 关键字段与约束                                                                                                               | 备注                         |
| ---------- | ---------------------------------------------------------------------------------------------------------------------------- | ---------------------------- |
| users      | id BIGINT PK AUTO_INCREMENT, username VARCHAR(64) UNIQUE NOT NULL, password_hash NOT NULL, created_at/updated_at DATETIME(6) | M1.2 认证使用；密码仅存哈希  |
| roles      | id BIGINT PK AUTO_INCREMENT, role_code VARCHAR(64) UNIQUE NOT NULL, description, created_at/updated_at DATETIME(6)           | 角色码稳定标识，描述可选     |
| user_roles | user_id BIGINT FK→users, role_id BIGINT FK→roles, created_at DATETIME(6), PK(user_id, role_id)                               | 级联删除；同一用户角色不重复 |

## 后续草案（未实现）

| 数据域   | 实体与关系                                   | 初步约束/索引                                    |
| -------- | -------------------------------------------- | ------------------------------------------------ |
| 知识     | knowledge_bases → documents → ingestion_jobs | owner_id、kb_id/status；checksum 与对象键可追踪  |
| 用例     | test_cases；后续 test_runs/test_results      | project_key/module/status 组合查询需解释执行计划 |
| 对话     | conversations → messages、agent_tool_calls   | user_id/created_at，conversation_id/时间索引     |
| 审批审计 | action_approvals、audit_logs                 | payload_hash、expires_at、幂等键、actor_id/time  |

类型、外键及 ID 生成方案随 M1 首次迁移冻结，不提前制造未验证 SQL。时间统一 UTC；状态遵循立项方案状态机。

Qdrant payload 计划包含 document_id、kb_id、owner_scope、chunk_index、version；查询前按已授权范围过滤。删除跨存储使用状态与补偿，Redis 不作事实唯一来源。
