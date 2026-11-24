# AI 工作台的持续对话

更新：2026-11-21（Asia/Shanghai）。状态：VERIFIED（本域静态、HTTP 回归及真实 Qdrant 范围）。

<!-- handoff:v1 -->

Task: WORKSPACE-CONVERSATION-AI-001
Role: ai
Baseline: codex/ai-ingestion 194d96c 加工作区已实现的 G2、G4/G5。
Contract: 工作台 2.0.0，签名内部 composeAnswer。
Scope: Python AI 的对话生成、检索与核对，清理废弃接口，维护本域测试与记忆。

## 现场与验证

<!-- checks -->

2026-11-20 至 21 日，已在 [answers](../../apps/ai-service/src/testops_ai/features/answers) 区分运行核对、文档解释、用例评审、Robot 草稿。生成路径带入运行、真实测量、本轮允许的历史和附件。检索综合当前提问与续问线索，兼顾当前与历史验收包的文档。模型输出须符合 JSON 契约、引用编号范围和 Robot 静态语法，首个无效结果允许一次修正；仍失败则返回可识别错误。Robot 仅在隔离子进程解析。删除废弃的整项目用例目录导入 HTTP 路由及 DTO，保留 Robot output.xml 解析、资料索引、块导出以及临时用例静态检查。

- 在 apps/ai-service 执行 Ruff check 与 format --check，通过全部 48 个文件。mypy 通过 31 个源文件。pytest 通过 137 项、跳过 3 项显式外部验收。第一次并发检查中一个索引测试轮询超时，单独重跑和完整重跑均通过。
- 单独启动隔离 Qdrant 1.17.0，指定 TESTOPS_AI_VERIFY_QDRANT_URL 后真实 HTTP 建索引、查询、越界过滤、删除回归通过 1 项，测试容器已停用。
- 503 含稳定错误码，/health/configuration 仅返回对话与向量配置是否齐全。简单向量 env 的维数 0 或空时服务仍可启动并显示未配置。以临时目录和自造字符串验证挂载文件名能被 Settings 正确读取。
- BLOCKED：部署方的真实聊天及 Embedding 配置尚未提供；上述结果不证明模型质量。自造完整对话真实模型验收在 tests/test_workspace_conversation.py::test_opt_in_real_model_produces_grounded_robot。

## 交接

<!-- next -->

Java 负责重验历史和附件、筛选/澄清运行以及最终确认依据；Python 对已绑定的运行可继续解读或生成。HTTP 响应中的 findings 在固定时间判定时给出可复核数值，诊断、评审与草稿走模型回答并附授权来源。工作区无提交或推送。部署接口仍由集成方在用户配置真实聊天和 Embedding 服务后验收。[总计划](../../docs/ROADMAP.md)。
