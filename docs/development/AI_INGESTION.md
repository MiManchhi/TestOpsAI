# AI 知识文档索引与受限检索

更新：2026-11-19。状态：VERIFIED（本机自动/HTTP 替身范围；真实外部服务待验收）；验证版本、命令与限制见 [任务 session](../../.memory/sessions/2026-11-18-09-ai-ingestion.md)。接口遵守 [冻结契约 1.0.0](../contracts/README.md)，不新增公共 DTO 或查询 HTTP 路由。

## 接口与身份

| 接口                                | operationId   | 成功响应     |
| ----------------------------------- | ------------- | ------------ |
| POST /internal/v1/index-jobs        | indexDocument | 202 IndexJob |
| GET /internal/v1/index-jobs/{jobId} | getIndexJob   | 200 IndexJob |
| POST /internal/v1/index-deletions   | deleteIndex   | 202 IndexJob |

复用 [RS256 服务与委托配置](AI_PARSER.md)。POST 要求服务 Bearer JWT、X-Delegation-Token 与 UUID Idempotency-Key；验证 operationId、项目/操作者以及签名中的知识库、文档和对象白名单。IndexRequest.contentHash 必须等于 ObjectRef.sha256，下载再核实实际大小和 digest。GET 要求 Java 服务身份且 token.operationId 对应任务，否则 404；不能用用户 JWT 查询任务。

下载在接收 POST 时完成，最长 15 秒，整个接收过程最长 120 秒；校验成功并持久化输入与任务后才返回 202。原始请求保留对象引用，不保存委托 token、服务 token、Embedding 密钥或 Java 数据库凭据。后台任务不重新使用过期的委托令牌。

幂等记录绑定项目、操作者、操作类型和请求键，保存 24 小时；同键同内容返回原任务，同键不同内容（含换文档）409。新 operationId 使用新请求键；相同逻辑索引可复用已发布向量，但保留本次 operationId 对应的独立任务。轮询复用该任务的 operationId 签发短期服务 token。

## 独立配置

配置均读取 TESTOPS_AI_ 前缀环境变量。既有 Robot/XML 接口保持独立；不配置文档状态目录时，新接口 503，健康接口不代表索引依赖已就绪。

| 环境变量                            | 用途 / 默认                                                |
| ----------------------------------- | ---------------------------------------------------------- |
| TESTOPS_AI_INGESTION_STATE_DIR      | 必填、显式指定的可写持久目录                               |
| TESTOPS_AI_INGESTION_CONCURRENCY    | 下载接收与后台任务各自上限，默认 2，范围 1–8               |
| TESTOPS_AI_INGESTION_QUEUE_LIMIT    | QUEUED/PROCESSING 任务总上限，默认 64                      |
| TESTOPS_AI_INGESTION_INPUT_QUOTA    | 未清理暂存输入字节额度，默认 536870912（512 MiB）          |
| TESTOPS_AI_DOCUMENT_TIMEOUT_SECONDS | 每次文档解析子进程时限，默认/最大 30 秒                    |
| TESTOPS_AI_EMBEDDING_PROFILES       | JSON：profile 名 → URL/model/dimensions/api_key/batch_size |
| TESTOPS_AI_QDRANT_URL               | 真实 Qdrant HTTP 地址，兼容现有 1.17.0                     |
| TESTOPS_AI_QDRANT_API_KEY           | 运行环境注入的密钥，可为空以适配无鉴权的隔离服务           |
| TESTOPS_AI_QDRANT_COLLECTION_PREFIX | 应用专属 collection 前缀，默认 testops_knowledge           |

本机可用忽略的 `.runtime/ai-ingestion` 作为持久目录；容器使用显式、非 root 可写的持久挂载。该目录含 AI 任务 SQLite/WAL、实例锁与输入暂存，不能提交、公开或被另一实例共用。版本迁移由 store.py 的递增 MIGRATIONS 管理，后续只能追加迁移。SQLite 不读取或修改 Java 表。当前只支持单实例、单应用进程，不使用 uvicorn --workers/--reload 运行持久任务。

Embedding profile 配置示意（运行时 JSON，密钥另由安全运行环境注入 api_key）：

```json
{
  "knowledge-default-v1": {
    "url": "https://embedding.example.invalid/v1/embeddings",
    "model": "configured-embedding-model",
    "dimensions": 1024,
    "batch_size": 16
  }
}
```

请求 embeddingProfile 必须命中配置白名单。适配器使用 OpenAI 兼容 JSON 协议（model/input/encoding_format=float），按响应 index 还原输入顺序，校验条数、维度、有限数值和非零向量。每次调用 15 秒，瞬时网络错误、429/500/502/503/504 最多重试两次；其他错误不重试，不返回上游原文。禁止重定向和请求传入 URL。profile 的 URL/model/dimensions/collection 前缀一经入库不可改，修改模型或维度须新增 profile 名；密钥轮换不改变索引身份。

## 处理与恢复语义

