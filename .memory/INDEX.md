# 工程记忆导航

最近完成：[CI 跨平台修复与发布](sessions/2026-11-17-04-ci-portability-fix.md)。修复提交 9fb1437 已推送，完整本地门禁、双平台类型检查、本机 smoke 及 Ubuntu CI 四个 job 均通过；不进入 M1。

此前分析：[远端 CI 失败](sessions/2026-11-17-03-ci-failure-analysis.md)。Ubuntu 的 Python 平台常量与测试隐藏文件清理问题已定位；该分析记录保留当时未修复状态。

最近完成：[M0 首次远程提交](sessions/2026-11-17-02-first-remote-push.md)。工程提交 977591d 已发布到 origin/main，MIT 许可证与初始历史保留；不进入业务开发。

最近完成：[正式开发规范与准入复核](sessions/2026-11-17-01-development-standards.md)。每个新会话须读取开发、目录、[代码规范](../docs/development/CODE_STYLE.md)；M0 搭建已完成，用户要求暂不进入下一阶段；M1.1 仅保留为将来计划。阶段起点见 [ROADMAP](../docs/ROADMAP.md)。目录整理的历史见 2026-11-16-04 session。

此前完成：[目录说明与归属约束](sessions/2026-11-16-03-directory-guide.md)。新文件归属查 [目录指南](../docs/development/DIRECTORY_LAYOUT.md)，根目录检查用 `scripts/check-layout.ps1`。

更新：2026-11-17。状态：IMPLEMENTED。

项目：TestOps AI，智能测试知识库与 AI Agent 协作平台。当前 M0。原企业项目与本项目代码、数据和运行环境独立，禁止使用企业私有资产。

恢复顺序：[仓库规则](../AGENTS.md) → [当前状态](CURRENT.md) → 相关模块 → 相关 session → 核对 Git、实际文件及测试。辅助命令：`./scripts/memory.ps1 -Mode Restore`。

| 记录                                                              | 用途                           |
| ----------------------------------------------------------------- | ------------------------------ |
| [CURRENT](CURRENT.md)                                             | 当前阶段、未解决事项、下一步   |
| [LESSONS](LESSONS.md)                                             | 缺陷、根因、解决状态           |
| [LEARNING](LEARNING.md)                                           | 学习与自测题，不推定掌握程度   |
| [EVIDENCE](EVIDENCE.md)                                           | 实现/验证/部署的证据边界       |
| [工程模块](modules/engineering.md)                                | 目录、工具、Git 与 CI          |
| [Java](modules/server.md)                                         | Java 业务入口                  |
| [Vue](modules/web.md)                                             | 前端模块                       |
| [AI](modules/ai-service.md)                                       | Python AI 运行时               |
| [初始化 session](sessions/2026-11-16-01-m0-bootstrap.md)          | 本会话前半段工程搭建           |
| [记忆与规范 session](sessions/2026-11-16-02-memory-governance.md) | 本次交付与恢复验收             |
| [会话模板](sessions/TEMPLATE.md)                                  | 新任务复制为日期/编号/主题文件 |

需求权威来源：[立项方案](../docs/PROJECT_BLUEPRINT.md)。正式决策：[ADR](../docs/ADR/0001-engineering-baseline.md)。规范：[开发指南](../docs/development/CONTRIBUTING.md)。日常使用：[记忆维护指南](../docs/development/MEMORY.md)。参考资料：[接收约定](../docs/references/README.md)。

状态：PLANNED → IN_PROGRESS → IMPLEMENTED → VERIFIED → DEPLOYED。BLOCKED 单列，不抹去原状态。状态升级必须有范围和证据；文档规则的 VERIFIED 只指文档检查通过。旧的临时规划保留于 [归档](archive/2026-11-16-m0/task_plan.md)，不再作为当前状态来源。
