# 跨服务验证

各应用单元/接口测试就近放在应用中；M0 容器冒烟入口为 scripts/smoke.ps1。

`engineering/check-layout.tests.ps1` 验证目录与记忆导航；`engineering/container.tests.ps1` 验证真实 Compose 六服务、运行用户、重复启动、保留卷停启和停止 AI 的失败检测。后者会短暂停止再恢复本项目服务，不删除本机数据卷，作为独立容器交付门禁。本机操作和 CI 隔离见 [容器指南](../docs/development/CONTAINERS.md)。

M2 引入真实浏览器 E2E，M3 引入权限隔离与引用评测，M4 引入受控工具契约验证。测试夹具仅用自造数据；避免创建永远通过的占位测试。
