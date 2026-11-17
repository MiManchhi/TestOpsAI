# 问题与经验

## 2026-11-17 容器交付

- 状态：VERIFIED（本机与 Ubuntu CI 容器验收范围）。Windows PowerShell 5 向 Bash 传递内嵌双引号会破坏 HTTP 探测参数；Qdrant 探测改用 Bash 内单引号，并严格匹配 HTTP 状态行。先前只见 GET 输出的尝试不作就绪证据，修正后基础与完整冒烟均通过。
- Web 镜像显式固定 npm 11.6.4 并复制 .npmrc，使容器遵守仓库 engines 和安装约束；基础镜像实际 digest 与依赖锁分别负责不同输入。
- AI 用多阶段、非 editable 运行依赖安装，最终镜像仅含运行环境；实际容器断言证明非 root、没有开发工具/源码工作区。健康检查与用户、端口、资源、鉴权和恢复验收分别提供证据。
- 容器停止/恢复属于有状态操作，版本读取应在测试恢复完成后顺序执行，避免诊断恰好读到服务停止。只短暂停启本项目，不清理本机卷。
- [交付会话](sessions/2026-11-17-05-container-delivery.md)、[运行指南](../docs/development/CONTAINERS.md)。

## 2026-11-17 CI 跨平台修复（本地与远端已验证）

- 状态：VERIFIED（Windows 完整门禁、本机 smoke、linux/win32 静态检查、Ubuntu CI）；9fb1437 已推送，run 44011247548 的四个 job 均成功。
- 已用 sys.platform 显式 if 分支隔离 Windows 常量；本地与 CI 均显式验证两个平台目标。运行时条件成立与静态检查能识别该条件是两个不同要求。
- 夹具点文件用 Force 删除；Windows 上显式设置 Hidden，让现有回归也覆盖 Linux 的隐藏文件约束。保留负例检查和 finally 路径范围校验，不提升系统权限。
- [修复会话与命令](sessions/2026-11-17-04-ci-portability-fix.md)。以下未修复记录为此前分析时点。

## 2026-11-17 CI 跨平台缺陷（历史分析时点未修复）

- 状态：PLANNED（修复）；原因分析已完成。Windows 本机检查通过不代表 Ubuntu CI 通过。
- Python：os.name 条件表达式未让 mypy 排除 Windows 专用 subprocess 常量；mypy 1.20.2 的 linux 目标复现 attr-defined，win32 目标通过。拟使用 mypy 可识别的平台分支并分别验证，不关闭类型检查。
- PowerShell：测试夹具点文件在 Unix 下具有 Hidden 语义，Remove-Item 缺少 Force 会报通用权限/隐藏/只读错误。拟仅调整临时夹具删除并保留范围约束；尚未运行 Linux PowerShell 实机复现。
- 文件与证据：[分析会话](sessions/2026-11-17-03-ci-failure-analysis.md)、[Python 脚本](../scripts/smoke-local.py)、[目录回归](../tests/engineering/check-layout.tests.ps1)。

## 本轮已解决（2026-11-16）

- VERIFIED：Wrapper 从根目录误找 .mvn，改为以脚本位置定位；Windows 路径末尾反斜杠会干扰 Java 参数引号，末尾点路径解决。根调用与 clean verify 均通过。
- VERIFIED：Vue 的两条排版规则与 Prettier 重叠，关闭这两条规则，保留语义检查及零警告门禁；类型、3 测试、构建均通过。
- VERIFIED：标准 CPython 已迁入 toolchains/python，生成 uv.lock 并完成依赖安装、类型/测试/构建；不使用 MSYS Python 环境承载应用。
- VERIFIED：工具默认在根生成 Ruff/mypy 缓存，改用 .tools/cache 下对应缓存，而非扩大根白名单。
- VERIFIED：本机 8080 已被其他进程占用，smoke 自动选择空闲端口，Vite 通过 TESTOPS_SERVER_URL 指向本次 Java；不停止用户已有进程。
- VERIFIED：PowerShell 5 的默认管道编码会损坏传入 Python 的中文，写入前设置 UTF-8；受影响的页面文本已恢复，并用 UTF-8 内容与原方案哈希检查复核。
- IMPLEMENTED：递归清理被自动审批拒绝，只给出 blocked by policy；改用可回退归档，60 项旧遗留已离开 .tools。不宣称磁盘释放，不绕过审批。

