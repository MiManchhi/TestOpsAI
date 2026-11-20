# 当前交接

## 学习任务（用户明确授权更新记忆）

2026-11-18：IN_PROGRESS。用户确认阶段 0（整体业务/架构/目录地图）完成；按用户自报记录，不额外推断掌握。当前进入阶段 1，沿登录请求 `POST /api/v1/auth/login` 从 Vue API 路径追踪至 Java Controller；本节仅讲路径/方法/代理/路由映射，认证算法、数据库及响应细节后续分节。每次教学对话结束同步 LEARNING、本段和学习 session。此前 CI 格式修复已推送，详见 [session](sessions/2026-11-18-06-learning-roadmap.md)。仅修改 .memory；应用代码只读。

更新：2026-11-17（Asia/Shanghai）。状态：VERIFIED（协作基线已发布、三个工作区准备与 Ubuntu CI 通过；客户端新会话实测待做）。

<!-- lane:integration -->

- WORKSPACE-BLUEPRINT-RELEASE-001（2026-11-20）：[蓝图交付检查与开工交接](sessions/2026-11-20-01-workspace-blueprint-release.md)。后续按[总计划](../docs/ROADMAP.md)完成 G1–G5，以 J1–J7 的真实验收为准。

- WORKSPACE-DECISIONS-001（2026-11-19）：VERIFIED（设计资产，验证范围见本轮交接）。六项集中评审决定已并入[产品蓝图](../docs/PRODUCT_DESIGN.md)、[总计划](../docs/ROADMAP.md)、三端详细执行计划及[联合验收](../docs/development/PARALLEL_DEVELOPMENT.md)。设计验证与未运行的真实链路见[定稿交接](sessions/2026-11-19-06-workspace-decisions.md)。业务实现 PLANNED。

- DEBUG-V2-PROJECTS-001：VERIFIED（2026-11-18）。按用户决定改后端对齐冻结契约：`GET /api/v2/projects` 返回 `{items,total,page,size}`，支持 page/size 与 400 校验；`description` null 归一为空串。真实 MySQL 下 ProjectApiTest 11/11 通过；同源 ajv 校验 12 条 item 全部合规；浏览器登录后概览页正常显示当前项目。仍未实现：`/api/v2/projects/{id}/test-suites`、`/test-cases`、`/overview` 返回 404，Robot 用例页因此不可用。见 [session](sessions/2026-11-18-09-web-blank-csp-debug.md)。未提交。
- DEBUG-WEB-CSP-001：VERIFIED（2026-11-18，只调试）。白屏根因为 CSP `script-src 'self'` 阻断 Ajv 运行期 `new Function` 编译，抛 EvalError 导致 Vue 未挂载；已在 infra/nginx/default.conf 放开 `'unsafe-eval'` 并重建 web，CDP 复核 `/login` 正常渲染、浏览器内 admin 登录可进入 `/overview`。同时修复 smoke.ps1 的 docker ps JSON 解析（UTF-8 解码 + 数组/逐行兼容），`local.ps1 start -SkipBuild` 在 PowerShell 5.1 下退出 0。**未修复**：`GET /api/v2/projects` 返回裸数组与 `ProjectPage` 契约不符，概览页显示「无法读取项目」，属契约级变更待决策。未提交。见 [session](sessions/2026-11-18-09-web-blank-csp-debug.md)。

## 集成

- ASSISTANT-CHAT-PLAN-001 与 WORKSPACE-BLUEPRINT-001 为本轮前的设计过程，交互与实施现以 WORKSPACE-DECISIONS-001 为准。

- THREE-TIER-INTEGRATION-001：VERIFIED（2026-11-19，本地已实现链路）。基线 `039c831` 加未提交的 Java 搜索修复，六服务 healthy，V8 已在本机应用；真实浏览器登录、项目、套件与用例筛选详情通过。隔离 MySQL/Redis 上完整门禁退出 0，Web 94 项、Java 65 项无跳过，AI 115 项通过、2 项外部验收跳过，真实 Qdrant HTTP 另验通过。BLOCKED（三端业务闭环）：缺 Java 导入与对象授权等接口，Compose 未启用 AI 解析和索引配置，详见 [联调交接](sessions/2026-11-19-03-three-tier-integration.md)。

