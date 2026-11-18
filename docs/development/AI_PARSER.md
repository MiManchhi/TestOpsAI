# AI Robot 解析接口与联调配置

日期：2026-11-18。状态：IMPLEMENTED；真实 Java 对象接口、生产密钥分发与部署仍待集成。本说明对应 [冻结契约 1.0.0](../contracts/README.md)，不改变公共 DTO。

## 接口与数据流

- `POST /internal/v1/parsers/robot`，operationId `parseRobotCatalog`，输入 RobotParseRequest，输出 RobotParseResult。
- `POST /internal/v1/parsers/robot-output`，operationId `parseRobotOutput`，输入 ResultParseRequest，输出 ResultParseResult。

Java 发服务 Bearer JWT 与 `X-Delegation-Token`，AI 验签、检查 operationId 和对象白名单后，只向配置的 Java 基址请求 `/internal/v1/objects/{objectId}/content`。回调携带 AI 服务 JWT、原委托令牌和 X-Request-Id；Java 必须再次校验实时成员、资源权限与 authorizationVersion。所有下载核验实际大小与 SHA-256，不接受用户 URL、本机路径、重定向或压缩响应。

解析器只读取 AST/XML，不执行 Robot，不加载 Python 库或变量文件，不连接真实设备，不写 Java 表。Library/Variables 引入和动态表达式产生 issue；控制流和 setup/teardown 不展开成已执行步骤。资源引用只允许快照内的安全相对路径。套件路径使用逻辑来源路径，SourceRef 保存原字节 hash 和原始行号，标签保持大小写。XML 用相对 source 对应目录；绝对/缺失 source 使用 `__unmapped__/` 标识并产生 issue，Java 不得按同名猜关联，必须保留未匹配结果。无明确时区的时间返回 null 并产生 issue。

结果中的 issues 必须展示并由 Java 导入策略处理，不能静默忽略。语法、重名、统计冲突、字段超限或禁止的 XML 使整次解析失败，没有部分成功/部分归档。Java 的 stableKey、快照 revision、目录事务、历史关联和 Run INCOMPLETE 状态由 Java 实现。

## 配置约定

配置读取环境变量，不从仓库配置文件加载私钥；PEM 值包含真实换行，密钥只通过安全的运行环境注入，不放 Git 或日志。

| 环境变量                           | 用途 / 默认                                                  |
| ---------------------------------- | ------------------------------------------------------------ |
| TESTOPS_AI_SERVER_BASE_URL         | Java origin，如 `http://server:8080`；无 path/query/用户信息 |
| TESTOPS_AI_SERVER_PUBLIC_KEY       | Java RSA 公钥 PEM，用于服务和委托令牌验签                    |
| TESTOPS_AI_PRIVATE_KEY             | AI RSA 私钥 PEM，仅签发回调服务 JWT                          |
| TESTOPS_AI_PARSER_CONCURRENCY      | 每个应用进程同时解析数，默认 2，范围 1–8                     |
| TESTOPS_AI_PARSER_TIMEOUT_SECONDS  | 每个解析子进程上限，默认 10 秒，最大 30 秒                   |
| TESTOPS_AI_REQUEST_TIMEOUT_SECONDS | 含下载/排队/解析的请求上限，默认/最大 120 秒                 |
| TESTOPS_AI_OBJECT_TIMEOUT_SECONDS  | 每个对象下载总上限，默认/最大 15 秒；无重试                  |

RS256 为本批用户确认的应用配置方案，固定算法白名单，RSA 至少 2048 位。Java 持自身私钥及 AI 公钥；AI 持自身私钥及 Java 公钥，不持 Java 私钥。公钥交换、密钥轮换、正式 ADR、Compose/密钥挂载由集成方协调；当前不实现远程 JWKS、动态选算法或自动降级到 HS256。缺少或无效配置时解析接口 503，live/ready 仅表示基础进程健康，不表示解析能力或 Java 依赖就绪。

Java→AI：issuer=testops-server、audience=testops-ai、有效期不超过 300 秒。AI→Java：issuer=testops-ai、audience=testops-server-tools、有效期 60 秒。委托：issuer=testops-server、audience=testops-server-tools、有效期不超过 120 秒。两类令牌须严格符合共享 claims、包含有效 UUID jti/operationId，且 exp>iat；每次调用校验有效期与 operationId。jti 是身份标识，不视为一次性消费键；重复只读解析不产生业务写入。

## 限制、错误与执行

沿用契约：Robot 单文件 2 MiB、总计 20 MiB、最多 200 文件；output.xml 50 MiB；响应字段及数组上限取共享 schema。入口 JSON 最多 512 KiB；XML 深度 64、节点 250,000；隔离进程结果文件最多 64 MiB。解析子进程使用 spawn，完成/超时/取消后均回收进程与临时目录。并发限制按应用进程生效，多 worker 部署需据内存预算相乘配置。

400 非法请求；401 无效令牌；403 operationId/委托范围不符；对象 404 保留不可见语义；413 超限；415 格式不支持；422 内容/hash/统计不合法或解析超时；503 缺配置、依赖/整体请求超时。错误均为 code/message/requestId，不返回原异常或令牌。X-Request-Id 接受有效 UUID，否则生成新值并回传。

本机安装：`uv sync --locked --project apps/ai-service`；启动：`uv run --locked --project apps/ai-service uvicorn testops_ai.main:app --host 127.0.0.1 --port 8000`。配置未完成前健康接口仍可探测，解析接口按上述规则关闭。

验收使用测试时临时生成 RSA 密钥与受控 Java HTTP 替身，包含真实解析子进程；运行 `uv run --locked --project apps/ai-service pytest apps/ai-service/tests`、`npm run contracts:check` 与 `scripts/check.ps1`。替身通过不代表 Java 联调或部署完成。真实联调下一步：Java 签发服务/委托 token、实现授权对象内容接口，注入双方签名配置，再验收两条解析链路与撤权行为。
