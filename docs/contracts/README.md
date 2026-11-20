# 三端接口交接

2026-11-19 · 设计基线 2.0.0。最终版开发契约为 [workspace.openapi.json](workspace.openapi.json)。每个 operationId 都有方法、路径、输入、成功状态、响应 schema、授权域和错误响应。以同一份文件驱动 Web 类型、Java provider、Python Pydantic 一致性测试。页面见[页面表](../WEB_UI_DESIGN.md)。

## 地址、身份和职责

| 方向          | 入口                                                                                                                                               | 身份与事实                                                                                    |
| ------------- | -------------------------------------------------------------------------------------------------------------------------------------------------- | --------------------------------------------------------------------------------------------- |
| 浏览器 → Java | 现有 v1 登录、当前用户；新工作区 /api/v3，按 projects 与 platform 分路                                                                             | 用户 JWT。用户、项目、成员与资料授权由 Java 每次读取；平台角色只适用于 platform 操作          |
| Java → Python | 已有 /internal/v1/parsers/robot、/robot-output、/index-jobs、索引轮询与删除；新增 /internal/v2/ai/answer 与 /internal/v2/index-jobs/{jobId}/blocks | 短期服务 JWT + Java 签发的 X-Delegation-Token；Java 推导 scope。Python 返回候选证据定位与草稿 |
| Python → Java | 已有 /internal/v1/objects/{objectId}/content；新增 /internal/v2/evidence/resolve                                                                   | 仅授权对象、运行事实、消息附件和版本。服务身份、委托、operationId、项目、资源、有效期都要匹配 |
| Java → 浏览器 | startTurn 202，随后 watchTurn 的 SSE；引用在 readCitation 重新鉴权                                                                                 | Java 注册引用、分配事件编号、保存最终回复，原始 Robot 状态来自已登记结果                      |

标记 java 的内部操作只供 Java 调用；python 只供 Python。服务 JWT 校验签名、issuer/audience、jti、exp ≤5 分钟；委托 ≤120 秒，绑定 actor/project/operation 与资源，撤权即失效。浏览器令牌不交给 Python 或模型。v1 内部参数、请求头及署名以 [AI 接口](ai.openapi.json)、[对象接口](tools.openapi.json) 和[共享类型](schemas.json)为准。已实现登录由 [public.openapi.json](public.openapi.json) 的 login/currentUser 提供。旧 v2、页面映射、工具和样例仍给现存客户端及回归使用，最终页面不消费旧草稿提交、项目概览或手选上下文。

## HTTP 通则

- 字符串业务 ID，轮次、附件、引用和任务用 UUID。时间 UTC ISO-8601；UI 按本地时区交互并转换。空数组与未提供字段的语义见 schema。q 为字面包含搜索；页从 0 起、默认 20、上限 100，排序固定并以 ID 收尾。授权过滤先于分页和统计。
- 新公开写操作要求 Idempotency-Key UUID，24 小时内按 actor、operation、资源及规范化请求哈希去重；同键同体返回同一结果，异体返回 409。上传按原始文件哈希；重放也要重验授权。成员新增 revision=0、修改传当前值，删除用 revision 查询参数，资料/项目/读者有乐观锁。依据首次 expectedRevision=0，后续按项目当前包 revision。
- 错误体为 code、message、requestId。400 VALIDATION_ERROR、401 UNAUTHENTICATED、403 FORBIDDEN（有身份但缺操作权限）、404 NOT_FOUND（私有或不可见对象）、409 VERSION_CONFLICT/IDEMPOTENCY_CONFLICT/TURN_IN_PROGRESS、413 CONTENT_TOO_LARGE、415 UNSUPPORTED_FORMAT、422 UNPROCESSABLE、429 RATE_LIMITED、503 DEPENDENCY_UNAVAILABLE。SSE 建立前用 HTTP 错误，建立后用终态事件。公开错误仅给脱敏原因。
- 附件以本人/项目/有效期绑定，返回 attachmentId、文件名、SHA-256、到期时间。未绑定临时文件 24 小时清理；发消息后归本人会话，正式登记后固定独立业务引用，回收按引用计数。待确认资料只给提交人与负责审核且可读的负责人看；索引就绪不等于可检索。限定读者为项目成员，默认包含审核负责人；撤权使缓存及历史摘要立即失效。
- 项目文件范围：正文 MD ≤2 MiB，PDF、DOCX ≤20 MiB；聊天 .robot/.resource ≤2 MiB、文本日志 ≤5 MiB；output.xml ≤50 MiB。检查内容与类型，DOCX 读取文档 XML、限制解压体积和条目，不运行宏、不加载外链。登记必须有 Robot XML，附日志单独存储并按行定位。仅保留经授权可上传的自造/脱敏资料。

## 平台账号与负责人交接

