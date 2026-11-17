# 开发准入与规范闭环

更新：2026-11-17。状态：VERIFIED。

目标：复核目录和本机环境；明确每次新 AI 会话遵守目录/开发规范、恢复并更新记忆；建立正式代码与格式规范，验收后移交 M1。沿用适度设计，不建空目录，不擅自提交/推送，不提前实现业务。

## 实施与验收计划

- [x] 读取 AGENTS、INDEX、CURRENT、规范和现有质量门禁；检查实际 Git 状态。
- [x] 新增统一代码/格式标准，明确规则所有者、手动评审项和自动检查范围。
- [x] 加强 AGENTS 新会话入口、记忆检查及本地/CI 门禁，说明加载规则的边界。
- [x] 执行前置工具、完整检查、本机链路、Compose 配置与恢复验证。
- [x] 更新记忆和 M0→M1 交接，记录实际结果及保留限制。

起始状态：本地 main 无提交、无 remote；之前骨架检查/构建/本机 smoke 已通过，容器部署和远端 CI 未执行。开发依赖安全公告与测试工具弃用提示有历史记录，不能据此宣称生产可用。

## 已发生的验证与处理

- doctor 成功；包含新增规范导航检查的完整 check.ps1 退出 0，9 项目录和 3 项导航回归均通过；Vue 3 测试、Java 2 测试、Python 1 接口测试与三端构建均通过。
- 本机动态端口 smoke 退出 0；独立 PowerShell Restore 成功读取 AGENTS、INDEX、CURRENT、CONTRIBUTING、DIRECTORY_LAYOUT、CODE_STYLE 六入口。
- 源码/资料相对链接、UTF-8、全部工程 PowerShell 语法检查通过，根源码文件未增加；新增资料归既有 docs 目录。
- 官方 AGENTS 指南明确按运行启动发现规则且受作用域/override/合并体积影响。仓库增加显式重读规则和机器导航门禁；不宣称已验证模型未来每轮行为。
- 为 M1 核实基础设施时发现本机缺少三份镜像，Docker 官方常规拉取连接慢且部分层重试。未更改全局 Docker 代理设置；改用官方 Docker Hub 指定 tag 的 linux/amd64 manifest，逐层校验 SHA-256 的分片传输与本机导入进行中。一切转存仅 .runtime/image-transfer，不引入仓库工具或新业务目录。

- 官方镜像传输首次因 300 秒公开拉取令牌到期返回 HTTP 错误而终止（已确认进程结束）。保留已完成分片，加入令牌到期前刷新与一次有界重试后继续；未禁用 TLS 或使用第三方镜像源。

## 本轮产物与真实验收

日期：2026-11-17（Asia/Shanghai），仓库根目录，无 Git commit SHA。

- 新增 docs/development/CODE_STYLE.md：代码职责、命名、Java/TS/Python/PowerShell 格式所有者、复制可用的格式与检查命令、测试与完成标准。
- 新增 docs/ROADMAP.md：M0 准入状态和 M1.1 数据基线起点；没有生成业务代码或空目录。
- 更新 AGENTS、CONTRIBUTING、MEMORY、README：每个新会话六入口读取，任务过程/收尾更新记忆，说明作用域和机械检查的边界；未安装复杂 hook。
- 更新 memory.ps1：规范存在与 AGENTS 导航检查、六入口恢复、可测试 RootPath 参数；check.ps1 与 CI 接入记忆检查。
- 扩展既有工程测试：目录 9 项、导航 3 项、11 个 PowerShell 脚本语法检查；缺代码文档、缺 AGENTS 导航均被拒绝。
- 项目 VS Code 设置分语言保存格式：前端/文档 Prettier，Python Ruff，Java 由 Spotless 命令行唯一负责；不修改用户全局设置，不要求保存动作代替门禁。
- Qdrant 增加真实 /readyz Docker 健康检查；dev.ps1 infra 启动后调用 smoke.ps1 -InfraOnly，校验三服务状态、MySQL 应用用户查询、Redis 鉴权与 Qdrant 就绪。

| 实际命令/检查                                     | 结果                                                                                                       |
| ------------------------------------------------- | ---------------------------------------------------------------------------------------------------------- |
| scripts/doctor.ps1                                | 退出 0，JDK/Node/npm/CPython/uv 前置工具正确                                                               |
| scripts/check.ps1                                 | 退出 0；目录/导航、格式、Vue 类型/lint/3 测试/构建、Java 2 测试/打包、Python lint/类型/1 接口测试/构建通过 |
| scripts/smoke-local.ps1                           | 退出 0，三端本机健康、Java 401 和网页代理通过                                                              |
| 新 PowerShell memory.ps1 -Mode Restore            | 退出 0，六入口均输出；这是恢复流程验证，不是新模型行为保证                                                 |
| docker compose up -d --wait mysql redis qdrant    | 退出 0，三服务 running/healthy                                                                             |
| scripts/dev.ps1 infra                             | 退出 0，健康状态、SELECT 1、Redis 鉴权与 Qdrant ready 验证通过，可重复执行                                 |
| Qdrant API Key 请求与无 Key 请求                  | 正确 Key 返回 collections；无 Key 返回 401/403，未输出密钥                                                 |
| 官方镜像逐层摘要                                  | 三镜像官方 manifest/config、压缩层和解压 diff_id 均校验成功后导入                                          |
| 文档链接、UTF-8、PowerShell AST、编辑器格式所有者 | 通过；根源码文件仍 13 个，原始立项方案保持原文                                                             |

## 收尾与下一阶段

M0 的目录、本机环境、开发规范、记忆、基础容器已达到本项目开发准入；可进入 M1.1：冻结用户/角色数据模型与契约，随后引入迁移及真实数据库测试。未擅自开始业务或提交/推送。

保留限制：完整应用容器部署、远端 CI 未执行；开发依赖历史安全公告和框架弃用提示仍按 CURRENT 维护。基础容器留在本机运行供开发；官方下载临时材料仅保留在 .runtime。所有记忆不含密码、Token、公司机密或完整对话。

补充验证：外层 PowerShell 5 的 *> 日志重定向曾将 Mockito 标准错误提示升级为 NativeCommandError，未据此修改或屏蔽测试；改为进程级 stdout/stderr 采集并按退出码复验，结果记录在本机 .runtime/gate-2026-11-17-final.log。该本机日志不纳入记忆或 Git。

## 用户最终边界（2026-11-17）

用户确认只需完成搭建，不进行下一步。当前交付停在 M0；M1 仍 PLANNED，等待另行明确任务，不自动开展数据建模、迁移或业务实现。规范与记忆维护持续适用。
