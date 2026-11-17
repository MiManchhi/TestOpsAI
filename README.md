# TestOps AI

面向测试研发的知识与智能协作平台。采用 Java 业务后端、Vue 前端和 Python AI 服务。

当前已有 **M1.2 后端身份认证**：数据库迁移、JWT 登录与当前用户接口已实现；Web 目前为健康检查页面，登录页面、知识库、RAG、Agent 仍待开发。验证版本与范围见 [当前工程记忆](.memory/CURRENT.md)。完整基线见 [立项方案](docs/PROJECT_BLUEPRINT.md)，阶段见 [路线图](docs/ROADMAP.md)。

## 本地一键启动（推荐）

安装并启动 Docker Desktop，选择 Linux 容器引擎。在仓库根目录的 PowerShell 执行：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\local.ps1
```

首次自动生成随机凭据到本机 `.env`，构建并启动 MySQL、Redis、Qdrant、Java、AI、Web 六个服务，等待就绪并运行冒烟检查。已有 `.env` 保留；此方式无需在宿主机安装 Java、Node、Python 或运行 setup。首次构建需要联网下载镜像与依赖，可能耗时数分钟。

成功后打开 [本地系统](http://127.0.0.1:8088)。目前看到的是欢迎与健康检查页。

```powershell
# 查看状态 / 持续查看日志（Ctrl+C 退出日志，服务继续运行）
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\local.ps1 status
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\local.ps1 logs
# 停止全部服务，保留容器和数据卷
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\local.ps1 stop
# 已构建过且代码未修改时，快速恢复
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\local.ps1 start -SkipBuild
```

代码修改后执行默认启动命令重新构建。需空闲端口：8088、3306、6379、6333；脚本不会停止占用端口的其他程序。若 `.env` 缺项或与现有数据库凭据不符，恢复原配置后重试，不删卷重置。策略参数仅作用于本次 PowerShell 进程。详细排查见 [容器指南](docs/development/CONTAINERS.md)。

## 目录与职责

```text
apps/web/           Vue 3 + TypeScript，浏览器只访问 Java 入口
apps/server/        Java 21 + Spring Boot，业务、身份、授权、审计
apps/ai-service/    Python 3.12 + FastAPI，后续承载 RAG 和 Agent
infra/nginx/        统一入口及 SSE 代理配置
scripts/           PowerShell 开发、检查与冒烟入口
docs/              架构、契约、数据草图、ADR、里程碑
samples/           自造或公开许可数据
tests/             跨服务测试规划与说明
.memory/           开发背景、任务交接、模块上下文与验证证据
```

初学者先读 [目录与文件归档指南](docs/development/DIRECTORY_LAYOUT.md)：逐项解释目录、根文件的用途、放置原因、可否移动及当前不足。新增文件按归属表放置；根目录白名单通过 `./scripts/check-layout.ps1` 检查。

## 开发环境

需要标准 CPython 3.12、JDK 21、Node 22.23.5（见 .nvmrc）、npm 11.6.4、uv，以及 Docker Desktop Linux 引擎。Maven 3.9.11 由 Wrapper 固定。不依赖全局 Python 包。npm、Python 的完整依赖分别由 `package-lock.json`、`apps/ai-service/uv.lock` 固定。

Windows PowerShell 在仓库根目录运行：

```powershell
. ./scripts/env.ps1
./scripts/doctor.ps1
./scripts/setup.ps1
./scripts/check.ps1
```

`env.ps1` 只修改当前终端；工具放 `.tools/toolchains/{java,node,npm,uv,python}`，缓存放 `.tools/cache/{npm,uv,ruff,mypy}`。新电脑须先安装上述兼容工具，或按该布局放置本机工具；doctor 检查版本并给出错误，不自动下载 SDK。`setup.ps1` 首次创建随机本地凭据，已有 `.env` 不覆盖。不要直接复制空密码的 `.env.example` 启动 Compose。

日常开发分别在三个终端执行：

```powershell
./scripts/dev.ps1 server
./scripts/dev.ps1 ai
./scripts/dev.ps1 web
```

Web 为 `http://127.0.0.1:5173`；Java 健康端点为 `http://127.0.0.1:8080/actuator/health`；AI 本地健康端点为 `http://127.0.0.1:8000/health/ready`。Java 需要 MySQL 与 JWT 配置；先 `./scripts/dev.ps1 infra`，并在 Java 终端设置与 `.env` 一致的 `MYSQL_*` 和 `JWT_SECRET` 环境变量（dev server 不会自动加载 `.env`）。整套运行优先使用上面的一键容器入口。

`./scripts/dev.ps1 infra` 现会启动并等待三项基础服务 healthy，再验证 MySQL 应用用户查询、Redis 鉴权与 Qdrant 就绪；可单独用 `./scripts/smoke.ps1 -InfraOnly` 复检。服务只绑定本机回环端口，真实密码由忽略的 .env 提供，不写入文档或记忆。

本机链路验收：完成构建后运行 `./scripts/smoke-local.ps1`，检查网页、Java/Python 健康及网页到 Java 的代理；脚本使用动态空闲端口，只停止自己创建的进程；平时开发默认端口被占用时自行改配置，不终止其他程序。

## 容器启动

启动 Docker Desktop，确认 `docker info` 正常。原有命令仍可用，与一键入口相同，无需先运行 setup：

```powershell
./scripts/dev.ps1 all
```

此命令先构建三个应用镜像，再执行 `docker compose up --no-build -d --wait --wait-timeout 240` 和完整容器冒烟。入口为 `http://127.0.0.1:8088`。AI 和 Java 不发布宿主机端口；中间件仅绑定回环地址。检查覆盖六服务健康、网页构建资源、Java 代理/401、AI 健康、基础设施鉴权查询及端口边界；不表示尚未实现的业务调用链就绪。

构建、重建、保留卷停启、失败排查与 CI 隔离详见 [容器运行与验收](docs/development/CONTAINERS.md)。集成验收命令为 `./tests/engineering/container.tests.ps1`，会短暂停止并恢复本项目服务，不删除本机卷。

```powershell
docker compose logs -f --tail 100
docker compose down
```

`down` 保留数据卷。不要在保留卷时随意更改数据库密码；变更 `.env` 不会自动修改已初始化 MySQL 用户。此配置用于本机开发，公网发布前需 HTTPS、服务认证和部署加固。

## 质量与协作

正式开发遵守 [代码与格式规范](docs/development/CODE_STYLE.md)。每个新 AI 会话先读取 AGENTS、记忆导航和当前状态，再读取开发、目录、代码规范；`scripts/memory.ps1 -Mode Restore` 可一次输出全部入口，`scripts/check.ps1` 包含规范导航与记忆检查。

Ubuntu CI 的 web/server/ai/compose 四项质量检查及 containers 容器验收均已通过。containers 等待四项质量检查后构建三端镜像，运行统一容器集成验收并清理自己的临时资源；已验证运行见 [CI 44026578462](https://github.com/MiManchhi/TestOpsAI/actions/runs/44026578462) 和 [证据台账](.memory/EVIDENCE.md)。

遵循 [开发与提交规范](docs/development/CONTRIBUTING.md)、[安全规范](docs/development/SECURITY.md) 与 [AGENTS](AGENTS.md)。新会话先读 [记忆导航](.memory/INDEX.md)。不提交密钥、下载工具或生成产物；项目沿用远程仓库已有的 [MIT 许可证](LICENSE)。
