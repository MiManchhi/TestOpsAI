# API 契约

## M0 已实现

| 服务 | 方法与路径                     | 响应                  | 可见性               |
| ---- | ------------------------------ | --------------------- | -------------------- |
| Java | GET /actuator/health           | 200 `{"status":"UP"}` | 公开，不显示内部组件 |
| Java | GET /actuator/health/liveness  | 200，Actuator probe   | 本地及容器内部       |
| Java | GET /actuator/health/readiness | 200，Actuator probe   | 本地及容器内部       |
| AI   | GET /health/live               | 200 `{"status":"UP"}` | 本地及容器内部       |
| AI   | GET /health/ready              | 200 `{"status":"UP"}` | 本地及容器内部       |

Java 的其他匿名路径返回 401（未来在 M1 实现认证）；AI 其他路径为 404。M0 无业务 OpenAPI，无假实现接口。Python 默认关闭交互文档及 schema 端点。

容器 Nginx 公开 `GET /healthz`，返回 200、`text/plain`、`OK`；它仅表示 Web 入口可用。完整六服务状态、代理与资源检查由 [容器冒烟](../scripts/smoke.ps1) 执行，不能只用该路径推定整套系统健康。

## M1–M4 契约草案

外部 REST 前缀 `/api/v1`；Java 为唯一业务入口。结构化错误含 `code/message/requestId`，鉴权失败 401、资源无权 403，分页排序字段白名单，写入按接口规定支持幂等键。具体 schema 与错误码待 M1 实现时冻结并导出 OpenAPI。

内部 AI 与 Tool API 使用服务身份、时效和最小授权上下文；不直接传递用户 JWT。流式事件为 `message.delta`、`tool.start`、`tool.end`、`message.done`、`error`，心跳用 SSE 注释行。字段草案见立项基线第 6 章，尚未实现。
