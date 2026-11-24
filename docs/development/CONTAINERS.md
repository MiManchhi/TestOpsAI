# 本机部署与恢复

在仓库根目录运行 `.\scripts\local.ps1 start`，或在已准备好本机环境文件时运行 `docker compose up --build -d --wait`。访问 [工作台](http://127.0.0.1:8088)。配置项和值的格式见[根目录启动说明](../../README.md)。

| 组件                 | 启动与存储                                                                             |
| -------------------- | -------------------------------------------------------------------------------------- |
| Web                  | 将静态文件和业务 API 发布到回环端口 8088。SSE 经同一个网关。                           |
| Server               | Flyway 升级已有 MySQL 表。原件保存于 workspace-objects。                               |
| AI                   | HTTP 服务接收已签名的解析、索引和问答请求。索引作业状态保存于 workspace-index。        |
| MySQL、Redis、Qdrant | 各自使用具名数据卷，宿主端口仅绑定回环地址。                                           |
| workspace-identity   | 一次性初始化服务。在独立卷生成两对签名密钥，完成后正常退出。重新启动会验证并复用密钥。 |

`scripts/local.ps1 status` 查看服务状态，`scripts/local.ps1 logs` 跟踪日志。修改模型地址后可执行 `scripts/local.ps1 start -SkipBuild`。修改源码后执行普通 start 重新构建。首次构建需要访问容器镜像及依赖源。

`scripts/smoke.ps1` 验证 MySQL 应用账号查询、Redis 认证、Qdrant 就绪、网页资源与 Java/Python 健康。`tests/engineering/container.tests.ps1` 验证停止与恢复以及卷身份。真实模型问答、依据判定、权限与原文另按[联合验收](PARALLEL_DEVELOPMENT.md)执行。

成组备份数据库、原件、Qdrant、AI 作业卷、两份身份卷以及部署凭据。恢复已有部署时使用原有配置和数据卷，签名服务会检查配对关系。
