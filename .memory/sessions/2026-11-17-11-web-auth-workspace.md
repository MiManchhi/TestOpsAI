# WEB-001 登录与导航工作台

更新：2026-11-17。状态：VERIFIED（最终本机门禁、浏览器与三端冒烟；未提交）。

<!-- handoff:v1 -->

Task: WEB-001
Role: web
Baseline: 101596d03adb8145e24aafd5a6f96632401720e3
Contract: docs/API.md，既有 Java M1.2 登录与当前用户接口
Scope: 用户授权实施已确认计划；apps/web、依赖锁、接口说明及排版规范兼容说明、本任务 session 与 Web 记忆；不提交、推送或合并主线。

## 目标与起始核对

- 2026-11-17 / Asia/Shanghai；codex/web-auth，工作区 TestOpsAI-web；HEAD 101596d，与本地主线无差异，起始工作树干净。
- 已恢复规则、规范、模块和相关历史 session；目标是中文登录闭环与概览/知识库/测试用例导航工作台。
- sessionStorage 保持标签页会话；刷新验证当前用户；JWT 过期、401、退出清理；业务占位明确待开放。
- 本工作区无依赖与工具链，使用同机已有锁定 Node 22 工具，依赖及缓存独立；不复制凭据。
- 过程更新：用户随后配置本工作区 .tools，已核实 Node 22.23.5/npm 11.6.4/JDK 21.0.13.0.2/uv 0.13.8/Python 3.12.16，后续检查切换本工作区工具；不覆盖用户配置。

## 实施与选择

- Vue Router 4.6.3 用于真实导航；认证/工作台 feature 承载实际职责，域内平铺；不引入 Pinia 或大型 UI 库。
- 公共文件变更需求：根 package-lock 随工作区依赖更新，docs/API 补齐已有 M1.2 接口，均限定本计划范围，不改 Java/AI/Compose/CI。
- 界面使用浅色青绿工作台、统一控件和有限过渡，键盘、小屏与减少动态效果纳入验收。
- auth/session 实现请求取消与 generation 防旧响应、10 秒请求期限、运行时响应校验及会话恢复。工作台 overview 展示真实账户/角色/创建时间和 Java health；其余入口待开放。
- Vue 表单/行内模板使既有 ESLint 排版规则与 Prettier 实际冲突；使用官方 eslint-config-prettier/flat 10.1.8 负责兼容，不关闭语义检查，更新 CODE_STYLE 对应说明。来源：[官方说明](https://github.com/prettier/eslint-config-prettier)。
- 修正移动控件 CSS 优先级、抽屉过渡后的焦点进入、桌面切换背景解除锁定；退出导航由 App 统一处理，避免两个 replace 竞争。
- 图标/品牌/装饰为自制 SVG/CSS，无外部图片字体请求。新增目录仅 auth 与 workspace，实际承载对应功能；favicon 放现有 app，未放宽目录白名单。
- 临时执行策略与 Git safe.directory 仅当前进程生效；浏览器/容器只读探测使用获自动审批的提权，未更改用户全局配置。临时脚本、截图、工具缓存与产物均忽略。

## 真实验证

<!-- checks -->

日期均为 2026-11-17 / Asia/Shanghai；目录 TestOpsAI-web，适用 101596d 上本任务未提交文件。

| 命令/检查                                                       | 结果                  | 范围与限制                                                                                                                                           |
| --------------------------------------------------------------- | --------------------- | ---------------------------------------------------------------------------------------------------------------------------------------------------- |
| Restore -ListOnly、memory Check                                 | 0                     | 初始 24 份，新增 session 后 25 份；角色 web，未 fetch 远端                                                                                           |
| npm ci；npm run check --workspace @testops/web                  | 0                     | 锁定依赖；类型、零警告 lint、37 测试、生产构建                                                                                                       |
| scripts/check.ps1（通过临时环境入口）                           | 0，最终代码文件集通过 | 目录/记忆/格式/工程反例、Web 37 测试、Java 14 项（9 项 DB 测试跳过）、Spotless、Python lock/Ruff/mypy/linux+win32 脚本检查/1 测试/构建               |
| scripts/smoke-local.ps1                                         | 0                     | 本任务独立临时端口启动 Java/AI/Web、健康与代理/401；finally 停止自己创建的进程。既有本机凭据只读注入临时进程，未复制或输出                           |
| Playwright CLI 0.1.24 → 本地 Vite 5173 → 既有 Nginx 8088 → Java | PASS                  | 真实错误/正确登录、角色、刷新恢复、服务连接、Enter、密码显隐、错误聚焦、仅令牌/到期时间存储                                                          |
| 浏览器 session 专项                                             | PASS                  | 重复提交、断网恢复、403 保留会话重试、401 清理、过期存储清理、减少动效下抽屉焦点、退出/返回保护；403/401/断网使用浏览器定向拦截，其余登录走真实 Java |
| 浏览器 navigation 专项                                          | PASS                  | 抽屉焦点循环/Escape/inert、切换桌面解除锁定、导航/前进后退/深链接刷新；375/768/1440 无水平溢出，三尺寸截图已查看                                     |

历史失败与修正：初次 ESLint 从根调用未发现应用配置，改为 workspace 命令；首次 workspace-only 安装不含根 Prettier，完整 npm ci 后解决；重复使用 Response 的测试夹具消费 body、相同路由导航被去重，修正夹具和真实测试步骤后 37 项通过。首次 favicon public 被目录门禁拒绝，迁入现有 app；完整门禁首次 Git safe.directory 反斜线不匹配，改进程级正斜线路径；Maven Wrapper 首次网络下载失败，准备本机忽略缓存后 verify 成功。浏览器沙箱不能访问用户启动的隔离会话，后续保持同一获批执行身份；脚本 logout URL 从精确无查询改为 pathname 断言，实际保留原目标符合登录返回行为。

Java MockBean/agent 和 Python Starlette TestClient 弃用提示保留；未处理无关依赖升级，不把跳过的 DB 测试算作通过。没有提交新容器、远端 CI 或生产部署证据。

## 交接

<!-- next -->

- VERIFIED；最终完整门禁退出 0，Prettier 冲突检查与 git diff --check 通过。当前无代码提交，未合入主线。
- 下一步：用户审查本工作区改动；后续业务页面依赖 Java M1.3 契约。提交/推送与主线合并仍需具体授权。
- BLOCKED：无。本地预览 5173 代理既有 Nginx 8088；测试浏览器退出后不留登录态。未复制 .env，不改变既有容器。
- [CURRENT](../CURRENT.md)、[Web](../modules/web.md)。

## 后续发布

- 用户随后明确授权提交并推送；本 session 的“未提交/不推送”描述原实施阶段，不继续限制本次已授权发布。发布版本与真实结果见 [发布 session](2026-11-17-12-web-auth-publish.md)。