- DEV-BRANCHES-MAIN-SYNC-001：VERIFIED（2026-11-19，本地反向同步）。Server、Web、AI 三个工作区已快进至 `main` 的 `039c831`，工作区均干净。远端开发分支尚未推送，见 [同步交接](sessions/2026-11-19-02-dev-branches-main-sync.md)。随后用户允许联调，结果见上条。

- AI-INGESTION-MERGE-001：VERIFIED（2026-11-19，Git 提交/合并/发布）。AI 功能 54eb59d 已无冲突合入最新 main 6fd6df8，合并提交 3399234 已普通推送 main，AI 分支也已正常推送。保留 Java/Web/学习成果，按用户“直接执行”要求未重复业务测试或等待 CI；真实服务验收限制不变，见 [发布交接](sessions/2026-11-19-01-ai-ingestion-merge.md)。

- WEB-V2-PUBLISH-MERGE-001：VERIFIED（2026-11-19，Git 发布）。已先拉取 main 9986ca7，无冲突合入 Web a0a830f（含 ed88272），合并提交 61524f3 已普通推送 main，保留最新 Java/AI/学习记录。用户要求直接执行，本轮不重复测试或等待 CI；真实联调与部署未执行，见 [发布交接](sessions/2026-11-19-01-web-v2-main-merge.md)。

- AUDIT-FIX-001：VERIFIED（2026-11-18，P0/P1/P2 审查修复）。V7 迁移修正种子数据 owner_id；PageResponse 统一到 platform/web（删除 2 份域内副本）；ARCHITECTURE/ROADMAP/DATABASE/API 状态标记同步至 M1.5 + 契约冻结 + Web v2 + AI 解析；API.md 新增 v1/v2 共存策略。完整门禁 exit 0。DB 集成测试因 Docker Desktop 未运行阻塞，待解除。见 [session](sessions/2026-11-18-08-audit-fixes.md)。
- WEB-V2-MERGE-001：VERIFIED（2026-11-18，Git 合并与普通推送）。合并提交 a6a5fad 已正常推送 origin/main，无冲突、无强推；用户追加“合并吧”授权；已 fetch 最新 main ff9f671，并在独立集成目录无冲突合并 Web 20b8e2e，保留 Java M1.5/V6、AI 解析与学习路线。依赖已安装；门禁环境入口失败、尚未开始测试。用户明确已验证并要求直接合并，停止追加验证，直接提交并普通推送。以后明确要求推送、合并时直接执行，不重复确认；冲突、分歧或未提交工作则保留现场说明。范围与恢复起点见 [集成 session](sessions/2026-11-18-07-web-v2-main-merge.md)。真实 v2 provider 联调仍 BLOCKED，不能以合并或旧 CI 解除。

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

- M1.6 v2 用例目录：IMPLEMENTED（未提交）。V8 迁移（suites、project_test_cases、project_case_tags、case_selections、selection_items）与 modules/catalog 交付 listSuites、listTestCases、getTestCase、createTestCase、updateTestCase、createCaseSelection 六个契约操作；CaseFilter 同时服务 HTTP 与后续工具查询。详见 [M1.6 session](sessions/2026-11-18-10-m1-6-case-catalog.md)、[ADR-0009](../docs/ADR/0009-v2-case-catalog.md)。证据：mvn verify BUILD SUCCESS（64 项，59 项因无 MYSQL_PASSWORD/REDIS_PASSWORD 跳过）、Spotless/contracts/memory/layout/Prettier 通过；**真实数据库未复验**，V8 与 CaseCatalogApiTest 仍需 MySQL 环境执行。
- M1.5 v2 项目隔离与契约基础已随主线 1cbe62e 接受；本次 AI 集成保留项目/成员、幂等框架与 V6，Java 45 项测试中 40 项 DB/Redis 跳过，不能当真实数据库/权限联调通过。源实现见 [M1.5 session](sessions/2026-11-18-01-m1-5-contract-compliance.md)。
- M1.1/M1.2：IMPLEMENTED；历史本机验证记录为 VERIFIED，见 [M1.1](sessions/2026-11-17-06-m1-1-data-baseline.md)、[M1.2](sessions/2026-11-17-07-m1-2-auth.md)。JWT、登录、当前用户、结构化错误、身份迁移已进入 Git。模块上下文见 [server](modules/server.md)。
- M1.3 知识库与测试用例 CRUD + 数据级权限：IMPLEMENTED（编译通过、Spotless 通过、Maven verify BUILD SUCCESS；DB 集成测试因无 MYSQL_PASSWORD 跳过）。V5 迁移三表 + 种子数据、两个业务域完整实现、集成测试覆盖 CRUD/权限/分页/过滤/排序白名单/错误码、文档同步 API.md/DATABASE.md/ADR-0005。详见 [M1.3 session](sessions/2026-11-17-11-m1-3-kb-tc-crud.md)。
- 证据边界：历史 session 记录真实 MySQL Maven verify、完整门禁与容器登录探测；本轮未重跑业务测试或容器验收，不据此确认当前运行健康与最新远端 CI。M1.3 开发分支原验证限于 JDK 21 编译 + Spotless + 非 DB 单元测试；本次 main 集成补验完整 scripts/check.ps1 退出 0（Java 33 项中 28 项 DB 测试跳过）。没有真实 DB 回归或新容器部署证据。
- M1.4 缓存限流：IMPLEMENTED，已随 main 41ef74c 同步；原实现证据见 [session](sessions/2026-11-17-12-m1-4-cache-ratelimit.md)。本轮完整门禁 Java 37 项中 32 项 DB/Redis 测试跳过，不当作真实缓存/限流回归。此前 PLANNED 快照已过期；后续新业务按 DESIGN-CONTRACT-001 的冻结设计推进，不能把设计当运行交付。
- 开发入口：同级 TestOpsAI-server / codex/server-m1。源码、规范与记忆就绪；本机环境和依赖按需单独准备。

