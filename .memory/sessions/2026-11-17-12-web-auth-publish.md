# WEB-001 提交与远端发布

更新：2026-11-17。状态：VERIFIED（Web 分支发布、主线集成与推送）。

<!-- handoff:v1 -->

Task: WEB-001-PUBLISH
Role: integration
Baseline: 101596d03adb8145e24aafd5a6f96632401720e3
Contract: docs/API.md 与 docs/development/AI_COLLABORATION.md
Scope: 用户明确授权提交推送 Web 成果，追加授权先拉取最新代码、文档、记忆与规范后合并并正常推送 main；同步当前 Web 分支，不强推或覆盖未提交工作。

追加授权：用户随后明确要求先拉取最新代码、文档、记忆与规范，再合并至 main。本任务范围据此扩展为安全集成、验证与正常推送 main；先保全 Web 检查点，主线工作区需保持干净，不强推或覆盖未提交工作。

## 目标与起始核对

- 2026-11-17 / Asia/Shanghai；工作目录 TestOpsAI-web，分支 codex/web-auth，HEAD 101596d。待提交内容是 [WEB-001](2026-11-17-11-web-auth-workspace.md) 已验收产物，未发现额外业务修改。
- 本地主线已到 15e98f8，比本分支多四个提交，新增 Java M1.3 与 Docker 启动器。本次不自动同步；知识库/测试用例 UI 仍仅待开放说明，后端新功能不在本分支中。
- 主线 docs/API.md 与 CURRENT 已有变动，后续用户合并须按字段与事实归并，不能整份覆盖主线记忆或契约。
- 初次远端只读核对受沙箱 SSH known_hosts 权限限制；使用获批的本机权限重试，不关闭主机验证、不修改 SSH 配置。

## 实施与选择

- 按 Conventional Commits 提交代码、测试、契约说明和相关记忆；随后正常推送同名工作分支。
- 本轮不改业务实现；复跑前端检查、格式、记忆与 diff 检查。既有完整门禁、浏览器与三端 smoke 证据保留原范围，不把历史 DB 跳过项或新远端 CI 视为通过。
- 发布成功后同步收尾记忆并再次推送；最后核对本地/远端 SHA 和工作树状态。

## 真实验证

<!-- checks -->

- Restore -ListOnly 与 memory Check 退出 0（初始 25 份活动记忆）；确认角色 Web 与本地主线四提交差异。
- npm run check --workspace @testops/web 退出 0：类型/零警告 lint、37 测试、生产构建。全仓格式与 memory Check（26 份）通过。
- 候选扫描通过：27 份源码/测试/文档，无生成物、私钥、GitHub Token 或 JWT 字面量；git diff --check 通过。
- 获批环境 git fetch origin 与 ls-remote 成功；origin/codex/web-auth 为 101596d，origin/main 与干净主线工作区均为 15e98f8。已读取最新主线 CURRENT、Java/工程模块、M1.3/启动器 session 和完整 API，保留其证据边界。

## 交接

<!-- next -->

- VERIFIED；Web 检查点 ad87e97945730d8836d3015068e71583b96a1763 已正常推送；main 合并提交 4cc2d1c 已正常推送，本地/远端 SHA 核对一致。
- BLOCKED：无已确认阻塞；SSH 访问已恢复，未更改 SSH 配置。
- 下一步：从最新 main 或本次同步后的 Web 工作区恢复；新业务需另行明确范围。其他 Java/AI worktree 未自动同步，本轮不部署容器或宣称远端 CI 成功。
- [CURRENT](../CURRENT.md)、[Web](../modules/web.md)。

## 主线集成

- 追加授权后，main 工作区初始干净；先执行 git pull --ff-only origin main，确认最新 15e98f8，再 git merge --no-ff --no-commit codex/web-auth。自动合并 CURRENT/API 成功，无文本冲突。
- 已核对合并保留最新 Java M1.3、Docker 启动器、相关 session、规范与契约；更新 README、路线图、容器指南、INDEX 和 Web/集成事实，明确后端 CRUD 已实现但前端业务页待开放。
- 集成完整 scripts/check.ps1 退出 0：格式/目录/28 份记忆/工程反例/14 份脚本解析、Web 类型/零警告 lint/37 测试/构建、Java verify/Spotless（33 项中 28 项 DB 测试跳过）、Python lock/Ruff/mypy/双平台脚本检查/1 测试/构建通过。先前 Web 37 测试、完整门禁、真实 Java 浏览器与 smoke 仍只覆盖各自原基线；不把现有容器当作 M1.3 + 新 Web 已部署证据。

## 发布结果

- git commit 创建保留 main 15e98f8 与 Web ad87e97 两侧历史的合并提交 4cc2d1c；正常 git push origin main 退出 0。git ls-remote 确认完整 SHA 为 4cc2d1c4f614dcb0c4852bb988e0385f7fe41ca1，与本地一致；没有强推。
- 本轮收尾仅更新发布事实，格式、memory Check 与 diff 检查后随独立 docs 提交推送；当前干净 Web 分支随后按授权快进至最新主线并正常推送。最终 tip 与干净状态以 Git refs 为准，不循环把文档自身 SHA 写入自身。
- 完整集成门禁已通过；Java 数据库集成测试 28 项跳过。未重新部署 M1.3 或新 Web，未核实新远端 CI，不外推历史容器和浏览器证据。
