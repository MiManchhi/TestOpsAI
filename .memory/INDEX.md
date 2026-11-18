# 工程记忆导航

2026-11-18：[设计与契约发布](sessions/2026-11-18-01-design-contract-publish.md)；下一批三端开工见 [并行开发指南](../docs/development/PARALLEL_DEVELOPMENT.md)。

2026-11-17 当前设计任务：[最终页面与三端契约](sessions/2026-11-17-14-final-design-contract.md)；开发输入：[最终设计](../docs/PRODUCT_DESIGN.md)、[冻结契约](../docs/contracts/README.md)。此前三方向探索已被此基线取代。

更新：2026-11-17。状态：IMPLEMENTED。

项目：TestOps AI，Java 管业务与授权，Vue 管交互，Python 管 AI。当前已集成 M1.3 后端、Docker 启动器，Web 登录工作台已合入主线并发布；协作入口与三个隔离工作区已就绪，具体任务和证据边界以 [CURRENT](CURRENT.md) 为准。

恢复顺序：[AGENTS](../AGENTS.md) → [CURRENT](CURRENT.md) → 开发规范和[协作指南](../docs/development/AI_COLLABORATION.md) → 所属模块与相关 session → 核对 Git、代码与证据。辅助命令：`scripts/memory.ps1 -Mode Restore`；检查：`scripts/memory.ps1 -Mode Check`。记忆不代替当前证据，不自动同步其他 worktree。

| 记录                                 | 用途                                       |
| ------------------------------------ | ------------------------------------------ |
| [CURRENT](CURRENT.md)                | 集成与三领域的当前任务、状态、阻塞和下一步 |
| [LESSONS](LESSONS.md)                | 缺陷与经验                                 |
| [LEARNING](LEARNING.md)              | 原理和待用户自测确认的学习点               |
| [EVIDENCE](EVIDENCE.md)              | 验证版本、范围和限制                       |
| [工程](modules/engineering.md)       | 工具、Git、CI                              |
| [Java](modules/server.md)            | 业务与认证上下文                           |
| [Vue](modules/web.md)                | 前端上下文                                 |
| [AI](modules/ai-service.md)          | Python 上下文                              |
| [session 模板](sessions/TEMPLATE.md) | 新任务边界与交接                           |

本轮：[Web 发布与主线集成](sessions/2026-11-17-12-web-auth-publish.md)。此前：[三个开发分支同步远端](sessions/2026-11-17-10-push-development-branches.md)。此前：[协作发布与三个工作区](sessions/2026-11-17-09-publish-collaboration-worktrees.md)、[自动恢复与协作初始化](sessions/2026-11-17-08-ai-collaboration-bootstrap.md)、[M1.2 身份认证](sessions/2026-11-17-07-m1-2-auth.md)、[M1.1 数据基线](sessions/2026-11-17-06-m1-1-data-baseline.md)。历史：[M0 容器交付](sessions/2026-11-17-05-container-delivery.md)、[跨平台修复](sessions/2026-11-17-04-ci-portability-fix.md)、[首次发布](sessions/2026-11-17-02-first-remote-push.md)、[规范基线](sessions/2026-11-17-01-development-standards.md)、[初始记忆](sessions/2026-11-16-02-memory-governance.md)。其他历史按 sessions 日期查阅。

需求基线：[立项方案](../docs/PROJECT_BLUEPRINT.md)；正式决策：[ADR](../docs/ADR/0001-engineering-baseline.md)；开发规范：[CONTRIBUTING](../docs/development/CONTRIBUTING.md)、[目录](../docs/development/DIRECTORY_LAYOUT.md)、[代码](../docs/development/CODE_STYLE.md)；记忆维护：[MEMORY](../docs/development/MEMORY.md)。旧计划保留在 [archive](archive/2026-11-16-m0/task_plan.md)。

功能状态：PLANNED → IN_PROGRESS → IMPLEMENTED → VERIFIED → DEPLOYED；BLOCKED 和是否合入主线分别记录。历史验证不因记忆更新自动适用于当前版本。
