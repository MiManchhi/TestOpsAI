# 本地容器运行与验收

更新：2026-11-17。状态：VERIFIED（启动入口在 M1.2 基线上默认构建启动与复用镜像启动通过，验证范围见 [CURRENT](../../.memory/CURRENT.md)）。适用 Windows Docker Desktop Linux 引擎与 Ubuntu CI；Java 已有 M1.3 数据库迁移、身份认证与业务元数据接口实现，Web 已实现登录与工作台，知识库/测试用例页面仅待开放说明；AI 仍为健康检查骨架。新增 M1.3 未在本任务重新部署；历史六服务恢复与 CI 证据见 [容器 session](../../.memory/sessions/2026-11-17-05-container-delivery.md)。

## 启动与停止

仓库根目录操作。只需启动 Docker Desktop 的 Linux 引擎，无需本机 SDK 或 setup。脚本自动生成随机 `.env`，已有文件不会覆盖。保留原有 MySQL/Redis/Qdrant 数据卷及凭据。

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\local.ps1
```

此命令依次构建三个应用镜像、启动六个服务、等待 healthy（上限 240 秒），再执行完整冒烟。构建时间不计入健康等待上限；首次拉取官方镜像和依赖可能较慢。

`scripts/dev.ps1 all` 转调同一入口。常用操作如下（脚本可从其他目录通过绝对路径调用，总是使用本仓库 Compose）：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\local.ps1 status
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\local.ps1 logs
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\local.ps1 stop
powershell -NoProfile -ExecutionPolicy Bypass -File .\scripts\local.ps1 start -SkipBuild
```

`stop` 保留容器和数据卷；`logs` 按 Ctrl+C 退出跟随，服务继续运行。`-SkipBuild` 仅供已有镜像且代码未修改时使用，缺少镜像会明确失败。失败保留当前容器便于排查，不自动回滚或清理数据。端口需空闲：8088、3306、6379、6333。

可分步定位故障：

```powershell
docker compose config --quiet
docker compose build server web ai-service
docker compose up --no-build -d --wait --wait-timeout 240
./scripts/smoke.ps1
docker compose ps --all
docker compose images
```

浏览器入口：`http://127.0.0.1:8088`。重复执行 `up --no-build` 不清理卷；修改应用、Dockerfile 或 Nginx 后先重新构建，再执行 up。Java/AI 不映射宿主机端口；三项中间件只映射 `127.0.0.1`。

停止、重启并保留数据：

```powershell
docker compose stop
docker compose up --no-build -d --wait --wait-timeout 240
./scripts/smoke.ps1
```

需要移除容器和网络时，`docker compose down` 保留命名卷。本机不要使用 `down --volumes`、卷清理命令或更改已有数据库密码来解决启动问题；修改 `.env` 不会修改已初始化的 MySQL 用户。

## 验证覆盖与限制

| 检查                                    | 实际验证内容                                                                                                                                                         |
| --------------------------------------- | -------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| `scripts/smoke.ps1 -InfraOnly`          | 三项基础服务 running/healthy、固定回环端口、MySQL 应用用户 SELECT 1、Redis 鉴权 PONG、容器内 Qdrant HTTP readiness                                                   |
| `scripts/smoke.ps1`                     | 上述基础检查，加上六服务状态、Java/AI 无宿主机映射、Nginx healthz、HTML 与引用的 JS/CSS MIME 类型、代理 Java health UP 且无组件详情、匿名业务请求 401、AI live/ready |
| `tests/engineering/container.tests.ps1` | 完整冒烟、重复启动、全部服务停止再启动、三个卷的名字/创建时间保持、停止 AI 的失败检测；finally 恢复全部服务                                                          |

```powershell
./scripts/smoke.ps1 -InfraOnly
./tests/engineering/container.tests.ps1
./scripts/check.ps1
```

容器集成测试会短暂停止本项目服务，恢复时不构建镜像、不删除卷。不要在依赖这些服务的其他开发任务进行时运行。完整工程门禁不会自动启停 Docker；容器集成测试是独立的交付门禁。

Java 现已连接 MySQL 并执行 Flyway 迁移；上述冒烟仍只覆盖列出的健康、代理与鉴权边界，不代表登录闭环、存储事务或 AI 业务链通过。Web 登录与工作台已有本机浏览器验证；浏览器脚本未接入 CI，容器冒烟本身不覆盖浏览器登录交互。新 Web 与 M1.3 的容器部署须单独验收。

集成测试也会验证 Java/AI 非 root、AI 最终镜像没有 uv 和 pytest/mypy/Ruff，且没有源码工作区或 `.env`。

## 镜像与构建输入

- 三端 Dockerfile 中的 `tag@sha256` 是应用基础镜像版本来源，digest 来自官方 manifest；保留 Maven 3.9.11/Java 21、Node 22.23.5、Nginx 1.28.0、uv 0.13.8、Python 3.12 系列。不通过改成 latest 排除构建失败。
- 中间件沿用已有固定版本 tag，不更改本机凭据或数据。依赖仍使用 npm lock、uv lock 和 Maven 既定版本。
- Web 显式安装 npm 11.6.4，复制 `.npmrc` 并运行 npm ci；Java 镜像内执行 Maven verify。Java 和 AI 使用非 root 应用用户。
- AI 为多阶段构建，最终镜像只复制非 editable 安装的运行环境，不包含 uv、源码工作区、pytest、mypy 等开发工具。
- 三份 `.dockerignore` 按构建实际需要开放输入。根上下文仅允许 Web 构建清单/配置、源码和 Nginx 配置；Java 仅 pom/src；AI 仅 pyproject/lock/src。环境文件、私有资料、工具和缓存不属于输入。
- 更新 digest 时同时验证构建、冒烟与 CI。镜像 digest 固定基础输入，不代表操作系统包仓库与构建后端的全部传递输入都已离线冻结。
- Compose 构建生成的 `testops-ai-服务名:latest` 是本机应用输出标签；与 Dockerfile 中固定 tag/digest 的基础镜像不同。本阶段不将应用镜像发布到镜像仓库。

## CI 隔离与诊断

容器 job 等待现有 web/server/ai/compose 四个 job 成功后运行，最长 20 分钟。项目名为 `testops-ci-运行号-重试号`；`COMPOSE_DISABLE_ENV_FILE=true` 禁止读取默认 `.env`，临时凭据每次随机生成、遮罩后仅传给本次 job。

CI 构建三个镜像、启动六服务，再运行同一份容器集成测试。失败只输出容器状态与限定长度的 Java/Web/AI 日志，不输出完整 Compose 展开配置。always 清理步骤检查 CI 项目前缀，再删除该项目容器、网络和临时卷；不用于本机清理。

本机诊断优先：

```powershell
docker compose ps --all
docker compose logs --no-color --tail 80 server web ai-service
```

- 端口占用：根据 Docker 错误查明占用程序；不停止用户其他程序，不自动改发布端口绕过检查。
- 镜像或依赖下载失败：核对官方地址、代理与网络；保留 TLS 验证，修复网络后重跑构建，利用已下载层缓存。
- unhealthy：先查看对应服务的有限日志与健康状态，不只延长超时。
- 凭据不匹配：确认使用原有 `.env` 和数据卷；按正式凭据轮换处理，不删卷重置。
- 日志可能含环境信息；分享前脱敏，不提交原始日志或凭据。可分享的长期结论记录到工程记忆。

完整公网部署、HTTPS、业务服务认证、备份恢复和镜像仓库发布不在此阶段验收范围。
