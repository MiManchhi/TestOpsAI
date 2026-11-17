# 数据设计草图

M0 没有业务表和迁移脚本，也不自动创建演示管理员。M1 引入 MySQL 驱动、MyBatis-Plus、Flyway，并执行真实 MySQL 集成测试。

| 数据域   | 实体与关系                                   | 初步约束/索引                                    |
| -------- | -------------------------------------------- | ------------------------------------------------ |
| 身份     | users、roles、user_roles                     | username/role_code 唯一；用户角色联合唯一        |
| 知识     | knowledge_bases → documents → ingestion_jobs | owner_id、kb_id/status；checksum 与对象键可追踪  |
| 用例     | test_cases；后续 test_runs/test_results      | project_key/module/status 组合查询需解释执行计划 |
| 对话     | conversations → messages、agent_tool_calls   | user_id/created_at，conversation_id/时间索引     |
| 审批审计 | action_approvals、audit_logs                 | payload_hash、expires_at、幂等键、actor_id/time  |

类型、外键及 ID 生成方案随 M1 首次迁移冻结，不提前制造未验证 SQL。时间统一 UTC；状态遵循立项方案状态机。

Qdrant payload 计划包含 document_id、kb_id、owner_scope、chunk_index、version；查询前按已授权范围过滤。删除跨存储使用状态与补偿，Redis 不作事实唯一来源。