- parserVersion 固定支持 `knowledge-v1`。Markdown 2 MiB、PDF 20 MiB；PDF 只提取文字，最多 500 页、提取文本 4 MiB，扫描/空白页返回 OCR_REQUIRED，加密和损坏内容失败，不执行 OCR、脚本或外部资源。Markdown 仅保留文本，不执行 HTML/代码块。
- PDF 在可终止子进程内解析，最长 30 秒；单个流解码上限 8 MiB、累计页面内容流上限 16 MiB，另限制解码工作量、容器元素与页树深度。压缩内容超预算明确失败，不因提取结果很短而放任解压；不调用外部 JBIG2 程序。
- 分块上限 1000 字符、优先段落边界、重叠 100 字符；PDF 不跨页，Markdown 保留原文行号与标题，标题超过 256 字符明确失败。每文档最多 10000 块，块 ID 由解析版本、原字节 hash、页/偏移与片段内容确定。PDF 缺少 Markdown 式标题时 heading 为空，不冒充 Java 文档标题。
- `QUEUED → PROCESSING → SUCCEEDED/FAILED/CANCELLED`；stage 为 PARSING/CHUNKING/EMBEDDING/CLEANUP/FINISHED，表示实际处理检查点，不是百分比。SQLite 独占锁阻止第二实例；启动把未完成 PROCESSING 恢复为 QUEUED，复用已校验输入并以确定性向量 ID 重建，不重新获取用户 token。
- 每个 profile 对应独立 collection，Cosine 距离及维度必须一致；创建/恢复时补建精确过滤字段索引。向量 payload 保存项目、知识库、文档、内容 hash、解析版本、profile、generation 与定位文本。全部批次写入确认后，SQLite 才在同一事务发布活动 generation 和成功任务。
- 失败或重启前的部分向量保持未发布，不能检索；同逻辑索引重试会先清理旧的未发布 generation，精确删除也会清除这些向量。原始输入成功/失败后清理；文件清理异常会记录脱敏 requestId，保留配额，启动时补清理，不伪称已释放空间。
- 删除先持久化项目/文档/hash 墓碑并关闭可见性，再取消相同版本的在途索引，通过文档版本锁顺序清理所有 profile/generation 和本地元数据。物理删除初次失败后最多重试三次；仍失败则 FAILED，但墓碑保持有效，新删除操作可继续清理。已删除版本的旧索引请求返回 409，不能重新写回。
- 已发出的向量批次写入有 15 秒总时限；取消索引时先等该次写入完成或超时，期间保持版本锁，再进入删除清理。远端超时仍可能存在结果不确定性，删除墓碑持续阻止旧版本命中，后续删除操作可继续清理。
- 删除按项目/文档/hash 清除所有 generation；已使用 profile 名保留在 SQLite，后续删除即使没有活动清单也会检查这些 collection，清除超时后晚到的残留写入。物理服务迁移和配置变更须保留原集合可达性。

主要错误：400 请求/幂等键不合法；401 无效服务或委托身份；403 范围不符；404 不可见任务；409 幂等冲突/版本已删除；413 文档/暂存额度超限；415 格式不支持；422 解析或版本校验失败；429 队列已满；503 配置/向量/Embedding 不可用。统一 code/message/requestId，任务错误同样脱敏。

## 受限检索核心

IngestionService.search 消费 KnowledgeSearchTool、已验签 AuthorizedScope、可信 READY 文档版本列表和配置 profile；只供后续 AI 编排内部调用，不接受浏览器/模型自称的 READY 元数据。签名范围与请求 KB/document IDs 取交集，空交集拒绝，过期或缺少 search_knowledge 权限拒绝。

本地索引 SUCCEEDED 不等于 Java 文档 READY；ready_versions=None 时 503，可信空列表返回无候选。单次查询最多覆盖 1000 个活动版本，全部条件（项目/KB/文档/hash/parser/profile/generation）在 Qdrant 中过滤，最多返回 10 条，含 Embedding 的检索总时限 15 秒。返回前再检查删除标记，不让查询期间的删除暴露旧片段。

返回对象是内部 Candidate（片段、定位、文档/hash/KB、score），不包含 citationId，也不作为冻结 KnowledgeSearchResult 的替代 DTO。正式引用必须经过可信注册结果转换；注册能力未接入时正式结果不可用。该核心不生成回答、不把分数当置信度、不新增 LLM/Agent/SSE 行为。

## 验收命令与边界

```powershell
uv sync --locked --project apps/ai-service
uv run --locked --project apps/ai-service pytest apps/ai-service/tests
npm run contracts:check
./scripts/check.ps1
```

测试材料由测试代码自造；自动测试使用临时密钥、Java 对象/Embedding HTTP 替身、实际 Qdrant 本地实现（含磁盘持久模式）。覆盖解析、精确过滤、幂等、恢复、部分写入、删除取消及补偿，不代表真实外部服务或语义质量已验收。

真实 Qdrant HTTP 验收：显式设置 TESTOPS_AI_VERIFY_QDRANT_URL 和可选 TESTOPS_AI_VERIFY_QDRANT_API_KEY，然后运行以下命令。只创建随机 `testops_ai_verify_` 前缀的隔离 collection，finally 清理该 collection，不使用现有业务 collection。

```powershell
uv run --locked --project apps/ai-service pytest apps/ai-service/tests/test_ingestion_external.py -k qdrant -q
```

真实 Embedding 验收：显式设置 TESTOPS_AI_VERIFY_EMBEDDING_URL、TESTOPS_AI_VERIFY_EMBEDDING_MODEL、TESTOPS_AI_VERIFY_EMBEDDING_DIMENSIONS，以及可选 TESTOPS_AI_VERIFY_EMBEDDING_API_KEY，再运行：

```powershell
uv run --locked --project apps/ai-service pytest apps/ai-service/tests/test_ingestion_external.py -k embedding -q
```

未配置 VERIFY 变量时以上两项明确跳过；不读取仓库或其他工作区 .env，不借用真实凭据。HTTP 验收分别记录结果；真实 Embedding 协议通过仍不等于检索质量评测通过。部署/持久挂载、Java READY 事实与引用注册接入不在本批 AI 交付范围内。
