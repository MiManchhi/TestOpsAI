# Java 模块

更新：2026-11-16。状态：VERIFIED（基础骨架范围）。

入口：[启动类](../../apps/server/src/main/java/io/testopsai/server/TestOpsApplication.java)，Java 21 / Spring Boot 3.5.16，依赖与插件固定在 [pom](../../apps/server/pom.xml)。

跨域安全配置归 [platform/security](../../apps/server/src/main/java/io/testopsai/server/platform/security/SecurityConfiguration.java)，未来业务归 modules/业务域，先平铺后按真实职责拆分，不建空包。测试对应源码包。

当前健康公开、其他路径默认拒绝；默认用户自动装配已排除。迁移后根目录直接调用 Wrapper 已 verify 成功，2 项边界测试通过；maven.ps1 提供统一入口。新的完整门禁和 clean 重建证据见 [本次 session](../sessions/2026-11-16-04-engineering-layout.md)。

MySQL、JWT、RBAC、迁移与业务审计尚未实现；进入 M1 时再引入，不因工程骨架可运行就宣称业务完成。
