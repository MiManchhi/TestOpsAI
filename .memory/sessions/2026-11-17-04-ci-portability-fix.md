# CI 跨平台修复与发布

更新：2026-11-17。状态：VERIFIED（本地门禁、smoke 与远端 Ubuntu CI 通过；代码已推送）。

## 目标与起始核对

- 用户授权修复已分析的两个问题并推送 origin/main；保持 M0，不增加业务功能。
- 已读取根 AGENTS、[INDEX](../INDEX.md)、[CURRENT](../CURRENT.md)、开发/目录/代码规范、工程模块与[前次分析](2026-11-17-03-ci-failure-analysis.md)。
- 起点 HEAD 33fe9c4，main 跟踪 origin/main；已有 4 份记忆修改和 1 份分析 session，均为上轮未提交记录，保留并随相关修复发布。

## 实施与验收计划

- Python 用 sys.platform 显式分支隔离 Windows 常量；本地门禁和 CI 都检查 linux、win32 两个目标，保留 strict。
- 点文件清理增加 Force；Windows 测试夹具显式设置 Hidden，使原缺陷也能在本机重现，保留测试路径清理边界。
- 运行相关回归、完整 scripts/check.ps1、本机 smoke；审查差异与敏感文件，Conventional Commit 后正常推送并核对远程 SHA；能读取时跟进远端 CI。

## 验证与交接

- 已实现：Python 显式平台分支；本地/CI 双平台 strict 检查；隐藏夹具 Force 清理与 Windows Hidden 回归。
- GitHub 元数据已核实原 run [44009166249](https://github.com/MiManchhi/TestOpsAI/actions/runs/44009166249)：33fe9c4，server/web 成功，ai/compose 失败，与截图一致。
- 本机 Python 3.12.16、mypy 1.20.2、Java 21.0.13.0.2；根目录运行：

| 命令                                                                                                                                          | 退出结果 | 范围                                                                                                                                             |
| --------------------------------------------------------------------------------------------------------------------------------------------- | -------- | ------------------------------------------------------------------------------------------------------------------------------------------------ |
| `apps/ai-service/.venv/Scripts/python.exe -m mypy --strict --platform linux --cache-dir .tools/cache/mypy-ci-analysis scripts/smoke-local.py` | 0        | Linux 静态目标                                                                                                                                   |
| 同上，将 `--platform linux` 改为 `--platform win32`                                                                                           | 0        | Windows 静态目标                                                                                                                                 |
| `scripts/check.ps1`                                                                                                                           | 0        | 9 目录/3 导航回归、11 PowerShell 语法检查、全仓格式、Vue 3 测试及构建、Java 2 测试/Spotless/打包、Python Ruff/类型/1 测试/构建，含双平台脚本检查 |
| `scripts/smoke-local.ps1`                                                                                                                     | 0        | 本机三端启动、健康/代理与 Java 401 边界                                                                                                          |

- 第一次门禁的外层 `*>` 日志重定向在 PowerShell 5 下把 Mockito stderr 提示升级为终止错误（退出 1），不是测试断言失败；移除诊断外层重定向，直接运行同一门禁通过，未关闭警告或更改应用配置。原诊断日志只在忽略的 .runtime。
- 正常运行仍有 Mockito 动态 agent 和 Starlette TestClient 弃用提示，不影响本次退出结果；已有依赖审计遗留不在本次修复范围。
- 提交前复核通过：`scripts/memory.ps1 -Mode Check` 退出 0（18 份活动记忆）；`tests/engineering/check-layout.tests.ps1` 退出 0（9+3 回归、11 脚本解析）；`git diff --check` 退出 0；全仓 `prettier --check .` 在本机用户下退出 0。沙箱用户枚举本机生成的 .pytest_cache 曾报 EPERM（退出 1），改由文件所属本机用户执行，未修改权限或排除规则。
- 变更清单：scripts/smoke-local.py、scripts/check.ps1、tests/engineering/check-layout.tests.ps1、.github/workflows/ci.yml；记忆 INDEX、CURRENT、LESSONS、LEARNING、EVIDENCE、modules/engineering；新增前次分析和本次修复 session。全部留在既有目录，无依赖或业务变更。
- `git commit -m "fix(ci): make engineering checks portable across platforms"` 退出 0，生成 9fb1437de0b2c87f414fdee726085e56c493767c；`git push origin main` 退出 0，远程从 33fe9c4 前进至 9fb1437，未强推；`git ls-remote origin refs/heads/main` 与本地 SHA 一致，推送后工作区干净。
- GitHub [run 44011247548](https://github.com/MiManchhi/TestOpsAI/actions/runs/44011247548) 的 head_sha 为该修复提交，completed/success；server、web、ai、compose 全部成功，原两个失败点及后续 pytest/AI 构建/Compose 配置校验均通过。读取 jobs 与 run 元数据确认，不以本机结果推定远端结果。
- 完整应用容器部署与 Linux 三端运行 smoke 不在本次验证范围。
- 本次授权包括提交和推送；不强推，不修改根目录结构，不降低门禁。

## 最终交接

- 两处缺陷修复与防回归检查均 VERIFIED，修复代码已推送；BLOCKED：无本次任务遗留阻塞。npm 审计/弃用提示等原有事项仍按 CURRENT 保留。
- 同步 INDEX、CURRENT、LESSONS、LEARNING、EVIDENCE、engineering 与本 session；记忆收尾随独立 docs 提交同步，最终提交号以 Git refs 为准，避免在文件中自引用未生成的 SHA。
- 下一起点：保持 M0，等用户明确新任务，不自动进入 M1。未新增目录、根文件或业务功能。
