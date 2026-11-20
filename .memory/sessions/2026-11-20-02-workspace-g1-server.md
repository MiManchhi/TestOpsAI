# S1/S2 账号、项目、成员与负责人交接

更新：2026-11-20（Asia/Shanghai）。状态：VERIFIED（账号、项目和交接接口）。

<!-- handoff:v1 -->

Task: WORKSPACE-G1-SERVER-001
Role: server
Baseline: 4c21d1a704c26ebb12c41ec966d07f6d5c6f3c7b
Contract: 工作台接口 2.0.0，公开 v3 / 内部 v2。
Scope: 按已确认的最终版规划开发 S1/S2 账号、项目、成员与负责人交接，保留本地未提交改动。

## 实施

已核对恢复材料和实际 Git 工作区。三个本地开发分支已快进到此基线。

Server 工作区新增 V9–V11，完成账号、令牌撤销、项目、成员、手动或代办交接，命令回执在 MySQL 同事务。旧 v2 项目不再把平台 ADMIN 默认当作成员。保留旧 EDITOR/VIEWER 数据，新授权通过负责人显式设置 TEST/DEV。资料 ACL 创表和回写属于后续 S3。

## 验证

<!-- checks -->

- Java 定向测试在新 MySQL 8.4.8 上 7/7。先发现虚拟/存储生成列与外键约束冲突，修复后 V1–V11 迁移通过。随后在另一个临时 MySQL 8.4.8 / Redis 7.4.8 上执行 `scripts/maven.ps1 -B -ntp verify`，67 项全部通过，无跳过。旧项目创建权限与最后一位负责人保护均有回归。
- 另建 V8 fixture 后升级到 V11 并跑真实浏览器，旧 VIEWER 在 v2 仍可访问既有项目，在 v3 无隐式授权。测试容器和本地服务已回收。
- Web 类型检查、lint、Vitest 95 项与构建通过。Playwright 连接实际 Java/MySQL/Redis 验证平台建账号与项目、窄屏首次改密、加成员、交接申请与批准以及前任权限回收。
- Python Ruff、mypy 和 pytest 通过，117 通过、2 个外部服务用例跳过。工作台新契约离线检查 54 个操作、69 个模型通过。

## 交接

<!-- next -->

S3 建立文档授权、对象隔离与审批事务。受限读者的负责人自动变更和审计应与已实现的交接事务整合，再补 J1 的相关资料权限断言。见[联合验收](../../docs/development/PARALLEL_DEVELOPMENT.md)。
