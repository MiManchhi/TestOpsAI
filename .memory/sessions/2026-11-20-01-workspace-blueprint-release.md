# 工作台最终版蓝图交付

更新：2026-11-20（Asia/Shanghai）。状态：VERIFIED（蓝图交付检查）。

<!-- handoff:v1 -->

Task: WORKSPACE-BLUEPRINT-RELEASE-001
Role: integration
Baseline: main 039c8319ae190a1fb3177f82da9dbd0bd573a986
Contract: 工作台设计契约 2.0.0，公开 v3，内部 v2。
Scope: 按用户授权提交定稿蓝图、页面、接口、回归脚本与对应记忆，普通推送 origin/main。业务开发按路线图另行启动。

## 起点

已核对入口、开发规则、各域记忆和 [定稿交接](2026-11-19-06-workspace-decisions.md)。本地和远端 main 起始 SHA 相同。三端本地开发分支位于原 main 基线。已有 Java 用例搜索修复及回归保留在本地，证据见 [已有三端联调](2026-11-19-03-three-tier-integration.md)。

## 验证

<!-- checks -->

- `node tests/contracts/workspace-review.cjs` 通过，验证 54 个操作与 69 个模型；`node --check tests/contracts/workspace-live.cjs` 通过。
- Edge / Playwright 以 1440 和 390 像素宽度复验全部 27 个样稿状态；交接、资料退回、整包发布、焦点和减少动态效果通过。
- 工作区运行 `scripts/check.ps1` 退出 0。Web 94 项通过；Java 65 项中 5 项执行、60 项未配数据库或 Redis 跳过；AI 115 通过、2 项外部向量库或 Embedding 跳过。工作区含起点记录的本地 Java 改动。最终版真实三端验收仍待开发。
- 暂存范围核对及 `git diff --cached --check` 通过，清理的旧界面资产在现行文档和应用中无引用。定位本次交付可用 `git log -1 --` 加本文件路径，远端发布以 `git ls-remote --heads origin main` 为准。

## 交接

<!-- next -->

从 [总计划](../../docs/ROADMAP.md) 的 G1 项目与身份开工。开发工作区取得包含本记录的 main 基线后，Server 走 S1–S2、Web 走 W1–W3、AI 走 A1，集成方用 [J1](../../docs/development/PARALLEL_DEVELOPMENT.md) 签收，再进入 G2。真实三端最终版验收仍为 PLANNED。
