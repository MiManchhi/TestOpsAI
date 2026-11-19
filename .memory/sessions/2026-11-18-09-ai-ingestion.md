# AI 文档索引与受限检索

更新：2026-11-19（Asia/Shanghai）。状态：VERIFIED（本机自动/HTTP 替身范围；真实外部服务另列）。

<!-- handoff:v1 -->

Task: AI-INGESTION-001
Role: ai
Baseline: 424f547614de0298e2ce7aaf25b833949ee50fff
Contract: docs/contracts/README.md 1.0.0、ai.openapi.json、schemas.json
Scope: 用户授权实施文档处理/SQLite 任务/HTTP Embedding/Qdrant/受限检索计划；apps/ai-service、本领域记忆与 AI 运行说明；Java/Web/契约/Compose 只读，不提交推送合并

## 起点与计划

- codex/ai-ingestion / 424f547，工作区干净，与本机 main 一致；不 fetch 或同步未授权的新基线。
- 已恢复入口与相关 AI 记忆、规范和冻结契约；复用既有服务鉴权与对象读取。新增 ingestion 目录承载实际文档处理/任务/向量职责，不建空架子。
- 阶段 1 IMPLEMENTED：依赖、严格 DTO、可定位解析与分块。
- 阶段 2 IMPLEMENTED：SQLite 版本迁移、独占锁、幂等任务与恢复、三个内部接口。
- 阶段 3 IMPLEMENTED：HTTP Embedding、Qdrant 发布/删除、受限检索。
- 阶段 4 VERIFIED：解析/安全/可靠性/API/实际本地 Qdrant 回归、完整门禁、说明与交接；真实 Qdrant HTTP/Embedding 待配置后单独验收。

## 发现与约束

- 持久化只存 AI 状态，不写 Java 表；任务接收前完成对象下载，不持久化 JWT/密钥。
- 可信 READY 版本集合和引用注册接口不在当前契约交付中；内部检索缺少可信 READY 信息时关闭，绝不自行生成 citationId。
- SQLite 单实例、并发 2、排队 64、输入额度 512 MiB；删除标记先于物理清理，generation 发布须在向量确认之后。
- PDF 使用 pypdf，只抽取文字；扫描件不假装成功，不扩展 OCR。新增解析必须保留原 64 项 Robot/XML 回归。

## 验证与进度

<!-- checks -->

- 初始 Git/Restore 退出 0。uv lock/sync --locked 获批执行成功，55 个依赖，新增 pypdf 6.21.4、qdrant-client 1.17.1、filelock 3.32.7 等；未升级原有依赖或关闭 TLS。
- 严格类型检查 26 个源文件通过。首轮完整 AI 测试 103 passed，含原 64 项回归，Qdrant 使用实际本地实现（含持久模式重启），HTTP Embedding/Java 对象接口使用替身。
- 新增 5 项可靠性测试全部通过：部分写入不可见、删除失败 3 次补偿重试（4 次总调用）及新操作恢复、在途任务取消后 dispatcher 继续、PROCESSING 重启从输入恢复、profile 不可变与 READY 版本过滤。
- Robot 旧客户端没有 Qdrant local-mode 属性，改为显式测试注入标志，生产默认 HTTP；集合初始化失败后会补建缺失 payload 索引。类型检查的 list 不变性与静态 payload 类型已纠正，未放宽 mypy/Ruff。
- 新增受控的真实 Qdrant/Embedding 验收测试入口，只有显式 VERIFY 环境配置才运行；没有读取现有私钥或 .env。真实 HTTP/Embedding 验收尚未执行，不用本地模式或替身冒充。
- 检索改为单次带完整 OR 范围过滤的 Qdrant 查询（最多 1000 个活动版本），总时限 15 秒；输入清理失败保留配额并在重启补清理，不将已发布索引改写为失败。新增严格定位/查询模型、单次分组过滤后最终完整门禁待执行。

