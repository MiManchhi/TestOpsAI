# TestOps AI 三端开发契约 1.0.0

日期：2026-11-17。状态：FROZEN（本轮确定的开发基线）；新增业务运行实现仍为 PLANNED。基线 main `41ef74c`。冻结表示三端必须共同遵守此版本；不表示接口已经部署。

## 唯一来源与使用方式

| 文件                                       | 职责                                          | 消费方          |
| ------------------------------------------ | --------------------------------------------- | --------------- |
| [public.openapi.json](public.openapi.json) | Web → Java：既有登录 + 完整新业务 HTTP 契约   | server / web    |
| [ai.openapi.json](ai.openapi.json)         | Java → Python：对话生成、确定性解析、索引     | server / ai     |
| [tools.openapi.json](tools.openapi.json)   | Python → Java：受控工具与授权对象读取         | server / ai     |
| [schemas.json](schemas.json)               | DTO、状态枚举、事件联合类型；所有契约共同引用 | 全部            |
| [tools.json](tools.json)                   | 模型可用工具、输入输出与副作用等级            | ai / server     |
| [pages.json](pages.json)                   | 页面、路由与 operationId 对应                 | web / 验收      |
| [examples.json](examples.json)             | 可验证的完整自造领域数据与事件序列            | 设计原型 / 测试 |

