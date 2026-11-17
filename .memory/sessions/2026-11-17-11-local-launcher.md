# 本地整套系统启动入口

更新：2026-11-17。状态：VERIFIED（本机启动、集成工程检查与远端发布）。

<!-- handoff:v1 -->

Task: ENG-011
Role: integration
Baseline: 101596d
Contract: compose.yaml、docs/development/CONTAINERS.md；不变更业务 API
Scope: 用户授权编写本地整套启动脚本与操作说明；随后明确授权提交并推送本次变更，不强推，不同步其他 worktree。

## 目标与起始核对

- main / 101596d，工作区干净，本地主线即当前 HEAD，未 fetch 远端。
- 已读取六入口、协作指南、四模块及 CURRENT 引用 session。Restore 因目录所有者检查失败，使用逐个读取与 git 单次 safe.directory 参数完成等效核对，不修改全局设置。
- 现有 dev all 能构建六服务；setup 生成配置却依赖本机 SDK。新增只依赖 Docker 的入口，复用已有凭据与冒烟。
- 计划：提取配置初始化；实现 start/stop/status/logs；同步文档；运行工程门禁、smoke-local 和可用的容器验证。

## 实施与选择

- 脚本归 scripts，说明归现有 README 与容器指南，不新增目录或根文件。默认重新构建，可显式复用镜像；停止保留卷。已有配置不覆盖。
- 新增 local.ps1 与 initialize-local-env.ps1；setup 共用配置初始化，dev all 转调新入口。配置随机生成 32 字节十六进制值，CreateNew 避免并发覆盖；不显示凭据。README/容器说明同步 M1.2 身份基线与本机配置前置条件。

## 真实验证

<!-- checks -->

- 初次 docker info 无权限读取 Docker 配置/命名管道，尚未取得容器验证证据。
- 随后通过受审批执行环境取得 Docker Linux 引擎访问；`local.ps1 start -SkipBuild` 与默认 `local.ps1` 均退出 0，三端镜像构建、六服务健康、基础设施查询与完整容器冒烟通过。未删除卷或改写既有配置，最终保持运行。
- 从仓库外通过绝对路径执行 `local.ps1 status` 退出 0，定位正确的六服务。
- 隔离 .runtime 夹具对 LF/CRLF 模板分别生成配置，五项密钥均匹配 64 位十六进制；再次初始化前后文件字节一致，退出 0。夹具未写入 Git，未输出凭据。
- `scripts/check.ps1` 退出 0：14 份 PowerShell 解析、目录/记忆回归、全仓格式、Vue 类型/lint/3 测试/构建、Java verify/Spotless（14 项、9 项 DB 测试跳过）、Python lock/Ruff/mypy/双平台 smoke 类型检查/1 测试/构建通过。跳过项不算真实数据库测试通过。
- `scripts/smoke-local.ps1` 首次退出 1：本机进程未加载 MYSQL_PASSWORD，MySQL 拒绝无密码连接。仅在本次进程注入已有 MYSQL/JWT 配置后重跑退出 0，Java/AI 健康、Web 代理与匿名 401 通过；未修改 smoke 源码，未将裸跑声明为通过。
- 未实跑 logs 跟随或 stop 动作，避免中断现有服务；对应 Docker 命令经过脚本解析与人工复核。新远端 CI 未运行。

## 交接

<!-- next -->

- 已实现并完成上述本机验证，BLOCKED：无本次遗留阻塞。未提交、不推送、不同步其他 worktree；六服务运行于本机 Docker，入口 http://127.0.0.1:8088。
- 下次从 README 一键命令运行；代码改变后默认重新构建。保留 .env 与数据卷，本机 Java 开发按文档显式注入配置。收尾复核记忆、格式与 diff，不扩大到业务开发。
- [CURRENT](../CURRENT.md)、[工程模块](../modules/engineering.md)。

## 追加发布授权

- 用户明确要求提交并推送。发布前 fetch 确认远端 main 为 3998cfe，比本地 101596d 多两条 M1.3 提交；规则文件未变化，CURRENT 仅 Java 区块变化，与本次集成区块互不覆盖。
- 先提交本次九份文件保全检查点，再将本次提交衔接到远端最新 main；不强推，不改远端既有提交。集成后重新验证并记录结果。

- 追加授权执行结果：先生成检查点 cd6ad09，rebase origin/main 无冲突，得到实现提交 72c0b9a；正常 push 退出 0，origin/main 从 3998cfe 前进到 72c0b9a。远端既有 M1.3 历史保留，没有强推。此前未提交描述是授权前历史。
- 集成后完整 scripts/check.ps1 退出 0：格式、工程/脚本、Vue、Java、Python 全通过；Java 33 项中 28 项 DB 测试跳过。仅重跑集成门禁，未部署 M1.3 或执行真实 DB 集成测试；旧 M1.2 容器证据不外推。
- README/容器说明随集成基线纠正为 M1.3 后端实现、Web 健康页面；发布收尾文档单独提交推送，最终 tip 以 Git refs 为准。新远端 CI 状态未核实，不记为通过。
