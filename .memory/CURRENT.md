# 当前交接

更新：2026-11-24（Asia/Shanghai）。状态：IN_PROGRESS（聊天已恢复；资料解析待恢复）。

## 学习任务（用户明确授权更新记忆）

2026-11-18：IN_PROGRESS。用户确认阶段 0（整体业务/架构/目录地图）完成；按用户自报记录，不额外推断掌握。当前进入阶段 1，沿登录请求 `POST /api/v1/auth/login` 从 Vue API 路径追踪至 Java Controller；本节仅讲路径/方法/代理/路由映射，认证算法、数据库及响应细节后续分节。每次教学对话结束同步 LEARNING、本段和学习 session。此前 CI 格式修复已推送，详见 [session](sessions/2026-11-18-06-learning-roadmap.md)。仅修改 .memory；应用代码只读。

<!-- lane:integration -->

根目录启动的工作台已部署到本机 8088，Web、Java、Python、MySQL、Redis、Qdrant 健康。12 个原有项目均可从平台查看；admin 的平台身份及项目 7、12 的负责人身份经用户确认恢复，并从浏览器复核。独立部署用协议测试服务走通聊天框两轮提问、双版本判定、查出处、复制 Robot 与刷新恢复。完整门禁通过。2026-11-23 复诊：旧聊天地址返回 403 insufficient_quota（免费额度耗尽），向量地址 200；资料 V1 重试返回 PROFILE_CHANGED：SQLite 中 default 的指纹仍是 dashscope 地址，与当前 maas 地址不一致，提交即被拒绝，Qdrant 无任何向量。2026-11-24 用户自测聊天已可用（聊天地址改用 token-plan.maas.qianwenaiapi.com，向量地址未变），资料解析仍待恢复。工作台三端整合已提交 `21f6170`，提交前完整门禁退出 0。细节与恢复选项见[百炼排查](sessions/2026-11-23-02-bailian-auth-debug.md)。已生成[抄表终端导入样例](../samples/README.md)。细项和签收边界见[本次交接](sessions/2026-11-20-07-workspace-cutover.md)、[百炼排查](sessions/2026-11-23-02-bailian-auth-debug.md)与[总计划](../docs/ROADMAP.md)。

<!-- lane:server -->

Java 负责权限、资料审核与依据、运行、对话、来源复核及审计。全新 MySQL + Redis 验证有 22 项通过，无跳过。见[Java 交接](sessions/2026-11-20-07-workspace-server.md)。

<!-- lane:web -->

角色首页、资料、运行和项目管理使用单一客户端。新版工作台支持个人历史、候选复测、草稿及可查来源。45 项测试、类型、lint、构建通过。见[Web 交接](sessions/2026-11-20-07-single-workspace-web.md)。

<!-- lane:ai -->

Python 接收签名范围，按本轮问题和有权使用的历史处理评审、草稿、运行判断及追问。137 项通过，3 项真实外部服务检查按配置执行。见[AI 交接](sessions/2026-11-20-07-workspace-conversation-ai.md)。
