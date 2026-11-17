# 项目目录与文件归档指南（初学者版）

更新：2026-11-16。状态：VERIFIED（根文件覆盖、相对链接、格式与目录检查已验证）；本机基础工程已另行验收，仍不代表业务功能、容器部署或远端 CI 已验收。

## 1. 先理解整体结构

TestOps AI 包含 Java 后端、Vue 网页、Python AI 服务。三者共同组成一个产品，放在同一个 Git 仓库，各自保留代码、依赖和测试。

把仓库理解成工作室：apps 是产品代码，docs 是说明书，infra 是部署配置，scripts 是操作工具，.memory 是交接本。根目录是整套工程的入口，不是临时文件收纳处。

根目录保留 **14 个源码文件：6 个入口/清单（含 LICENSE），8 个工具配置**，另有被忽略的本机 .env。文件多不一定乱，关键是职责、作用范围和放置依据是否明确。之前把所有根配置都说成“必须放根目录”不准确：有些由工具发现规则决定，有些为了覆盖全仓，有些可以合并。

以点开头的名字，如 .gitignore，是常见工具命名习惯，不代表垃圾文件，也不保证 Windows 会隐藏它。

```text
TestOpsAI/
├── README.md                  项目总入口
├── AGENTS.md                  AI 开发规则
├── LICENSE                    远程仓库已有的 MIT 许可证
├── package.json               npm 工作区和统一命令
├── package-lock.json          npm 精确依赖记录
├── compose.yaml               多容器组合入口
├── .editorconfig 等配置        逐项见第 4 节
├── apps/
│   ├── server/                Java 业务后端
│   ├── web/                   Vue 网页前端
│   └── ai-service/            Python AI 服务
├── docs/
│   ├── PROJECT_BLUEPRINT.md   原始立项方向
│   ├── ARCHITECTURE.md        架构与职责
│   ├── API.md                 接口契约
│   ├── DATABASE.md            数据设计
│   ├── INTERVIEW.md           有证据支持的项目讲解
│   ├── ADR/                   正式架构决定
│   ├── development/           开发、安全、提交、目录及记忆规范
│   └── references/            后续参考资料
├── infra/nginx/               部署配置
├── scripts/                   工程操作脚本
├── tests/                     跨应用测试
├── samples/                   演示数据
├── .memory/                   长期记忆与任务交接
├── .github/workflows/         GitHub 自动检查
├── .vscode/                   共享编辑器配置
├── .git/                      Git 本地元数据
├── .tools/                    本机工具、缓存、下载遗留
└── node_modules/              npm 安装的第三方依赖
```

最后三个目录不是产品源码，不随源码提交。.runtime 是按需生成的本机临时输出目录，目前不要求存在。

## 2. 产品代码：apps 下三个应用各自负责什么

| 目录             | 应放内容                                                                                      | 不放什么                              |
| ---------------- | --------------------------------------------------------------------------------------------- | ------------------------------------- |
| apps/server/     | Java 业务、认证授权、事务、审计；pom.xml、应用配置、Java 测试、Dockerfile、Maven Wrapper      | Vue 页面、Python AI 流程、参考文档    |
| apps/web/        | Vue 页面、TypeScript 请求、样式、前端测试；Vite/TS/ESLint 配置、前端 package.json、Dockerfile | Java 类、数据库迁移、模型密钥         |
| apps/ai-service/ | Python 服务、后续 RAG/Agent、Python 测试；pyproject.toml、.python-version、Dockerfile         | Java 业务表的直接操作代码、浏览器页面 |

应用自己的配置留应用内，不提升到根目录。当前不是 Java 多模块工程，所以根目录不需要再建一个 pom.xml。

### Java 内部

