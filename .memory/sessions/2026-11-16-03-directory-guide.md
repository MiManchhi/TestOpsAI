# 目录说明与结构评估

更新：2026-11-16（Asia/Shanghai）。状态：VERIFIED。

目标：面向初学者解释实际目录和每个根文件的用途、放置原因、能否移动，并评估当前设计的不足。沿用现有三端结构，不把文档检查当作环境验收。

起始核对：已读 AGENTS、INDEX、CURRENT、目录说明、清单与 ignore；Git main 无提交，所有源码文件尚未跟踪。用户明确关注根目录配置过多以及结构是否合理。

计划：扩写 docs/development/DIRECTORY_LAYOUT.md，核对官方工具约定，增加新手阅读路径、文件归属例子与改进顺序；同步 README 和记忆，验证内容覆盖、相对链接与格式。

## 变更

- 扩写 [目录说明](../../docs/development/DIRECTORY_LAYOUT.md)：实际目录、三端内部结构、14 个根文件用途/放置原因/可否移动、新文件归档表、白名单、现状评估和新手阅读路线。
- 新增 [check-layout.ps1](../../scripts/check-layout.ps1)，接入 [check.ps1](../../scripts/check.ps1) 及 [CI](../../.github/workflows/ci.yml)。只检查根目录，不把规则夸大成所有深层文件都能自动分类。
- 修改 [AGENTS](../../AGENTS.md) 与 [README](../../README.md)，要求唯一归属、限制新增根项并提供入口。
- 更新 INDEX、CURRENT、工程模块、LEARNING 与 EVIDENCE，保留先前环境待办。
- 核对 npm、Prettier、EditorConfig、Docker、Git 的官方配置约定，来源链接写入指南。未搬迁三端、删除工具或创建提交。

## 实际验证

日期：2026-11-16，Asia/Shanghai。工作目录：仓库根，无 commit SHA。

| 检查                               | 结果与限制                                                         |
| ---------------------------------- | ------------------------------------------------------------------ |
| `./scripts/check-layout.ps1`       | 当前真实根目录通过                                                 |
| PowerShell 子进程 + 独立临时夹具   | 合法根文件/目录通过；scratch.py 和未知根目录均返回失败；夹具已清理 |
| Python 覆盖扫描                    | 实际根文件 14 个，指南均有说明                                     |
| Python Markdown 相对链接扫描       | 通过（原始方案、历史归档除外）                                     |
| PowerShell AST 解析                | 新脚本无语法错误                                                   |
| Prettier 格式检查                  | 本轮文档和 CI 配置通过                                             |
| `./scripts/memory.ps1 -Mode Check` | 13 份活动记录通过                                                  |

## 交接

当前目录归属已明确，结构检查本机已验证；远端 CI 未执行。当前不足包括 .tools 的重复下载遗留、Wrapper/Vue/Python 环境待办。只建立规则和解释，不把文档交付算作应用验收。下一次新增文件按归档表放置，常规任务不增加根目录项。
