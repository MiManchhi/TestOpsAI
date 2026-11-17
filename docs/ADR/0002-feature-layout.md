# ADR-0002：按业务域组织与本机工具归档

更新：2026-11-16。状态：采纳并实施，验收证据见工程记忆。

## 背景与决定

用户要求目录工程化、唯一归属、根目录整洁，并确认三端内部一起整理、工具保留 .tools、完成基础运行验收。

保留 apps/web、server、ai-service 单仓库分工；应用配置留应用，跨应用工程入口留根。Prettier 设置并入 package.json，将根源码文件收敛为 13 个。依赖版本沿用既有选择，npm 固定 11.6.4，Python 补齐 uv.lock。

| 范围                  | 整理前                    | 整理后                                              |
| --------------------- | ------------------------- | --------------------------------------------------- |
| Java 安全             | server 根包下 config      | platform/security，测试对应同包结构                 |
| Java 后续业务         | 尚未实现                  | modules/业务域，随真实职责拆分，不建立空壳          |
| Vue 启动              | src 下 main/App/style     | src/app                                             |
| Vue 健康检查          | 根组件与 src/api          | src/features/health 内聚合请求、测试、组件          |
| Python 工厂/配置/健康 | main.py 和 config.py      | app.py、core/settings.py、features/health/router.py |
| Python 外部启动       | testops_ai.main:app       | 保持稳定，main.py 负责 ASGI 入口                    |
| 本机工具              | .tools 下多个安装尝试目录 | .tools/toolchains 下 java/node/npm/uv/python        |
| 缓存                  | npm-cache、uv-cache       | .tools/cache/npm、uv                                |

不为“统一”移动 Compose、各应用 Dockerfile、EditorConfig 和 Git 配置；这些位置有明确作用范围。正式文档归 docs，交接归 .memory，临时输出归忽略的 .runtime。

## 约束、成本与替代

结构检查覆盖根白名单、必需入口、应用目录与配置归属；检查本身有正反例回归测试。深层业务语义仍需人工审查，不能靠文件名推断全部职责。

拒绝只按 controller/service/mapper 或 views/components 全局分层，以减少同一业务散落；拒绝引入 DDD 框架或几十个空包。代价是各业务域必须保持边界，提取共享代码需要真实复用证据。

迁移同时修复 Windows Wrapper 以脚本位置定位 .mvn（路径末尾用点避免反斜杠吞引号），并保留统一 maven.ps1 操作入口。不将目录检查通过等同于部署通过，不创建自动提交。
