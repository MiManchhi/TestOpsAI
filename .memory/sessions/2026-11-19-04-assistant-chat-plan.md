# AI 助手直接对话方案与研发顺序

更新：2026-11-19（Asia/Shanghai）。状态：PLANNED（改造方案待评审）。

<!-- handoff:v1 -->

Task: ASSISTANT-CHAT-PLAN-001
Role: integration
Baseline: main 039c831，保留正在工作的 Java 搜索修复与联调记录
Contract: docs/contracts/README.md 1.0.0，拟评审自动检索模式
Scope: 分析用户截图、核对 Vue/Java/Python 与冻结契约、完善整体路线图。此次只更新规划和交接。

## 现状与目标

- 用户希望左侧是会话历史，右侧进入即可提问，默认自动引用当前项目知识，避免首问前的表单步骤。
- 用户截图显示显式新建、上下文选择、历史区错误。Vue 禁用未建会话的输入，当前契约只支持 SELECTED 和 NONE；Java 对话接口的 404 已见 [本机联调](2026-11-19-03-three-tier-integration.md)。

## 验证

<!-- checks -->

- 已按截图、现行 Vue、请求 schema 和 Java/Python 路由核对。未重新启动、执行业务测试或实现页面。
- 路线图、CURRENT 与本 session 定向 Prettier 检查通过，`scripts/memory.ps1 -Mode Check` 通过 53 份记录，`git diff --check` 退出 0。

## 交接

<!-- next -->

- [整体路线图](../../docs/ROADMAP.md) 承载评审方案和后续验收顺序。建议用首问创建、项目内自动检索和可核对引用做最小切片，Robot 导入与知识上传共用授权对象和签名。
- 下一步是评审首问、回答中、历史与引用四种界面状态，确认后再更新设计和契约。当前源码、现行产品设计和冻结契约未改动。