- 后续 AI 回归 109 passed、2 skipped（显式 VERIFY_QDRANT/EMBEDDING 未配置），保留 Starlette 弃用提示；两项跳过不计真实 HTTP/语义验收通过。真实 Qdrant 本地模式覆盖精确项目/KB/文档/hash/parser/profile/generation 过滤、维度不符拒绝和删除隔离。
- 实施期间外部将分支从 424f547 快进到 210ec4e（reflog: merge main）。已重新 Restore 并核查差异，只涉及 Java 项目分页、Nginx/smoke 和其他任务记忆；AGENTS/规范/AI 源码/公共契约无变化，本次未提交成果保留。未执行该同步操作，也未修改这些外部成果；最终验证绑定实际基线 210ec4e。
- 2026-11-18 首次完整 scripts/check.ps1 退出 0（Web 59、Java 48 中 43 项 DB/Redis 跳过、AI 109 passed/2 skipped、类型/格式/契约/构建通过）。随后增加 PDF 压缩流预算与部分批次写入后的恢复回归，该证据不替代最终变更集复验。
- 2026-11-19 恢复时核实 HEAD 仍为 210ec4e、仅授权文件未提交。PDF 设置单流解码 8 MiB/累计内容流 16 MiB 及页树/容器预算，新增压缩单流、重复流阵列两项拒绝测试；部分向量写入后关闭重开 SQLite/Qdrant，确认重建无重复数据。相关 20 项测试通过；Ruff/format/mypy strict（26 源文件）通过。
- 复核修正幂等键范围：绑定项目/操作者/操作类型，不把 documentId 纳入键范围；同键换文档也必须 409。API 15 项测试通过，含该回归。最终完整门禁与 smoke 正在复验。
- 2026-11-19 完整门禁再次退出 0（AI 112 passed/2 skipped、Web 59、Java 48 中 43 项跳过）；随后复核取消竞态，新增有界写入保护：Qdrant 请求已经发出时，即使索引取消也等待该请求完成/15 秒超时再释放版本锁。删除现在按版本跨 generation 清理，并利用持久 profile 名在清单已移除后继续检查集合，允许后续删除清理远端超时后晚到的写入。
- 新增在途向量写入/删除竞态与晚到残留补清理，以及 PDF 20 MiB 输入/4 MiB 文本预算测试；解析、可靠性、向量专项 24 passed（15.92 秒）。Ruff/strict 类型检查通过，最终变更集完整门禁待复验。
- scripts/smoke-local.ps1 退出 1：Java Flyway 连接 MySQL 时没有 MYSQL_PASSWORD，进程就绪前退出；脚本清理自己创建的三端进程。不借用 .env/凭据，不声明三端冒烟通过；AI 独立验证不受该 Java 配置阻塞。
- 最后一次门禁与独立 HTTP 冒烟误并行，共用的 .venv 被 uv 重建：门禁在 Python Ruff 前因 Windows 活跃文件拒绝删除而退出 1，冒烟因模块重建缺失退出 1；不算业务失败或通过证据。已确认两个进程结束，uv sync --locked 成功恢复环境，后续串行执行。
- 串行独立 HTTP 冒烟退出 0：真实 Uvicorn 三个索引/轮询/删除端点 → 回环 HTTP 授权对象替身（验签）→ spawn 文档子进程 → 回环 HTTP Embedding JSON 替身 → 实际本地 Qdrant 写入/精确删除；仅内存临时 RSA 密钥和自造 Markdown，finally 关闭自身服务器/临时状态。不是生产 Java、真实 Embedding 或 Qdrant HTTP 证明。
- 最终串行 scripts/check.ps1 退出 0（2026-11-19，Windows，210ec4e + 本批完整未提交文件集）：44 份记忆、布局/工程回归/14 份 PowerShell 解析、全仓 Prettier、契约 109 schema/65 operation/827 引用/15 页面/45 示例与非法输入/SSE；Web 类型/lint/59 测试/构建；Java verify/Spotless（48 项中 43 项 DB/Redis 跳过）；Python lock 55 包/Ruff/strict 26 源文件/双平台脚本/115 passed、2 skipped（35.35 秒）/sdist 与 wheel 构建通过。包含原 64 项 Robot/XML/健康回归。既有 Starlette 与 Java agent 弃用提示保留。
- 外部验收分别记录：实际 Qdrant 本地实现及磁盘重启 VERIFIED；真实 Qdrant HTTP PLANNED（无 TESTOPS_AI_VERIFY_QDRANT_URL）；真实 Embedding PLANNED（无显式 VERIFY URL/model/dimensions）。测试替身不作为生产回退或语义质量证据；运行命令见说明。
- 收尾检查 25 份候选均属授权范围，无 Java/Web/契约/Compose 变更；选定私钥/Token/冲突标记模式与产物路径扫描通过，git diff --check 退出 0。该模式扫描不等于全面安全审计；未创建提交、暂存、推送或合并。

## 交接

<!-- next -->

- 本批 AI 实现与本机验收已完成，尚未提交/推送/合入主线；CURRENT AI 区块、模块、本 session 和运行说明同步交付。收尾仅修改说明/记忆，定向 Prettier、memory Check（44 份）和 git diff --check 均退出 0，不重复无变动业务测试。
- BLOCKED：无实现阻塞；真实 Qdrant HTTP/Embedding 待显式配置，三端 smoke 待 Java 数据库配置。部署/持久挂载、可信 Java READY 集合与引用注册不在本批独立交付中，不据本地 SUCCEEDED 放开正式检索。
- 下一步精确起点：按 AI_INGESTION.md 的 VERIFY 变量运行独立真实服务验收；协调 Java 对象和签名配置、可信 READY 事实与引用注册后再启用编排调用。没有新任务授权时不扩展聊天模型/Agent/SSE，不操作其他领域工作区。
- [CURRENT](../CURRENT.md)、[契约](../../docs/contracts/README.md)。

## 追加提交与合并授权

2026-11-19 用户明确“提交并合并（直接执行即可）”。上文未提交/不合并描述开发阶段历史；本次提交与合并发布结果见 [合并 session](2026-11-19-01-ai-ingestion-merge.md)，既有真实服务验收限制不变。