v1 登录后调用 myAccount。mustChangePassword=true 的账号仅可访问本人状态与 changePassword，其余公开业务返回 403 PASSWORD_CHANGE_REQUIRED；更新密码、重置或停用都会推进凭据版本并使旧 JWT 失效。管理员用 createAccount 创建普通账号，临时密码仅经加密连接输入，使用现有密码哈希；响应/幂等回执/日志/审计均不含密码，使用带服务端密钥的请求摘要去重。变更密码成功后重新登录。listAccounts 仅列受管理的普通账号，禁止通过这些写接口授予平台 ADMIN、管理内置管理员或停用仍负责项目的账号。平台管理员的初始供应走部署流程。

负责人以 projectMembers 选择现有成员，通过 requestOwnerTransfer 建立 PENDING 申请。待决定期间角色和 ACL 不动。ownerTransferHistory/adminTransferHistory 读取项目内记录。管理员通过 decideOwnerTransfer 接受或退回该申请，只有接受才原子换人。原负责人已离岗时 eligibleOwners + proxyOwnerTransfer 可由管理员代办，unavailableReason 必填。已有待办先处理，同项目同时最多一条待办。生效时重查接任人仍在项目且可用、原负责人仍匹配，保存新旧角色和审核读者授权明细，更新 authRevision。代办同样验权并留痕。

## 三个关键时序

**资料进入可检索范围**。Web 上传附件并提交版本，Java 固定原件和 hash，状态为 PENDING/NOT_STARTED。负责人通过 downloadOriginalVersion 核对原件，退回保存原因，批准才在同一事务写 APPROVED/QUEUED 和索引 outbox。Python 接收 indexDocument，持委托 readAuthorizedObject，再解析、分块、建索引。Java 轮询 getIndexJob，成功后用 exportIndexBlocks 逐页取原文定位，核对版本、hash、parserVersion 和作业号，完整导入后才将版本置 READY。已发布版本的 readVersion 与 resolveEvidence 从同一套定位读取。setBasis 独立发布整包。

**复测结果进入项目**。Web 上传 XML 与可选文本日志 → registerRun 202。Java 保存执行时间、提供人、对象和任务 → parseRobotOutput → Java 校验计数后一次写入运行、用例结果及日志定位。没有用例库映射时仍保留名称、原始状态及运行事实。listRunArtifactsV3 / downloadArtifact 仅对成员开放，下载附件重新鉴权。平台排错只看 platformJobs，序列化白名单字段，不跨用业务对象 DTO。

**自动查证据与回答**。Web 首问可省略 conversationId，startTurn 在一笔事务中建本人会话并接受轮次；重试携带同一 clientMessageId 和幂等键。项目由路径决定；附件是本人已上传的对象，会话与 runId 必须同项目。Java 按 run.executedAt 取当时生效的整包，再取当前生效包，解析用户有权查看的版本、日志、附件，送入 composeAnswer。Python 在 Java allowlist 内检索，只用 resolveEvidence/授权对象读取补事实。Context.versions 携带每个可读版本对应的 knowledgeBaseId 和 hash，files 携带允许读取的临时对象及 SHA-256，runs 携带确定性的 Result 和有定位的 Proof。Java 在读取入口将同一请求带入的文本和附件约束在本人会话内。Python 从已核实的 RunContext 得到 Robot 状态，从 facts 查实测值；对缺失定位走 resolveEvidence，不凭摘要猜事实。三组载荷必须分别是 scope.versionIds、attachmentIds、runIds 的子集；服务端签发委托的 objectIds 也只含这些对象。多份运行无法唯一识别时返回澄清，禁止猜一份。Java 检查候选资源、内容 hash、定位和原文，核对实测/单位/比较符，创建 citationId 后发布最终回复。复制动作不回写项目，也不触发真实测试执行。

## 整套发布

setBasis 的 label 是项目内唯一的包版本名，versionIds 为一份或多份不可变文档版本，只能来自同一项目、已经 APPROVED/READY，每个逻辑文档至多一版。expectedRevision 对整个项目当前包做 CAS。提交中有一份不合格则整笔失败，旧包仍生效。包内明确适用条款冲突且无法裁定时给 NEEDS_CONFIRMATION 并列可见出处；禁止按文件位置猜优先级。Finding.versionLabel 显示包名，具体文件版本及定位在各条引用中。

## 轮次与证据语义

