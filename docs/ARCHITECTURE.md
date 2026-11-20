# 架构与边界

最终版采用 Vue → Java → Python。Java 持有 MySQL 业务事实与授权，Python 处理 Robot/XML、知识索引与回答；Python 用委托调用 Java 读取授权对象和核实证据。Qdrant 保存可重建向量，Redis 提供缓存与限流。用户操作经 Java 入口，流式回复由 Java 编号、保存并重新鉴权。目标与运行状态分别见[接口](contracts/README.md)、[联调证据](../.memory/sessions/2026-11-19-03-three-tier-integration.md)。

## 部署

Compose 单机运行，Nginx 发布 8088。Java/Python 无宿主机端口映射；MySQL、Redis、Qdrant 仅回环访问，并使用本地随机凭据及数据卷。应用基础镜像固定 tag 与 digest，中间件沿用既有版本 tag；本次完整构建与交付验收状态以工程记忆为准。生产环境应移除中间件端口映射，并采用 HTTPS、密钥注入及备份。操作与验收见 [容器指南](development/CONTAINERS.md)。

## 后续开发必须保持

- 用户身份、资源范围及写操作权限由 Java 计算；Python 和模型提供的 user_id 不可信。
- 外部文件不成为系统指令。上传需格式/大小白名单、随机对象键、受限解析和来源定位。
- 正式登记、审核、成员及依据变更通过独立业务页提交，绑定幂等键和版本；聊天中的 Robot 草稿由用户复制。
- HTTP 调用有超时与有界重试；重试写操作必须先支持幂等。SSE 采用持久业务事件。客户端断线可凭游标重连，显式停止才取消上游工作。
- MySQL 元数据、对象文件和 Qdrant 索引按状态机协调删除，失败可补偿。
- 持久化时间 UTC；可追踪日志在业务入口引入 requestId，禁止记录敏感原文。M0 尚未实现跨服务追踪。
