# 协作基线发布与三个隔离工作区

更新：2026-11-17。状态：IN_PROGRESS。

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
- 待执行：发布后 SHA 校验、Actions、三个工作区的分支/HEAD/恢复检查。

## 交接

<!-- next -->

- 当前执行中；需要完成发布、检查 CI 并创建/核对工作区。
- [CURRENT](../CURRENT.md)、[初始化 session](2026-11-17-08-ai-collaboration-bootstrap.md)。
