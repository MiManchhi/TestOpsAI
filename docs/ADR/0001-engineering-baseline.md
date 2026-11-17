# ADR-0001：单仓库与分阶段工程基线

状态：采纳（M0）；版本解析结果见 lock、pom、ENVIRONMENT。

Java 21 + Spring Boot 3.5.x 负责业务，Vue 3 + TypeScript 负责 Web，Python 3.12 + FastAPI 负责 AI。选择 npm workspace、Maven Wrapper、uv lock，各语言保持原生构建，不增加 Nx/Turbo。

首阶段只引入可运行与可验证骨架所需依赖。持久化、Router/Pinia、LangGraph 等随业务引入，避免未使用组件及虚假集成。Java 默认拒绝业务路径，Python 仅有健康端点。

本地随机凭据只写入忽略的 .env；工具安装到 .tools，当前终端激活，避免覆盖系统 Java 11/Node 20。PowerShell 为 Windows 开发入口，CI 使用各语言原生命令。

代价：多语言工具需要分别更新；跨服务契约需显式维护；容器版本 tag 仍可变，发布前需 digest 锁定与漏洞审查。

兼容性依据：[Spring Boot 3.5](https://docs.spring.io/spring-boot/3.5/system-requirements.html)、[Vite](https://vite.dev/guide/)、[uv lock](https://docs.astral.sh/uv/concepts/projects/sync/)。不要求追逐最新主版本。
