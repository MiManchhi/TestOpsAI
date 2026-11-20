# 三端联调与业务链路验证

更新：2026-11-19（Asia/Shanghai）。状态：VERIFIED（本机已实现链路与修复）。

<!-- handoff:v1 -->

Task: THREE-TIER-INTEGRATION-001
Role: integration
Baseline: main 039c8319ae190a1fb3177f82da9dbd0bd573a986
Contract: docs/contracts/README.md 1.0.0
Scope: 用户允许进行三端联调。验证本地真实容器、浏览器、Java 接口与 Python 接口。数据库写入型回归使用隔离环境。

## 目标与起始核对

- main 与三个开发分支均为 039c831。保留上轮未提交的 [同步记录](2026-11-19-02-dev-branches-main-sync.md)。
- 本机 Compose 六服务均 healthy，应用三容器为用户刚启动的实例，入口 HTTP 200。
- 先跑容器冒烟及浏览器真实链路，再核对 Java 真实数据库回归、Java 到 Python 的连通与业务路由。按实际证据记录通过、失败和缺失的接口。

## 验证

<!-- checks -->

- `scripts/smoke.ps1` 通过六服务健康、端口、静态资源、Java 代理和 AI 健康检查，以及真实 MySQL/Redis/Qdrant 连通性。Java 容器日志确认 Flyway 校验 8 个迁移，并在用户现有数据库成功应用 V8。
- 浏览器工具初次启动遇到系统 npm 缓存目录权限错误，改用仓库可写缓存。
- 真实 API 登录、用户、项目、成员、套件、用例列表与详情均 200，匿名项目列表 401；工作台聚合、导入历史、运行、v2 知识库与会话均 404。浏览器确认真实套件 2 个、用例 6 条，选定套件为 4 条，关键词搜索报错。
- Java 容器可达 AI 就绪端点；当前 Compose 未配置 AI 服务令牌/对象源与索引目录，解析及索引探针分别返回 503 SERVICE_NOT_CONFIGURED、INDEX_NOT_CONFIGURED。AI 真 Qdrant HTTP 隔离集合写入、受限检索、删除测试通过。
- 隔离 MySQL/Redis 首轮 Maven verify：64 项全部执行，4 失败。两项来自目录 SQL 的反斜杠 ESCAPE 字面量；标签测试与其他用例共用 DEMO 数据，限流测试共用 Redis 客户端键。针对搜索和测试隔离作小范围修复，待真实服务复验。
- 搜索改用明确的 LIKE 转义符，保留按字面匹配通配符的语义。增加该回归，隔离种子标签数据和 MockMvc 限流键。重建并更新本机 Java 容器、重载 Nginx 后，六服务冒烟通过。浏览器搜索「费率」返回 4 条，加精确标签 smoke 返回 2 条；用例详情显示真实来源与步骤。
- 修复后完整 `scripts/check.ps1` 退出 0。Java 阶段在隔离 MySQL 8.4.8 / Redis 7.4.8 上执行，65 项全部通过、0 跳过，含目录 17 项、项目 11 项与 Redis 限流 2 项。Web 94 项通过，AI 115 项通过；真实 Qdrant HTTP 在独立命令通过，真实 Embedding 缺配置未验。临时数据库和 Redis 容器已清理。

## 交接

<!-- next -->

- 已通链路包括 Web 登录、项目与成员、套件与用例，以及 Java 容器到 AI 就绪接口。文档分块向量的写入、受限检索和删除使用独立真实 Qdrant HTTP 验收。
- BLOCKED（三端业务闭环）：main 缺 Java 导入编排、授权对象接口、服务签名接入，以及运行、聚合、v2 知识文档和会话服务。现有 Compose 的 AI 解析和索引配置未启用，也无真实 Embedding 端点。恢复起点是 Java Robot 导入编排与对象授权，再加载匹配的 AI 服务配置，以自造 Robot 文件验证结果落库和 Web 查询。
- 源码与记忆在 main 工作区，尚未提交或推送。