<!-- lane:web -->

- WEB-CSP-FIX：IMPLEMENTED（未提交）。v2 项目列表已按契约改为分页响应，概览页在真实浏览器可用（2026-11-18 复核）；容器内白屏已定位并修复（CSP unsafe-eval + smoke 解析）；登录页与工作台外壳在真实浏览器可用。项目上下文读取仍因 v2 返回裸数组而失败，页面显示「无法读取项目」，待后端分页契约对齐后复测。

## Web

- WEB-V2-003：VERIFIED（2026-11-19，Web 与隔离浏览器）。已完成知识库/文档/授权/引用、AI 会话与认证 SSE、人工草稿修订/确认保存和业务上下文入口；94 测试、类型/lint/构建与契约检查通过，严格 CSP、权限/重连/停止/IME/项目切换和 375px/768px 已验。已随 Web a0a830f 提交推送，并经 61524f3 合入与普通推送 main；真实 API/模型未联调；上一批 ed88272 已推送且 CI 44326861254 成功，见 [本批交接](sessions/2026-11-18-12-web-knowledge-assistant.md)。

- WEB-V2-002：VERIFIED（2026-11-18，仅 Web 与隔离浏览器）。按冻结契约完成导入、任务历史/轮询、运行记录/结果/日志证据、工作台真实聚合、项目创建/设置和成员操作；构建期校验器替代浏览器 Ajv 编译，在严格 CSP 下可用。Web 75 测试、类型/lint/构建与契约检查通过，生产 dist 的桌面/375px/768px、权限/异常/项目切换已验证。只改 apps/web 与 Web 记忆；已随 ed88272 正常推送 Web，真实 API 未联调；知识库/AI 状态见 WEB-V2-003，见 [本批交接](sessions/2026-11-18-11-web-v2-workflows.md)。

- WEB-V2-003 门禁边界（2026-11-19）：完整 scripts/check.ps1 仍在既有 AI .pytest_cache 格式扫描处 EPERM，中断前 45 条记忆、目录/记忆回归与 14 脚本语法通过；不处理其他端，Web 独立 94 测试与构建等检查通过。

- WEB-V2-002 门禁边界：完整 scripts/check.ps1 在全仓 Prettier 扫描既有 AI .pytest_cache 时 EPERM 中断，未处理非 Web 目录；Web 独立检查与契约通过，不将其外推为全栈门禁成功。

- WEB-NEXT-PLAN-001：PLANNED（2026-11-18）。用户要求扩大下一阶段范围；拟按基础与真实用例联调 → Robot/XML 导入 → 运行证据 → 工作台/成员 → 知识文档 → AI 会话/草稿推进。本轮仅规划，尚未启动开发；跨端依赖、主线未提交 CSP 调试与验收边界见 [计划交接](sessions/2026-11-18-10-web-next-stage-plan.md)。

