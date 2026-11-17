# 远端 CI 失败原因分析

更新：2026-11-17。状态：VERIFIED（原因分析范围；修复仍 PLANNED）。

## 目标与起始核对

- 用户要求先分析远程流水线失败；不实施修复、不提交、不推送，不进入 M1。
- 已核对根规则、[记忆导航](../INDEX.md)、[当前交接](../CURRENT.md)及开发、目录、代码规范。
- 起点：main 跟踪 origin/main，HEAD 为 33fe9c4，工作区干净。以用户两张失败截图及实际 [CI](../../.github/workflows/ci.yml) 为证据，不推定未展示的 job 状态。

## 分析与验证

- [smoke-local.py](../../scripts/smoke-local.py) 第 64 行在 os.name 条件表达式中访问 Windows 专用 subprocess.CREATE_NO_WINDOW；本机 mypy 1.20.2 按 linux 平台检查重现相同错误（退出 1），按 win32 检查通过（退出 0）。
- [目录回归](../../tests/engineering/check-layout.tests.ps1) 第 52 行删除临时点文件时没有 Force；Unix 中点文件具有 Hidden 属性，PowerShell 在未指定 Force 时拒绝删除并报告通用权限错误。截图显示前面 9 项目录断言已通过，错误发生在测试夹具清理，不是这些断言失败。
- 证据：[mypy 平台检查](https://mypy.readthedocs.io/en/stable/common_issues.html#python-version-and-system-platform-checks)、[Remove-Item Force](https://learn.microsoft.com/en-us/powershell/module/microsoft.powershell.management/remove-item#-force)、[.NET Unix 属性实现](https://github.com/dotnet/runtime/blob/v8.0.11/src/libraries/System.Private.CoreLib/src/System/IO/FileStatus.Unix.cs)、[PowerShell 删除实现](https://github.com/PowerShell/PowerShell/blob/v7.4.6/src/System.Management.Automation/namespaces/FileSystemProvider.cs)。实现资料用于解释行为，未核实截图 runner 精确补丁版本。
- 最小修复方向：使用 mypy 能识别的 sys.platform 平台分支隔离 Windows 常量；仅对临时夹具点文件清理使用 Force，保留路径安全边界。不要降低检查标准或更改 runner 来掩盖跨平台缺陷。本轮不实施。

## 真实验证

执行目录为仓库根目录，Python 来自项目虚拟环境，mypy 1.20.2。

| 命令                                                                                                                                          | 退出结果 | 验证范围                                      |
| --------------------------------------------------------------------------------------------------------------------------------------------- | -------- | --------------------------------------------- |
| `git status --short --branch`、`git log -2 --format="%h %s"`、`git remote -v`                                                                 | 0        | 起始 main 干净，HEAD 33fe9c4，origin 地址一致 |
| `apps/ai-service/.venv/Scripts/python.exe -m mypy --strict --platform linux --cache-dir .tools/cache/mypy-ci-analysis scripts/smoke-local.py` | 1        | 重现第 64 行 CREATE_NO_WINDOW 属性错误        |
| `apps/ai-service/.venv/Scripts/python.exe -m mypy --version`                                                                                  | 0        | 1.20.2                                        |
| `apps/ai-service/.venv/Scripts/python.exe -m mypy --strict --platform win32 --cache-dir .tools/cache/mypy-ci-analysis scripts/smoke-local.py` | 0        | 同一源码在 Windows 目标类型检查通过           |

收尾检查：`scripts/memory.ps1 -Mode Check` 退出 0（17 份活动记录）；项目锁定 Prettier 对本轮 5 份 Markdown 的 `--check` 退出 0；`git diff --check` 退出 0。已查看实际 diff 和新 session，Git 仅有 4 份记忆修改与 1 份未跟踪 session。无源码、CI、目录结构或依赖变更；未重跑三端完整门禁。

## 交接

- 原因分析完成：本机 Windows 与 CI Ubuntu 的平台差异未被原验证覆盖。未获取完整运行日志/元数据，无法确认其他 job 状态。
- 修复仍 PLANNED；Linux PowerShell 实机复现与修复后的远端 CI 尚未执行。BLOCKED：CI 整体验收失败，需修复后重跑解除。
- 下次从两个精确行号实施最小修复，再验证 linux/win32 类型检查、Ubuntu 目录回归和完整门禁；不自动进入 M1，不提交/推送。
