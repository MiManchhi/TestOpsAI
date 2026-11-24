# 三端资料、审核与验收依据联调

更新：2026-11-20（Asia/Shanghai）。状态：VERIFIED（三端隔离链路已验证）。

<!-- handoff:v1 -->

Task: WORKSPACE-G2-001
Role: integration
Baseline: main 4c21d1a
Contract: 工作台 2.0.0，公开 v3，内部块导出 v2 与索引/对象 v1。
Scope: 按用户授权先发布 G1，再开展 三端资料、审核与验收依据联调。

## 实施

G1 已分别推送并核对远端 Server 70def6d、Web d6b47e9、AI 194d96c。资料开发从已确认的项目与成员权限开始。

## 验证

<!-- checks -->

- Server 70def6d、Web 功能 d6b47e9、Web 镜像修复 e668b29、AI 194d96c 已推送并核对远端。G2 更新了 Compose 覆盖配置、知识专项验收命令与三端本地工作区。
- 在隔离浏览器、Java、Python、MySQL、Redis、Qdrant 上验证待审与退回无索引作业，Markdown/DOCX/PDF 原文定位与无文本失败，批准后 READY，整套发布和历史，撤权后正文和原件均失效，交接后审核权限转移。
- `node tests/contracts/workspace-live.cjs --knowledge-only` 通过。Embedding 使用固定响应的本地 HTTP fixture，真实供应侧 Embedding 未运行。最终版问答及其引用注册按 S5/S6/A4 验收。

## 交接

<!-- next -->

G2 本地改动待提交。按[总计划](../../docs/ROADMAP.md)进入 Robot 复测登记。
