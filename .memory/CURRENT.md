# 当前交接

更新：2026-11-17（Asia/Shanghai）。状态：IN_PROGRESS（协作基线发布与三个隔离工作区准备；仓库侧检查已通过，客户端新会话实测待做）。

<!-- lane:integration -->

## 集成

- 当前任务 ENG-009：用户明确授权提交、推送协作初始化，并创建 Java/Web/AI 三个分支及同级 worktree；核实 CI 覆盖和本次结果。执行计划与结果见 [发布 session](sessions/2026-11-17-09-publish-collaboration-worktrees.md)，本轮不开发业务。

- ENG-008 仓库侧已完成：统一文件入口、自动恢复与记忆检查，见 [本任务 session](sessions/2026-11-17-08-ai-collaboration-bootstrap.md)。仅协作工程变更，无业务变更、分支创建、提交或推送。
- 起始基线 main / fc826ba；本轮开始工作区干净。M1.2 实现提交 e11c558、CI 变量修复 aeed610 已存在；本地 origin/main 引用同为 fc826ba，未在本轮联网核实远端最新状态或 CI。
- 规则入口：[AGENTS](../AGENTS.md)、[协作指南](../docs/development/AI_COLLABORATION.md)。各领域区块是对应基线的快照；分支测试通过与主线集成通过分开记录。
- BLOCKED：无仓库侧阻塞；客户端新会话自动注入仍待实测。
- 本机完整 `scripts/check.ps1` 退出 0；Java 14 项中 9 项数据库测试跳过，不作为真实数据库复验。自动 integration 恢复、显式 Web 恢复和 ListOnly 清单通过；新增结构反例通过。下一步：审查并提交本次工程变更后，在各客户端新会话输入“汇报当前进度，先不改文件”，核对入口注入与实际读取记录，再按授权准备 worktree 和业务任务。

<!-- lane:server -->

## Java

- M1.1/M1.2：IMPLEMENTED；历史本机验证记录为 VERIFIED，见 [M1.1](sessions/2026-11-17-06-m1-1-data-baseline.md)、[M1.2](sessions/2026-11-17-07-m1-2-auth.md)。JWT、登录、当前用户、结构化错误、身份迁移已进入 Git。模块上下文见 [server](modules/server.md)。
- 证据边界：历史 session 记录真实 MySQL Maven verify、完整门禁与容器登录探测；本轮未重跑业务测试或容器验收，不据此确认当前运行健康与最新远端 CI。
- M1.3 业务元数据、资源级权限及 M1.4 缓存限流：PLANNED。暂无已授权的下一项业务任务；先确定最小任务和 API 契约。

<!-- lane:web -->

## Web

- 健康检查骨架已实现，业务登录页和管理页面仍 PLANNED，见 [web](modules/web.md)。
- 后续候选：基于 M1.2 契约完成登录闭环；须先分配明确任务、工作区与验收。没有运行中的前端开发任务。

<!-- lane:ai -->

## AI

- FastAPI 健康检查骨架已实现；文档解析、分块、模型适配、RAG 与 Agent 仍 PLANNED，见 [ai-service](modules/ai-service.md)。
- 后续候选：自造样例的独立文档处理；跨服务接入依赖 Java 业务/授权与 AI 契约。暂无已授权的 AI 业务任务。

## 历史证据与限制

- M0 容器和 Ubuntu CI 历史证据见 [容器 session](sessions/2026-11-17-05-container-delivery.md) 和 [EVIDENCE](EVIDENCE.md)，只适用各自版本，不等于 M1.2 的远端验收。
- 历史开发依赖审计提示、Python TestClient 弃用和 Java agent 提示未在本轮复核，不声明已修复。
- 所有后续任务仍受明确授权范围约束；既有“停止 M0”仅描述历史任务，当前事实以已授权 M1 实现和本轮协作初始化为准。
