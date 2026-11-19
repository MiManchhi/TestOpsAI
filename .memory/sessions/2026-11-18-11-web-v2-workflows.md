# Web 导入与运行工作流

更新：2026-11-18。状态：VERIFIED（Web 类型/lint/75 测试/生产构建与隔离浏览器范围；真实 API 未联调）。

<!-- handoff:v1 -->

Task: WEB-V2-002
Role: web
Baseline: codex/web-auth 424f547614de0298e2ce7aaf25b833949ee50fff
Contract: docs/contracts/README.md 1.0.0、public.openapi.json、schemas.json、pages.json、docs/PRODUCT_DESIGN.md
Scope: 用户明确继续且只负责 Web，按契约开发；修改 apps/web 与 Web 记忆，不改 Java/AI/公共契约/infra，不提交推送

## 本批目标

- 先完成扩大计划前四项中的 Web：消除浏览器运行时 schema 编译、导入任务与历史、运行列表/结果/产物、真实契约概览、项目成员管理。
- 后端能力不作为 Web 实现前提，不迁就裸数组、不生产回退示例；真实接口联调单独标记，后续知识库与 AI 页面仍按计划分批接续。
- 新增 imports/runs feature 分别承载真实领域职责，复用现有认证、请求错误、项目上下文和冻结视觉。

## 验证

<!-- checks -->

- 起始 Restore/status 通过；已有 CURRENT 和计划 session 未提交，保留。
- 计划执行 Web type/lint/test/build、契约检查、浏览器桌面/窄屏/键盘/异常/项目切换与严格 CSP 校验；完整门禁如实记录环境限制。

<!-- next -->

- 本批页面与契约消费已实现；知识库/AI 保留后续批次，未提交推送或真实 API 联调。

## 实施结果与取舍

- 新增 imports/runs feature：目录文件与逻辑路径、完整快照确认、运行上下文 JSON multipart、任务状态轮询与错误/计数/版本、历史分页；运行完整性筛选、结果精确筛选、只读失败信息、产物分页和日志行定位。HTML 产物不执行，导入不执行关键字或连接设备。
- 工作台消费 getOverview，不生成假统计；项目页消费详情/创建/更新及成员分页/角色/撤销。OWNER 操作在 UI 按角色显示，所有者不提供降级/删除入口，授权仍由服务端确认。
- 复用认证请求，上传采用 120 秒有界超时；同一意图失败重试复用 UUID，新输入/项目生成新键。页面请求随条件/项目/身份切换同步取消与清空，旧结果不能回写。新建项目入口在无项目/项目 API 失败时也可访问。
- apps/web/src/app/contract-build.ts 仅在 Vite/Vitest 的 Node 阶段消费锁定 Ajv 6 的 sourceCode/refVal，输出静态函数；浏览器不引入 Ajv 编译器，不需要 unsafe-eval。不升级依赖、不改 Nginx 或冻结 schema。初验发现生成器遗漏 enum 所需 schema 和一处数组形式 refVal，补齐后以原 Ajv 对照及禁止字符串代码生成的 VM 回归通过。
- 保持冻结布局和青绿色视觉；增加统一请求状态/分页、导入文件核对、原生确认 dialog、表格局部滚动与移动布局。浏览器发现 HTML pattern 的 v 模式不接受未转义连字符，改用等价交替写法；下拉框补精确无障碍名称。
- 范围复核：只改 apps/web 和 Web 记忆；不改 server/ai-service/infra/公共契约/根锁或主线工作区。

## 验证结果与限制

- npm run check --workspace @testops/web 退出 0：strict 类型、ESLint 零警告、14 个文件 75 项测试、生产构建通过；新增测试覆盖 multipart、路径/大小/重复、资源与分页绑定、项目切换旧响应、成员操作及 CSP。末次仅修 HTML pattern/新建成功提示和排版，重建并定向复核。
- npm run contracts:check 退出 0：109 schemas / 65 operations / 827 refs / 15 pages / 45 examples 及拒绝输入/SSE 序列。
- 隔离 Playwright 会话 webflows，生产 dist 通过临时 HTTP 服务返回严格 script-src 'self'，未改现有部署；自造契约 HTTP 替身验证目录/结果导入、任务轮询/计数、历史分页、运行空筛选/FAIL/证据、只读文本、成员编辑/撤销与 Escape、新建项目、项目切换清理、VIEWER 控件、403 恢复、断网保留输入/同键重试。375px 四页无整页溢出，768px 检查、移动抽屉 inert/Escape、减少动效场景通过；截图位于忽略的 .runtime/playwright-web-workflows，并已检查。
- 初次浏览器因沙箱用户目录无法发现 Chrome、沙箱临时服务对本机浏览器不可达，改用同一用户环境的隔离 Chrome 与临时回环 HTTP 服务后完成；未安装新浏览器。初次脚本下拉定位不精确后补无障碍名称并通过。
- 真实 API 未联调；本轮按用户限定只负责 Web，不实施其他端、部署或发布。Vite 保留未来 native config loader 的扩展名/JSON 属性提示，当前构建与 lint 均通过。
- 最终 scripts/check.ps1 退出 1：44 条记忆、目录/记忆回归、14 份 PowerShell 语法通过；格式输出已匹配文件合规，但遍历既有 apps/ai-service/.pytest_cache 时 EPERM，中断于 npm run format:check，未进入其后全栈步骤。本轮不处理非 Web 缓存、不声明完整门禁通过。独立 Web check 已退出 0，最终 HTML pattern 修复后重建及浏览器原生有效/无效输入复核通过；定向 Web/记忆格式和导航另验。
- 最终未提交变更限 apps/web、CURRENT Web 区块、Web 模块及两份本轮/上轮计划 session；无根锁、公共契约或后端代码变化。临时浏览器会话与回环 HTTP 服务收尾关闭，截图/脚本仅在忽略目录。

## 追加发布授权

2026-11-18：用户明确要求提交并推送当前 Web 成果，但不合并，然后继续知识库与 AI 页面开发。本次仅发布 codex/web-auth，保留原门禁限制；前述不提交推送为实施阶段历史。下一批开发从本发布检查点继续，不修改 main 或其他端。
