# Vue 模块

2026-11-18 WEB-V2-002：VERIFIED（仅 Web 与隔离浏览器）。新增 imports/runs 工作流、真实契约概览、项目/成员管理；build-time Ajv 静态函数替代浏览器编译，严格 CSP 可挂载。共享 resource 管请求取消/旧响应，上传重试保持请求键，生产无示例回退；75 测试/类型/lint/构建与契约检查通过。未提交推送，真实 API 未联调；后续知识库/AI 仍 PLANNED。实现范围/浏览器证据见 [session](../sessions/2026-11-18-11-web-v2-workflows.md)。

2026-11-18 主线集成：合并提交 a6a5fad 已普通推送 main，保留最新 Java/AI/学习记录。用户明确已验证、要求直接合并，停止追加门禁；新 CI 未核实，真实 v2 联调阻塞保持。以后明确要求推送或合并时直接执行，遇冲突、远端分歧或未提交工作则保留现场说明。详见 [集成 session](../sessions/2026-11-18-07-web-v2-main-merge.md)。

2026-11-18 发布 VERIFIED：用户追加授权提交推送 WEB-V2-001、暂不合并。功能 c74c03b、最小 Docker 上下文修复 35b5a59 已普通推送 Web；[CI 44256756644](https://github.com/MiManchhi/TestOpsAI/actions/runs/44256756644) 五项全成功，含 Web 59 测试和六服务构建/健康/恢复/隔离清理。首次失败来自镜像未复制 schemas/examples JSON，修复仅增加现有 .dockerignore 两文件白名单与 Web Dockerfile COPY，没有改契约或合并主线；本机 Docker 引擎未运行，未本机部署。收尾仅补发布记忆，最终 tip/CI 以 refs/Actions 为准。以下“未提交”保留开发阶段事实，真实 v2 联调阻塞不因此解除。

2026-11-18 / WEB-V2-001：IMPLEMENTED；[切片交接](../sessions/2026-11-18-03-web-v2-catalog.md) 记录最终 59 测试、契约与隔离浏览器验证。projects feature 管授权项目上下文；test-cases feature 管分页套件/用例查询、精确过滤、选择与只读详情。app/contracts.ts 类型从冻结 schema 提取并以同源 Ajv 验证；app/http.ts 经 auth.getAccessToken 获取验证后的短期令牌、处理取消/超时/401 与旧会话响应。项目选择以 URL projectId 表示，无权/歧义深链接不自动切换数据；切换项目或退出清理内容与选择，FILTER 快照由 Java 确认，网络重试复用请求键。测试示例只在 test-data.ts 与忽略目录，生产没有回退。无新增依赖、公共契约或后端变更；未提交推送。收尾新 main 1cbe62e 未同步；其项目列表静态返回数组与冻结 ProjectPage 不符，v2 套件/用例/选择仍待交付，真实联调受阻。以下为设计与 WEB-001 的历史范围，不能外推为本片真实后端已联调。

2026-11-17 最终设计更新：[PRODUCT_DESIGN](../../docs/PRODUCT_DESIGN.md) 与 [15 页面原型](../../docs/design/index.html) 取代 V0.1 三方向探索。工作台聚合运行/资料/待办；AI 助手专门承担问答/引用/草稿；登录、知识库、Robot 用例、运行、导入、管理均有完整设计。Web 仅消费 [public.openapi](../../docs/contracts/public.openapi.json)，页面 operationId 映射见 pages.json；新业务 v2，认证沿用 v1。生产 Vue 未替换，本轮产出为最终设计与跨端契约。

2026-11-17：用户明确产品面向智能电表测试；[总体页面设计](../../docs/WEB_UI_DESIGN.md) 已产出（IMPLEMENTED，仅设计材料，待评审），包含四项导航、领域上下文、AI 问答/来源阅读/草稿保存及三张视觉稿。业务前端与 AI 能力没有新增；后续从 [WEB-002-DESIGN](../sessions/2026-11-17-13-web-ai-design.md) 恢复，不把图片当运行证据。

更新：2026-11-17。状态：VERIFIED（WEB-001 本机检查与浏览器范围，Web ad87e97 已发布，main 4cc2d1c 已合并并推送）。

[app](../../apps/web/src/app/App.vue) 组装会话状态与页面，main.ts 注册 Vue Router；style.css 管基础视觉与登录页，workspace.css 管工作台。auth feature 内聚合登录页、API、会话及测试；workspace feature 承载导航布局、概览与待开放说明；[health](../../apps/web/src/features/health/HealthStatus.vue) 展示真实 Java 健康检查。

按业务域组织，域内平铺。Vue Router 4.6.3 服务实际导航，认证使用 Vue 响应式模块；没有 Pinia、大型组件库或通用 HTTP 框架。AppIcon 在登录和工作台真实复用，图标与装饰为本任务自制 SVG/CSS。

Java 业务入口为 POST /api/v1/auth/login 与 GET /api/v1/users/me。浏览器仅在 sessionStorage 存 accessToken/expiresAt，不存密码/用户缓存；刷新向 Java 验证身份后才展示工作台。401/过期/退出清理会话，403 或临时网络错误隐藏工作台并提供重试；请求取消和版本号防止旧响应恢复退出状态。无刷新令牌与服务端 JWT 撤销能力。

History 路由：/login、/overview、/knowledge、/test-cases。后两页只显示待开放说明；权限由 Java 判断，路由守卫不代替服务端授权。移动导航支持焦点循环、Escape/遮罩关闭及焦点返回，切至桌面解除背景 inert；支持 prefers-reduced-motion。

ESLint 使用 eslint-config-prettier 10.1.8 消除真实排版冲突，保留 Vue/TS 语义检查与零警告。Vite 默认代理 Java 8080；可用进程变量 TESTOPS_SERVER_URL 指定其他后端，本次浏览器使用既有 Nginx 8088 入口。

基于 101596d 实现并发布为 ad87e97 的 WEB-001：37 项 Vitest、类型/lint/构建、真实 Java 浏览器登录和三尺寸交互、完整 scripts/check.ps1 与本机三端 smoke 均通过；Java 9 项数据库测试跳过，完整结果及历史失败见 [session](../sessions/2026-11-17-11-web-auth-workspace.md)。浏览器脚本和截图仅在忽略的 .runtime；未新增 CI 浏览器 E2E job，已发布 Web 分支，未部署新容器。既有依赖审计及弃用提示未声称修复。
