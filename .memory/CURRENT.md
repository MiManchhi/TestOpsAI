# 当前交接

更新：2026-11-17（Asia/Shanghai）。状态：IN_PROGRESS（M1.1 身份数据基线与错误契约已 VERIFIED；待提交）。

## 当前任务

M1.1 身份数据基线已完成实施与本机验证：users/roles/user_roles 迁移、结构化错误 {code,message,requestId}、X-Request-Id 头、集成测试覆盖约束与请求追踪。完整门禁 scripts/check.ps1 通过；IdentitySchemaIntegrationTest 在无 MYSQL_PASSWORD 环境下按预期跳过。详见 [session](sessions/2026-11-17-06-m1-1-data-baseline.md)。

此前任务已完成：用户明确授权后，容器交付提交 173fe32 正常推送 origin/main 并核对 SHA 一致；[CI 44026578462](https://github.com/MiManchhi/TestOpsAI/actions/runs/44026578462) completed/success，server/web/ai/compose/containers 五个 job 均成功。容器日志确认重复启动、保留卷停启、停止 AI 负例及恢复通过，最后仅清理 testops-ci-44026578462-1 的六容器、网络及三个临时卷。BLOCKED：无本次任务遗留阻塞；收尾文档随独立 docs 提交同步，最新 tip 以 Git refs 为准。

本机六服务已 DEPLOYED（Docker Desktop Linux x86_64，2026-11-17），入口 http://127.0.0.1:8088。完整/基础冒烟、非 root/AI 精简镜像、重复启动、保留卷停启、停止 AI 的退出码 1 负例、完整工程门禁及用户入口 scripts/dev.ps1 all 均通过；本机三个数据卷保留，最终六服务 healthy。过程见 [容器会话](sessions/2026-11-17-05-container-delivery.md)。保持 M0，不自动开始 M1，不修改业务。

2026-11-17 最新要求已完成：Python 改用 sys.platform 显式分支，本地/CI 添加 linux、win32 双目标检查；目录回归对隐藏点文件使用 Force，并在 Windows 显式创建 Hidden 夹具。提交 9fb1437 已正常推送 origin/main 并核对 SHA 一致；双目标 mypy、完整 scripts/check.ps1、本机 smoke 和 [Ubuntu CI 44011247548](https://github.com/MiManchhi/TestOpsAI/actions/runs/44011247548) 均通过，server/web/ai/compose 四个 job 成功。见 [修复会话](sessions/2026-11-17-04-ci-portability-fix.md)。BLOCKED：无本次 CI 修复遗留阻塞；继续停留 M0，等待用户新任务。收尾记忆随独立 docs 提交同步，最新 tip 以 Git refs 为准。

2026-11-17 新授权：先将已搭建的 M0 工程提交并推送至 git@github.com:MiManchhi/TestOpsAI.git；不进入业务开发。远程原始 MIT LICENSE 已保留；工程提交 977591d 已正常推送并核对本地/远程 SHA 一致，本地 main 跟踪 origin/main，见 [session](sessions/2026-11-17-02-first-remote-push.md)。此前不自动进入 M1 的边界继续有效。

确认本机环境、建立正式代码与格式标准、每个新 AI 会话读取规则与记忆。本轮已完成，用户明确要求停留 M0，不进入下一阶段。新规范见 [CODE_STYLE](../docs/development/CODE_STYLE.md)，本轮见 [session](sessions/2026-11-17-01-development-standards.md)。此前适度目录整理已完成且继续遵守，不创建空业务目录。

## 已完成与待验收

- 此前根源码文件为 13 个；保留远程 LICENSE 后为 14 个；Java platform/security、Vue app/features/health、Python app/core/features/health 已迁移；无新增业务、无空业务目录。
- .tools 只保留 toolchains/cache；60 项旧遗留归档至忽略的 .runtime，保留回退能力，没有递归删除。
- doctor/setup 成功；Vue 全量检查及构建通过，Java 2 项测试和打包通过，Python 锁定依赖、lint、类型、测试和构建通过；本机三端、代理与安全边界 smoke 已通过。
- Windows 本机目录检查及 9 项回归通过；scripts/check.ps1 退出 0，覆盖格式、Vue 类型/lint/3 测试/构建、Java 2 测试/打包、Python lint/类型/1 接口测试/sdist 与 wheel 构建及 smoke 脚本质量检查。Java clean verify 和本机动态端口 smoke 另行通过。这些证据不覆盖 Linux，不能据此认定远端 CI 通过。
- Git main 跟踪 origin/main，origin 为 git@github.com:MiManchhi/TestOpsAI.git；工程提交 977591d 已发布，未强推。该任务的提交/推送已获用户授权，其他任务仍不自动提交/推送。原始 PROJECT_BLUEPRINT 保留原文。

## 限制与后续

- 本机六服务部署与五项远端 CI 均通过，三项本机基础卷保持。原失败 run 44009166249 留作历史；本次完整验收以 173fe32 的 run 44026578462 为证据。六服务健康不等于尚未实现的存储业务或 Java/AI 调用链通过；未做公网部署或镜像发布。
- npm audit 检出一条 minimatch 公告（globparts amplification，GHSA-g3hv-75wg-m249），影响 7 个开发工具依赖包；0 high/critical。当前自动建议会跨版本降级 typescript-eslint，未盲目执行。后续单独评估兼容修复；不是新增业务可用性结论。
- pytest 有 Starlette 对 httpx TestClient 的弃用提示；测试通过，后续随测试工具升级处理。
- Java Mockito 动态 agent 提示不影响当前测试；后续按 JDK 升级评估。
- 原始立项范围内的 JWT/RBAC、数据库业务、RAG、LangGraph、用例和知识库业务仍 PLANNED。

## 后续边界

用户于 2026-11-17 明确要求：搭建完成即可，不进行下一步。本轮停止在 M0，不自动实现 M1 的数据模型、迁移或业务代码；M1 路线图仅为将来参考，等待用户明确新任务。每个新会话仍需读取规范和记忆，不能把“准入已满足”解释为继续业务开发的授权。

本轮新增 CODE_STYLE、ROADMAP 和六入口恢复，记忆检查接入本地/CI；结构回归 9 项、规范导航正反例 3 项，共 12 项及 11 份 PowerShell 语法检查通过，15 份活动记忆有效。规范检查验证文件/导航和记录格式，不能证明模型对每一条文字规则都已遵从。

基础服务已留在本机运行，供 M1 使用；可 docker compose stop mysql redis qdrant 停止而保留卷。不上传密钥；首次工程提交已推送，细节见本轮 session；本次记忆收尾随独立 docs 提交同步，当前 tip 以 Git refs 为准。官方镜像传输已逐层校验压缩/解压 SHA-256 后导入，临时传输材料仅在忽略的 .runtime/image-transfer。
