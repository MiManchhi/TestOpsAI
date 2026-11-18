# Java、Web、AI 并行开发开工指南

更新：2026-11-18。状态：IMPLEMENTED（任务划分与操作指南；以下业务任务均 PLANNED，需在各会话明确启动）。适用契约版本 1.0.0。

## 1. 三个工作区、三个会话、一个集成入口

| 工作区（仓库同级） | 现有分支           | 首批任务                                                | 写入归属                                                      |
| ------------------ | ------------------ | ------------------------------------------------------- | ------------------------------------------------------------- |
| TestOpsAI-server   | codex/server-m1    | SERVER-V2-001：项目/成员授权与 v2 用例精确查询基础      | apps/server、Java 模块记忆、CURRENT Java 区块、自己的 session |
| TestOpsAI-web      | codex/web-auth     | WEB-V2-001：正式 Vue 布局与用例/项目页面，按契约接入    | apps/web、Web 模块记忆、CURRENT Web 区块、自己的 session      |
| TestOpsAI-ai       | codex/ai-ingestion | AI-PARSER-001：Robot 定义与 output.xml 的确定性静态解析 | apps/ai-service、AI 模块记忆、CURRENT AI 区块、自己的 session |
| TestOpsAI          | main               | 契约协调、跨端验收、发布与后续联调                      | 公共契约、根锁、Compose/CI、全局证据                          |

每个目录只开一个会写文件的活跃执行器。不要让三个会话同时写主目录。可以同时工作，但不是同时开发整张路线图。

## 2. 先同步，再分配任务

本次发布前 worktree 的代码版本不同。每个开发会话开场应先读规则、核对未提交工作，确认本轮设计/契约已经在自己的分支；不能仅凭主目录已更新就开始。

工作区干净且分支没有独有提交时，可在该工作区运行：

```powershell
git status --short --branch
git fetch origin
git merge --ff-only origin/main
```

以上是把主线带入开发分支，不是把业务合回 main。用户在对应会话明确“先安全同步 origin/main”即可授权该步骤。若有未提交工作或 ff-only 被拒绝，先汇报具体差异，保留工作；不要自动 stash/reset/clean、丢弃提交或强行覆盖。共享 Git refs，不共享工作区文件。

同步后执行 `scripts/memory.ps1 -Mode Restore -ListOnly`，按清单读取；共同阅读：

- [最终产品与页面设计](../PRODUCT_DESIGN.md)
- [三端冻结契约](../contracts/README.md) 与本领域 OpenAPI
- [领域与迁移模型](../DOMAIN_MODEL.md)
- [页面/操作映射](../contracts/pages.json)、[共享类型](../contracts/schemas.json)
- [Robot 领域背景](../references/2026-11-17-robot-framework-background.md)

## 3. 第一批可以真正并行的三个任务

**Java：先交付稳定业务事实。** 建立 v2 项目与成员隔离、前进式迁移、字符串 ID/数组标签适配、项目内套件/用例读取和选择快照基础。首个检查点以项目授权 + 精确查询为界，覆盖跨项目拒绝、分页/标签/版本及与旧 v1 的兼容。旧用例迁移必须明确归属，不能直接开放给所有成员。Robot 解析尚未接好时用本领域受控测试夹具验证查询，不在生产代码返回假数据。

**AI：先做无需模型和数据库的解析。** 实现 Robot 定义和 output.xml 的受限静态解析及测试，输出契约 RobotParseResult/ResultParseResult。解析器不执行关键字、加载 Python 库或接真实设备；测试包含标签、套件/同名用例、非法路径、外部资源、XML 实体、无效结果及统计一致性。授权对象读取的 HTTP 适配依赖 Java 对象接口，先隔离为适配边界，不发明新的跨端字段，也不把尚未接通的内部 HTTP 标为已联调。

**Web：将最终设计落实为正式 Vue。** 复用已验证登录/会话机制，落实新导航和页面结构，先完成项目上下文、套件树、用例筛选/选择/详情。响应严格匹配 v2；可以用契约示例驱动开发和测试，模拟入口必须显式隔离，生产请求失败不能悄悄回退示例数据。Java 切片可用后切换真实联调，区分页面完成与接口联调完成。不要复制 docs/design 的内存演示逻辑作为生产业务实现。

