# 智能电表 Robot 测试域与迁移设计

日期：2026-11-17。状态：FROZEN（设计），未执行新迁移。对象字段的网络表示以 [共享 schema](contracts/schemas.json) 为准。

## 关系与事实所有权

| 聚合       | 关系与持久化重点                                                          | 权威方                                      |
| ---------- | ------------------------------------------------------------------------- | ------------------------------------------- |
| 项目       | projects → project_members；项目 key 唯一，成员 role=OWNER/EDITOR/VIEWER  | Java / MySQL                                |
| Robot 目录 | projects → catalog_imports → suites；用例关联 suite 与 source revision    | Java 应用事务；Python 仅静态解析            |
| 用例       | test_cases 归项目，ROBOT/MANUAL 分开；case_tags 独立精确标签              | Java / MySQL                                |
| 圈选       | case_selections → selection_items(case_id,version)；不可变快照及摘要/到期 | Java / MySQL                                |
| 运行       | projects → runs → results；结果保留历史 caseName/suitePath，caseId 可空   | Java / MySQL                                |
| 产物       | runs → artifacts，随机对象键、sha256、大小、归属                          | Java 元数据 + 对象存储                      |
| 知识       | projects → knowledge_bases → documents → chunks；限制库 grants            | Java 元数据/ACL；Python 处理；Qdrant 可重建 |
| 会话       | user + project → conversations → messages → turns → turn_events           | Java / MySQL，事件先持久化再发布            |
| 草稿       | conversation → drafts → draft_items；确认后生成 MANUAL 用例               | Java / MySQL 事务                           |
| 审计       | actor/project/requestId/操作/资源/结果/时间；幂等记录与结果关联           | Java / MySQL                                |

数据库 BIGINT 资源 ID 通过 v2 以字符串输出；会话/轮次/事件归属任务等 UUID 独立。UTC DATETIME(6)，API 为 ISO UTC。所有跨聚合关联查询必须带 projectId，并检查复合资源归属；用户传来的父 ID 不足以证明关联有效。

## 最小持久化约束

- projects.key 唯一；project_members PK(project_id,user_id)，删除最后 OWNER 拒绝。
- suites 唯一(project_id,source_path,revision)，父子不能跨项目或成环；归档前保留历史 revision。
- test_cases 唯一(project_id,stable_key)，version 乐观锁。标签唯一(case_id,tag)，迁移时处理旧逗号字符串，不能以 substring LIKE 代替精确标签。
- runs 绑定 catalogRevision/import_job_id；results 唯一(run_id,suite_path,case_name)，存结果快照；统计从叶子结果计算并校验，而非模型生成。
- selection_items 唯一(selection_id,case_id)，固定版本集合；删除定义采用归档，历史结果与引用不断链。
- documents 内容不可变；唯一(kb_id,content_hash,version_label) 或按幂等上传规则返回既有对象；索引任务唯一(document_id,content_hash,parser_version,embedding_profile)。
- turn_events 唯一(turn_id,sequence)；同会话最多一个活动 turn；终态使用 CAS，序号事务性递增，保留重放 24 小时。conversation 删除采用逻辑隐藏并保留必要审计。
- drafts.revision/payload_hash/expires_at + unique committed_draft_id；提交与用例创建同一事务。幂等键唯一(actor_id,operation_id,resource_scope,key)，存请求 hash 和结果引用，不存敏感原始内容。
- 对象文件、块与向量通过 documentId/contentHash 对齐。删除采用 DELETING/清理重试/DELETED，不将仅 SQL 删除当作跨存储删除完成。

## 既有数据迁移顺序

1. 不修改 V1–V5 或其他已执行迁移。实现时从当时最新编号继续，不在设计阶段预占版本号。
2. 新建项目/成员与 legacy-resource 映射，旧资源保持旧 API；未经明确归属核对不自动共享至所有项目成员。
3. 为用例增加项目、来源/版本/稳定键与规范化标签，人工旧用例映射 MANUAL。v1 DTO 保持原样，通过适配层输出；v2 只读取已迁移且项目授权有效的数据。
4. 引入 suites/imports/selection；解析完整快照后事务性切换当前目录版本，再逐步启用用例页面。
5. 引入运行/结果/产物，完成导入映射与统计一致性后启用运行分析；不提前在产品宣称已接外部执行器。
6. 引入文档/索引/ACL、会话/事件、草稿/幂等审计，各切片完成后运行 provider 测试，再接对应 Web 页面。

## 实现归属

Java 复用 identity、knowledge、testcase；projects、runs、conversation 等仅在有实现时建立所属业务域。跨域安全技术归 platform。Python 在实际实现时增加 robot_parser、ingestion、assistant，解析与模型调用分离。Vue 按页面域组织，不建立无实际复用的万能 shared。

背景文档中的建议 examples 目录按本仓库约定落到 samples，不能新建根 examples。原企业系统独立运行；本项目不复制其库、关键字实现、设备配置或私有测试数据。