以下为历史发现，解决后的现状以上述记录、CURRENT 与最新证据为准。

更新：2026-11-16。状态：IMPLEMENTED。

| 编号 | 问题/根因                                                       | 处理与状态                                                                              | 相关文件                                                                   |
| ---- | --------------------------------------------------------------- | --------------------------------------------------------------------------------------- | -------------------------------------------------------------------------- |
| L01  | PowerShell 默认编码导致中文文档乱码                             | VERIFIED：读取显式 UTF-8；原文保留不重写                                                | [方案](../docs/PROJECT_BLUEPRINT.md)                                       |
| L02  | 系统 npm 缓存位于 Program Files，无写权限；launcher 固定旧 Node | IMPLEMENTED：当前终端使用项目缓存与隔离 Node/npm，安装成功；整套脚本待验收              | [env](../scripts/env.ps1)                                                  |
| L03  | npm 10 解析依赖时出现 edgesOut 异常                             | VERIFIED（安装范围）：切 npm 11.6.4、镜像源后 184 包安装成功；不等于构建通过            | [lock](../package-lock.json)                                               |
| L04  | MSYS Python 证书链错误且 uv 不识别其平台                        | IN_PROGRESS / BLOCKED：保持 TLS 校验，改用 Windows 工具与标准 CPython；安装状态下次核实 | [AI 模块](modules/ai-service.md)                                           |
| L05  | Maven Wrapper 从根目录查找 .mvn 导致失败                        | IN_PROGRESS：已定位工作目录假设，尚未修复                                               | [Wrapper](../apps/server/mvnw.cmd)、[脚本](../scripts/check.ps1)           |
| L06  | Vue 推荐格式规则与 Prettier 输出冲突                            | IN_PROGRESS：lint 8 警告且 max-warnings=0；test/build 因此未执行                        | [eslint](../apps/web/eslint.config.js)、[App](../apps/web/src/app/App.vue) |
| L07  | 根目录堆放过程计划，且文档引用了不存在的进度页                  | IMPLEMENTED：计划迁入记忆归档，README 改指 CURRENT/EVIDENCE；本轮检查链接               | [README](../README.md)、[归档](archive/2026-11-16-m0/progress.md)          |

新问题按“日期、症状、根因、尝试、最终修复、验证范围”追加；未解决时保留状态，不编写虚构解决结论。无需保留原始错误日志或敏感输出。

## 2026-11-17 补充

- VERIFIED：新会话规则和代码标准需要根 AGENTS 明确导航，不能只把文档放进 docs；memory Check 现验证规范存在与导航，缺文档/缺导航的反例都被拒绝。此检查不证明未来模型行为。
- VERIFIED：官方镜像慢速传输超过 300 秒拉取令牌有效期，续传保留已完成分片并刷新令牌；最终逐层压缩与解压 SHA-256 校验后导入，未禁用 TLS 或修改全局代理。临时搬运不作为仓库正式工具。
- VERIFIED：Windows 传递带空格的 mysql -e SQL 字符串不稳定，改为固定 SQL 经 stdin 传入，应用用户查询返回 1。Docker 原生命令截断输出可能造成管道退出码，不据此误判程序缺失。

## M1.1 容器环境变量遗漏（2026-11-17）

- 缺陷：引入 Flyway + DataSource 后未同步更新 compose.yaml 的 server 环境变量，导致 CI containers job 失败。
- 根因：本机验证时 .env 已包含 MYSQL_PASSWORD，server 通过 application.yaml 的默认值连上了本机 MySQL；但容器环境中 server 服务的 environment 只写了 SERVER_ADDRESS，MYSQL_* 未传递。本机 `docker compose up` 之所以通过，是因为 compose 会自动将宿主机环境变量注入容器——而 CI 使用 COMPOSE_DISABLE_ENV_FILE=true 且依赖 GITHUB_ENV 注入，行为不同。
- 解决：compose.yaml server 服务显式声明 MYSQL_HOST/MYSQL_PORT/MYSQL_DATABASE/MYSQL_USER/MYSQL_PASSWORD 并添加 depends_on mysql healthy。
- 预防规则：凡新增持久化/外部服务依赖，必须同步检查 compose.yaml 环境变量传递、depends_on 顺序、CI workflow 变量注入三条链路；不能仅凭本机 `docker compose up` 通过就认定容器交付完整。
- 状态：RESOLVED（8d4d27d）。