OpenAPI 采用 3.1.1，显式选择 JSON Schema Draft-07 dialect，以复用仓库已锁定的 Ajv 6.15.0；不是把 Draft-07 伪装为 2020-12。[OpenAPI 官方方言说明](https://spec.openapis.org/oas/v3.1.1.html)允许声明 schema dialect。schema 为纯 JSON，模型工具定义直接引用相同类型，禁止再手写另一套字段。

运行 `npm run contracts:check` 校验 DTO schema、所有跨文件引用、分页/鉴权/幂等约束、页面映射、工具映射、自造示例、拒绝非法输入与事件顺序。此命令纳入本地完整门禁和 CI。它验证契约自身一致性，尚不证明未实现服务的行为符合契约；每个业务里程碑还必须加入真实 HTTP provider 验证。

## 接口版本与兼容性

- 保留现有 `/api/v1/auth/login`、`/api/v1/users/me`：数字 ID、令牌有效期和 sessionStorage 行为不变。最新主线登录限流 60 秒/10 次/IP，超限 429 `RATE_LIMITED`，读取 `Retry-After` 秒数；Redis 降级沿用 ADR-0006，不在本轮修改。
- v1 知识库/用例 CRUD 仍是旧接口；这里不改其真实行为。新项目隔离、数组标签、字符串 ID、Robot 来源与运行语义统一使用 `/api/v2`。最终 Web 新业务只接 v2，不混用 v1/v2 用例或知识库 ID。
- v1 的登录身份由 Java 映射到 v2 字符串 actorId。v2 资源 ID 为十进制字符串；会话/轮次/导入任务/草稿/引用 ID 为 UUID；禁止浏览器把 BIGINT 转为浮点数。
- 内部 API 单独版本 `/internal/v1`，只在服务网络开放。v1 用户 JWT 不透传给 Python，不进入模型。
- 不自动转换旧用例标签、所有权或共享范围。迁移由前进式 Flyway 新脚本完成，先建项目并明确归属，校验后切换，保留既有脚本原文。无映射数据保持 legacy-only。
- 新增必填字段、改枚举含义、改副作用、改权限属于不兼容变更，必须改版本与 ADR，更新所有消费方及正反例；不允许“前端先临时兼容”。

## 通用 HTTP 规则

JSON camelCase；所有时间为带时区 ISO-8601 UTC。v2 请求体拒绝未知字段；空字符串、缺失与 null 按 schema 区分。页面采用 `page=0` 起、`size=20`，最大 100；超出范围返回 400，不悄悄截断。分页响应始终 `items/total/page/size`。列表排序必须稳定并以 ID 作为末位排序，不用不可重现的自然顺序。

错误统一 `{code,message,requestId}`。现有 requestId 在某些限流路径可为 `unknown`，因此 schema 保持非空字符串，不能为好看而虚构 UUID。新增服务应生成 UUID 并透传 `X-Request-Id`。输入错误 400，未认证 401，已知项目无权 403，不可见/跨项目资源 404，版本/状态冲突 409，超限 413，不支持格式 415，内容不可处理 422，频率限制 429，依赖不可用 503。请求体之外的内部异常不得泄露。

有副作用且标注 Idempotency-Key 的接口要求 UUID 请求键。Java 将键绑定 actor + operation + resource + canonical request hash，保存 24 小时；同键同内容重放原结果/任务 ID，同键不同内容返回 409 `IDEMPOTENCY_CONFLICT`。进行中的相同键返回相同任务，不重复启动。授权在每次请求重新检查；成功后被撤权不允许借重放获得旧资源。单条已有 v1 接口没有此保证，不能宣称其已具备。

## 领域与授权

Project 是用例、套件、运行、知识库、导入和审计的隔离根。OWNER 管项目与成员，EDITOR 导入/维护/保存草稿，VIEWER 只读及问 AI；全局 ADMIN 为受审计的管理特权。创建项目允许已认证用户并成为 OWNER。最后一个 OWNER 不可删除，当前版本不提供所有权转移，返回 409 `LAST_OWNER`。

PROJECT 知识库由项目成员读取、EDITOR/OWNER 写入；RESTRICTED 知识库只对授权成员与 OWNER 可见，WRITE 同时要求项目 EDITOR，READ 不授予项目外访问。授权对象必须是现有项目成员。会话只属于创建者；项目成员不能读取他人的会话。引用按当前资源权限实时解析，不因缓存或历史摘要绕过撤权。

Java 必须在列表查询、聚合统计、流式订阅、工具执行、引用、草稿提交和文件读取时校验权限。聚合不能先算全量后仅隐藏明细。资源缓存键包含项目与有效权限版本；撤权需失效，Redis 不能成为权限真相。现有 v1 知识库缓存不能直接复用于 v2。

### Robot 定义与选择

TestCase 保存定义，不保存某一次运行状态。`lifecycleStatus=ACTIVE/ARCHIVED`；`sourceType=ROBOT/MANUAL`。TestResult 保存运行结果 `PASS/FAIL/SKIP`；Run 表示导入后的 `COMPLETED/INCOMPLETE`，没有“点击按钮已经执行真实设备”的含义。

Robot 导入只接受用户有权使用的 `.robot/.resource` 完整快照，multipart `files` 与 `logicalPaths` 同长且一一对应。每文件 ≤2 MiB，总计 ≤20 MiB，最多 200 文件；拒绝绝对路径、盘符、反斜线、`..`、重复 logicalPath 和外部资源解析。解析器仅做静态结构提取，不 import Python 库、不求值变量、不执行 setup/关键字。无法静态求值的项记录 issue，不猜测运行值。

Java 以项目 + 规范化 suite sourcePath + 原始 caseName 的 UTF-8 长度前缀序列生成 SHA-256 stableKey，保持大小写；同一 suite 内重名拒绝导入。源文件路径变化作为移除/新增处理；sourceRef 保存文件内容 SHA-256 与行号。项目快照 revision 为 logicalPath 排序后的 `path + NUL + contentHash + LF` 串的 SHA-256。只有完整解析和校验成功才事务性应用新快照，缺失的 ROBOT 用例归档，MANUAL 不受影响；失败绝不部分归档。

`tags` 为去空白、区分大小写、去重的数组，单标签精确匹配；`tagsMatch=ALL` 默认。query 对标题/说明作大小写不敏感文字包含，module 为精确匹配；suiteId 默认含子套件。直接列表与 `search_test_cases` 使用同一个 Java 查询实现和相同 CaseFilter。禁止让向量库决定命中集合。

跨页选择使用 `EXPLICIT` 或 `FILTER`，二者互斥；FILTER 由 Java 按同一规则计算且应用 excludedCaseIds。结果保存不可变 caseId/version 集合和摘要，最多 1000 条，超限返回 422 `SELECTION_TOO_LARGE`；有效期 24 小时，不接受客户端传入的总数。过期需重新选择，使用时再次验证资源可见性，不能静默丢掉越权项。

Robot 来源正文与步骤只读；修改需更改有权维护的源文件后重新导入。人工建议保存为 MANUAL，不转换成可执行 Robot 程序，也不冒充已回写外部平台。MANUAL 使用 CasePatch.version 乐观锁，旧版本返回 409 `VERSION_CONFLICT`。

### 运行与产物

导入只接受单个自造/授权 `output.xml`，最大 50 MiB；附 `name/catalogRevision/testContext`，testContext multipart 部分为 `application/json`。只解析白名单 XML 结构；禁用实体/网络/DTD，限制节点、深度和解析时长。`report.html/log.html` 不能作为统计事实来源，也不在本轮首期上传范围。

用 `catalogRevision + suitePath + caseName` 关联用例，不靠同名猜测；无法匹配时 Result.caseId=null，保留快照名称，计入 unmapped，并置 Run 为 INCOMPLETE，不丢失原始结果。全量结果满足 total=pass+fail+skip；Robot 根套件统计只作核对，不重复累加父子统计。历史 Run 保留当次用例名称/来源，不随定义重导入改变。

结果不可编辑。初版导入页只接受 `.robot/.resource` 与 `output.xml`，无真实执行或外部企业地址配置。解析后的日志通过脱敏文本行 API 展示；未来 HTML 产物只作 attachment 或隔离域 sandbox 展示，不在业务同源执行脚本。

### 知识文档与引用

上传 Markdown ≤2 MiB 或 PDF ≤20 MiB，无远程 URL 导入、无归档包。Java 校验内容类型、签名和配额，生成对象键；Python 从授权对象 API 读取，不接受本地磁盘路径。每次上传为不可变 Document + contentHash + versionLabel，修改内容必须创建新文档版本，不能原地覆盖旧引用。

状态 `QUEUED → PROCESSING → READY/FAILED`；删除进入 DELETING，文件与向量清理完成后 DELETED，失败保持 DELETING 并由补偿重试。READY 只在元数据、分块和向量写入均成功后公布。幂等索引键为 documentId/contentHash/parserVersion/embeddingProfile，版本不同不复用索引。stage 用于显示真实步骤，不伪造百分比。

Java 轮询内部 IndexJob 并更新公开 Document；无需新增一个易重复的回调入口。jobId 与 operationId 的映射持久化在 Java；客户端刷新不丢任务。内容阅读用 blocks + nextCursor，PDF 用页码，Markdown 用行号/章节。

Citation 由 Java 注册/复核后发给浏览器，模型不能自己编 citationId；必须校验资源属于授权范围、版本存在、locator 可定位、excerpt 来自来源。AI 无有效证据返回 evidenceStatus=NONE 或 PARTIAL。打开引用时撤权返回 404，来源已有新版本显示旧版本标记；不能擅自替换旧文档内容。

## 对话与 SSE 精确规则

1. 创建本人会话，绑定 projectId，不允许后续跨项目切换。
2. POST messages：Java 校验 MessageContext；knowledgeMode=NONE 要求 knowledgeBaseIds/documentIds 为空；SELECTED 至少有一个可读且就绪的文档/知识库。documentIds 必须属于所选知识库。selectionId、runId 必须属于同一项目。保存原始用户消息与资源版本快照。
3. `clientMessageId` 与 Idempotency-Key 共同去重；同会话一次只允许一个非终态轮次，冲突返回 409 `TURN_IN_PROGRESS`。
4. 返回 202 Turn 后，Web 使用带 Authorization 的 fetch 读取 `GET turns/{turnId}/events`；不把 JWT 放 URL，不依赖不能设认证头的裸 EventSource。
5. 事件的 SSE `event` 等于 JSON `event`，SSE `id` 等于 JSON sequence。sequence 从 1 单调递增且在同一轮次连续。JSON 为 `turnId/sequence/occurredAt/event/data`，完整联合类型见 schemas。
6. 顺序：turn.started；零个或多个 stage/tool/delta/citation/draft 事件；恰好一个 turn.completed/failed/cancelled。completed 的 answer 是持久化最终真相，delta 只是展示增量。引用可早于完成，业务操作只在 completed 后启用。
7. Java 持久化并编号事件后再发给 Web；Python 事件经 Java 重新校验并编号，不能直接透传供应商协议。心跳 `: heartbeat` 每 15 秒，不占 sequence。单轮总时限 120 秒，单工具 15 秒，重试只读工具最多一次。
8. 断网不等于用户主动停止；Java 最多保留上游任务 30 秒供重连，超时取消。Web 使用 Last-Event-ID 重放更大的 sequence，去重旧事件；24 小时外返回 409 `EVENT_REPLAY_EXPIRED`，重新读取消息与 Turn，不悄悄重发问题。
9. 主动停止调用 cancel，Java 持久化 CANCELLED 并关闭上游；取消与完成竞争以数据库终态 CAS 的获胜方为准。已完成后取消返回原 COMPLETED，不追加第二个终态。
10. HTTP 流建立前出错返回普通错误 JSON；建立后发送 turn.failed 后关闭。流式订阅每次重连重新鉴权，运行中撤权取消并隐藏不可再见内容。删除会话先取消活动轮次，不删除已提交业务用例及审计。

## 内部身份与工具

服务调用使用短期服务 JWT（由部署环境签名配置），`iss=testops-server`/`testops-ai`、固定 `aud`、`exp≤5min`、jti 与 operationId 校验。Java 的授权委托令牌另以 `X-Delegation-Token` 提供给 AI 调用工具/对象接口，含 actor/project/允许工具/资源范围/operationId/exp≤120s；Java 持有授权真相，每次重新校验 membership 与 grant 版本。签名密钥只在服务端，不写入本契约或 Web。

claim 字段分别以 ServiceClaims 和 DelegationClaims 冻结；JWT iat/exp 为 Unix 秒，并验证 exp>iat、时长上限、签名/issuer/audience，不能只做 JSON 校验。Java 调用 AI 的 POST 操作须通过请求头交付不透明委托 token，AI 仅在回调 Java 工具/对象接口时使用，不放入模型提示或日志。对象读取还要验证 objectIds 白名单，不能凭逻辑文件名请求任意对象。

AuthorizedScope 是 Java 推导的上下文，浏览器、模型或 Python 自行提交的 userId 不构成授权。Java→AI respond 只发脱敏历史与最小必要上下文；不发数据库凭据、用户 JWT 或全部原始资料。

caseScope=PROJECT 表示在已授权项目内查询，caseIds 必须为空；caseScope=SELECTION 表示只能读取 caseIds 的固化集合，空集合不代表全部。runIds 是允许读取的明确运行集合，空集合不允许查询运行。knowledgeBaseIds 是可检索库白名单，documentIds 非空时进一步收窄，不得当作扩大范围的并集。

search_knowledge 为 AI 本地受限 RAG 工具，不绕回自己的 HTTP 入口。它先将请求 ID 与 Java scope 取交集，空交集拒绝；Qdrant 使用精确 kb/document/contentHash 过滤且只含 READY 版本。Java 对最终引用再次鉴权。其他工具对应 tools.openapi 的真实 Java 查询；get_failed_cases 固定过滤 FAIL，不能由模型改为任意状态或任意 SQL。

create_case_draft 只创建 EDITABLE 草稿，属于 DRAFT_ONLY；不创建测试用例、不执行 Robot。模型工具中不存在 commitDraft、执行 Shell、任意 URL、设备操作或真实运行工具。所有工具参数受 schema 校验，未知字段/超限拒绝，工具摘要与审计脱敏。

## 草稿人工确认

AI 草稿有效期 24 小时，绑定本人会话、项目与引用。VIEWER 可以看建议，只有 EDITOR/OWNER 可以编辑和提交。PATCH 必带 revision，服务端返回新 revision 和 payloadHash；提交发送当前 revision/hash/selectedItemIds，Java 以存储内容验证，不信任浏览器传入“已批准”。

payloadHash 为 Java 对规范化、字段排序的完整草稿 items JSON（UTF-8、紧凑分隔、不转义中文）的 SHA-256。前端直接使用响应摘要，不自行实现第二套 hash。选中 ID 集合也纳入幂等请求 hash。先重新鉴权、验证版本/有效期/引用可见性及业务字段，再在同一事务中创建选中 MANUAL 用例、标记草稿 COMMITTED 并写审计；任一失败全部回滚。相同草稿不同幂等键也只提交一次，后续返回原 createdCaseIds；不能部分成功然后重复创建。

## 实现交接与验收

server：项目隔离/迁移 → 精确查询与导入事务 → 知识文档/对象 → 会话/事件持久化 → 工具网关 → 草稿确认。ai：静态 Robot/XML 解析 → 文档处理 → 授权 RAG → 受限工具编排。web：按 pages.json 和最终设计开发，所有接口从本目录生成或逐项验证，不从图片猜字段。

每个切片合入前必须有：正常请求、无权/跨项目、schema 非法、状态冲突、幂等重放的 provider 测试；工具查询与直接查询集合一致；Robot 解析与索引一致；事件重连/重复/停止竞争；引用定位/撤权；草稿重复提交。具体页面验收见 [最终产品设计](../PRODUCT_DESIGN.md)。

本轮不创建几十张空表或空服务接口；正式数据模型在 [数据与迁移设计](../DOMAIN_MODEL.md)，只在实现对应切片时添加前进式迁移。接口命名、字段、权限与状态现在冻结，开发可以分阶段完成，禁止用“以后再说”重定义相同对象。
