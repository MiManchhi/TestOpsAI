# AI 知识索引提交与主线合并

更新：2026-11-19（Asia/Shanghai）。状态：IN_PROGRESS。

<!-- handoff:v1 -->

Task: AI-INGESTION-MERGE-001
Role: integration
Baseline: AI 210ec4ee02606b3d1dd8ec09dd7173cb207db9bd；main/origin/main 61524f3abd71b481ff1a243b5ba8211e9b452f0e
Contract: docs/contracts/README.md 1.0.0；不修改公共契约
Scope: 用户明确“提交并合并（直接执行即可）”；提交已验收 AI 成果与记忆，保留最新 main 的 Java/Web 工作，合入 main 并正常推送；不强推、不部署

## 起点

- AI 分支未提交的 25 份文件均为 AI-INGESTION-001 产物；主线工作区干净。fetch 后 main 与 origin/main 同为 61524f3，已包含其他会话新增的 Java 用例目录与 Web 工作流。
- 新主线没有修改 AGENTS、开发规范、AI 服务或冻结契约；CURRENT 的其他领域新增记录必须保留。
- 原实现阶段“不提交/推送/合并”保留为历史事实，本次追加授权解除该限制。完整业务验收已在上一轮完成；本次直接提交合并，不追加业务开发或重复验收。

## 验证

<!-- checks -->

- 上轮完整 scripts/check.ps1 退出 0：AI 115 passed/2 skipped、Web 59、Java 48 中 43 项 DB/Redis 跳过，构建/契约/格式/类型通过；独立 AI HTTP 替身冒烟通过。证据范围见 [实现 session](2026-11-18-09-ai-ingestion.md)。
- 真实 Qdrant HTTP/Embedding、Java READY/引用注册与部署仍待验收；三端 smoke 的 Java 数据库配置限制未解除。发布与合并不替代这些验收。
- 当前核对 status、fetch、主线差异成功；提交前执行格式、memory 与 diff 检查，合并后核对父提交、远端 SHA 与工作区。

## 交接

<!-- next -->

- 提交/合并/推送进行中；结果以本 session 后续记录及 Git refs 为准。
- BLOCKED：无当前 Git 阻塞；不覆盖其他领域工作，不将旧业务证据外推至新增主线功能。
- [CURRENT](../CURRENT.md)。
