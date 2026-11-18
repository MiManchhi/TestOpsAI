# AI 服务模块

2026-11-17：Robot 静态解析、索引、回答与受控工具的 [三端契约 1.0.0](../../docs/contracts/README.md) 已确定；AI 内部 API /internal/v1 与工具 schema 为后续唯一开发输入。业务授权/精确查询/事件持久化/确认写入由 Java 负责，AI 不直接写业务表。现有运行仍为基础骨架，不能把契约当成功能实现；见 [设计 session](../sessions/2026-11-17-14-final-design-contract.md)。

更新：2026-11-16。状态：VERIFIED（基础骨架范围）。

Python 3.12 + FastAPI，保留 src 布局。稳定 ASGI 入口 testops_ai.main:app；[工厂](../../apps/ai-service/src/testops_ai/app.py) 负责组装，core/settings.py 管配置，[health router](../../apps/ai-service/src/testops_ai/features/health/router.py) 管 live/ready。无业务功能时不预建更多 feature。

[uv.lock](../../apps/ai-service/uv.lock) 已生成，31 个依赖解析，使用标准 CPython 3.12.16；uv sync --locked 已成功，环境位于应用 .venv。Ruff、mypy、1 项覆盖健康/关闭文档/未实现接口的测试，以及 sdist/wheel 构建已通过。

main.py 兼容导出 create_app，测试使用 app.py 工厂。本机 live/ready 运行验证已通过。测试框架有一条 httpx/Starlette 弃用提示，不等于业务测试失败。

LangGraph、RAG、工具认证、模型适配与存储连接仍 PLANNED；业务事实与授权继续归 Java。
