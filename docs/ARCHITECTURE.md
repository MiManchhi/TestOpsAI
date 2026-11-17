# 架构与边界

状态：M0。Java 是模块化单体业务服务，Python 为独立 AI 运行时；不引入 Spring Cloud。

浏览器 → Nginx（开发时 Vite proxy）→ Java → Python；Python 工具操作回调 Java 并再次授权。Java 持有 MySQL 业务事实，Redis 只缓存，Qdrant 是可重建检索索引。当前仅实现健康端点，未建立 Java/Python 业务调用及存储连接。

## 部署

Compose 单机运行，Nginx 发布 8088。Java/Python 无宿主机端口映射；MySQL、Redis、Qdrant 仅回环访问，并使用本地随机凭据及数据卷。应用基础镜像固定 tag 与 digest，中间件沿用既有版本 tag；本次完整构建与交付验收状态以工程记忆为准。生产环境应移除中间件端口映射，并采用 HTTPS、密钥注入及备份。操作与验收见 [容器指南](development/CONTAINERS.md)。

## 后续开发必须保持

- 用户身份、资源范围及写操作权限由 Java 计算；Python 和模型提供的 user_id 不可信。
- 外部文件不成为系统指令。上传需格式/大小白名单、随机对象键、受限解析和来源定位。
- 有副作用的 Agent 操作先生成草案，确认对象绑定 payload hash，并校验有效期、权限和幂等键。
- HTTP 调用有超时与有界重试；重试写操作必须先支持幂等。SSE 采用业务事件协议，中断需取消上游请求。
- MySQL 元数据、对象文件和 Qdrant 索引按状态机协调删除，失败可补偿。
- 持久化时间 UTC；可追踪日志在业务入口引入 requestId，禁止记录敏感原文。M0 尚未实现跨服务追踪。
