# 验证与能力证据

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
