# WEB-001 提交与远端发布

更新：2026-11-17。状态：IN_PROGRESS。

<!-- handoff:v1 -->

Task: WEB-001-PUBLISH
Role: web
Baseline: 101596d03adb8145e24aafd5a6f96632401720e3
Contract: docs/API.md 与 docs/development/AI_COLLABORATION.md
Scope: 用户明确授权提交本工作区 Web 登录与工作台成果并正常推送 origin/codex/web-auth，包含必要规范、锁文件及记忆；不强推、不同步或合并主线。

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

- IN_PROGRESS；已获本次提交/推送授权，尚未提交或推送，不标为远端发布成功。
- BLOCKED：无已确认阻塞；SSH 本机权限重试中。
- 下一步：提交并推送 Web 检查点，按追加授权在最新 main 上合并、验证并同步正式文档与记忆，正常推送 main。
- [CURRENT](../CURRENT.md)、[Web](../modules/web.md)。
