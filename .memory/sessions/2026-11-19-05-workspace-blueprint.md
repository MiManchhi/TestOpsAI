# AI 工作台全界面与三端执行蓝图

更新：2026-11-19（Asia/Shanghai）。状态：IMPLEMENTED（设计评审材料与验收工具）。

<!-- handoff:v1 -->

Task: WORKSPACE-BLUEPRINT-001
Role: integration
Baseline: main 039c8319ae190a1fb3177f82da9dbd0bd573a986
Contract: 工作台 2.0.0-rc.1 为唯一新开发候选；旧 1.0.0 仅供现存应用兼容。
Scope: 用户要求补全界面、权限、三端接口与逐步实施文档。仅设计交付，保留现有未提交修改。

## 起点

已恢复入口、模块与 session，核对 Git、现有路由、契约、立项与 Robot 背景。基本工作台方向获认可，管理界面需用户看到后评审。

## 验证

<!-- checks -->

- Edge / Playwright：桌面与窄屏共 52 次页面检查，无页面异常；管理、来源、业务页返回原会话及角色可见性在样稿中通过。[工作台界面说明](../../docs/WEB_UI_DESIGN.md)。
- `node tests/contracts/workspace-review.cjs` 通过 48 操作、63 模型、正反例和事件形状。旧 `npm run contracts:check` 通过 109 模型/65 操作。真实联调脚本仅作语法及静态检查，目标 v3 provider 尚未开发。
- 完整 `scripts/check.ps1` 退出 0：Web 94 项通过；Java 65 项中 5 项运行、60 项隔离 DB/Redis 测试因未配置跳过；AI 115 项通过、2 项真实外部服务检查跳过。未据此宣称最终版三端已联调。记忆与布局检查通过。
- 清理旧原型、截图、三方向探索和失效的开工计划，替换现有文档入口。保留 Robot 原始参考、Git 与工程验证历史、运行时兼容契约。

## 交接

<!-- next -->

用户从[全界面说明](../../docs/WEB_UI_DESIGN.md)走查，以[五项建议](../../docs/PRODUCT_DESIGN.md)集中评审后冻结。按[联调手册](../../docs/development/PARALLEL_DEVELOPMENT.md)分端交付。未提交、未推送。
