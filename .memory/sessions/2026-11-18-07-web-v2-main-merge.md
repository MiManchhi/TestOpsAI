# Web v2 主线集成

更新：2026-11-18。状态：IMPLEMENTED（无冲突合并候选；按最新指令直接发布）。

<!-- handoff:v1 -->

Task: WEB-V2-MERGE-001
Role: integration
Baseline: origin/main ff9f671605f72d3834c2174ffc2df928850b32df；origin/codex/web-auth 20b8e2e02a87059cb746589a644125a546b2ea6f
Contract: docs/contracts/README.md 冻结 1.0.0；本次不改 DTO 或公共接口
Scope: 用户在 Web 提交推送完成后明确追加“合并吧”；授权先获取最新 main、集成 Web、更新相关记忆、验证并普通推送 main；不强推或覆盖其他工作区变更

日期/时区：2026-11-18 / Asia/Shanghai。分支 codex/integrate-web-v2-20261118；独立工作区位于 Web 工作区忽略的 .runtime/integration-web-v2-20261118。

## 目标与起始核对

- 已按 [入口](../../AGENTS.md)、[导航](../INDEX.md) 恢复规范、模块和相关 session，并 fetch origin；原 Web 与主线工作区均干净。main 比共同基线多 8 提交，Web 多 5 提交。
- 主线 ff9f671 保留 M1.5/V6、AI 静态解析和学习路线；Web tip 20b8e2e 的 [CI 44257614573](https://github.com/MiManchhi/TestOpsAI/actions/runs/44257614573) 五项成功。后者仅证明合并前 Web 基线。
- 历史 Web“暂不合并”保持原时点事实；本次追加授权单独记录，不追改旧 session。

## 实施与选择

- 从最新主线建立独立集成工作区，执行 merge --no-ff --no-commit origin/codex/web-auth，无冲突。学习正文及其他领域成果保留；不修改认证、冻结契约或缺失的后端能力。
- Web 新布局、项目上下文、套件树/筛选/分页/选择/详情及 Docker JSON 上下文修复一起集成；新增领域开发不在本轮范围。
- 在集成目录独立安装锁定 npm/Python 依赖，复用已有 SDK，不链接原 Web node_modules，确保测试当前合并代码。
- BLOCKED：真实 v2 联调。Java 项目列表静态返回数组而冻结契约要求 ProjectPage，且 v2 套件/用例/选择尚缺；需后端按契约补齐再做真实权限、分页、快照联调。本轮不修改契约规避差异。

## 真实验证

<!-- checks -->

| 日期 / 命令                                    | 目录 / 代码版本 / 环境     | 结果   | 范围与跳过项                                                                                                                                     |
| ---------------------------------------------- | -------------------------- | ------ | ------------------------------------------------------------------------------------------------------------------------------------------------ |
| 2026-11-18 Restore -ListOnly -Role integration | 独立合并候选               | 退出 0 | 40 条记忆；清单后分批读取，不当作测试证据                                                                                                        |
| scripts/check.ps1                              | ff9f671 + 20b8e2e 合并候选 | 退出 1 | 临时环境 PATH 无法解析 powershell，补系统 PATH 后仍受 env 重建影响，测试尚未开始；用户明确已验证并要求直接合并，停止追加验证。不声明完整门禁通过 |

## 交接

<!-- next -->

- 用户最新明确已验证并要求直接合并；以后明确指示推送、合并时直接执行，不重复确认、不自动追加验证流程。存在冲突、远端分歧或未提交工作则保留现场说明。本次直接创建合并提交并普通推送 main。
- npm ci / uv sync --locked 退出 0；npm 既有 14 项审计提示未处理。仅在原主线工作区仍干净且可快进时同步；新 CI 自动运行但不等待追加验收，未部署。
