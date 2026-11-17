# Vue 模块

更新：2026-11-16。状态：VERIFIED（基础骨架范围）。

[app](../../apps/web/src/app/App.vue) 负责组装页面，main.ts 启动，style.css 为全局样式。[health feature](../../apps/web/src/features/health/HealthStatus.vue) 内聚合健康组件、请求和测试，前端只访问 Java 入口。

按业务域组织，域内先平铺，真实复用后再提取 shared；不预建路由、Pinia、全局通用 service 等空架子。

类型检查、零警告 lint、3 项请求测试、生产构建已通过。ESLint 只关闭与 Prettier 重叠的两条排版规则，语义规则保留。Vite 开发代理默认 Java 8080；TESTOPS_SERVER_URL 可在进程环境中指定其他后端地址，不暴露给浏览器。

本机 smoke 已验证网页入口及代理；尚无业务 E2E。当前只有欢迎页与连接检查，无登录、知识库、用例页面。依赖审计的一条 minimatch 公告影响 7 个开发工具依赖包，未强制跨主版本升级，详见 CURRENT。
