# 当前交接

更新：2026-11-17（Asia/Shanghai）。状态：VERIFIED（协作基线已发布、三个工作区准备与 Ubuntu CI 通过；客户端新会话实测待做）。

<!-- lane:integration -->

## 集成

- ENG-010：用户追加授权后，三个开发分支已正常推送到 origin，并分别跟踪同名远程分支；初次推送 SHA 为 03d2d37，本地/远端逐一核对一致。收尾记忆同步后各空工作分支再快进到同一最新主线，最终版本以 Git refs 为准。见 [远端分支 session](sessions/2026-11-17-10-push-development-branches.md)。ENG-009 中“仅本地”是当时范围，已被本次追加授权更新。

- ENG-009 已完成发布与工作区准备：用户明确授权的协作提交 0a61b30 正常推送 main，三个本地开发分支和同级 worktree 已创建并通过自动角色、布局和干净状态检查。执行结果见 [发布 session](sessions/2026-11-17-09-publish-collaboration-worktrees.md)，本轮不开发业务。收尾记忆以独立 docs 提交同步，最终 SHA 以 Git refs 为准。

- ENG-008 仓库侧已完成：统一文件入口、自动恢复与记忆检查，见 [初始化 session](sessions/2026-11-17-08-ai-collaboration-bootstrap.md)。其未提交变更已在 ENG-009 获授权后随 0a61b30 发布；不改变 ENG-008 当时未提交的历史事实。
- M1.2 实现 e11c558、CI 修复 aeed610 已在主线。协作实现 0a61b30 的 [CI 44048755494](https://github.com/MiManchhi/TestOpsAI/actions/runs/44048755494) 五个 job 全成功；compose 日志确认新版记忆正反例通过，containers 完成六服务验收与隔离清理。证据不包含真实数据库测试或客户端新会话实测。
- 规则入口：[AGENTS](../AGENTS.md)、[协作指南](../docs/development/AI_COLLABORATION.md)。各领域区块是对应基线的快照；分支测试通过与主线集成通过分开记录。
- BLOCKED：无仓库侧阻塞；客户端新会话自动注入仍待实测。
- 本机完整 `scripts/check.ps1` 退出 0；Java 14 项中 9 项数据库测试跳过，不作为真实数据库复验。三个新 worktree 的真实分支自动恢复也通过。当前均已准备跟踪源码；工作区位置和后续验证见发布 session。尚未启动业务开发或配置自动生产部署。

<!-- lane:server -->

## Java

- M1.1/M1.2：IMPLEMENTED；历史本机验证记录为 VERIFIED，见 [M1.1](sessions/2026-11-17-06-m1-1-data-baseline.md)、[M1.2](sessions/2026-11-17-07-m1-2-auth.md)。JWT、登录、当前用户、结构化错误、身份迁移已进入 Git。模块上下文见 [server](modules/server.md)。
- 证据边界：历史 session 记录真实 MySQL Maven verify、完整门禁与容器登录探测；本轮未重跑业务测试或容器验收，不据此确认当前运行健康与最新远端 CI。
- M1.3 业务元数据、资源级权限及 M1.4 缓存限流：PLANNED。暂无已授权的下一项业务任务；先确定最小任务和 API 契约。
- 开发入口：同级 TestOpsAI-server / codex/server-m1。源码、规范与记忆就绪；本机环境和依赖按需单独准备。

<!-- lane:web -->

## Web

- WEB-001：VERIFIED（2026-11-17 本机最终门禁、浏览器与三端冒烟）；未合入主线。基线 101596d，当前为 codex/web-auth 未提交工作区，见 [任务 session](sessions/2026-11-17-11-web-auth-workspace.md)。
- 已实现 Java M1.2 登录、sessionStorage 标签页会话、刷新验证、过期/401/退出清理、403/网络故障重试、概览与响应式导航。知识库/测试用例仅待开放说明；真实 Java 浏览器联调和本机三端冒烟已通过，前端 37 测试与最终 scripts/check.ps1 退出 0；Java 9 项数据库测试跳过，详见 session，见 [web](modules/web.md)。
- 开发入口：同级 TestOpsAI-web / codex/web-auth。用户已追加授权提交与正常推送，发布进行中，见 [发布 session](sessions/2026-11-17-12-web-auth-publish.md)；主线合并仍由用户负责。公共文件为本任务依赖锁、API 与排版兼容说明。

<!-- lane:ai -->

## AI

- FastAPI 健康检查骨架已实现；文档解析、分块、模型适配、RAG 与 Agent 仍 PLANNED，见 [ai-service](modules/ai-service.md)。
- 后续候选：自造样例的独立文档处理；跨服务接入依赖 Java 业务/授权与 AI 契约。暂无已授权的 AI 业务任务。
- 开发入口：同级 TestOpsAI-ai / codex/ai-ingestion。没有复制密钥或依赖环境；跨服务接入仍需满足契约前置条件。

## 历史证据与限制

- M0 容器和 Ubuntu CI 历史证据见 [容器 session](sessions/2026-11-17-05-container-delivery.md) 和 [EVIDENCE](EVIDENCE.md)，只适用各自版本，不等于 M1.2 的远端验收。
- 历史开发依赖审计提示、Python TestClient 弃用和 Java agent 提示未在本轮复核，不声明已修复。
- 所有后续任务仍受明确授权范围约束；既有“停止 M0”仅描述历史任务，当前事实以已授权 M1 实现和本轮协作初始化为准。
