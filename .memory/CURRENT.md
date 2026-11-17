# 当前交接

更新：2026-11-17（Asia/Shanghai）。状态：VERIFIED（M0 正式开发准入；M1 待开始）。

## 当前任务

2026-11-17 新授权：先将已搭建的 M0 工程提交并推送至 git@github.com:MiManchhi/TestOpsAI.git；不进入业务开发。当前远程 main 初始提交仅含 MIT LICENSE，本地已保留并跟踪；首次工程提交与推送准备中，见 [session](sessions/2026-11-17-02-first-remote-push.md)。此前不自动进入 M1 的边界继续有效。

确认本机环境、建立正式代码与格式标准、每个新 AI 会话读取规则与记忆。本轮已完成，用户明确要求停留 M0，不进入下一阶段。新规范见 [CODE_STYLE](../docs/development/CODE_STYLE.md)，本轮见 [session](sessions/2026-11-17-01-development-standards.md)。此前适度目录整理已完成且继续遵守，不创建空业务目录。

## 已完成与待验收

- 此前根源码文件为 13 个；保留远程 LICENSE 后为 14 个；Java platform/security、Vue app/features/health、Python app/core/features/health 已迁移；无新增业务、无空业务目录。
- .tools 只保留 toolchains/cache；60 项旧遗留归档至忽略的 .runtime，保留回退能力，没有递归删除。
- doctor/setup 成功；Vue 全量检查及构建通过，Java 2 项测试和打包通过，Python 锁定依赖、lint、类型、测试和构建通过；本机三端、代理与安全边界 smoke 已通过。
- 目录检查及 9 项回归通过；scripts/check.ps1 退出 0，覆盖格式、Vue 类型/lint/3 测试/构建、Java 2 测试/打包、Python lint/类型/1 接口测试/sdist 与 wheel 构建及 smoke 脚本质量检查。Java clean verify 和本机动态端口 smoke 另行通过。
- Git 为 main，已有远程 LICENSE 初始父提交并跟踪 origin/main；本轮用户明确授权首次工程提交与推送，其他任务仍不自动提交/推送。原始 PROJECT_BLUEPRINT 保留原文。

## 限制与后续

- MySQL、Redis、Qdrant 已 running/healthy；应用用户 SQL 查询、Redis 鉴权、Qdrant ready 与 API Key/未授权拒绝验证通过。完整应用容器部署和远端 CI 仍未执行。
- npm audit 检出一条 minimatch 公告（globparts amplification，GHSA-g3hv-75wg-m249），影响 7 个开发工具依赖包；0 high/critical。当前自动建议会跨版本降级 typescript-eslint，未盲目执行。后续单独评估兼容修复；不是新增业务可用性结论。
- pytest 有 Starlette 对 httpx TestClient 的弃用提示；测试通过，后续随测试工具升级处理。
- Java Mockito 动态 agent 提示不影响当前测试；后续按 JDK 升级评估。
- 原始立项范围内的 JWT/RBAC、数据库业务、RAG、LangGraph、用例和知识库业务仍 PLANNED。

## 后续边界

用户于 2026-11-17 明确要求：搭建完成即可，不进行下一步。本轮停止在 M0，不自动实现 M1 的数据模型、迁移或业务代码；M1 路线图仅为将来参考，等待用户明确新任务。每个新会话仍需读取规范和记忆，不能把“准入已满足”解释为继续业务开发的授权。

本轮新增 CODE_STYLE、ROADMAP 和六入口恢复，记忆检查接入本地/CI；结构回归 9 项、规范导航正反例 3 项，共 12 项及 11 份 PowerShell 语法检查通过，15 份活动记忆有效。规范检查验证文件/导航和记录格式，不能证明模型对每一条文字规则都已遵从。

基础服务已留在本机运行，供 M1 使用；可 docker compose stop mysql redis qdrant 停止而保留卷。不上传密钥；本轮已配置 origin 并保留远程初始提交，工程提交和推送状态见本轮 session。官方镜像传输已逐层校验压缩/解压 SHA-256 后导入，临时传输材料仅在忽略的 .runtime/image-transfer。
