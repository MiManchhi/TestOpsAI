# TestOps AI 系统学习路线与进度

更新：2026-11-18（Asia/Shanghai）。状态：IN_PROGRESS。

<!-- handoff:v1 -->

Task: LEARNING-ROADMAP-001
Role: integration
Baseline: 8c3d49303845bda814cb183328309aa9b097156b
Contract: docs/PRODUCT_DESIGN.md、docs/DOMAIN_MODEL.md、docs/contracts/README.md（作为学习资料；实现状态以当前代码/CURRENT 为准）
Scope: 用户授权更新 .memory 中的学习规划与进度；应用/契约/规范文件只读。用户于 2026-11-18 明确授权将本次记忆更新提交并推送 main；不合并其他分支。

## 目标与起始核对

- 用户希望从 C/C++ 背景系统学习本项目涉及的架构、业务、技术与代码，并指出此前讲授零散、缺总览和验证。
- 当前分支 main；起始 HEAD 8c3d493。起始 `git status --short` 为空；只读检查恢复清单和当前工程状态。仓库实际已有 Java 登录、知识库/测试用例等代码；新智能电表 Robot 设计与 v2 契约不能直接当作运行实现。
- 学习依据：[当前交接](../CURRENT.md)、[架构](../../docs/ARCHITECTURE.md)、[产品设计](../../docs/PRODUCT_DESIGN.md)、[领域模型](../../docs/DOMAIN_MODEL.md)、[契约](../../docs/contracts/README.md)、[目录指南](../../docs/development/DIRECTORY_LAYOUT.md)。

## 实施与选择

- 建立固定阶段：系统地图→一条 Web 请求→Java 业务调用链→数据与可靠性→身份授权→Web 闭环→Python AI 服务→RAG/工具链→运行与验收。
- 用户要求严格按序、不临时拓展；语言差异只在读到相关项目代码时解释。每次对话注明阶段和精确进度；用户未确认理解时留在原知识点。
- 新建/更新文件：`.memory/LEARNING.md`（课程路线与进度）、`.memory/CURRENT.md`（当前教学任务/恢复点）、`.memory/INDEX.md`（导航）、本 session。未修改代码、契约或其他用户文件。

## 真实验证

<!-- checks -->

| 日期 / 命令 | 目录 / 代码版本 / 环境 | 结果 | 范围与跳过项 |
| --- | --- | --- | --- |
| 2026-11-18 `scripts/memory.ps1 -Mode Restore -ListOnly` | 仓库根目录，main，起始 HEAD 8c3d493 | 退出 0 | 读取清单并核对 Git；只读 |
| 2026-11-18 `scripts/memory.ps1 -Mode Check` | 仓库根目录，main，起始 HEAD 8c3d493 | 退出 0 | 38 条记录通过；只检查记忆结构与导航 |

## 交接

<!-- next -->

- 当前学习任务 IN_PROGRESS；路线已记录，用户掌握程度不作推定。
- 下次精确起点：阶段 0。先讲智能电表 Robot 测试业务流、全系统职责图与目录地图，清楚标注已实现/冻结/规划；用一个简短复述确认后才进入阶段 1。
- 每次后续教学对话收尾更新 LEARNING、CURRENT、本 session 的进度和下一步。纯问答若无进度变化记录原因即可。
- BLOCKED：无。未执行应用测试或构建；本任务只涉及记忆结构检查。
