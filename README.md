# TestOps AI

面向测试研发的知识与智能协作平台。采用 Java 业务后端、Vue 前端和 Python AI 服务。

当前为 **M0 工程骨架，正式开发准入已通过**：三端检查、构建、本机健康链路及 MySQL/Redis/Qdrant 基础容器健康验证通过，开发/目录/代码/格式规范与记忆门禁已建立。完整应用容器部署和远端 CI 尚未验收。登录、知识库、RAG、Agent 均尚未实现。完整基线见 [立项方案](docs/PROJECT_BLUEPRINT.md)，下一步见 [路线图](docs/ROADMAP.md) 和 [当前工程记忆](.memory/CURRENT.md)。

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

Web 为 `http://127.0.0.1:5173`；Java 健康端点为 `http://127.0.0.1:8080/actuator/health`；AI 本地健康端点为 `http://127.0.0.1:8000/health/ready`。M0 无存储连接，应用可独立启动。M1 引入数据库后，先 `./scripts/dev.ps1 infra`。

`./scripts/dev.ps1 infra` 现会启动并等待三项基础服务 healthy，再验证 MySQL 应用用户查询、Redis 鉴权与 Qdrant 就绪；可单独用 `./scripts/smoke.ps1 -InfraOnly` 复检。服务只绑定本机回环端口，真实密码由忽略的 .env 提供，不写入文档或记忆。

本机链路验收：完成构建后运行 `./scripts/smoke-local.ps1`，检查网页、Java/Python 健康及网页到 Java 的代理；脚本使用动态空闲端口，只停止自己创建的进程；平时开发默认端口被占用时自行改配置，不终止其他程序。

## 容器启动

启动 Docker Desktop，确认 `docker info` 正常。在完成 setup 后：

```powershell
./scripts/dev.ps1 all
```

等价于 `docker compose up --build -d --wait` 后执行 `scripts/smoke.ps1`。入口为 `http://127.0.0.1:8088`。AI 和 Java 不发布宿主机端口；中间件仅绑定回环地址。Qdrant 的 readiness 由冒烟脚本单独验证。应用健康检查目前仅反映 M0 进程可用，不能证明后续存储或 AI 链路已就绪。

```powershell
docker compose logs -f --tail 100
docker compose down
```

`down` 保留数据卷。不要在保留卷时随意更改数据库密码；变更 `.env` 不会自动修改已初始化 MySQL 用户。此配置用于本机开发，公网发布前需 HTTPS、服务认证和部署加固。

## 质量与协作

正式开发遵守 [代码与格式规范](docs/development/CODE_STYLE.md)。每个新 AI 会话先读取 AGENTS、记忆导航和当前状态，再读取开发、目录、代码规范；`scripts/memory.ps1 -Mode Restore` 可一次输出全部入口，`scripts/check.ps1` 包含规范导航与记忆检查。

CI 配置了格式检查、Vue 类型/静态检查/单测/构建、Java 格式/测试/打包、Python lint/类型/测试，以及 Compose 配置检查；远端 CI 尚未运行。真实结果见 [证据台账](.memory/EVIDENCE.md)。

遵循 [开发与提交规范](docs/development/CONTRIBUTING.md)、[安全规范](docs/development/SECURITY.md) 与 [AGENTS](AGENTS.md)。新会话先读 [记忆导航](.memory/INDEX.md)。不提交密钥、下载工具或生成产物；项目沿用远程仓库已有的 [MIT 许可证](LICENSE)。
