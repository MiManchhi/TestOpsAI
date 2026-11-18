# AI 解析分支合入主线

更新：2026-11-18。状态：VERIFIED（本机合并候选完整门禁；主线发布待完成）。

<!-- handoff:v1 -->

Task: AI-PARSER-MERGE-001
Role: integration
Baseline: 1cbe62e27572363cda5daf6c2c17b24138488dd4
Contract: docs/contracts/README.md 1.0.0、docs/development/AI_COLLABORATION.md
Scope: 用户明确要求合并到 main；安全合并 codex/ai-ingestion、集成验收、更新相关记忆、正常提交推送 main；不强推、不部署、不修改其他领域工作区

## 目标与起始核对

- AI 分支 852697e 已发布且干净；首次主线只读核对受沙箱权限限制，获批后读取成功。
- fetch 后远端 main 为 1cbe62e，比 AI 公共基线 17ed8ee 新增 Java M1.5 两个提交；主线工作区已被既有 pull 快进至 1cbe62e 且干净。已重查实际 HEAD/status/reflog、新 Java session/ADR，未覆盖未提交工作。
- 已恢复集成规则与模块；主线新增项目隔离/成员/幂等基础与 V6，AI 新增解析/服务鉴权，两者无源码交叉修改。验收为合并无冲突、保留双方功能、完整门禁与普通 push/远端 SHA 一致。

## 实施与选择

- 在 main 再次核对预期 SHA 和干净状态后执行 git merge --no-ff --no-commit codex/ai-ingestion，退出 0，无冲突，暂存差异检查通过。尚未创建合并提交。
- 保留 V6/Java 项目域、既有 Web 与冻结契约；AI 接口的真实 Java 对象读取/密钥配置联调仍未完成，不将合并当部署。
- 新 Java session 报告此前前端类型检查失败，本轮以主线真实完整门禁结果为准；DB/Redis 跳过项单列。

## 真实验证

<!-- checks -->

- fetch、主线/AI 状态/差异核对、自动合并及 cached diff --check 退出 0。完整 scripts/check.ps1 在主线 1cbe62e + AI 852697e 合并候选上退出 0：37 份记忆、目录与工程反例、14 份 PowerShell 解析、全仓格式与契约（109 schema/65 operation/827 引用/15 页面/45 示例及非法输入/SSE）、Web 类型/零警告 lint/37 测试/构建、Java verify/Spotless（45 项中 40 项 DB/Redis 跳过）、Python lock/Ruff/strict/双平台脚本/64 测试/构建通过。

## 交接

<!-- next -->

- 合并候选 VERIFIED；下一步补记忆、创建合并提交并普通推送 main。AI 源码/锁文件/说明与 852697e 一致，Java 源码/V6/ADR 与主线 1cbe62e 一致；cached diff --check 无空白问题，无未解决冲突。
- BLOCKED：无本次合并阻塞；真实 Java 对象接口/签名配置联调、部署仍 PLANNED，原三端 smoke 数据库限制与 40 项跳过不算通过。本轮不重新运行已知缺 DB 环境的 smoke，不据本机门禁推定远端 CI 或运行健康。
- [CURRENT](../CURRENT.md)、[AI 发布](2026-11-18-04-ai-parser-publish.md)。
