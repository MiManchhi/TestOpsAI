# 对齐三端工作区与跟踪 J1

更新：2026-11-20（Asia/Shanghai）。状态：VERIFIED（G1 已实现的账号、项目与交接主链）。

<!-- handoff:v1 -->

Task: WORKSPACE-G1-001
Role: integration
Baseline: 4c21d1a704c26ebb12c41ec966d07f6d5c6f3c7b
Contract: 工作台接口 2.0.0，公开 v3 / 内部 v2。
Scope: 按已确认的最终版规划开发 对齐三端工作区与跟踪 J1，保留本地未提交改动。

## 实施

已核对恢复材料和实际 Git 工作区。三个本地开发分支已快进到此基线。

三端本地开发分支快进至 4c21d1a。应用实现分别在 Server、Web、AI worktree，main 上此前的 Java 工作区改动仍在原路径。Java 全量回归发现上次联调已解决的目录/限流问题，把三份修复复制到 Server worktree，同时隔离了会改写种子管理员角色的旧用例。

## 验证

<!-- checks -->

- Java 定向测试在新 MySQL 8.4.8 上 7/7。先发现虚拟/存储生成列与外键约束冲突，修复后 V1–V11 迁移通过。全量 `scripts/maven.ps1 -B -ntp verify` 在临时 MySQL 8.4.8 / Redis 7.4.8 上退出 0，67 项全部通过。
- 另建 V8 fixture 后升级到 V11 并跑真实浏览器，旧 VIEWER 在 v2 仍可访问既有项目，在 v3 无隐式授权。测试容器和本地服务已回收。
- Web 类型检查、lint、Vitest 95 项与构建通过。Playwright 连接实际 Java/MySQL/Redis 验证平台建账号与项目、窄屏首次改密、加成员、交接申请与批准以及前任权限回收。
- Python Ruff、mypy 和 pytest 通过，117 通过、2 个外部服务用例跳过。工作台新契约离线检查 54 个操作、69 个模型通过。

## 交接

<!-- next -->

G2 从 S3/A3/W4 的原件、审核、索引及读者开始。J1 中受限资料随负责人变更须等待 S3，再按 [联合验收](../../docs/development/PARALLEL_DEVELOPMENT.md) 补证。所有代码保留在三个开发工作区，当前无新提交。