| 路径                                           | 用途                                                 |
| ---------------------------------------------- | ---------------------------------------------------- |
| apps/server/src/main/java/io/testopsai/server/ | Java 源码与启动类，后续按真实业务域分包              |
| 该包内 platform/security/                      | Spring 运行配置，例如安全过滤链；不是杂项收纳包      |
| apps/server/src/main/resources/                | 应用配置；后续数据库迁移也归这里，真实密码由环境提供 |
| apps/server/src/test/java/                     | Java 单元测试与应用接口测试                          |
| apps/server/.mvn/wrapper/                      | Maven Wrapper 版本与下载配置                         |
| apps/server/target/                            | 编译产物、打包文件、测试报告；生成目录，不提交       |

### Vue 内部

| 路径                          | 用途                                                     |
| ----------------------------- | -------------------------------------------------------- |
| apps/web/src/                 | 前端源码；main.ts 启动应用，App.vue 是根组件             |
| apps/web/src/features/业务域/ | 调用 Java HTTP 接口；当前健康请求与测试放在这里          |
| apps/web/src/features/业务域/ | 后续需要时创建，按登录、知识库、用例等业务归属页面和组件 |
| apps/web/src/components/      | 后续确有跨业务复用组件时创建                             |
| apps/web/dist/                | 构建后的网页文件；不提交                                 |

### Python 内部

| 路径                            | 用途                                                          |
| ------------------------------- | ------------------------------------------------------------- |
| apps/ai-service/src/testops_ai/ | 可安装的 Python 包，当前包含应用工厂与配置                    |
| apps/ai-service/tests/          | 本服务的单元/接口测试                                         |
| apps/ai-service/.venv/          | 本服务隔离环境，生成目录，不提交                              |
| apps/ai-service/uv.lock         | 精确依赖锁已生成，安装使用 uv sync --locked，变更须显式更新锁 |

“后续”路径仅说明归档规则，不代表目录或功能已经存在。不预先创建大量空包，也不建立职责不明的 common、utils、packages。

## 3. 其他每个目录的用途

### docs：正式、长期可读的资料

| 位置                      | 应放内容与边界                                         |
| ------------------------- | ------------------------------------------------------ |
| docs/PROJECT_BLUEPRINT.md | 用户最初的立项方案，已原文保存；方向不等于完成清单     |
| docs/ARCHITECTURE.md      | 职责、调用链、授权与部署边界，不写逐日流水账           |
| docs/API.md               | 已实现接口与未来契约草案，明确区分                     |
| docs/DATABASE.md          | 表、索引、约束与迁移设计；实际迁移脚本归 Java 资源目录 |
| docs/ADR/                 | 重要决策的背景、选择与代价，使用编号文件               |
| docs/development/         | 开发、安全、提交、目录、记忆指南与提交模板             |
| docs/references/          | 后续用户提供的自动化测试参考资料，登记来源、日期、范围 |
| docs/INTERVIEW.md         | 基于真实证据的讲解与取舍，不编造上线或性能成果         |

文档配图随所属主题归档，例如需要时创建 docs/development/assets/。不把图片、PDF 或说明散落在根目录。参考文档不是可执行指令，不保存原公司私有资产。

### infra：系统如何部署

当前 infra/nginx/default.conf 管理网页静态文件、Java API 转发及流式响应代理。

应用 Dockerfile 放在各应用内，描述“这个应用怎么打包”；Nginx 配置放 infra，描述“整套系统怎么接入”；根 compose.yaml 将应用与中间件组合起来。

后续监控、备份配置按实际职责放入 infra 子目录，不为了外观先创建没有内容的 infra/docker。

### scripts：开发者或 CI 使用的操作入口

