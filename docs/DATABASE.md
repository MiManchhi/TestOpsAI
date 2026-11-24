# 工作台持久化

[领域模型](DOMAIN_MODEL.md)定义项目、验收依据、运行与会话的关联。[Flyway 迁移](../apps/server/src/main/resources/db/migration)是实际物理结构的来源。已有数据库按版本升级，应用启动期间保留原有数据。

| 任务       | 主要记录                                                                                                                             |
| ---------- | ------------------------------------------------------------------------------------------------------------------------------------ |
| 账号和项目 | users、roles、user_roles、projects、project_members                                                                                  |
| 成员与交接 | workspace_owner_transfer、workspace_audit_event、workspace_command_receipt                                                           |
| 项目知识   | workspace_attachment、workspace_document、workspace_doc_reader、workspace_doc_version、workspace_document_block、workspace_index_job |
| 验收依据   | workspace_basis、workspace_basis_version                                                                                             |
| 复测与证据 | workspace_run、workspace_run_artifact、workspace_run_result                                                                          |
| 私有对话   | workspace_chat、workspace_chat_turn、workspace_chat_turn_file、workspace_chat_citation                                               |

文件字节保存在 Java 原件卷。向量库中的位置标识与 MySQL 内记录的获准文档版本、内容哈希、段落位置配合使用。读取受限历史和原件时按当前权限检查。登录限流计数存于 Redis。
