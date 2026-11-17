# AI 协作入口与自动恢复初始化

更新：2026-11-17。状态：VERIFIED（仓库侧本机检查；客户端实测另列）。

<!-- handoff:v1 -->

Task: ENG-008
Role: integration
Baseline: fc826ba
Contract: AGENTS.md 与 docs/development/MEMORY.md
Scope: 仓库协作规则、恢复脚本、工程回归、当前记忆；不修改业务、不提交推送、不创建分支。

## 目标与起始核对

- 用户希望减少每次会话的提示，通过文件自动加载规则和恢复进度。
- 已读根规则、六份入口与相关模块；起点 main / fc826ba，工作区干净。
- 本机 DeepSeek Harness 0.2.2-rc.3 的安装包 README 与源码显示 dsh-agent-instructions 默认通过 dsh-base 启用，首次请求加载 AGENTS.md；未修改应用程序、用户配置或凭据。
- Codex 官方 AGENTS 指南确认启动时发现指令；文档 helper 因本机脚本策略和系统 Node 路径权限失败，改查官方网页。
- 现有 INDEX/CURRENT/server 模块有过期状态，本轮按 Git 与历史验证范围纠正，不宣称重新完成 M1.2 测试或远端 CI。

## 实施与选择

- 复用根 AGENTS 作为两客户端统一入口；详细协作归开发文档，动态状态归 CURRENT。
- 恢复脚本按分支选择领域并读取模块和 CURRENT 引用的 session；不自动修改 Git 或启动业务。
- 新交接结构用显式版本标记检查，历史 session 保持原文。
- 新增 docs/development/AI_COLLABORATION.md；修改 AGENTS、CONTRIBUTING、DIRECTORY_LAYOUT、MEMORY、ROADMAP、memory.ps1 与工程回归；校正 CURRENT/INDEX/server 模块，更新 session 模板与全局经验/证据。
- 默认 AI 用 Restore -ListOnly 获取清单，再分批读，防止全量文本被工具输出截断；清单不视为已读证据。未改客户端安装包、全局设置或 Git 配置。

## 真实验证

<!-- checks -->

日期：2026-11-17；目录为仓库根，适用 fc826ba 上本次未提交协作变更，Windows 本机。

| 命令                                                                               | 结果 | 范围与限制                                                                                                                          |
| ---------------------------------------------------------------------------------- | ---- | ----------------------------------------------------------------------------------------------------------------------------------- |
| powershell -NoProfile -ExecutionPolicy Bypass -File scripts/memory.ps1 -Mode Check | 0    | 22 份活动记忆；结构与引用有效，不证明文字语义正确                                                                                   |
| tests/engineering/check-layout.tests.ps1                                           | 0    | 9 项目录回归；既有导航、新指南缺失、CURRENT 缺失/重复区块、新交接字段/角色/验证与下一步缺失反例；12 份脚本解析                      |
| scripts/memory.ps1 -Mode Restore；-Role web；-ListOnly -Role web                   | 0    | 自动 integration、显式 Web、HEAD 与本地主线差异、模块和 session 清单正确；未创建开发分支，未做三客户端实际会话验收                  |
| Prettier --write 指定变更 Markdown；最终指定文件 --check                           | 0    | 只格式化本次文档；最终结果收尾复核                                                                                                  |
| scripts/check.ps1                                                                  | 0    | 全量格式、Vue 类型/lint/3 测试/构建，Java verify/Spotless（14 项，9 跳过），Python lock/Ruff/mypy/双平台 smoke 静态检查/1 测试/构建 |
| git diff --check                                                                   | 0    | 本轮跟踪文件空白检查，新增文件另行检查                                                                                              |

首次完整门禁在沙箱内因旧 .pytest_cache 读取 EPERM 于 Prettier 阶段退出 1；自动审批允许沙箱外重跑后退出 0。脚本执行使用当前 PowerShell 进程的 ExecutionPolicy Bypass，没有更改全局策略；沙箱内 Git 使用进程级 safe.directory 参数，不修改全局 Git 信任。最后增加 ListOnly 后重新运行相关工程/恢复/格式检查，不重复无关业务测试。

## 交接

<!-- next -->

- 仓库侧 IMPLEMENTED / 本机工程检查 VERIFIED；变更未提交，用户仍负责主线合并。
- 实际 DeepSeek 新会话与 Codex 新会话注入尚未验收；安装包支持不等于已观察运行效果。
- 下一步：审查并提交，再从正确工作区各新开会话，用“汇报当前进度，先不改文件”验证实际工具读取；随后按具体授权创建 worktree 和业务任务。无需重复粘贴固定规则。
- [CURRENT](../CURRENT.md)、[导航](../INDEX.md)。
