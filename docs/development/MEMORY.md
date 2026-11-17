# 工程记忆使用指南

2026-11-17 补充：每个新会话先读 AGENTS、INDEX、CURRENT，再读 CONTRIBUTING、DIRECTORY_LAYOUT 和 [CODE_STYLE](CODE_STYLE.md)。Restore 现同时输出上述六份入口；Check 验证规范文件及 AGENTS 导航没有缺失，并已接入本地/CI 配置。

Codex 会在运行开始时发现仓库 AGENTS；同一持续会话里修改规则后，要求 AI 显式重读。其他 AI 客户端需支持 AGENTS 或手动提供相同入口。本仓库没有后台自动保存、会话级强制 hook 或能证明模型已经遵从所有规则的机制。此检查验证导航和记录格式，不等同于检查模型行为。依据：[官方 AGENTS 指南](https://learn.chatgpt.com/docs/agent-configuration/agents-md)。

更新：2026-11-17。状态：IMPLEMENTED。

## 开始任务

在项目目录打开会话，先读取 [AGENTS](../../AGENTS.md)、[INDEX](../../.memory/INDEX.md)、[CURRENT](../../.memory/CURRENT.md)，或运行 `./scripts/memory.ps1 -Mode Restore` 输出恢复入口与 Git 状态。随后读取相关模块并核对实际代码。恢复脚本只读取，不安装依赖、不执行测试、不修改 Git。

AI 通过仓库 AGENTS 规则在任务边界读取和更新记忆；不是后台自动保存，也不承诺不读取规则的任意 AI 客户端会自动执行。必要时明确提示：“先按 AGENTS 恢复 .memory 再开始”。

## 过程与收尾

复制 [session 模板](../../.memory/sessions/TEMPLATE.md)，按 `YYYY-MM-DD-NN-topic.md` 命名。记录目标、变更、选择、真实验证、失败与交接。重要阶段变化立即更新 CURRENT，避免会话中断丢失上下文。

完成时更新 CURRENT 与 session；缺陷归 LESSONS，学习归 LEARNING，验证/部署证据归 EVIDENCE，稳定技术上下文归 modules。正式决策只在 docs/ADR 维护正文，记忆链接过去。运行 `./scripts/memory.ps1 -Mode Check` 检查格式和引用，再按本次变更运行必要项目检查。

每份有效记录包含日期、状态、相关文件。CURRENT 保持简洁，只保留当前事实与下一步；session 保存历史，修正错误采用注明日期的补充，不篡改旧验证记录。INDEX 只维护导航，避免各文件重复大段内容。

## 状态和证据

PLANNED（计划）→ IN_PROGRESS（正在做）→ IMPLEMENTED（有产物）→ VERIFIED（适用检查通过）→ DEPLOYED（指定环境部署并验证）。BLOCKED 单独注明原因、影响和解除条件。部分通过逐项记录；改动使证据失效时标记待复验。未执行的测试明确写“未执行”。

验证记录至少含执行日期、命令、目录、结果、适用版本/commit 和限制；无 commit 时说明工作区状态。文档检查只证明记忆结构与导航有效，无法证明业务功能正确。

不保存凭据、Token、公司资料、敏感日志、完整对话、源码副本。Git 负责代码历史，.memory 记录背景与交接。提交遵守 [规范](CONTRIBUTING.md)，未经授权不提交、不推送。
