# 百炼 401 配置排查

更新：2026-11-24（Asia/Shanghai）。状态：IN_PROGRESS（用户自测聊天已恢复；资料解析仍被 PROFILE_CHANGED 阻塞）。

<!-- handoff:v1 -->

Task: BAILIAN-AUTH-002
Role: integration
Baseline: 本机 main 4c21d1a，工作台三端已整合。
Contract: AI 聊天 Chat Completions 与资料 Embeddings 的兼容接口。
Scope: 排查资料解析失败和聊天失败。仅读取本机配置、调用一次最小探测请求、更新交接，不打印密钥。

## 核查与验证

<!-- checks -->

- 容器配置：聊天地址为 `https://dashscope.aliyuncs.com/compatible-mode/v1`，模型 `qwen-plus`；向量地址为其 `/embeddings`，模型 `qwen3.7-text-embedding`，维数 1024。
- 本机两个 API Key 都识别为 `sk-sp-` Token Plan 格式。Key 长度和前缀已核对，完整内容没有输出。
- 从 AI 容器发最小聊天 JSON 和向量 JSON 请求，均返回 HTTP 401，错误码 `invalid_api_key`，消息为 `Incorrect API key provided`。
- 页面截图中的资料状态 `EMBEDDING_UNAVAILABLE` 与聊天 `ANSWER_MODEL_FAILED` 是该 401 在 Java/Web 的业务投影。
- AI 服务已把百炼 401/403 映射为 `ANSWER_PROVIDER_AUTH_FAILED` 和 `EMBEDDING_PROVIDER_AUTH_FAILED`，Java 与 Web 会显示明确的 API Key 与 Base URL 配置提示，不再只显示通用调用失败。
- 定向 Python 测试通过，15 passed、3 skipped。`scripts/local.ps1 start` 完成六容器构建与冒烟检查，Web、Java、AI、MySQL、Redis、Qdrant 均为 healthy。`memory Check`、`git diff --check` 和布局检查通过。
- 真实浏览器复核：Demo Project 中发送“你好”显示“聊天模型拒绝了 API Key”；对既有技术规范 V1 点击“重试解析”后显示“向量服务拒绝了 API Key”。

## 交接

BLOCKED：Token Plan 官方使用范围不允许把套餐 Key 放入自建应用后端自动调用。解除条件是用户提供并在本机 `.env` 配置百炼标准 API Key（通常 `sk-`，不是 `sk-sp-`），保持同地域 Base URL，再运行 `scripts/local.ps1 start -SkipBuild`。随后点击资料的“重试解析”，新开聊天验证。

<!-- next -->

用户配置标准 API Key 后，重新加载服务，重试 V1 解析并发送一条聊天问题。记录百炼返回状态、索引状态和来源引用。

## 2026-11-23 复诊：换成 maas 中转后仍失败

<!-- checks -->

- 2026-11-24 用户自报：聊天实测可用。`.env` 聊天地址已改为 `https://token-plan.maas.qianwenaiapi.com/compatible-mode/v1`，向量地址未变；上一轮 403 `insufficient_quota` 只对应旧聊天地址，本轮未重放请求。资料解析仍未恢复。
- 2026-11-23 时点配置（已脱敏）：聊天与向量都指向 `https://maas.qianwenaiapi.com/compatible-mode/v1`；两处 Key 同为 `sk-ws` 前缀、长度 116。现聊天已改为 token-plan 地址，向量未变。
- 容器内最小探测：`/chat/completions` 返回 HTTP 403 `insufficient_quota`（Free quota exhausted）；`/embeddings` 返回 HTTP 200 且维数正常。Key 对该中转站有效，聊天失败是免费额度耗尽，不是 Key 与 Base URL 不匹配。
- 聊天记录 `workspace_chat_turn.failure_code=ANSWER_PROVIDER_AUTH_FAILED`（05:45–06:10 共 6 条），AI 日志对应 `POST /internal/v2/ai/answer 503`。生成代码把 401/403 一律映射为鉴权失败，页面提示因此与真实原因不符。
- 资料解析：`workspace_doc_version.error_code=PROFILE_CHANGED`；索引任务 06:08 提交连续失败 3 次后 FAILED。SQLite `profiles` 记录 `default` 的指纹为 `3317e6…`，等于 dashscope 地址；当前配置指纹为 `62c10c…`（maas 地址）。`bind_profile` 检测到同名定义变化即返回 503 `PROFILE_CHANGED`，请求没有到达向量服务。
- Qdrant 仅 `testops_knowledge_37a8eec1ce19687d` 一个集合、0 点；05:44 的 `EMBEDDING_UNAVAILABLE` 与 06:01 的 `EMBEDDING_PROVIDER_AUTH_FAILED` 是换地址、换 Key 过程中的历史失败。
- Web 的 `indexErrors` 未收录 `PROFILE_CHANGED`，页面直接显示原始错误码。

## 交接（复诊）

RESOLVED（聊天，用户自测 2026-11-24）：改用 token-plan 聊天地址后可用。旧聊天地址的 `insufficient_quota` 仍建议在代码侧与真正的 401 区分，避免同样提示误导。
BLOCKED（解析）：Embedding profile 指纹包含 URL，改地址后同名 profile 被拒绝。可行恢复一是确认旧集合无数据后重置 `default` 绑定，二是改用新的 profile 名并同步修改 `TESTOPS_WORKSPACE_EMBEDDING_PROFILE`，随后重试解析。两者都会改运行配置或持久数据，需用户确认后执行。

## 2026-11-24 提交检查点

- 工作台三端整合已提交 `21f6170`（`feat(workspace): 交付单工作台三端整合`），提交前 `scripts/check.ps1` 退出 0；Maven 22 项中 16 项因未提供隔离数据库凭据跳过。
- 本记忆与下一轮解析修复在同一工作区继续；资料解析仍未恢复。
