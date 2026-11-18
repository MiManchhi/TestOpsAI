# 当前交接

## 学习任务（用户明确授权更新记忆）

2026-11-18：IN_PROGRESS。用户正在通过本对话学习 TestOps AI，允许写入学习规划与进度。本轮已完整回顾课程阶段，尚未做理解验证；掌握情况不变。严格按 [学习路线](LEARNING.md#testops-ai-系统学习路线) 顺序；当前停在阶段 0（整体业务/架构/目录地图尚未由用户复述确认）。下一节只讲业务流、端到端系统架构、技术职责、目录和已实现/冻结/规划的边界，再用小步验证；未确认前不进入阶段 1。每次教学对话结束须同步 LEARNING、本段和学习 session。CI 后续发现本学习记忆的两个 Markdown 格式问题，已在本地按 Prettier 修复并通过全仓格式/记忆检查，准备推送修复提交。仅修改 .memory；应用代码只读。详见 [session](sessions/2026-11-18-06-learning-roadmap.md)。

更新：2026-11-17（Asia/Shanghai）。状态：VERIFIED（协作基线已发布、三个工作区准备与 Ubuntu CI 通过；客户端新会话实测待做）。

<!-- lane:integration -->

## 集成

- AI-PARSER-MERGE-001：VERIFIED（2026-11-18，本机集成门禁与主线发布）。主线 1cbe62e 与 AI 分支 852697e 无冲突合并，Java M1.5 项目域/V6 保留；完整 scripts/check.ps1 退出 0（Web 37、Python 64，Java 45 中 40 项 DB/Redis 跳过），已随合并提交 a215e34 正常推送 origin/main，远端 SHA 一致；收尾记忆随后独立提交，最终 tip 以 Git refs 为准。见 [集成 session](sessions/2026-11-18-05-ai-parser-main-merge.md)。真实 Java 对象/密钥联调与部署仍待做，新远端 CI 未核实。

- DESIGN-CONTRACT-PUBLISH-001：VERIFIED（2026-11-18 提交/正常推送与远端 SHA 核对）。用户认可的最终设计/契约、检查与 [三端开工指南](../docs/development/PARALLEL_DEVELOPMENT.md) 已随 0a1a2b3 推送 origin/main；本轮完整 scripts/check.ps1 退出 0（Web 37，Java 37 项中 32 项 DB/Redis 测试跳过，Python 1 项与检查/构建通过）。指南定义 SERVER-V2-001 / WEB-V2-001 / AI-PARSER-001 首批任务与可粘贴指令；三个工作区只读核实均干净，但尚未同步冻结基线、未自动启动业务任务。收尾记忆随后独立提交推送，最终 tip 以 Git refs 为准；新远端 CI 未核实。见 [session](sessions/2026-11-18-01-design-contract-publish.md)。

- DESIGN-CONTRACT-001：VERIFIED（2026-11-17 最终设计原型/契约自身/工程门禁，不代表新业务运行）。已 pull --ff-only 至 main 41ef74c，M1.4 保留。已保留 Robot 背景，交付 [最终产品设计](../docs/PRODUCT_DESIGN.md)、[领域模型](../docs/DOMAIN_MODEL.md)、[契约 1.0.0](../docs/contracts/README.md) 与 15 页面原型；取代 WEB-002 的选图流程。109 schema/65 operation/827 引用/45 示例与非法输入、SSE 序列检查通过；完整 scripts/check.ps1 退出 0（Web 37 测试，Java 37 项中 32 项 DB/Redis 测试跳过，Python 检查/测试/构建通过）。浏览器主要链路及桌面/手机/平板范围见 [session](sessions/2026-11-17-14-final-design-contract.md)。未提交、未推送，新增业务仍 PLANNED；后续直接消费冻结接口并补真实 provider 测试。

- WEB-002-DESIGN：IMPLEMENTED（2026-11-17 总体设计材料，待用户评审）。用户明确产品针对智能电表测试；已 fetch 核实 main 与 origin/main 同为 72c19b5，工作区起始干净。新增 [页面与 AI 交互设计](../docs/WEB_UI_DESIGN.md) 及三张领域视觉稿，定义测试上下文、提问/引用/草稿保存闭环；未改业务代码、API、立项基线，未提交或推送。完整 scripts/check.ps1 退出 0（Web 37 测试；Java 33 项中 28 项 DB 测试跳过；Python 检查/测试/构建通过），仅验证当前工程，不证明设计中的新能力可用。新增产品能力仍 PLANNED；验证与恢复起点见 [设计 session](sessions/2026-11-17-13-web-ai-design.md)。下一步先评审视觉与领域结构，再细化页面和跨端契约。

- WEB-001 主线集成本机验证通过，待发布：先拉取最新 origin/main 15e98f8，再将已推送的 Web 检查点 ad87e97 合入；自动合并无冲突，保留 M1.3 后端、Docker 启动器及其文档记忆。集成完整 scripts/check.ps1 退出 0（Web 37 测试、Java 33 项中 28 项 DB 测试跳过、Python 检查与构建）；本轮不重新部署容器，见 [集成 session](sessions/2026-11-17-12-web-auth-publish.md)。

- ENG-011：本地整套启动入口 VERIFIED（初次验证基线 101596d；实现提交 72c0b9a 已推送 main）。新增 `scripts/local.ps1`，支持 start/stop/status/logs 与 start -SkipBuild；首次生成配置仅需 Docker，已有凭据与卷保留。默认构建启动、复用镜像启动及完整容器冒烟通过，六服务保持 healthy；入口 http://127.0.0.1:8088。完整门禁退出 0（Java 14 项中 9 项 DB 测试跳过），本机 smoke 注入已有 MYSQL/JWT 配置后退出 0，首次裸跑因缺少 DB 密码失败。见 [启动入口 session](sessions/2026-11-17-11-local-launcher.md)。发布前衔接远端 M1.3 / 3998cfe，集成完整门禁退出 0（Java 33 项中 28 项 DB 测试跳过）；没有重新部署 M1.3，不将 M1.2 容器证据外推。收尾文档随独立提交同步，其他 worktree 未同步。

- ENG-010：用户追加授权后，三个开发分支已正常推送到 origin，并分别跟踪同名远程分支；初次推送 SHA 为 03d2d37，本地/远端逐一核对一致。收尾记忆同步后各空工作分支再快进到同一最新主线，最终版本以 Git refs 为准。见 [远端分支 session](sessions/2026-11-17-10-push-development-branches.md)。ENG-009 中“仅本地”是当时范围，已被本次追加授权更新。

- ENG-009 已完成发布与工作区准备：用户明确授权的协作提交 0a61b30 正常推送 main，三个本地开发分支和同级 worktree 已创建并通过自动角色、布局和干净状态检查。执行结果见 [发布 session](sessions/2026-11-17-09-publish-collaboration-worktrees.md)，本轮不开发业务。收尾记忆以独立 docs 提交同步，最终 SHA 以 Git refs 为准。

- ENG-008 仓库侧已完成：统一文件入口、自动恢复与记忆检查，见 [初始化 session](sessions/2026-11-17-08-ai-collaboration-bootstrap.md)。其未提交变更已在 ENG-009 获授权后随 0a61b30 发布；不改变 ENG-008 当时未提交的历史事实。
- M1.2 实现 e11c558、CI 修复 aeed610 已在主线。协作实现 0a61b30 的 [CI 44048755494](https://github.com/MiManchhi/TestOpsAI/actions/runs/44048755494) 五个 job 全成功；compose 日志确认新版记忆正反例通过，containers 完成六服务验收与隔离清理。证据不包含真实数据库测试或客户端新会话实测。
- 规则入口：[AGENTS](../AGENTS.md)、[协作指南](../docs/development/AI_COLLABORATION.md)。各领域区块是对应基线的快照；分支测试通过与主线集成通过分开记录。
- BLOCKED：无仓库侧阻塞；客户端新会话自动注入仍待实测。
- 本机完整 `scripts/check.ps1` 退出 0；Java 14 项中 9 项数据库测试跳过，不作为真实数据库复验。三个新 worktree 的真实分支自动恢复也通过。当前均已准备跟踪源码；工作区位置和后续验证见发布 session。尚未启动业务开发或配置自动生产部署。

<!-- lane:server -->

## Java

- M1.5 v2 项目隔离与契约基础已随主线 1cbe62e 接受；本次 AI 集成保留项目/成员、幂等框架与 V6，Java 45 项测试中 40 项 DB/Redis 跳过，不能当真实数据库/权限联调通过。源实现见 [M1.5 session](sessions/2026-11-18-01-m1-5-contract-compliance.md)。
- M1.1/M1.2：IMPLEMENTED；历史本机验证记录为 VERIFIED，见 [M1.1](sessions/2026-11-17-06-m1-1-data-baseline.md)、[M1.2](sessions/2026-11-17-07-m1-2-auth.md)。JWT、登录、当前用户、结构化错误、身份迁移已进入 Git。模块上下文见 [server](modules/server.md)。
- M1.3 知识库与测试用例 CRUD + 数据级权限：IMPLEMENTED（编译通过、Spotless 通过、Maven verify BUILD SUCCESS；DB 集成测试因无 MYSQL_PASSWORD 跳过）。V5 迁移三表 + 种子数据、两个业务域完整实现、集成测试覆盖 CRUD/权限/分页/过滤/排序白名单/错误码、文档同步 API.md/DATABASE.md/ADR-0005。详见 [M1.3 session](sessions/2026-11-17-11-m1-3-kb-tc-crud.md)。
- 证据边界：历史 session 记录真实 MySQL Maven verify、完整门禁与容器登录探测；本轮未重跑业务测试或容器验收，不据此确认当前运行健康与最新远端 CI。M1.3 开发分支原验证限于 JDK 21 编译 + Spotless + 非 DB 单元测试；本次 main 集成补验完整 scripts/check.ps1 退出 0（Java 33 项中 28 项 DB 测试跳过）。没有真实 DB 回归或新容器部署证据。
- M1.4 缓存限流：IMPLEMENTED，已随 main 41ef74c 同步；原实现证据见 [session](sessions/2026-11-17-12-m1-4-cache-ratelimit.md)。本轮完整门禁 Java 37 项中 32 项 DB/Redis 测试跳过，不当作真实缓存/限流回归。此前 PLANNED 快照已过期；后续新业务按 DESIGN-CONTRACT-001 的冻结设计推进，不能把设计当运行交付。
- 开发入口：同级 TestOpsAI-server / codex/server-m1。源码、规范与记忆就绪；本机环境和依赖按需单独准备。

<!-- lane:web -->

## Web

- WEB-001：VERIFIED（2026-11-17 本机最终门禁、浏览器与三端冒烟）；已合入主线 4cc2d1c 并正常推送。Web 实现提交 ad87e97 已推送 origin/codex/web-auth，当前 main 合并候选本机验证通过，待发布，见 [任务 session](sessions/2026-11-17-11-web-auth-workspace.md)。
- 已实现 Java M1.2 登录、sessionStorage 标签页会话、刷新验证、过期/401/退出清理、403/网络故障重试、概览与响应式导航。知识库/测试用例仅待开放说明；真实 Java 浏览器联调和本机三端冒烟已通过，前端 37 测试与最终 scripts/check.ps1 退出 0；Java 9 项数据库测试跳过，详见 session，见 [web](modules/web.md)。
- 开发入口：同级 TestOpsAI-web / codex/web-auth。用户已追加授权提交、推送并先拉取最新主线后合并，Web 分支已发布，main 4cc2d1c 已合并并推送，见 [发布 session](sessions/2026-11-17-12-web-auth-publish.md)；本次主线合并按明确授权执行。公共文件为本任务依赖锁、API 与排版兼容说明。

<!-- lane:ai -->

## AI

- AI-PARSER-PUBLISH-001：VERIFIED（2026-11-18，Git 发布与远端 SHA 核对）。功能提交 e1a2d23 已正常推送 origin/codex/ai-ingestion，源码/测试/说明和同步记忆一起发布，无强推，后续已通过 a215e34 合入 main；收尾记忆随后独立提交推送，最终 tip 以 Git refs 为准。见 [发布 session](sessions/2026-11-18-04-ai-parser-publish.md)。新远端 CI 未核实，不把发布当作部署或真实 Java 联调。
- AI-PARSER-001：VERIFIED（2026-11-18，功能提交 e1a2d23，限本机 AI 与替身验证）。已实现 Robot/XML 静态解析、严格契约 DTO、RS256 服务/委托校验、授权对象客户端、两条内部路由与可取消/超时回收进程。最终 scripts/check.ps1 退出 0（Python 64、Web 37，Java 37 中 32 项 DB/Redis 跳过），真实 Uvicorn→回环 HTTP 替身→解析子进程冒烟通过。真实 Java 对象接口/签名配置联调与部署仍 PLANNED；本机三端 smoke 因 Java 数据库连接不可用退出 1，不算通过。见 [本任务](sessions/2026-11-18-03-ai-parser.md) 与 [运行说明](../docs/development/AI_PARSER.md)。
- AI-SYNC-001：2026-11-18 已获授权 fetch origin 并将 codex/ai-ingestion 从 101596d 快进至 origin/main 17ed8ee（12 个提交），最新产品设计、契约 1.0.0 与并行指南已进入本工作区；无冲突、未推送。同步后恢复与收尾验证见 [同步 session](sessions/2026-11-18-02-ai-sync-main.md)。同步任务当时未启动业务开发；当前解析状态见 AI-PARSER-001，本区块的“尚未同步”旧快照已更新，其他工作区状态不外推。
- FastAPI 健康检查与 Robot/XML 解析接口已实现；知识文档解析/分块、模型适配、RAG 与 Agent 仍 PLANNED，见 [ai-service](modules/ai-service.md)。
- 当前授权为 AI-PARSER-001，Java 真实联调不在本批完成范围；本批交付后从冻结契约与解析说明协调 Java 对象读取和签名配置，再确定知识文档处理任务。
- 开发入口：同级 TestOpsAI-ai / codex/ai-ingestion。依赖在本工作区独立安装，无真实密钥复制；功能提交 e1a2d23 已推送 AI 分支，现已通过 a215e34 合入 main；其他工作区未自动同步。

## 历史证据与限制

- M0 容器和 Ubuntu CI 历史证据见 [容器 session](sessions/2026-11-17-05-container-delivery.md) 和 [EVIDENCE](EVIDENCE.md)，只适用各自版本，不等于 M1.2 的远端验收。
- 历史开发依赖审计提示、Python TestClient 弃用和 Java agent 提示未在本轮复核，不声明已修复。
- 所有后续任务仍受明确授权范围约束；既有“停止 M0”仅描述历史任务，当前事实以已授权 M1 实现和本轮协作初始化为准。