- WEB-V2-MERGE-001：VERIFIED（2026-11-18，Git 合并与发布，未追加业务验证）。已随 a6a5fad 合入并普通推送 main；Web tip 20b8e2e 的最终 [CI 44257614573](https://github.com/MiManchhi/TestOpsAI/actions/runs/44257614573) 五项成功；用户已追加授权合入最新 main，先前“暂不合并”属于历史事实。本次合并与发布证据见 [集成 session](sessions/2026-11-18-07-web-v2-main-merge.md)；真实 API 联调阻塞保持。

- WEB-V2-001 发布：VERIFIED（2026-11-18 提交/正常推送与远端 CI）。功能 c74c03b 与最小镜像修复 35b5a59 已普通推送 Web；[CI 44256756644](https://github.com/MiManchhi/TestOpsAI/actions/runs/44256756644) 五项全成功，含 Web 59 测试、镜像构建、六服务交付/恢复与隔离清理。初次 JSON 缺失失败及本机 Docker 未运行限制保留在 [session](sessions/2026-11-18-03-web-v2-catalog.md)；修复仅为 .dockerignore 两文件白名单与 Web Dockerfile COPY。暂不合并，未推送 main，真实 v2 联调仍 BLOCKED。收尾仅补充三份记忆并普通推送，最终 tip/CI 以 refs/Actions 为准。

- WEB-V2-001：IMPLEMENTED（2026-11-18）；Web 类型/lint/最终 59 测试/构建、契约与隔离浏览器范围 VERIFIED。正式布局、项目上下文/资料/账户、套件树、精确筛选排序分页、跨页选择快照及只读详情/来源已实现。原完整入口受 pytest 缓存 EPERM 阻断，全部源码格式与其余原步骤的等效门禁退出 0（当时 Web 57、Java 37 中 32 DB/Redis 跳过、Python 1）；收尾仅新增两项契约回归并复跑 Web/契约。1440/768/375 与键盘/异常通过，示例仅测试隔离。收尾核实远端 main 新增至 1cbe62e（未改公共契约/Web），静态发现项目列表 Controller 返回数组而契约要求 ProjectPage，且套件/用例/选择未交付，真实联调 BLOCKED。开发验收基线为 0dfe373，发布事实见上条；未合并新主线或部署，见 [开发 session](sessions/2026-11-18-03-web-v2-catalog.md)。下列“仍 PLANNED”保留历史时点。

- WEB-SYNC-001：VERIFIED（2026-11-18 本地同步、远程发布与发布提交 CI）。codex/web-auth 已包含 main 17ed8ee 的最新代码、设计、契约 1.0.0、规范和记忆；两份同步记录随 1c71344 普通推送远程 Web，SHA 一致且当时工作树干净。发布提交 [CI 44235617730](https://github.com/MiManchhi/TestOpsAI/actions/runs/44235617730) 五个 job 全成功，含容器交付/恢复与隔离清理；Java 37 中 32 项 DB/Redis 测试跳过。本机原完整入口因 pytest 缓存 EPERM 失败，全部源码格式及其余步骤的等效门禁退出 0，见 [同步 session](sessions/2026-11-18-02-web-main-sync.md)。收尾仅补充这两份记忆并普通推送，最终 tip/CI 以 refs/Actions 为准；未改远程 main，WEB-V2-001 仍 PLANNED，下列历史证据保留原范围。

- WEB-001：VERIFIED（2026-11-17 本机最终门禁、浏览器与三端冒烟）；已合入主线 4cc2d1c 并正常推送。Web 实现提交 ad87e97 已推送 origin/codex/web-auth，当前 main 合并候选本机验证通过，待发布，见 [任务 session](sessions/2026-11-17-11-web-auth-workspace.md)。
- 已实现 Java M1.2 登录、sessionStorage 标签页会话、刷新验证、过期/401/退出清理、403/网络故障重试、概览与响应式导航。知识库/测试用例仅待开放说明；真实 Java 浏览器联调和本机三端冒烟已通过，前端 37 测试与最终 scripts/check.ps1 退出 0；Java 9 项数据库测试跳过，详见 session，见 [web](modules/web.md)。
- 开发入口：同级 TestOpsAI-web / codex/web-auth。用户已追加授权提交、推送并先拉取最新主线后合并，Web 分支已发布，main 4cc2d1c 已合并并推送，见 [发布 session](sessions/2026-11-17-12-web-auth-publish.md)；本次主线合并按明确授权执行。公共文件为本任务依赖锁、API 与排版兼容说明。

<!-- lane:ai -->

## AI

- AI-INGESTION-001：VERIFIED（2026-11-19，本机自动与 HTTP 替身范围；起点 424f547，功能提交 54eb59d，已通过 3399234 合入 main）。已交付 Markdown/PDF 定位分块、SQLite 任务/幂等/恢复、三个索引接口、HTTP Embedding、Qdrant 发布/精确删除与可信 READY 受限检索核心；完整 scripts/check.ps1 退出 0，AI 115 passed/2 skipped（保留原 64 项回归），Web 59、Java 48 中 43 项 DB/Redis 跳过。独立 Uvicorn→HTTP 对象/Embedding 替身→解析子进程→本地 Qdrant 冒烟通过；真实 Qdrant HTTP/Embedding 缺显式 VERIFY 配置仍待验收，三端 smoke 因 Java 无数据库密码退出 1。Java READY/引用注册接入、正式回答与部署仍待后续；本批已提交、合入并普通推送 main，见 AI-INGESTION-MERGE-001。见 [本任务](sessions/2026-11-18-09-ai-ingestion.md) 与 [运行说明](../docs/development/AI_INGESTION.md)。
- AI-PARSER-PUBLISH-001：VERIFIED（2026-11-18，Git 发布与远端 SHA 核对）。功能提交 e1a2d23 已正常推送 origin/codex/ai-ingestion，源码/测试/说明和同步记忆一起发布，无强推，后续已通过 a215e34 合入 main；收尾记忆随后独立提交推送，最终 tip 以 Git refs 为准。见 [发布 session](sessions/2026-11-18-04-ai-parser-publish.md)。新远端 CI 未核实，不把发布当作部署或真实 Java 联调。
- AI-PARSER-001：VERIFIED（2026-11-18，功能提交 e1a2d23，限本机 AI 与替身验证）。已实现 Robot/XML 静态解析、严格契约 DTO、RS256 服务/委托校验、授权对象客户端、两条内部路由与可取消/超时回收进程。最终 scripts/check.ps1 退出 0（Python 64、Web 37，Java 37 中 32 项 DB/Redis 跳过），真实 Uvicorn→回环 HTTP 替身→解析子进程冒烟通过。真实 Java 对象接口/签名配置联调与部署仍 PLANNED；本机三端 smoke 因 Java 数据库连接不可用退出 1，不算通过。见 [本任务](sessions/2026-11-18-03-ai-parser.md) 与 [运行说明](../docs/development/AI_PARSER.md)。
- AI-SYNC-001：2026-11-18 已获授权 fetch origin 并将 codex/ai-ingestion 从 101596d 快进至 origin/main 17ed8ee（12 个提交），最新产品设计、契约 1.0.0 与并行指南已进入本工作区；无冲突、未推送。同步后恢复与收尾验证见 [同步 session](sessions/2026-11-18-02-ai-sync-main.md)。同步任务当时未启动业务开发；当前解析状态见 AI-PARSER-001，本区块的“尚未同步”旧快照已更新，其他工作区状态不外推。
- FastAPI 健康、Robot/XML 解析、知识文档处理与受限检索核心已实现；聊天模型、正式引用注册、RAG 回答与 Agent/SSE 仍 PLANNED，见 [ai-service](modules/ai-service.md)。
- 当前授权 AI-INGESTION-001 已完成本机交付；下一步仅在显式服务配置就绪后运行说明中的真实 Qdrant/Embedding 验收，并协调 Java 对象/签名、可信 READY 事实和引用注册；不自动扩展问答或跨端实现。
- 开发入口：同级 TestOpsAI-ai / codex/ai-ingestion。依赖独立安装，无真实密钥复制；此前解析 e1a2d23 已发布并合入 main，本批索引 54eb59d 已推送 AI 分支，并经 3399234 合入/推送 main；其他开发分支未自动同步。

## 历史证据与限制

- M0 容器和 Ubuntu CI 历史证据见 [容器 session](sessions/2026-11-17-05-container-delivery.md) 和 [EVIDENCE](EVIDENCE.md)，只适用各自版本，不等于 M1.2 的远端验收。
- 历史开发依赖审计提示、Python TestClient 弃用和 Java agent 提示未在本轮复核，不声明已修复。
- 所有后续任务仍受明确授权范围约束；既有“停止 M0”仅描述历史任务，当前事实以已授权 M1 实现和本轮协作初始化为准。
