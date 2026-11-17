# 记忆系统、目录与规范

更新：2026-11-16（Asia/Shanghai）。状态：VERIFIED。

## 目标与起始核对

用户明确要求 .memory 持久记忆、规范读取更新、状态与证据分离；同时要求目录整洁、规范提交，并保存最初方案作为方向。本轮只处理这些工程事项。

已读 AGENTS、docs、临时规划，核对 git status。main 无提交，所有工程文件尚未跟踪；原本无 .memory。发现根目录过程文件杂乱、README 的 ROADMAP/ENVIRONMENT 链接无目标，环境验收尚未完成。

## 实施与文件清单

- 新建 .memory 的 INDEX、CURRENT、LESSONS、LEARNING、EVIDENCE。
- 新建 modules/engineering、server、web、ai-service 四份模块记忆。
- 新建 sessions/ 两次任务记录与 TEMPLATE。
- 原 task_plan、findings、progress 移入 archive/2026-11-16-m0，保留内容。
- CONTRIBUTING、SECURITY 从根目录移到 docs/development；.python-version 移到 apps/ai-service。
- 修改根 AGENTS：任务开始/过程/收尾规则、证据状态、目录和 Conventional Commits 纪律。
- 修改 README：指向当前记忆、证据与迁移后的规范，明确 M0 未验收。
- 修改 .prettierignore：排除记忆归档，保留历史过程文件原文。
- 新建 docs/development/MEMORY.md、DIRECTORY_LAYOUT.md、docs/references/README.md、提交模板和 scripts/memory.ps1。
- 本轮无业务代码变更，无提交/推送。

## 真实验证

日期：2026-11-16，Asia/Shanghai。执行目录：仓库根。无 commit SHA，针对未提交工作区。

| 命令/检查                                                                  | 实际结果                                                                 |
| -------------------------------------------------------------------------- | ------------------------------------------------------------------------ |
| `./scripts/memory.ps1 -Mode Check`                                         | 退出 0，12 份活动记录通过日期/状态/链接检查，归档除外                    |
| 新进程 `powershell -NoProfile -File scripts/memory.ps1 -Mode Restore`      | 退出 0，定位规则、导航、当前任务、原初始化 session、main 与 Wrapper 待办 |
| Python Markdown 相对链接及根文件扫描                                       | 通过；已搬迁的过程文件/规范不再留在根目录；原方案内草案不重写            |
| 原方案与 docs/PROJECT_BLUEPRINT.md 的 SHA-256 比对                         | 相同，原文保留                                                           |
| `git branch --show-current`、`git rev-list --all --count`、`git remote -v` | main、0、无 remote；没有提交/推送                                        |
| `git check-ignore`                                                         | .env/.tools/node_modules/target 被忽略，.memory 不被忽略                 |
| `git config --local --get commit.template`                                 | docs/development/COMMIT_TEMPLATE.txt；本地模板，没有自动提交或校验 hook  |
| `git diff --check`                                                         | 无错误；全是未跟踪文件，不能代替全部文件检查                             |

结构检查不证明 AI 必然遵循规则，也不证明业务或环境通过。恢复测试证明独立 PowerShell 会话能从文件找到正确上下文。

## 交接

本次约定交付已完成：目录、规范、Git、原始需求、持久记忆。环境待办保留在 [CURRENT](../CURRENT.md)，不升级成完整 M0 验收通过。新任务从 AGENTS/INDEX/CURRENT 进入，建立新 session，不覆盖本记录。
