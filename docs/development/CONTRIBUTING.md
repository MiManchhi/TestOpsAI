# 开发、目录与提交约定

正式开发同时遵守 [代码与格式规范](CODE_STYLE.md)、[目录归属](DIRECTORY_LAYOUT.md)、[记忆流程](MEMORY.md)。每个新 AI 会话从根 AGENTS 开始读取这些规则，不能仅凭上一轮印象继续。

并行开发遵守 [AI 协作流程](AI_COLLABORATION.md)：一个 worktree 一个活跃写入者，按任务归属修改，公共文件由集成方协调；用户执行主线合并。同步前保全未提交工作，代码、规范和记忆一起同步，冲突按事实归并。未经授权不提交、不推送、不自动 merge/rebase；分支默认使用 `codex/server-*`、`codex/web-*`、`codex/ai-*` 以供恢复脚本识别。

更新：2026-11-17。状态：IMPLEMENTED（规则生效，提交/推送须有当前任务授权）。

## 目录纪律

保持适度复杂度：按真实功能建立目录，域内先平铺，职责增多后再拆分；没有实现就不建空层，没有实际复用就不提取共享模块。目录数量和抽象层数不是工程质量指标，避免为单个文件连续套层。

根目录只保留工程入口及必要配置。业务代码归 apps 下对应应用；部署归 infra；文档归 docs；开发记录归 .memory；跨服务测试归 tests；示例数据归 samples；工具脚本归 scripts。工具缓存、下载和构建产物不得进入 Git。

新增文件先判断归属。禁止无目的重构、目录搬迁和随手创建根目录草稿；必要调整先说明原因、检查影响，再同步更新链接、脚本和配置。不覆盖既有用户文件。按功能成长建立目录，不创建大量无实现的空包。

## 提交格式

采用 Conventional Commits：`type(scope): 描述`。scope 可省略，优先 web、server、ai、infra、memory。描述写清变更目的，避免“修改一下”“update”等无信息标题。

| type     | 用途                 | 示例                             |
| -------- | -------------------- | -------------------------------- |
| feat     | 新功能               | feat(server): 增加知识库创建接口 |
| fix      | 修复缺陷             | fix(web): 修复会话切换后消息串流 |
| docs     | 文档与记忆           | docs(memory): 建立工程交接记录   |
| style    | 纯格式调整           | style(server): 统一 Java 格式    |
| refactor | 不改变外部行为的重构 | refactor(ai): 提取检索适配器     |
| perf     | 性能改进             | perf(server): 优化用例分页查询   |
| test     | 测试                 | test(server): 覆盖知识库越权访问 |
| build    | 依赖与构建           | build(web): 更新锁定依赖         |
| ci       | 持续集成             | ci: 增加类型检查任务             |
| chore    | 工程维护             | chore: 整理开发工具配置          |
| revert   | 撤回变更             | revert: 撤回错误的接口变更       |

破坏性变更在 type/scope 后加 `!`，正文写 `BREAKING CHANGE:`，说明影响和迁移办法。一个提交只解决一组相关问题；提交正文按需记录原因、验证与风险。格式约定由开发者/AI 执行，目前未安装 commit-msg hook，不能声称自动拦截。未经用户授权不创建提交或推送。

## 实现与验收

- 分支建议 `feat/<topic>`、`fix/<topic>`、`chore/<topic>`；提交采用 `feat:`、`fix:`、`docs:`、`test:`、`chore:` 等语义前缀。
- 一个变更解决一个可验证问题。PR 描述说明问题、行为变化、验证命令和剩余限制。
- Java 先按业务域分包，再在域内拆分接口、应用、持久化职责；避免万能 common 模块。Java 跨域技术放 platform，业务归 modules/业务域；Vue 的页面、请求与组件归 features/业务域，真实复用后提取 shared；Python 使用 src 布局与可替换外部适配器。
- 业务尚未出现时不建立几十个空包。Pinia、Router、MyBatis-Plus、Flyway、LangGraph 在对应里程碑引入，评审必要性及兼容性。
- Java 使用 Spotless / Google Java Format；Python 使用 Ruff；前端与文档使用 Prettier。格式修复：`npm run format`、`apps/server/mvnw.cmd -f apps/server/pom.xml spotless:apply`、`uv run --project apps/ai-service ruff format apps/ai-service`。
- 修改依赖后重新生成 lock，并运行检查；日常安装用 npm ci、uv sync --locked。禁止通过关闭 TLS 校验绕过下载问题。
- 测试放各应用内部；跨服务契约、浏览器 E2E 和 AI 评测分别在 tests 中按真实需求新增。
- 数据库引入后采用 Flyway 前进式迁移，测试应覆盖事务、越权、重试、幂等及删除补偿。
- 合并前 `scripts/check.ps1` 通过。容器变更额外构建镜像并运行 smoke；无法执行时明确写出原因。
