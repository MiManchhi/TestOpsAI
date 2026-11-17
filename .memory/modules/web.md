# Vue 模块

更新：2026-11-17。状态：VERIFIED（WEB-001 本机检查与浏览器范围，Web ad87e97 已发布，main 4cc2d1c 已合并并推送）。

[app](../../apps/web/src/app/App.vue) 组装会话状态与页面，main.ts 注册 Vue Router；style.css 管基础视觉与登录页，workspace.css 管工作台。auth feature 内聚合登录页、API、会话及测试；workspace feature 承载导航布局、概览与待开放说明；[health](../../apps/web/src/features/health/HealthStatus.vue) 展示真实 Java 健康检查。

按业务域组织，域内平铺。Vue Router 4.6.3 服务实际导航，认证使用 Vue 响应式模块；没有 Pinia、大型组件库或通用 HTTP 框架。AppIcon 在登录和工作台真实复用，图标与装饰为本任务自制 SVG/CSS。

Java 业务入口为 POST /api/v1/auth/login 与 GET /api/v1/users/me。浏览器仅在 sessionStorage 存 accessToken/expiresAt，不存密码/用户缓存；刷新向 Java 验证身份后才展示工作台。401/过期/退出清理会话，403 或临时网络错误隐藏工作台并提供重试；请求取消和版本号防止旧响应恢复退出状态。无刷新令牌与服务端 JWT 撤销能力。

History 路由：/login、/overview、/knowledge、/test-cases。后两页只显示待开放说明；权限由 Java 判断，路由守卫不代替服务端授权。移动导航支持焦点循环、Escape/遮罩关闭及焦点返回，切至桌面解除背景 inert；支持 prefers-reduced-motion。

ESLint 使用 eslint-config-prettier 10.1.8 消除真实排版冲突，保留 Vue/TS 语义检查与零警告。Vite 默认代理 Java 8080；可用进程变量 TESTOPS_SERVER_URL 指定其他后端，本次浏览器使用既有 Nginx 8088 入口。

基于 101596d 实现并发布为 ad87e97 的 WEB-001：37 项 Vitest、类型/lint/构建、真实 Java 浏览器登录和三尺寸交互、完整 scripts/check.ps1 与本机三端 smoke 均通过；Java 9 项数据库测试跳过，完整结果及历史失败见 [session](../sessions/2026-11-17-11-web-auth-workspace.md)。浏览器脚本和截图仅在忽略的 .runtime；未新增 CI 浏览器 E2E job，已发布 Web 分支，未部署新容器。既有依赖审计及弃用提示未声称修复。
