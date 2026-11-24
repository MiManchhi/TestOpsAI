# 最终版入口、复测与会话

更新：2026-11-20（Asia/Shanghai）。状态：VERIFIED（本机三端联合与浏览器，模型和 Embedding 使用显式替身）。

<!-- handoff:v1 -->

Task: WORKSPACE-FINAL-INTEGRATION-001
Role: integration
Baseline: main 4c21d1a 与 Server 70def6d、Web e668b29、AI 194d96c 上的本地变更。
Contract: 工作台 2.0.0，公开 v3、内部 AI 签名 v1/v2。
Scope: 按同一最终版定稿修正预览入口，三端联调资料、复测与对话。

## 验证

<!-- checks -->

8089 已重建三端，并在原独立预览库前进迁移到 V14。修正了 Robot 对象委托缺少运行范围、SSE 异步完成重复认证、预览的登录和导航仍显示旧工作台三个问题。`node tests/contracts/workspace-review.cjs` 与 `npm run contracts:check` 通过。`node tests/contracts/workspace-live.cjs` 在独立测试项目通过审批、双版本、真正 Robot 解析、逐行日志与原件、SSE、访问隔离和撤权后的历史/事件遮蔽。真实 Edge 验证三类登录、手机历史栏、首问与引用、复测登记与对比、聊天附件到资料/运行并返回原对话。`/p/2/chat` 和 `/p/5/runs/4` 可复验。

## 交接

<!-- next -->

遵循[总计划](../../docs/ROADMAP.md)与[三端验收](../../docs/development/PARALLEL_DEVELOPMENT.md)。BLOCKED（最终版真实验收）：缺部署方的聊天模型与真实 Embedding 配置。完成生成/补写质量、全部恢复异常及部署签收后才能标记最终版交付。现有开发树改动未提交。
