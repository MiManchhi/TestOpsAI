# 验证与能力证据

## E20：本机三端联调与用例搜索修复（2026-11-19）

状态：VERIFIED（已实现路径与本机修复）。基线为 main `039c831` 加未提交的 Java 修复。

- 本机 V8 迁移应用成功，重建 Java 镜像后六服务冒烟退出 0。真实浏览器已走通登录、项目上下文、用例列表、套件/关键词/标签筛选及详情。关键词报 500 的问题修复后复测 200。
- 隔离真实 MySQL/Redis 下 `scripts/check.ps1` 退出 0，Web 94 项，Java 65 项、0 跳过，AI 115 项、2 项外部验收跳过。真实 Qdrant HTTP 隔离集合验收另外通过，真实 Embedding 未验。
- Java 容器到 AI 就绪接口通过。Robot 导入、运行记录、工作台聚合、v2 知识库和会话，以及 AI 解析和索引的运行配置缺口见 [联调交接](sessions/2026-11-19-03-three-tier-integration.md)。

## E19：AI 解析与最新 Java 主线集成（2026-11-18）

状态：VERIFIED（本机集成工程门禁与 Git 主线发布；远端新 CI 未核实）。

- 适用 main 1cbe62e + AI 852697e 的无冲突合并，提交 a215e34 已正常推送 origin/main 并核实远端 SHA；保留 Java M1.5 项目/成员/V6 与既有 Web，不修改冻结契约。
- scripts/check.ps1 退出 0：37 份记忆、布局/回归/14 份脚本解析、全仓格式、契约 109 schema/65 operation/827 引用/15 页面/45 示例及非法输入/SSE、Web 37 测试/类型/lint/构建、Java verify/Spotless（45 项中 40 项 DB/Redis 跳过）、Python lock/Ruff/mypy/双平台脚本/64 测试/打包通过。
- AI 分支独立真实 Uvicorn/授权 HTTP 替身/解析子进程证据保持原版本范围；本轮未重新运行三端 smoke、容器部署或真实 Java 对象联调，不把 DB 跳过视为通过，新远端 CI 未核实。
- [集成 session](sessions/2026-11-18-05-ai-parser-main-merge.md)、[实现证据与限制](sessions/2026-11-18-03-ai-parser.md)。

## E18：Web 登录工作台主线集成（2026-11-17）

状态：VERIFIED（本机集成检查与 Git 发布；远端新 CI 未核实）。

- Web 实现 ad87e97 与主线合并 4cc2d1c 均已正常推送，main 本地/远端 SHA 一致；先 pull 最新 main 15e98f8，再进行保留双方历史的合并。CURRENT 与 API 自动合并无文本冲突，保留后端 M1.3 与 Docker 启动器；README、API、路线图、容器指南、规范和记忆已核对。
- 合并候选完整 scripts/check.ps1 退出 0：Web 37 测试/类型/零警告 lint/构建，Java verify/Spotless（33 项、28 项 DB 测试跳过），Python lock/Ruff/mypy/双平台脚本检查/1 测试/构建；目录/格式/28 份记忆及工程反例通过。
- 先前 Web 分支真实 Java 登录、浏览器三尺寸交互与 smoke 证据仍限原基线；本次不重建或部署容器，不将既有健康状态推定为 M1.3 + 新 Web 已部署。远端新 CI 不据本机通过推定成功。
- 发布提交、远端 SHA 和最终恢复起点见 [发布与集成 session](sessions/2026-11-17-12-web-auth-publish.md)。

## E17：协作发布、隔离工作区与 Ubuntu CI（2026-11-17）

状态：VERIFIED（Git 发布、工作区准备与远端工程/容器验收）。

