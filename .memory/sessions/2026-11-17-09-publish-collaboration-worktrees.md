# 协作基线发布与三个隔离工作区

更新：2026-11-17。状态：VERIFIED（发布、工作区与远端 CI；客户端新会话实测待做）。

<!-- handoff:v1 -->

Task: ENG-009
Role: integration
Baseline: fc826ba
Contract: docs/development/AI_COLLABORATION.md
Scope: 用户明确授权提交推送现有协作变更并创建三分支/worktree；核实 CI 覆盖与运行结果，不开发业务、不自动部署。

## 目标与起始核对

- 起点 main / fc826ba；工作区是 ENG-008 的 15 份协作变更，无额外业务修改；已读取六入口和协作指南。
- 创建计划：codex/server-m1 → 同级 TestOpsAI-server；codex/web-auth → 同级 TestOpsAI-web；codex/ai-ingestion → 同级 TestOpsAI-ai。初始均来自发布后的同一主线基线，目标目录不得覆盖现有内容。
- GitHub workflow 在 push / pull_request 触发，compose job 已调用 memory Check 和工程反例测试；web/server/ai 检查应用，containers 在四个 job 成功后构建并验收六服务，最后清理独立 CI 资源。
- 当前无部署目标的 CD；容器验收不是线上部署，结构门禁不能证明模型理解规则或动态记忆无语义过期。

## 实施与选择

- 修正 AGENTS 中未加代码标记的分支通配符被 Markdown 格式化为斜体的问题。
- 不重复添加已有 CI 检查；提交前复核工程、格式、差异；正常推送，不强推。优先用已连接 GitHub 获取 Actions 结果。
- 新 worktree 只准备跟踪源码，不复制密钥、node_modules、.venv 或 .tools；可执行只读恢复，业务开发环境仍需在对应目录按需准备。

## 真实验证

<!-- checks -->

- `tests/engineering/check-layout.tests.ps1` 退出 0：9 项布局回归、新增记忆反例与 12 份脚本语法检查通过。
- 本轮全部变更 Markdown 的 Prettier check、`memory.ps1 -Mode Check`（23 份活动记忆）、`git diff --check` 均退出 0。业务和恢复脚本未在本轮另改；ENG-008 的完整本机门禁证据仍适用其范围。
- `git ls-remote origin refs/heads/main` 退出 0，远端起始 SHA 为 fc826ba，与本机一致。
- GitHub 连接器的 combined status 未返回 Actions check runs；匿名 REST 探测受共享 IP 限流，不能据此判断 CI 失败。后续按可用的运行页/Actions 工具核实。
- 发布后 SHA 校验、Actions 和三个工作区的分支/HEAD/恢复检查已完成，详见下方；各工作区布局检查退出 0，确认 .git 为 worktree 文件，未复制 .env 或 .tools。

### 发布与工作树检查

- 协作提交 0a61b30b4e1a23719182abc8c19b25432930c680 已正常推送 origin/main，`git ls-remote` 与本地 HEAD 一致；没有强推。
- 三个工作树均从 0a61b30 创建，真实自动角色分别为 server/web/ai；`memory.ps1 -Mode Restore -ListOnly` 退出 0，本地主线差异为 0，各工作区干净。收尾记忆提交后统一快进到最终 main，并以实际 Git refs 为最终基线。

| 分支               | 仓库同级目录     | 内容                                 |
| ------------------ | ---------------- | ------------------------------------ |
| codex/server-m1    | TestOpsAI-server | Java；源码与记忆，不含本机密钥和依赖 |
| codex/web-auth     | TestOpsAI-web    | Web；源码与记忆，不含本机密钥和依赖  |
| codex/ai-ingestion | TestOpsAI-ai     | AI；源码与记忆，不含本机密钥和依赖   |

- 当前只创建本地开发分支；后续业务交付时按任务授权推送对应分支。主线已推送。
- [CI 44048755494](https://github.com/MiManchhi/TestOpsAI/actions/runs/44048755494) 对应 0a61b30：server/web/ai/compose/containers 五个 job 全部 completed/success；compose 日志确认新增协作结构正反例和 23 份记忆检查在 Ubuntu 成功。Java 14 项中 9 项数据库测试跳过，不能当成真实 DB 回归。containers 的构建、六服务健康、交付/恢复验收及清理步骤全部成功。

## 交接

<!-- next -->

- 已实现并验证协作发布和三个本地隔离工作区，无业务变更；仅创建本地开发分支，未推送空开发分支或启动业务开发。收尾记忆随独立 docs 提交同步，最终 main 和工作树 SHA 以 Git refs 为准。
- BLOCKED：无本次发布和工作区准备阻塞。后续只需从对应目录打开会话并分配具体任务；客户端实际注入/读取仍须新会话观察。开发环境安装、密钥注入和共享资源隔离在对应业务任务中安排。
- CI 会对之后的开发分支 push / PR 执行同样检查；当前没有生产 CD，也未更改远端分支保护。结构检查无法证明模型逐条理解规则或文字事实无过期。
- [CURRENT](../CURRENT.md)、[初始化 session](2026-11-17-08-ai-collaboration-bootstrap.md)。
