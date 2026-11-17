# AI 服务模块

更新：2026-11-16。状态：VERIFIED（基础骨架范围）。

Python 3.12 + FastAPI，保留 src 布局。稳定 ASGI 入口 testops_ai.main:app；[工厂](../../apps/ai-service/src/testops_ai/app.py) 负责组装，core/settings.py 管配置，[health router](../../apps/ai-service/src/testops_ai/features/health/router.py) 管 live/ready。无业务功能时不预建更多 feature。

[uv.lock](../../apps/ai-service/uv.lock) 已生成，31 个依赖解析，使用标准 CPython 3.12.16；uv sync --locked 已成功，环境位于应用 .venv。Ruff、mypy、1 项覆盖健康/关闭文档/未实现接口的测试，以及 sdist/wheel 构建已通过。

main.py 兼容导出 create_app，测试使用 app.py 工厂。本机 live/ready 运行验证已通过。测试框架有一条 httpx/Starlette 弃用提示，不等于业务测试失败。

LangGraph、RAG、工具认证、模型适配与存储连接仍 PLANNED；业务事实与授权继续归 Java。
