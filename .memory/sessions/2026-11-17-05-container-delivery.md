# M0 完整容器交付与自动验收

更新：2026-11-17。状态：IN_PROGRESS。

## 目标与起点

- 用户要求实施已确认的容器交付计划：本机六服务、统一冒烟、失败检测、保留卷重启及 Ubuntu CI 容器 job；不进入业务开发，不删除本机卷。
- 已读取根 AGENTS、INDEX、CURRENT、开发/目录/代码规范和工程模块；[导航](../INDEX.md)。
- 起点 f5229bc，main 跟踪 origin/main，工作区干净。Docker 27.4.0 Linux x86_64，Compose 2.31.0；仅 MySQL、Redis、Qdrant running/healthy，8088 无监听。
- 验收：三端镜像构建、六服务 healthy、完整/InfraOnly 冒烟、重复启动、停启保留卷、服务停止负例、scripts/check.ps1、远端 CI。命令/失败/范围按实际补充。

## 实施与决策

- 延用现有目录、六服务与本机凭据；镜像固定当前版本系列的实际 digest；不将本机环境或下载材料复制到镜像。
- 构建与启动分开，健康等待 240 秒。CI 独立项目与随机临时凭据，仅清理自己的临时资源。
- 先完成本机可评审产物；提交/推送遵守根规则，本轮不沿用上次修复任务的 Git 授权。
- 三个 Dockerfile 固定六个官方 manifest digest；Web 固定 npm 并读取 .npmrc；Java 镜像内 verify，Java/AI 非 root；AI 多阶段只复制非 editable 运行环境。三份 dockerignore 按构建所需开放输入。
- 统一 smoke 保留 InfraOnly，检查六服务/回环端口/内部服务无映射、HTML/JS/CSS、Nginx/Java proxy/401、AI live/ready、MySQL/Redis/Qdrant；新增容器集成测试覆盖重复启动、保留卷恢复与停止 AI 负例。
- CI containers job 等待四项质量检查，20 分钟超时，随机遮罩凭据、禁用默认 .env、独立项目、限定失败诊断；always 只清理自己前缀的 CI 项目及临时卷。
- 新增文件仅本 session、[容器运行文档](../../docs/development/CONTAINERS.md)、[容器测试](../../tests/engineering/container.tests.ps1)。修改三端 Dockerfile、三份 dockerignore、scripts/dev.ps1、scripts/smoke.ps1、Nginx、现有 CI；更新 README、架构/API/路线图/目录指南、tests/README 及相关记忆。没有新增目录或根文件，没有业务或依赖清单变更。

## 真实验证

执行目录为仓库根目录，本机 PowerShell 5 / Docker Desktop Linux x86_64。

| 命令                                                                          | 结果      | 范围                                                                                                                                              |
| ----------------------------------------------------------------------------- | --------- | ------------------------------------------------------------------------------------------------------------------------------------------------- |
| `git status --short --branch`、`git log -1 --oneline`                         | 0         | 起点 f5229bc，工作区干净                                                                                                                          |
| `docker buildx imagetools inspect` 六个应用基础镜像                           | 0         | 官方实际 digest 固定到 Dockerfile；中间件沿用原版本 tag                                                                                           |
| `docker compose --progress plain build server web ai-service`                 | 0         | 三端构建；Java 镜像内 2 测试；Web npm 11.6.4；AI 锁定运行依赖                                                                                     |
| `docker compose --progress plain build web`                                   | 0         | 独立 Web 构建，与主构建共享 BuildKit 缓存                                                                                                         |
| `docker compose up --no-build -d --wait --wait-timeout 240`                   | 0         | 六服务 healthy，基础卷/凭据保留                                                                                                                   |
| `scripts/smoke.ps1`、`scripts/smoke.ps1 -InfraOnly`                           | 0         | 完整与基础设施检查通过                                                                                                                            |
| `tests/engineering/container.tests.ps1`                                       | 0         | 非 root/AI 精简运行环境、重复启动、停启后三个卷身份保持、停止 AI 拒绝、finally 恢复                                                               |
| AI 停止时 `powershell -NoProfile -File scripts/smoke.ps1`                     | 1（预期） | 真实进程非零退出；finally 恢复 AI 并再次完整 smoke 退出 0                                                                                         |
| `scripts/check.ps1`                                                           | 0         | 目录 9/导航 3 回归、12 份 PowerShell 解析、格式、Vue 3 测试及构建、Java 2 测试/Spotless/打包、Python Ruff/类型/1 测试/构建，脚本 linux/win32 mypy |
| `docker compose exec -T ai-service python --version`、server `java --version` | 0         | 运行时 Python 3.12.16、Java 21.0.13；最终六服务 running/healthy                                                                                   |

