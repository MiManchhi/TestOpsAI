# Web 知识库与 AI 协作

更新：2026-11-19。状态：VERIFIED（Web 与隔离 HTTP/SSE 浏览器范围；真实 API/模型未联调）。

<!-- handoff:v1 -->

Task: WEB-V2-003
Role: web
Baseline: codex/web-auth ed8827253f5fbc73792ef333fe7ea5e68df0803a
Contract: docs/contracts/README.md 1.0.0、public.openapi.json、schemas.json、pages.json、docs/PRODUCT_DESIGN.md
Scope: 用户要求先提交推送已有 Web、不合并，然后继续；已普通推送 WEB-V2-002，当前仅开发 apps/web 和 Web 记忆中的知识库、AI 会话和人工草稿，不改其他端/契约/infra，不合并 main

## 发布检查点

- WEB-V2-002 已提交 ed88272 并普通推送 origin/codex/web-auth；ls-remote SHA 一致，推送后工作树干净。未合并或更新 main，保留此前完整门禁的 AI 缓存 EPERM 限制，没有追加重复验证延迟推送。
- 本地 main 比上一开发基线新增 3 提交；只读核对冻结契约、设计和规范没有差异，不自动合并主线或处理其他端。

## 本批计划

1. 按契约补 Web 类型/构建期校验，知识库列表/详情/文档、访问权限与引用阅读。
2. AI 会话/消息与认证 fetch SSE，流分片、重放、去重、停止和终态持久数据恢复。
3. 人工草稿编辑、选中项确认和同键重试；业务入口显式带上下文，不自动提问。
4. Web/契约/严格 CSP 浏览器验收与记忆更新；区分页面完成与真实 API 联调。

## 验证与风险

<!-- checks -->

- 起始 Git/Restore 和规范/设计/契约读取完成；新功能测试待执行。
- UI 保持冻结青绿工程布局；引用读取实时重新鉴权，不能从旧事件摘录绕过权限；完成前不开放业务保存。
- SSE 必须核对 turnId/sequence/event，禁止 JWT URL；断网与主动停止区分，恢复不得重复发问。

<!-- next -->

- 当前从冻结 schema/API 开始实施；新业务未提交、未推送、未部署或真实联调。

## 实施检查点

- 已实现 knowledge 三页与实时引用抽屉、assistant 会话/上下文/受控 Markdown/认证 SSE、drafts 人工确认页面及 API；已接入导航和运行/所选用例/知识入口，尚待新增 API 测试和完整浏览器验收。
- WEB-V2-002 发布 CI 44326861254 已 completed/success（对应 ed88272），仅为上一批已发布源码证据。
- 当前新代码初步类型/lint/生产构建通过；新增 SSE 和会话回归后 86 测试通过，知识与草稿 API 测试随后添加，完整 Web check 正在执行，日志 .runtime/web-collaboration-check.log。
- 构建校验器改为共享引用函数且只附必要 schema；新页面改为懒加载，需核对最终构建大小与严格 CSP。
- 下一步：处理 Web check 如有失败，启动隔离浏览器生产 dist + 契约 HTTP/SSE 替身，验收资料上传/状态/授权/引用撤权、上下文不自动提问、停止/重连、草稿修订与幂等保存、项目切换和移动焦点。不合并或推送新批源码。

## 最终交接（2026-11-19 / Asia/Shanghai）

- WEB-V2-002 已完成提交与普通推送 ed8827253f5fbc73792ef333fe7ea5e68df0803a，远端同 SHA；CI 44326861254 completed/success。用户要求不合并，main 未被修改。本条发布证据仅对应上一批。
- WEB-V2-003 已实现知识库列表/创建/设置/访问授权、Markdown/PDF 上传与就绪状态/重试/删除、正文游标分页/引用定位、AI 本人会话/上下文/消息分页、认证 fetch SSE、实时引用抽屉、人工草稿编辑与确认保存。业务页带显式范围，不自动提问；生产无模拟回退。
- SSE 核对 turnId/event/sequence，任意 UTF-8 分片与 CRLF、心跳/去重/断线重放有回归；取消与完成竞争、事件过期和已完成幂等消息从持久化记录恢复，不重发问题。权限撤销/项目切换清空旧内容，引用每次读取重新鉴权；草稿使用服务端 revision/hash，明确选择并人工确认，同键重试，后续已提交请求显示原保存结果。Markdown 为受控文本渲染，不执行 HTML/外链/图片。
- npm run check --workspace @testops/web 最终退出 0：18 文件/94 测试，strict 类型、ESLint 零警告、生产构建通过；新增知识/草稿 API、SSE/会话回归。npm run contracts:check 退出 0（109 schema/65 operation/827 refs/15 pages/45 examples 与拒绝/SSE）。收尾仅补上下文错误自动展开属性和排版，另跑构建/定向浏览器复核。
- 隔离会话 webcollab 在严格 script-src self 的生产 dist 下使用自造 HTTP/SSE 替身：知识库搜索/创建、文档上传/状态轮询/失败重试/删除、正文分页、限制库授权/撤销；打开上下文不发问、主动停止、Last-Event-ID=3 恢复且不重复 POST、引用撤权隐藏来源、草稿修订后服务端新 hash/版本、断网同键重试保存和实际 case 链接通过。IME Enter 不发送；项目切换隐藏旧问题/答案；375px 五页、768px、来源抽屉 Escape、VIEWER 不可编辑/提交通过。截图在忽略的 .runtime/playwright-web-collaboration，已检查。
- 浏览器跨日继续时原临时进程已结束，重新启动隔离会话并从全新替身状态复验；授权撤销断言先前等待不足，改为等待 UI 刷新后通过。未据此修改服务端代码。
- 新增 knowledge/assistant/drafts 目录均有实际职责；复用上批请求/取消/分页/错误处理。构建期 Ajv 仍为锁定版本，类型/静态函数消费冻结 schema；路由懒加载后登录入口约 119 kB（gzip 46 kB），共享校验上下文约 626 kB（gzip 46 kB），保留大 chunk 与未来 native config loader 提示，未调高阈值或升级依赖。
- 本批只修改 apps/web、CURRENT Web 区块、Web 模块和本 session；新批未提交/推送，未合并 main、未改 Java/AI/infra/公共契约/锁文件，未部署或真实 API/模型联调。此前检查点的待执行文字保留过程时点，以本段为最终状态。

- 2026-11-19 最终完整 scripts/check.ps1 退出 1：45 条记忆、目录/记忆回归、14 份脚本语法通过；全仓 Prettier 扫描既有 apps/ai-service/.pytest_cache 仍 EPERM，未进入后续全栈步骤，未改其他端或绕过门禁。Web 独立 check 退出 0（94 测试），收尾构建退出 0，定向 Web/记忆格式、memory Check、diff 空白与范围另验。
- 最后定向浏览器复核：无效附带上下文会自动展开显示错误，用户可明确清除范围；严格 CSP 保持 script-src self，未加入 unsafe-eval。最终截图 assistant-desktop-final.png 已检查；隔离 webcollab 与临时 1446 回环服务收尾关闭。

## 追加提交与合并授权（2026-11-19）

用户明确要求直接提交并合并，不追加重复验证。本次允许发布 WEB-V2-003 到 Web 后合入最新 main，并普通推送；此前不合并/不提交为当时范围，保留历史。已 fetch 核实最新 origin/main 为 9986ca7，新增 v2 用例 provider；冻结契约、规范和 Web 源码没有主线独有差异。保留已有 94 测试与浏览器证据及完整门禁缓存权限限制，本轮只做 Git/冲突与发布核对。