- 协作提交 0a61b30b4e1a23719182abc8c19b25432930c680 正常推送 origin/main，远端 SHA 与本机一致；没有强推。收尾记忆另随 docs 提交同步，最终 refs 以 Git 为准。
- [CI 44048755494](https://github.com/MiManchhi/TestOpsAI/actions/runs/44048755494) 的 server/web/ai/compose/containers 五个 job completed/success；compose 日志包含新版协作结构正反例成功，containers 构建、健康、恢复验收与清理步骤成功。Java 9 项数据库测试跳过，未做客户端新会话验收或生产部署。
- 同级 TestOpsAI-server / codex/server-m1、TestOpsAI-web / codex/web-auth、TestOpsAI-ai / codex/ai-ingestion 均创建成功；实际 Restore -ListOnly 正确自动识别 server/web/ai，布局检查退出 0，工作树干净，未复制 .env、.tools 或依赖。各树随收尾 docs 快进对齐主线。
- 详细命令、授权、范围与恢复起点见 [发布 session](sessions/2026-11-17-09-publish-collaboration-worktrees.md)。

## E16：统一 AI 入口、自动恢复与协作结构检查（2026-11-17）

状态：VERIFIED（Windows 本机工程检查），实际客户端新会话注入仍待实测。

- 适用 fc826ba 上本次未提交协作变更。完整 `scripts/check.ps1` 退出 0：Vue 3 测试、Java 14 项中 9 跳过、Python 1 测试与相关格式/类型/构建；不包含数据库复验、容器验收和新远端 CI。
- 自动 integration、显式 Web 和 ListOnly 恢复正确输出真实 Git 状态及模块/交接材料；记忆结构反例覆盖缺少协作指南、CURRENT 缺失/重复区块、新版交接缺字段/非法角色/缺验证或下一步标记。结构检查不证明模型遵守规则、进度文字真实或文件修改未越界。
- 本机 Harness 0.2.2-rc.3 安装包的 dsh-agent-instructions 文档与源码确认默认 AGENTS 加载能力，Codex 机制按官方指南核实；没有通过实际新会话测试证明两客户端运行效果。
- 首次沙箱门禁因旧缓存读取 EPERM 阻断，获准在沙箱外重跑成功；无全局配置改动。完整命令、限制与下一步见 [本轮 session](sessions/2026-11-17-08-ai-collaboration-bootstrap.md)。

## E15：M0 六服务容器交付与 Ubuntu CI（2026-11-17）

状态：DEPLOYED（本机 Docker Desktop Linux x86_64）；Ubuntu CI 容器验收 VERIFIED。

- `docker compose --progress plain build server web ai-service` 退出 0；Java 镜像内 2 测试通过，Web npm 11.6.4 与锁定依赖构建通过，AI 多阶段非 editable 安装完成。六个应用基础镜像 digest 从官方 manifest 读取并固定在 Dockerfile。
- `docker compose up --no-build -d --wait --wait-timeout 240`、`scripts/smoke.ps1`、`scripts/smoke.ps1 -InfraOnly` 均退出 0。六服务 running/healthy，端口边界、网页 JS/CSS、Java 代理/401、AI 健康和基础设施查询通过。
- `tests/engineering/container.tests.ps1` 退出 0：非 root Java/AI、AI 没有 uv/pytest/mypy/Ruff/源码工作区、重复启动、停启保留三个卷身份及鉴权、停止 AI 的负例拒绝，finally 恢复全部服务。
- 独立 PowerShell 进程在 AI 停止时运行 smoke 退出 1；AI 恢复后的完整 smoke 退出 0。最终六服务 healthy；Java 运行时 21.0.13，Python 3.12.16。应用镜像 ID：server 2ea3b8c7a0ec、ai-service f6bbe945d796、web 4e25edbe2535。
- `scripts/check.ps1` 退出 0（Vue 3 测试、Java 2 测试、Python 1 测试及既有格式/类型/构建；12 份 PowerShell 语法检查）。现有弃用和 npm 开发依赖审计提示保留，未跨版本升级。
- 提交 173fe32ba41891c3ee73df4c0cd4dd7f80e5fc50 正常推送，远程 SHA 核对一致；[run 44026578462](https://github.com/MiManchhi/TestOpsAI/actions/runs/44026578462) completed/success，五个 job 全部通过。容器 job 133848422570 的日志逐项确认健康/端口/构建资源/401、非 root/精简运行环境、重复启动、保留卷停启、停止 AI 负例与最终恢复通过；清理步骤仅删除独立 CI 项目 testops-ci-44026578462-1 的六容器、网络和三个临时卷。未复制原始日志或临时凭据到记忆。
- `scripts/dev.ps1 all` 补验退出 0，验证用户实际使用的构建/启动/等待/冒烟入口。具体变更、限制与恢复起点见 [容器 session](sessions/2026-11-17-05-container-delivery.md)。
- 官方 actionlint 1.7.12 对现有工作流语义检查退出 0；下载包 SHA-256 已校验，临时工具被 Git 忽略。不以静态检查推定新增容器 CI job 已通过。

## E14：CI 跨平台修复与远端验收（2026-11-17）

状态：VERIFIED（本地检查与远端 Ubuntu CI 范围）。

- mypy 1.20.2 的 linux 与 win32 目标均退出 0；Windows 隐藏夹具清理后完成 9 项目录、3 项记忆导航回归及 11 份 PowerShell 解析检查。
- 直接执行 `scripts/check.ps1` 退出 0：格式、Vue 类型/lint/3 测试/构建、Java 2 测试/Spotless/打包、Python 锁定依赖/Ruff/类型/1 测试/sdist 与 wheel，包含新增的双平台脚本类型检查。
- `scripts/smoke-local.ps1` 退出 0：修复后本机三端启动、健康入口、Java 代理及 401 边界通过；未据此声称 Linux 进程运行或完整应用容器部署通过。
- 提交 9fb1437de0b2c87f414fdee726085e56c493767c 正常推送，`git ls-remote origin refs/heads/main` 与本地 HEAD 一致；GitHub [run 44011247548](https://github.com/MiManchhi/TestOpsAI/actions/runs/44011247548) 对应同一 SHA，completed/success，server/web/ai/compose 全部成功。该证据覆盖 Ubuntu 下双目标 mypy、目录回归、三端检查/构建及 Compose 配置解析；不覆盖 Linux 三端运行 smoke 或完整应用容器部署。
- [完整过程](sessions/2026-11-17-04-ci-portability-fix.md)，原失败 run 44009166249 的 server/web 成功、ai/compose 失败已通过 GitHub 元数据核实。

## E13：远端 CI 失败分析（2026-11-17）

状态：VERIFIED（Python 平台检查差异与源码定位；不表示修复或远端验收通过）。

- 工作区起点 33fe9c4；Git 状态干净，main 跟踪 origin/main。用户截图显示 Ubuntu AI/compose job 失败，未获取完整远端运行元数据，不推定其他 job 结果。
- 本机 mypy 1.20.2：`--strict --platform linux scripts/smoke-local.py` 退出 1，重现第 64 行 attr-defined；相同命令改为 `--platform win32` 退出 0。仅切换静态检查目标，不是 Linux 实机执行。
- PowerShell 失败点为第 52 行临时点文件删除，9 项目录断言已通过；官方 .NET Unix 文件属性实现与 PowerShell 删除实现支持 Hidden 根因，未本机运行 Linux PowerShell。
- 此前全量门禁成功仅覆盖 Windows；远端 CI 整体验收未通过。修复 PLANNED，未修改源码或 CI，未提交或推送。完整命令与交接见 [分析会话](sessions/2026-11-17-03-ci-failure-analysis.md)。

## E12：首次 GitHub 工程发布（2026-11-17）

状态：VERIFIED（仓库发布范围，不代表应用部署）。工程提交 977591db002312988ed7cd614002792021e67f5a 正常推送 origin/main，git ls-remote SHA 与本地一致；保留原始 MIT LICENSE 初始历史，不强推。

完整 scripts/check.ps1 退出 0；102 份候选文件约 386 KiB 已筛查真实本机凭据匹配、私钥/Token、产物与异常体积。暂存 git diff --check 通过；原始方案的双空格硬换行按该文件专属属性保留。工具与本机配置均未上传。记忆收尾使用独立 docs 提交同步，最终 tip 以 Git refs 为准。

远程：[MiManchhi/TestOpsAI](https://github.com/MiManchhi/TestOpsAI)。详情：[session](sessions/2026-11-17-02-first-remote-push.md)。远端 CI 未宣称成功，未开始业务开发。

## E11：正式开发规范与会话入口（2026-11-17）

状态：VERIFIED（规范与本机检查范围）；基础设施运行验证亦已完成；不推定完整应用容器部署成功。

- doctor、包含记忆检查的 scripts/check.ps1 均退出 0。结构回归 9 项、规范导航正反例 3 项、Vue 3 测试、Java 2 测试、Python 1 接口测试及三端构建通过。
- scripts/smoke-local.ps1 退出 0；独立 PowerShell Restore 成功输出 AGENTS/INDEX/CURRENT/CONTRIBUTING/DIRECTORY_LAYOUT/CODE_STYLE 六入口。
- 全部 PowerShell 源码语法检查、30 份资料相对链接和 UTF-8 检查通过；根入口未新增、无 Git 提交或推送。
- docker compose up --wait 和 scripts/dev.ps1 infra 均退出 0：三基础服务 healthy、MySQL 应用用户查询返回 1、Redis 鉴权 PONG、Qdrant ready/API 授权与无授权拒绝通过；官方镜像压缩及解压层摘要全部校验。
- CODE_STYLE 明确 Java/TS/Python/脚本的代码与格式、测试和完成标准；保存格式与命令行各有所有者。AI 入口规则可发现/导航可检查，未声称能证明模型未来行为。

本轮详情：[session](sessions/2026-11-17-01-development-standards.md)，阶段交接：[ROADMAP](../docs/ROADMAP.md)。以下保留历史证据。

## E10：适度工程化整理及本机基础验收（2026-11-16）

状态：VERIFIED。完整 scripts/check.ps1 退出 0；Java clean verify、本机三端 smoke、setup、Compose 配置、原方案哈希、文档链接与 UTF-8 检查通过。结构回归 9 项、Vue 测试 3 项、Java 2 项、Python 1 项（覆盖多个健康/封闭接口断言）；三端构建产物均生成。无 commit SHA，适用本轮工作区；容器部署和远端 CI 未执行。

工具遗留 60 项可回退归档到忽略的 .runtime，没有递归删除或释放磁盘声明。npm 审计一条 [minimatch 公告](https://github.com/advisories/GHSA-g3hv-75wg-m249) 关联 7 个开发依赖包；未强制降级，见 CURRENT。详细命令、结果、迁移与限制见 [session](sessions/2026-11-16-04-engineering-layout.md)。以下保留历史证据。

## E09：目录指南与结构约束（2026-11-16）

状态：VERIFIED，仅限文档和结构检查；无 commit SHA，适用本轮未提交工作区。

- `scripts/check-layout.ps1`：当前仓库通过；独立临时目录验收允许项通过、误放根源码 scratch.py 被拒绝、未知根目录被拒绝。临时测试目录已清理。
- Python 文档覆盖扫描：14 个实际根文件全部在指南出现；本地 Markdown 相对链接检查通过。
- PowerShell AST 解析：新增检查脚本无语法错误。
- Prettier 对本轮文档/配置格式检查通过；`scripts/memory.ps1 -Mode Check` 为 13 份活动记录通过。
- 本地 check 与 CI 已接入结构检查；未因此重新运行三端全量门禁或远端 CI。

产物：[指南](../docs/development/DIRECTORY_LAYOUT.md)、[检查脚本](../scripts/check-layout.ps1)、[会话](sessions/2026-11-16-03-directory-guide.md)。此前验证保留如下，不受本轮文档通过结论替代。

更新：2026-11-16。状态：IMPLEMENTED。

当前无 Git commit SHA；以下只对应本会话工作区和明确列出的执行时点。代码改动后需判断证据是否仍适用。

| ID  | 范围/命令                                                                                                                      | 真实结果                                                                                 | 结论                                                                                              |
| --- | ------------------------------------------------------------------------------------------------------------------------------ | ---------------------------------------------------------------------------------------- | ------------------------------------------------------------------------------------------------- |
| E01 | `mvn.cmd -B -ntp -f apps/server/pom.xml spotless:apply verify`                                                                 | 退出 0，BUILD SUCCESS，2 tests，0 failures                                               | VERIFIED：当时 Java 骨架；后续 application.yaml 修改使当前完整结论待复验                          |
| E02 | `npm run check`                                                                                                                | typecheck 通过，lint 8 warnings 并失败；最外层命令退出值未可靠传播                       | 前端整体 IMPLEMENTED；该链未执行 test/build，不能报全部通过                                       |
| E03 | `npm install --registry=https://registry.npmmirror.com --no-audit --no-fund`                                                   | 退出 0，184 packages                                                                     | VERIFIED：依赖安装，不代表构建/安全审计通过                                                       |
| E04 | `docker compose config --quiet`（仅临时验证变量）                                                                              | 校验无错误，后续 pull 开始                                                               | VERIFIED：配置解析；无部署证据                                                                    |
| E05 | `apps/server/mvnw.cmd -B -ntp -f apps/server/pom.xml verify`（根目录）                                                         | 退出 1，查找错误的 .mvn 路径                                                             | Wrapper 入口未通过验收                                                                            |
| E06 | JDK 21.0.13.0.2 官方包 SHA-256 比对，`java -version`                                                                           | 哈希相等，版本为 21                                                                      | VERIFIED：本机工具安装；不复存完整校验输出                                                        |
| E07 | `docker info --format '{{.ServerVersion}}'`                                                                                    | 返回 27.4.0                                                                              | VERIFIED：Docker 引擎可连接；无容器健康证据                                                       |
| E08 | `scripts/memory.ps1 -Mode Check`；新 PowerShell 进程 `-Mode Restore`；文档链接扫描；原方案 SHA-256 比对；Git/ignore/根目录核对 | 全部通过；12 份活动记录有效；恢复找到当前任务与初始化未完成项；main、提交数 0、无 remote | VERIFIED：规范、目录、Git 和记忆机制；详见 [session](sessions/2026-11-16-02-memory-governance.md) |

无实际部署、远端 CI、RAG/Agent 或性能指标，不得编入简历成果。

相关文件：[Java测试](../apps/server/src/test/java/io/testopsai/server/platform/security/BoundaryTest.java)、[前端门禁](../apps/web/package.json)、[Compose](../compose.yaml)。原始构建日志只留本地生成目录，不复制到长期记忆。