- 用户输入 text 与附件至少一项，单会话最多一个活跃 turn。没有 runId 时，已明确提到的合法对象由 Java 在 scope 确定前消歧；禁止空授权集合解释为全集。
- watchTurn 使用带 Authorization 的 fetch。sequence 从 1 连续增长，SSE id=sequence。turn.started 的 data 为空对象；turn.stage 给出 stage；turn.completed 的 data 为 {answer: Answer}；turn.failed 为 {error: Error}；turn.cancelled 为空对象。心跳 15 秒不编号；最后只允许一个终态。Java 在收到并校验 Python 完整候选答复前可持续给进度，最终文本一次发布。主动停止与完成以单次 CAS 决定终态。
- 用 Last-Event-ID 补大于该序号的事件；重放前重新鉴权，24 小时后 409 EVENT_REPLAY_EXPIRED，改读 getMyTurn/myMessages；不能自动重复发问。Python 超时或模型不可用时发失败终态，信息不足但模型正常时产生 INSUFFICIENT。权限变化可屏蔽已存回复。引用读失败不返回历史摘录。
- basisAtRun/basisCurrent 分别为一个 VisibleBasis 或 null；仅下发获准的文件版本。包内有不可见版本时 coverage=PARTIAL，不下发被隐藏的 ID、文件名或条款。若缺的证据影响判断则 INSUFFICIENT。不同包版本则分别给出判断；相同版本可合并显示，保留两个适用身份。时间不明、文件缺失或依据未指定时不虚构。纯设计评审使用 DESIGN 及 NOT_RUN。候选的 sources 只含定位，模型不生成最终 citationId。数值型指标尽量结构化为 metric，Java 重新比较；模糊文本标为待人工确认。
- 注册表和 DB 约束见[数据模型](../DOMAIN_MODEL.md)。新契约及样例的自检命令为 `node tests/contracts/workspace-review.cjs`；现存兼容契约继续运行 `npm run contracts:check`。真实 provider、真实模型及浏览器验收见[联合验收](../development/PARALLEL_DEVELOPMENT.md)。

## 操作与代码定位

[接口实现对照](workspace.bindings.json)逐项指定提供方、处理方法、消费者、工作包和联合验收编号。路径、模型和权限从同名 OpenAPI 操作读取。exportIndexBlocks 每页最多 100 个块，按不可变原文顺序返回；游标绑定作业、版本和解析器版本。首请求不带 cursor，最后一页 nextCursor 为 null。每个 locator 在同一版本内唯一，纯扫描件用 NO_TEXT 提示提交有可读文本的版本。正式业务验收以各端的真实 HTTP 结果为准。

当前提问使用 Context.currentMessageId，用户粘贴内容以 USER_MESSAGE、该 ID 和原始行号核对；允许的历史消息限本会话已重验的 history。复制回复时，Java 产生的 Citation.projectSource 指向共享业务原件，文档带 versionId/locator，运行带 runId/artifactId/fromLine；个人消息和附件为 null，只复制可见文字和来源说明。接手者的浏览器去对应项目页并按本人权限重验；私聊的 citationId 不能作为共享入口。readVersion 首次定位用 locator，下一页用 cursor，两者同时给出返回 400。

## 内部服务身份的配对

三项新内部操作使用 Authorization: Bearer 服务 JWT 和 X-Delegation-Token。service.operationId 是本次工作 UUID（问答用 turnId、块导出用 jobId），OpenAPI 的 composeAnswer 等名称是操作类型。两者分开匹配。Java 创建受委托工作时保存 actor、project、业务 UUID、允许动作与对象、授权 revision 和到期时间。私钥只留在签发服务。

| 方向                     | 服务令牌 iss → aud                | 委托和资源约束                                                                                                                                   |
| ------------------------ | --------------------------------- | ------------------------------------------------------------------------------------------------------------------------------------------------ |
| Java → composeAnswer     | testops-server → testops-ai       | Java 签发问答委托，operationId 等于 scope.turnId；Python 验项目、有效期、scopeDigest 及 allowActions 含 composeAnswer。                          |
| Python → resolveEvidence | testops-ai → testops-server-tools | Python 用同一 UUID 签 callback_token，并原样回送 Java 的问答委托；Java 验 allowActions、turn 所有人、实时 authRevision、候选引用所属已授权对象。 |
| Java → exportIndexBlocks | testops-server → testops-ai       | Java 签发只含 exportIndexBlocks 的索引委托，operationId 与 URL 的 jobId 一致；版本 ID、项目、原件 hash 必须匹配成功作业。                        |

新内部端点专用的委托 claims 包含 iss=testops-server、aud=testops-server-tools、iat/exp/jti、operationId、actorId、projectId、authorizationVersion、allowActions。问答委托额外含 scopeDigest，为 UTF-8 JSON 数组 `[projectId,turnId,authRevision,sortedVersionIds,sortedRunIds,sortedAttachmentIds]` 的 SHA-256 小写 hex；各列表去重按 ASCII 字符串升序，紧凑序列化，不含空白。块导出委托额外含 versionId/contentHash。使用与 v1 相同的 RS256 验签材料和生存期，v2 单独建 claims 类型，不改变旧 parser/对象服务所接受的 v1 DTO。长任务由 Java 验权后重新签发，不延长过期 token 的有效期。

composeAnswer 携带需读取原文件的 files 时，Java 同时用 X-Object-Delegation-Token 提供现有 v1 对象委托（与回调 service.operationId 相同，只列本次允许的 objectIds）。Python 读取原件时把该值作为旧对象接口的 X-Delegation-Token；文件为空则无需这个头。原件 hash、大小及归属错误立即丢弃。J2/J5 验一次正确往返，以及交换两个作业令牌、撤权后重放和用问答令牌读取未列出的原件。