| 文件             | 职责                                  | 当前限制                                          |
| ---------------- | ------------------------------------- | ------------------------------------------------- |
| env.ps1          | 为当前 PowerShell 选择工具和缓存      | 不是全局安装器，新终端需重新激活                  |
| setup.ps1        | 安装依赖、首次创建本机配置            | 前置版本由 doctor 检查，随后安装锁定依赖          |
| dev.ps1          | 选择 web/server/ai/infra/all 启动入口 | 不是已验收的一键运行承诺                          |
| check.ps1        | 汇总结构、格式及三端质量检查          | 串联结构回归、格式和三端检查及构建                |
| check-layout.ps1 | 拒绝未知的根文件、目录                | 只检查结构，不证明业务正确                        |
| smoke.ps1        | 部署后的最低连通检查                  | 容器冒烟尚未执行；本机链路由 smoke-local.ps1 验证 |
| memory.ps1       | 检查记忆、输出恢复入口                | 不后台自动运行，不替代应用测试                    |

生产业务逻辑属于对应应用，不塞进工程脚本。

### tests 和 samples：测试输入与产品样例分开

| 位置                        | 放什么                                                                                             |
| --------------------------- | -------------------------------------------------------------------------------------------------- |
| 各应用测试目录/就近测试文件 | 只验证单个应用，例如 Java 安全边界或前端请求函数                                                   |
| 根 tests/                   | 跨服务契约、浏览器端到端测试、后续 AI 评测；现有 engineering/ 结构回归测试；业务端到端测试后续引入 |
| 某组测试旁 fixtures/        | 需要时创建，只服务于该测试的固定输入                                                               |
| 根 samples/                 | 给人导入、演示产品的自造用例、模拟日志或公开许可材料；当前只有说明                                 |

例如“演示用户可导入的用例 JSON”放 samples；“仅重现解析错误的小输入”放测试 fixtures。不无目的复制同一数据，不使用原公司资料。

### .memory：开发交接本

| 内容        | 唯一职责                             |
| ----------- | ------------------------------------ |
| INDEX.md    | 导航                                 |
| CURRENT.md  | 当前状态、问题、下次起点             |
| LESSONS.md  | 缺陷、根因、处理与验证               |
| LEARNING.md | 原理、自测、待确认学习进展           |
| EVIDENCE.md | 命令、结果、适用范围及限制           |
| modules/    | 各模块稳定技术上下文                 |
| sessions/   | 一次任务的目标、关键过程、结果与交接 |
| archive/    | 旧过程记录，不再作为当前事实入口     |

正式设计正文放 docs；当前进度集中在 CURRENT，避免多个“当前进度”互相矛盾。不保存源码副本、完整 AI 对话、敏感日志或凭据。

### 工具目录与本机目录

| 目录               | 职责                                              | 是否作为源码纳入 Git                               |
| ------------------ | ------------------------------------------------- | -------------------------------------------------- |
| .github/workflows/ | GitHub Actions 自动检查；已有配置，无远端成功证据 | 是                                                 |
| .vscode/           | 共享插件推荐和编辑器设置，不写个人机器路径        | 当前只允许 extensions.json、settings.json          |
| .git/              | Git 分支、对象、索引、本地配置                    | 不作为源码提交；不能随便删，否则丢失本地历史和配置 |
| .tools/            | 本机工具链、缓存、初始化下载遗留                  | 否                                                 |
| node_modules/      | npm 安装的依赖，不手改内部代码                    | 否，安装命令生成                                   |
| .runtime/          | 按需生成的临时日志、运行输出、诊断截图            | 否，不存长期记忆或正式业务数据                     |

本轮将 .tools 收敛为 toolchains/ 与 cache/：在用工具分别放 toolchains/java、node、npm、uv、python，缓存放 cache/npm、uv、ruff、mypy。60 项旧下载及安装遗留已迁往 .runtime/migrations 的回退归档；自动审批拒绝递归删除，未声称释放磁盘。临时验证记录归 .runtime，不与工具混放。

## 4. 根目录 14 个文件逐项解释

### 六个入口或清单