这三个首任务分别解决业务查询、解析能力与展示交互，彼此有明确接口，能够同步推进；本批不同时铺开 RAG、任务执行或全部 65 个操作。

## 4. 可以直接粘贴的开场指令

在对应工作区分别新开会话，粘贴一段。下列指令授权启动该任务与安全同步，不自动授权提交/推送、主线合并或改公共契约。

### Java 会话

```text
先按 AGENTS 恢复并核对 Git，保留未提交工作，安全同步最新 origin/main；若不能快进先汇报差异。读取 docs/development/PARALLEL_DEVELOPMENT.md，启动 SERVER-V2-001：按冻结契约 1.0.0 实现 v2 项目/成员授权和项目内用例精确查询基础。修改 apps/server 及本领域记忆，分小检查点验证；不重定义公共 DTO，不实施 AI 业务，不提交推送。完成后交付接口、迁移和真实测试结果，标明 DB 跳过项。
```

### AI 会话

```text
先按 AGENTS 恢复并核对 Git，保留未提交工作，安全同步最新 origin/main；若不能快进先汇报差异。读取 docs/development/PARALLEL_DEVELOPMENT.md，启动 AI-PARSER-001：按冻结契约实现 Robot 与 output.xml 的确定性静态解析及安全边界测试。修改 apps/ai-service 及本领域记忆；不执行关键字、不接企业设备、不直接写 Java 表，不重定义公共 schema，不提交推送。先交付可独立验证的解析结果，明确对象读取接口尚待联调的部分。
```

### Web 会话

```text
先按 AGENTS 恢复并核对 Git，保留未提交工作，安全同步最新 origin/main；若不能快进先汇报差异。读取 docs/development/PARALLEL_DEVELOPMENT.md，启动 WEB-V2-001：按最终设计落实正式 Vue 布局和项目/Robot 用例页面，复用既有认证；使用 public.openapi 和 pages.json 的 v2 字段。修改 apps/web 及本领域记忆，开发示例与生产请求明确隔离，不重定义契约、不提交推送。做键盘/窄屏和筛选选择验证，分别报告页面完成与真实 API 联调状态。
```

如果你希望各会话完成后自行发布，在对应会话另外明确“允许为本任务提交并推送当前开发分支”；不要因此授权它合并 main。

## 5. 怎样防止三边跑偏

- docs/contracts、ADR、公共 API 文档、根 package-lock、Compose、CI 和跨服务测试由 main 集成会话协调。需要改变时，开发会话提供字段/路径/原因/影响提案，集成方先更新基线并发布，再由三边同步。
- 各方提交自己的 CURRENT 区块与 session。记忆冲突按区块和事实合并，不整份选 ours/theirs。不同工作区的未提交进度不会自动传播。
- 每个检查点执行 `npm run contracts:check` 与本应用检查；完整交付执行 `scripts/check.ps1`。schema 检查通过不等于 provider 行为正确；Java 需真实 HTTP/权限测试，AI 需解析/安全测试，Web 需浏览器验证。
- 每次报告包含实际 SHA、改动范围、operationId、测试/跳过项、未完成项和下一步。不能只说“完成了”。
- 端口、测试库、Qdrant collection 和 Compose 项目隔离。初期完整六服务只由主线统一联调；不要在三个工作区同时对同一个有数据的库跑写入测试，不复制凭据到 Git 或记忆。

## 6. 合并与下一批顺序

用户审查并合并各开发分支；集成会话可以准备和验收，不擅自替用户合并。优先把 Java 项目/查询基础合入，再把 AI 解析切片与 Java 导入编排接通，Web 随后接真实 API；各自独立测试可以此前并行完成。

第一个共同验收闭环：自造 Robot 定义 → 解析 → Java 索引 → 标签/套件精确查询 → Vue 列表/选择一致。通过后，再推进结果导入 → 运行详情 → AI 受控查询；知识上传/引用/RAG 与会话/SSE 按冻结契约接续。这样每批都有可演示结果和清晰失败边界。
