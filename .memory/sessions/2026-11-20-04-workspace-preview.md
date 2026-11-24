# 本机新版工作台预览

更新：2026-11-20（Asia/Shanghai）。状态：VERIFIED（本机 G1/G2 预览与三个角色页面）。

<!-- handoff:v1 -->

Task: WORKSPACE-PREVIEW-001
Role: integration
Baseline: main 4c21d1a；Server 70def6d、Web e668b29、AI 194d96c 加各工作区现有 G2 本地变更。
Contract: 工作台 2.0.0，账号、项目、资料与验收依据。
Scope: 回答旧界面与启动入口问题，保留现有 8088 服务并提供隔离的本机手工验收环境。

## 实施

- 通过忽略目录中的 `.tools/workspace-preview/start.ps1` 启动独立的 `testops-workspace-preview` Compose 项目，仅为 Web 绑定 `127.0.0.1:8089`。使用 G2 联调产出的 Server JAR 和 Web 镜像、对应的 AI 源码以及独立的数据库、对象和索引卷。
- 建立自造的智能电表项目、项目负责人和开发工程师体验账号，准备一份已解析的 Markdown、一份待审核资料和一套已发布的验收依据。预览的 Embedding 使用本机固定响应。项目资料入口为 `http://127.0.0.1:8089/p/2/documents`。

## 验证

<!-- checks -->

- `.\.tools\workspace-preview\start.ps1` 退出 0，`GET /healthz` 返回 200，`docker ps` 同时显示 8088 与 8089 的服务均健康。
- `node .tools/workspace-preview/check.cjs` 用 Edge 验证负责人能看项目成员、验收依据和待审核操作，开发能读解析正文与下载原件，平台管理员能进入账号、平台项目和处理状态。浏览器检查未更改待审核示例。
- 本次复验面向当前预览产物，未重跑完整质量门禁。G3 Robot 复测登记与 G4 新聊天工作台沿用[总计划](../../docs/ROADMAP.md)。

## 交接

<!-- next -->

预览继续运行，重启复用同一组本机产物和数据卷。需要更换预览版本时先重新构建对应三个工作区的产物。G2 开发范围、协议与质量门禁见[联调交接](2026-11-20-03-workspace-g2-integration.md)。
