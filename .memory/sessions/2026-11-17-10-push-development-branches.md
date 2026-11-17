# 三个开发分支同步到远端

更新：2026-11-17。状态：VERIFIED（Git 远端同步与跟踪关系）。

<!-- handoff:v1 -->

Task: ENG-010
Role: integration
Baseline: 03d2d37
Contract: docs/development/AI_COLLABORATION.md
Scope: 用户授权将现有三个开发分支推送 origin 并建立同名 upstream，维护交接；不改业务、不强推、不合并业务分支。

## 目标与起始核对

- 已读取六入口，起始 main 和三个本地工作分支均在 03d2d37，主线工作区干净。三个工作树已存在；开发分支尚无 upstream。
- 目标为 origin/codex/server-m1、origin/codex/web-auth、origin/codex/ai-ingestion。
- 相关风险：远端若已有并行更新，普通推送应拒绝覆盖，需先核实。验收为 push 成功、upstream 同名、本地/远端 SHA 一致、各工作树干净。

## 实施与选择

- 正常 `git push -u origin`，不使用 force；收尾记忆单独提交，三个空工作分支随后快进同步并正常推送。
- 本轮仅 Git 引用与记忆变化，使用 memory Check、指定 Markdown 格式检查和 diff 检查，不重复业务测试。03d2d37 的五项远端 CI 已在上一任务通过；新推送的 CI 状态独立记录，不据旧结果推断。

## 真实验证

<!-- checks -->

- 2026-11-17，仓库根：`git ls-remote --heads origin main codex/server-m1 codex/web-auth codex/ai-ingestion` 确认此前仅 main 在远端。
- `git push --atomic -u origin codex/server-m1 codex/web-auth codex/ai-ingestion` 退出 0，三个远程分支创建成功；`git ls-remote` 与 `git for-each-ref` 逐一确认 SHA 为 03d2d37251c26fdef3dc43a2b55cc9ca8cdf5c17，upstream 为 origin 下同名分支。
- 本轮仅 Git 引用与记忆变更。收尾执行 memory Check、指定 Markdown 格式、diff 检查，记录以独立 docs 提交同步；最终 SHA 通过 Git refs 核对，不重复业务测试。
- 新分支推送会按既有 workflow 触发 CI；本任务没有将新推送的 CI 或部署标为已验证。

## 交接

<!-- next -->

- 三个远端分支和同名跟踪关系已建立；没有强推或业务变更。收尾记忆随后单独提交，三个干净工作分支快进同步并正常推送，最终以实际 refs 为准。
- BLOCKED：无。后续从对应工作树开发，提交/推送仍按具体任务授权执行，主线合并由用户负责。
- [CURRENT](../CURRENT.md)、[工作树准备](2026-11-17-09-publish-collaboration-worktrees.md)。
