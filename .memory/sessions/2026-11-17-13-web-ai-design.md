# Web 与 AI 总体设计

更新：2026-11-17。状态：VERIFIED（设计产物与工程门禁；方案待用户评审）。

<!-- handoff:v1 -->

Task: WEB-002-DESIGN
Role: integration
Baseline: 72c19b5a7357a24dce9b71387ef55ba06f859c1e
Contract: docs/PROJECT_BLUEPRINT.md、docs/API.md；本轮仅产品设计，不冻结新接口
Scope: 用户授权同步最新代码并先交付总体页面设计稿；docs 下设计材料与本任务记忆；不实施业务 UI、不提交、不推送，不同步其他 worktree

## 目标与起始核对

- 2026-11-17 / Asia/Shanghai；TestOpsAI / main，起始工作区干净。
- 已读取六入口、协作指南、四模块及 Restore 清单的相关 session，核对现有 Vue 导航/概览/样式和 API。
- 本机脚本策略用当前子进程 Bypass；Git 使用进程级 safe.directory，未改全局设置。沙箱内 fetch 因 FETCH_HEAD 写权限失败，获批环境重试退出 0；HEAD 与 origin/main 均为 72c19b5，无需合并。
- 当前登录/导航已合并，业务 UI 仍占位，Python 仅健康骨架。设计需区别未来目标与当前实现。

## 实施与选择

- 先定义全站信息架构、提问/上下文/回答/引用/业务落地闭环，再给三种独立视觉方向；保留既有青绿色基调。
- 正式方案归 docs/WEB_UI_DESIGN.md；本轮不更改 apps 或 API。
- 用户补充明确系统针对智能电表测试；设计改为计量、通信规约、费率/时钟、费控、事件等领域，新增显式测试上下文建议；不虚构实际规程阈值或设备接入。三张图按领域全部重绘，以最新集合为准。
- 使用内置 Image Gen 生成三种独立方向：智能电表 AI 工作台、回答与依据并排、用例页面 AI 侧栏；提示包含现有颜色/字体、1440×1024 目标、2026-11-17 日期及自造示例约束。最终图片保存在 docs/assets/web-ui-ai-workspace.png、web-ui-evidence-studio.png、web-ui-context-copilot.png，链接见总体设计。旧通用探索图未纳入仓库。
- 已人工查看生成结果；规范内容均为示例。生成稿中第三方图标、冗长标题及用例查重范围问题在正式方案注明，后续实施须修正，未冒充像素级最终稿。
- BLOCKED：无。Product Design saved context 不存在，使用当前仓库需求与样式。

## 真实验证

<!-- checks -->

- git fetch origin 退出 0；HEAD 与 origin/main SHA 一致。
- Restore -ListOnly 在进程级 Git 配置下退出 0，28 份既有活动记忆检查通过。
- 指定 Markdown Prettier write 退出 0；初次完整 scripts/check.ps1 因沙箱无法扫描既有 apps/ai-service/.pytest_cache 在格式阶段退出 1，不算通过。获批环境重新完整执行退出 0。
- 完整门禁：29 份记忆、布局/9 项布局回归/记忆正反例/14 份脚本解析、全仓格式、Web 类型/lint/37 测试/构建、Java verify/Spotless（33 项中 28 项 DB 测试因未配置凭据跳过）、Python lock/Ruff/mypy/双平台 smoke 静态检查/1 测试/构建通过。既有 Java agent 与 Starlette 弃用提示未处理。
- 未部署容器、未调用真实模型业务、未执行真实电表或真实数据库回归；图片是设计材料。收尾仅更新记忆与一处文案后补跑定向格式、memory Check 和 diff 检查。

## 交接

<!-- next -->

- 总体设计材料 IMPLEMENTED、工程门禁 VERIFIED；方案待用户评审，新增产品能力仍 PLANNED。无业务代码、接口、根配置变化；新增 docs/WEB_UI_DESIGN.md 与三张配图，更新 CURRENT、Web 模块及本 session；未提交、未推送。
- BLOCKED：无本轮遗留环境阻塞。主线已核实最新 72c19b5；其他 worktree 未同步。
- 后续用户选定方向再进入 UI 实施；AI 能力依赖跨端契约和真实服务。
- [CURRENT](../CURRENT.md)、[Web](../modules/web.md)。
