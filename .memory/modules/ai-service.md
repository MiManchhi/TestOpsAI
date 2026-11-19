# AI 服务模块

2026-11-19：AI-INGESTION-001 VERIFIED（功能 54eb59d 已通过 3399234 合入并推送 main，本机自动/HTTP 替身范围）。features/ingestion 承载 knowledge-v1 文档/定位分块、独立 SQLite 状态、后台恢复/幂等/删除墓碑、HTTP Embedding 与 Qdrant、可信 READY 范围检索；完整门禁与 115 项 AI 测试通过，2 项真实外部验收缺配置跳过。写入取消保持版本锁至请求完成/超时；删除跨 generation，持久 profile 名支持晚到残留补清理。配置与限制见 [运行说明](../../docs/development/AI_INGESTION.md)，证据见 [本任务](../sessions/2026-11-18-09-ai-ingestion.md)。真实服务/Java READY/引用注册仍待接入，聊天模型、RAG 回答/Agent/SSE 未实现，检索不自行生成引用 ID。

2026-11-18：AI-PARSER-001 VERIFIED（本机解析/HTTP 替身/64 项测试与完整门禁；实现 e1a2d23 已推送 AI 分支，已通过 a215e34 合入 main）。新增 features/robot_parser：严格契约模型、Robot 7 静态 AST、受限 XML、对象客户端、可终止进程与两个 /internal/v1/parsers HTTP 入口；core/service_tokens 管 RS256 服务/委托验签及回调签名。配置、路径/超时/并发与错误策略见 [解析说明](../../docs/development/AI_PARSER.md)，验证范围见 [本任务](../sessions/2026-11-18-03-ai-parser.md)。不执行 Robot、不直写业务表、不调用模型；真实 Java 对象接口、双方密钥部署尚待联调。

2026-11-17：Robot 静态解析、索引、回答与受控工具的 [三端契约 1.0.0](../../docs/contracts/README.md) 已确定；AI 内部 API /internal/v1 与工具 schema 为后续唯一开发输入。业务授权/精确查询/事件持久化/确认写入由 Java 负责，AI 不直接写业务表。现有运行仍为基础骨架，不能把契约当成功能实现；见 [设计 session](../sessions/2026-11-17-14-final-design-contract.md)。

更新：2026-11-16。状态：VERIFIED（基础骨架范围）。

Python 3.12 + FastAPI，保留 src 布局。稳定 ASGI 入口 testops_ai.main:app；[工厂](../../apps/ai-service/src/testops_ai/app.py) 负责组装，core/settings.py 管配置，[health router](../../apps/ai-service/src/testops_ai/features/health/router.py) 管 live/ready。无业务功能时不预建更多 feature。

[uv.lock](../../apps/ai-service/uv.lock) 已生成，31 个依赖解析，使用标准 CPython 3.12.16；uv sync --locked 已成功，环境位于应用 .venv。Ruff、mypy、1 项覆盖健康/关闭文档/未实现接口的测试，以及 sdist/wheel 构建已通过。

main.py 兼容导出 create_app，测试使用 app.py 工厂。本机 live/ready 运行验证已通过。测试框架有一条 httpx/Starlette 弃用提示，不等于业务测试失败。

以上骨架记录保留历史版本；当前知识文档、HTTP Embedding 和向量连接交付见 AI-INGESTION-001，依赖锁共 55 项。LangGraph、RAG 回答与 Agent 仍 PLANNED；内部服务/委托认证已实现，真实对象/READY/引用注册联调仍待做；业务事实与实时授权继续归 Java。
