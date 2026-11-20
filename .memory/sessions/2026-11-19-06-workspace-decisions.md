# 最终工作台业务规则定稿

更新：2026-11-19（Asia/Shanghai）。状态：VERIFIED（设计、契约与样稿走查）。

<!-- handoff:v1 -->

Task: WORKSPACE-DECISIONS-001
Role: integration
Baseline: main 039c8319ae190a1fb3177f82da9dbd0bd573a986
Contract: 工作台公共 v3 / 内部 v2，沿用既有兼容 v1。
Scope: 按本次六项选择更新设计、样稿、三端计划及契约验收；保留原有未提交工作。

## 起点

已读取恢复清单，核对 [上次设计](2026-11-19-05-workspace-blueprint.md)与现有文档、工作区和测试。重点核对交接、先审原件和整套依据的端到端一致性。

## 验证

<!-- checks -->

工作台 2.0.0 包含 54 个操作、69 个 schema，每个操作映射提供方、消费者和联合验收。离线 Node 检查通过。Edge/Playwright 以 1440×960、390×844 两种视口走查 27 个页面状态；交接、退回、整包换版、键盘与减少动画断言通过。新增 `node tests/contracts/workspace-design.cjs` 可重跑界面断言，`node tests/contracts/workspace-review.cjs` 覆盖授权、审核状态、回执、整包、来源、委托摘要金样本。离线样例通过，`node --check tests/contracts/workspace-live.cjs` 通过。

- `scripts/check.ps1` 首轮因新浏览器验收脚本的格式失败，按仓库 Prettier 重排后复跑退出 0。Web 18 文件、94 测试通过；Java 65 项中 5 执行、60 项 DB/Redis 测试跳过；AI 115 通过、2 项外部 Qdrant/Embedding 跳过，构建成功。
- 核对 12 份主要文档的相对链接，缺失文件 0；`git diff --check` 通过。工作区原有三份 Java 文件改动独立于此次定稿。未提交、未推送。
- 尚未开发最终版 provider，未运行最终版 J1–J7 真实三端链路与模型。此前运行记录仅说明已有功能。

## 交接

<!-- next -->

用户启动开发后从[总计划](../../docs/ROADMAP.md) G1、W1/S1/A1 开始。使用[操作实现表](../../docs/contracts/workspace.bindings.json)、[联合验收](../../docs/development/PARALLEL_DEVELOPMENT.md)及三端执行计划。发布整包按实际确认时刻生效，跨语言 scope 摘要、服务身份及 UI/服务超时参数为本次技术设计取值。