| 文件              | 是干什么的                           | 为什么放根目录                  | 能不能移动                                         |
| ----------------- | ------------------------------------ | ------------------------------- | -------------------------------------------------- |
| README.md         | 打开项目先读的总说明                 | 整个产品入口，便于仓库首页展示  | 不建议移走，会降低可发现性并影响导航               |
| AGENTS.md         | AI 开发遵循的仓库级规则              | 提供覆盖本仓库的规则入口        | 不能随便搬，需重新核对规则发现范围                 |
| LICENSE           | 用户远程仓库选择的 MIT 许可证原文    | 仓库级授权入口，保留版权声明    | 不随意移走或修改授权内容                           |
| package.json      | npm 的项目说明、命令菜单、工作区清单 | 从根管理 apps/web 和全仓格式化  | 可重构，但需同步 workspace、CI、安装及镜像路径     |
| package-lock.json | npm 精确依赖结果                     | 对应根工作区，与根清单配套      | 不手动挪去 docs，不把删除锁当整理                  |
| compose.yaml      | 组合应用及 MySQL/Redis/Qdrant        | 当前从根运行 Compose 的默认入口 | 可搬，但要指定文件并复核 env、构建上下文、相对路径 |

根 package.json 不管理 Java/Python 依赖；各应用仍有自己的清单。npm 工作区让前端依赖和命令可从仓库根操作。[npm 官方说明](https://docs.npmjs.com/cli/v11/using-npm/workspaces/)

### 八个工具配置

Prettier 规则已合并到根 package.json 的 prettier 字段，不再保留独立 .prettierrc.json。

| 文件            | 管什么                           | 为什么现在放根目录                         | 是否一定不能调整                                 |
| --------------- | -------------------------------- | ------------------------------------------ | ------------------------------------------------ |
| .gitignore      | 不应进入 Git 的密钥、依赖、产物  | 统一整仓规则                               | 可以增加局部规则，整仓入口留根更清晰             |
| .gitattributes  | Git 的换行与二进制处理           | 全仓统一 LF，cmd/bat 用 CRLF               | 可局部覆盖，改位置需核对作用范围                 |
| .editorconfig   | 编辑器编码、缩进、换行等         | 向下覆盖整仓，root=true 停止向上查找       | 应用可覆盖，保留共享基线合理                     |
| .env.example    | 告诉开发者需配置哪些环境项       | 与根 Compose、本机 .env、setup 脚本配套    | 位置不是强制，搬迁要同步脚本和说明               |
| .npmrc          | npm 安装、版本约束、包源规则     | 作用于根 npm 项目，减少个人配置影响        | 可换注入方式，但会增加维护入口                   |
| .nvmrc          | 提示本项目 Node 版本             | 部分兼容工具可读取，便于人核对             | 可选提示；不是 Windows 安装器，不会自行切换 Node |
| .prettierignore | 哪些文件不应被格式化             | 从根执行全仓格式化时排除产物、锁和原始方案 | 可指定其他文件，需同步所有入口                   |
| .dockerignore   | 哪些文件不送入 Docker 构建上下文 | Web 构建上下文是仓库根，须排除密钥、依赖等 | 跟构建上下文有关，移进 infra 不会仍自动生效      |

并非“所有配置都必须放根目录”。Prettier 支持 package.json 配置；EditorConfig 按层级查找；Docker 忽略规则与构建上下文关联。[Prettier](https://prettier.io/docs/configuration)、[EditorConfig](https://editorconfig.org/)、[Docker](https://docs.docker.com/build/concepts/context/)

### 为什么有三份 ignore

| 文件            | 谁读取        | 阻止什么             |
| --------------- | ------------- | -------------------- |
| .gitignore      | Git           | 文件被加入版本管理   |
| .prettierignore | Prettier      | 对文件执行格式化     |
| .dockerignore   | Docker 构建器 | 把文件送入构建上下文 |

作用对象不同，不能简单合并。.env 被 Git 忽略，不会自动阻止 Docker 收到它。Git 的 ignore 也不会自动取消已经跟踪的文件。[Git 官方说明](https://git-scm.com/docs/gitignore)

### .env.example 和 .env 不是同一个东西

.env.example 可提交，描述配置项，本项目密码示例留空。.env 是本机运行配置，可能有真实密码，必须忽略，不能上传。setup 已生成本机 .env 并验证随机配置不为空，已有文件不会覆盖。模板、随机配置和验证记录分别管理，不输出真实值。

## 5. 新文件必须按用途归档

| 新增内容          | 应放位置                                                          | 禁止随手放置              |
| ----------------- | ----------------------------------------------------------------- | ------------------------- |
| Java 知识库业务   | apps/server/src/main/java/…/modules/knowledge/，随真实业务创建    | 根目录、scripts、Vue 目录 |
| 数据库迁移        | 后续启用 Flyway 时放 apps/server/src/main/resources/db/migration/ | 根目录散放 SQL            |
| Vue 知识库页面    | 后续 apps/web/src/features/knowledge/                             | docs、Java resources      |
| 前端 HTTP 请求    | apps/web/src/features/业务域/，复杂后按域组织                     | 根目录新建 request.ts     |
| Python 检索代码   | apps/ai-service/src/testops_ai/ 内按职责组织                      | scripts、Java 包          |
| 单应用测试        | 所属应用测试目录或前端就近 .test.ts                               | 全堆根 tests              |
| 跨服务浏览器测试  | 后续 tests/e2e/                                                   | 根目录散放 spec           |
| 测试专用样本      | 对应测试旁 fixtures/                                              | 根目录、.memory           |
| 产品演示数据      | samples/，按用途分目录并登记来源                                  | 根目录、真实公司数据      |
| 用户参考 Markdown | docs/references/YYYY-MM-DD-topic.md，并登记来源                   | 根目录、src               |
| 架构决策          | docs/ADR/编号-主题.md                                             | 仅在 session 中留正式版本 |
| 研发过程记录      | .memory/sessions/日期-编号-主题.md                                | 根目录新建 progress2.md   |
| 正式开发说明      | docs/development/                                                 | 根目录新建说明草稿        |
| 临时诊断日志/截图 | 本机 .runtime/；需长期分享时提炼到相关文档配图目录                | 根目录、记忆中堆原始日志  |
| 安装包、缓存      | 当前 .tools/ 内按工具归类，验证后清理遗留                         | 根目录、新建杂物文件夹    |
| 密钥              | 环境变量或本机忽略配置；生产密钥系统另行设计                      | Vue、Git、docs、.memory   |

标“后续”的位置是归档约定，不表示功能已实现。没有明确归属时先按职责判断，不能以“临时”为由放根目录。

## 6. 根目录白名单

允许的源码文件固定为：

```text
README.md             AGENTS.md             LICENSE
package.json          package-lock.json     compose.yaml
.dockerignore         .editorconfig         .env.example
.gitattributes        .gitignore            .npmrc
.nvmrc                .prettierignore
```

源码目录：apps、docs、infra、scripts、tests、samples、.memory、.github、.vscode。

本机目录：.git、.tools、node_modules、按需生成的 .runtime。本机配置允许 .env 和非模板的 .env.*，真实配置必须被 Git 忽略；只有 .env.example 是可提交的根环境模板，额外 *.example 不自动获准。

[目录检查](../../scripts/check-layout.ps1) 拒绝名单之外的根目录项，接入 [本地检查](../../scripts/check.ps1) 和 [CI](../../.github/workflows/ci.yml)。检查根白名单、必需入口、应用顶层配置归属和现有源码入口；深层业务语义仍需人工审查。

常规开发不新增根目录项。工具确实需要根配置时，先说明发现规则、为什么不能归入应用或已有配置，再同步本文、检查脚本、调用入口。禁止把临时杂物加入白名单绕过检查。

## 7. 当前结构是否合理，以及怎么改进

**评估：三个应用的分工可以沿用，但初始化交付不够扎实，先前解释也不充分。应按具体问题改进，不需要为了外观重建仓库。**

| 现状                                 | 判断                                         | 改进顺序                                              |
| ------------------------------------ | -------------------------------------------- | ----------------------------------------------------- |
| 三端分放 apps                        | 边界明确，适合当前项目                       | 保持，不升级成复杂微服务或多个仓库                    |
| 根目录 14 个文件                     | 大部分有明确用途                             | 本轮逐项解释、明确归属、加白名单检查                  |
| Prettier 配置已收敛                  | 已合并到根 package.json，减少一个文件        | 已同步根白名单，格式检查使用合并后的配置              |
| .nvmrc、engines、CI 都涉及 Node      | 消费者不同，但有同步成本                     | 升级版本同步检查，勿只改一处                          |
| .tools 工具及缓存归档                | 统一 toolchains/ 与 cache/，清理验证后的遗留 | 迁移清单见本次 session，工具目录不提交                |
| Wrapper、Vue 格式规则、Python 依赖锁 | 本轮修复，三端构建和单应用测试已通过         | 真实结果见 EVIDENCE；仍不声称容器部署或远端 CI 已运行 |
| docs 与 .memory 并存                 | 用途不同，需防重复                           | docs 管正式设计，CURRENT 管当前进度，session 管历史   |
| tests 有工程回归，samples 仅有说明   | 已声明职责的预留位置，不代表测试/数据完成    | 有实际内容再细分，不堆空目录                          |

本轮已按用户确认的方案同步整理三端内部职责、在用工具和基础环境；保持应用顶层位置、健康接口与 ASGI 入口，不涉及新业务功能。

## 8. 初学者的日常阅读路线

1. 想知道项目做什么：README → docs/PROJECT_BLUEPRINT.md。
2. 想知道下一步：.memory/CURRENT.md。
3. 找网页看 apps/web/src；业务接口看 apps/server/src/main/java；AI 看 apps/ai-service/src/testops_ai。
4. 新文件放哪先查第 5 节，根目录查第 6 节。
5. 结构检查运行 `./scripts/check-layout.ps1`；记忆检查运行 `./scripts/memory.ps1 -Mode Check`；应用按对应检查命令验证。

不必一次记住全部配置。先理解三端分工、docs/.memory 的区别，以及源码/生成物的区别；用到工具时再查第 4 节。

## 9. 本轮实际源码与工具目录

```text
apps/server/src/main/java/io/testopsai/server/
  TestOpsApplication.java
  platform/security/SecurityConfiguration.java
  # 后续业务归 modules/业务域，按真实需要创建
apps/web/src/
  app/main.ts、App.vue、style.css
  features/health/api.ts、api.test.ts、HealthStatus.vue
apps/ai-service/src/testops_ai/
  main.py                  # 稳定 ASGI 入口
  app.py                   # 组装应用
  core/settings.py         # 运行配置
  features/health/router.py
.tools/
  toolchains/java、node、npm、uv、python
  cache/npm、uv、ruff、mypy
```

Java 测试对应源码包；Vue 请求、组件、测试归同一 feature；Python 服务测试仍在应用 tests/。新增业务代码按域归属，不建全局万能 service/common 收纳目录。scripts/doctor.ps1 检查工具，maven.ps1 提供稳定 Maven 入口，smoke-local.ps1 临时启动三端并在验收后停止自己创建的进程。

## 10. 适度设计规则

目录必须承载真实职责，业务域内先平铺。只有职责明显增多或真实复用出现时才拆分子目录；不为可能的未来创建 modules/shared/service/repository 等空架子，不给单个文件连续套层。当前启动、健康、安全配置分组都有真实实现，本轮不再继续扩张。临时迁移备份只在 .runtime，工具归档不是新的源码层级。

2026-11-17 远程提交准备：用户建立的远程仓库已有 MIT LICENSE；本地保留其原文及初始历史，因此 LICENSE 正式加入根白名单。14 个根源码文件不包括忽略的本机 .env。
