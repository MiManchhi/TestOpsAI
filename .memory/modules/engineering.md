# 工程模块

2026-11-17 本地入口：状态 VERIFIED（101596d 上未提交工作区）。[local.ps1](../../scripts/local.ps1) 提供 Docker-only start/stop/status/logs，默认构建，start -SkipBuild 复用镜像；dev all 转调此入口。setup 与 local 共用 [配置初始化](../../scripts/initialize-local-env.ps1)，只创建缺失的 .env，既有文件不覆盖。默认启动/完整容器冒烟、复用镜像、外目录 status、LF/CRLF 随机凭据及保留既有配置通过。完整工程门禁退出 0，DB 测试 9 项跳过；本机 smoke 需在进程中先注入现有 MYSQL/JWT 配置，本次带配置复验通过。详见 [ENG-011](../sessions/2026-11-17-11-local-launcher.md)。

2026-11-17 容器更新：状态 DEPLOYED（本机六服务），新增 CI 容器 job 为 VERIFIED（173fe32 的 run 44026578462 五个 job 全通过，含隔离清理）。应用基础镜像 digest 固定，构建上下文按需开放；dev all 分开构建/启动并等待 240 秒。统一 smoke 核对状态、端口、构建资源、代理/401、AI 健康与基础查询；tests/engineering/container.tests.ps1 验证非 root/精简运行环境、重复启动、保留卷停启与停止 AI 的失败检测，finally 恢复。本机门禁、用户入口和 Ubuntu 容器集成验收均通过，见 [容器 session](../sessions/2026-11-17-05-container-delivery.md)；[操作说明](../../docs/development/CONTAINERS.md)。不将六服务健康推定为未实现的业务链路完成。

2026-11-17 更新，状态：VERIFIED（本地与远端 CI 跨平台修复范围）。本地/CI 对 smoke 脚本显式检查 linux、win32；使用 sys.platform 分支隔离 Windows 专用启动标志。目录回归显式覆盖 Windows Hidden 夹具，点文件清理使用 Force，保留安全范围校验。完整本地门禁、smoke 与 Ubuntu CI 四个 job 均通过；9fb1437 已推送，远端证据见 [最新修复会话](../sessions/2026-11-17-04-ci-portability-fix.md)。以下旧的“远端 CI 未执行”描述仅对应各自历史时点。

更新：2026-11-16。状态：VERIFIED（本机基础工程范围）。

目录约定见 [指南](../../docs/development/DIRECTORY_LAYOUT.md) 和 [ADR-0002](../../docs/ADR/0002-feature-layout.md)。根源码文件 14 个（含远程原有 LICENSE），Prettier 设置并入 package.json。三个应用保持顶层位置，内部按真实业务职责组织，域内先平铺，不创建无实现的 modules/shared 等目录。

[结构检查](../../scripts/check-layout.ps1) 覆盖根白名单、必需入口、应用配置与源码入口；[9 项回归](../../tests/engineering/check-layout.tests.ps1) 已通过。新增目录须有实际职责，工具自动生成的缓存不应成为放宽白名单的理由。

工具：.tools/toolchains/java、node、npm、uv、python；缓存：.tools/cache/npm、uv、ruff、mypy。[env](../../scripts/env.ps1) 激活当前终端，[doctor](../../scripts/doctor.ps1) 验证前置工具，不自动安装 SDK。npm 固定 11.6.4，子进程使用本机 shim，.nvmrc 是 Node 版本来源。

60 项旧安装目录/压缩包/分片已从 .tools 迁入 .runtime/migrations 的回退归档；递归删除被自动审批拒绝，未尝试绕过，也不声称释放了磁盘。当前 .tools 只留 toolchains 与 cache。

setup 已成功安装 npm/Python 锁定依赖并编译 Java 测试。Wrapper 已按自身位置找 .mvn，Windows 末尾点避免反斜杠吞引号；[maven.ps1](../../scripts/maven.ps1) 是稳定操作入口。

[本机 smoke](../../scripts/smoke-local.ps1) 使用标准 Python 子进程、动态空闲端口验证三端和代理，并只停止自己启动的进程。默认开发端口若被其他程序占用，请改配置；不能终止用户其他程序。本机 8080 已被其他进程占用，smoke 不依赖该端口。

Git main 跟踪 origin/main，远程 git@github.com:MiManchhi/TestOpsAI.git；首次工程提交 977591d 已推送并核对远程 SHA，MIT 原文和初始历史保留。仅本机 .env 含随机凭据且被忽略；Compose 配置解析成功。容器部署、远端 CI、业务功能均不在本轮成功范围。

最终完整 scripts/check.ps1 退出 0，Java clean verify 与本机三端 smoke 通过；容器/远端 CI 未执行。目录约束并非安全审计门禁，npm 开发依赖审计遗留另见 CURRENT。

2026-11-17：新增 [CODE_STYLE](../../docs/development/CODE_STYLE.md)，AGENTS 要求每个新会话六入口读取与任务收尾更新记忆。memory Check 已验证规范存在/根规则导航，并接入 check 与 CI；9 项目录 + 3 项导航回归通过。编辑器保存格式：前端/文档 Prettier、Python Ruff、Java 交由 Spotless 命令行。基础容器 MySQL/Redis/Qdrant 已 running/healthy，SQL/Redis 鉴权/Qdrant 就绪与 API 授权验证通过；dev.ps1 infra 会串联健康与查询检查，smoke.ps1 -InfraOnly 可复检。完整应用容器部署与远端 CI 未验证，下一阶段见 ROADMAP。