本机镜像 ID：server 2ea3b8c7a0ec（315MB）、AI f6bbe945d796（135MB）、Web 4e25edbe2535（48.4MB）。本地构建输出标签 latest 不代表浮动基础输入；Dockerfile 已固定基础 digest。

首次 Qdrant Bash 参数在 Windows 引号处理下只输出 GET，不作为就绪证据；改成 Bash 内单引号并严格匹配 HTTP 状态行后复测通过。一次与停启测试并发的版本诊断遇到服务暂时停止，测试恢复后顺序重试成功。既有 Mockito/Starlette/ESLint 弃用及 npm 开发依赖审计提示未跨版本处理。

## 授权前交接（历史时点）

- 持续目标复核：当前 HEAD 仍 f5229bc，25 份本机候选修改未提交。补充使用官方 actionlint 1.7.12 检查 `.github/workflows/ci.yml`，退出 0；发行包 SHA-256 与官方 release 元数据一致，工具和压缩包仅留忽略的 .tools/toolchains/actionlint 与 .tools/cache/actionlint。此证据覆盖工作流语义，不代替远端构建/容器运行；未收到新的提交/推送授权。
- 最终复核：25 份候选文件的本机凭据/私钥/产物扫描通过，根源码文件仍 14 个；全仓 Prettier、git diff --check 均退出 0。记忆 Check 通过（19 份），目录 9 项/导航 3 项回归和 12 份 PowerShell 语法检查通过。已查看实际代码差异和新文件；没有创建提交或推送。
- 本机容器交付状态 DEPLOYED，测试与运行指南 VERIFIED；CI 配置 IMPLEMENTED，新 job 未推送/执行。未删除本机卷，最终六服务健康且入口为 http://127.0.0.1:8088。
- BLOCKED：本次提交/推送授权仍缺失，该条件已在原实施轮和连续两次目标续行中确认。最终本地复核已完成；最新远端 run 44011572887 仍对应 f5229bc 的旧工作流，新容器 job 未执行。目标阻塞，不能认定验收项 4 完成；解除条件为用户授权本次提交/推送，或用户自行发布这些变更后提供可核对的 CI 运行。恢复时先核对工作区与远端，再跟进新容器 job；不重做已通过的构建或停启测试，除非代码/环境改变。
- 保持 M0，不进入身份、数据库业务、RAG 或 Agent；全栈容器的进程健康不代表业务调用已实现。长耗时构建输出不复制到长期记忆。

## 授权恢复与远端验收

- 2026-11-17 用户明确答复授权本次提交并推送、继续验证 CI；此前 Git 发布授权阻塞解除，不再重复请求授权。
- 发布前重新 fetch origin/main：本地/远端均为 f5229bc，分歧 0/0；25 份本机候选变更保留，六服务仍 healthy。当前尚未推送，新 CI 待执行。
- 先复核凭据/产物排除、格式、导航与工作流语义，再按 Conventional Commits 发布并核对远端 SHA。没有源码变动，不重复已经通过的镜像构建或停启测试；以新 CI 的真实运行记录作为远端验收依据。
