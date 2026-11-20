# A1 内部服务身份与委托校验

更新：2026-11-20（Asia/Shanghai）。状态：VERIFIED（A1 校验器，接口尚待后续对接）。

<!-- handoff:v1 -->

Task: WORKSPACE-G1-AI-001
Role: ai
Baseline: 4c21d1a704c26ebb12c41ec966d07f6d5c6f3c7b
Contract: 工作台内部 v2，原内部 v1 兼容。
Scope: 实现按工作台动作校验 Java 短期服务令牌及委托，修改 AI 工作区。

## 验证

<!-- checks -->

- 在 AI worktree 的 apps/ai-service 执行 `uv run --locked ruff check .`、`uv run --locked mypy`、`uv run --locked pytest -q`，均退出 0，117 通过、真实 Qdrant 和 Embedding 配置缺失的 2 项跳过。
- 新测试核对公共 fixture 摘要及 RS256 签名、跨项目、错误版本、动作错配和过期行为。现有内部 v1 解析与索引测试同时通过。

## 交接

<!-- next -->

S3/A3 对接块导出后使用已实现的 verify_workspace_delegation 校验索引授权；A4 问答在生成前校验 AnswerScope。Java 签发方与真实 HTTP 往返尚未完成。
